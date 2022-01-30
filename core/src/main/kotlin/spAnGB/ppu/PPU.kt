package spAnGB.ppu

import spAnGB.memory.RAM
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.mmio.*
import spAnGB.utils.KiB
import spAnGB.utils.bit
import java.nio.ByteBuffer
import kotlin.experimental.and

const val HDRAW_CYCLES = 960
const val HBLANK_CYCLES = 272
const val SCANLINE_CYCLES = HDRAW_CYCLES + HBLANK_CYCLES

const val VBLANK_HEIGHT = 68
const val VDRAW_HEIGHT = 160
const val TOTAL_HEIGHT = VBLANK_HEIGHT + VDRAW_HEIGHT

class PPU(
    val framebuffer: ByteBuffer,
    val mmio: MMIO
) {
    val palette = RAM(1 * KiB)
    val vram = VRAM()
    val attributes = RAM(1 * KiB)

    val displayControl = DisplayControl()
    val displayStat = DisplayStat()
    val vcount = VCount()
    val bgControl = Array(4) { BackgroundControl() }
    val bgXOffset = Array(4) { BackgroundOffset() }
    val bgYOffset = Array(4) { BackgroundOffset() }

    enum class PPUState { HDraw, HBlank, VBlank }

    var state = PPUState.HDraw
    var cyclesLeft = HDRAW_CYCLES

    fun Short.toColor() = ByteArray(3).apply {
        val c = toUShort().toInt()
        set(0, c.and(0x1F).shl(3).toByte())
        set(1, c.ushr(5).and(0x1F).shl(3).toByte())
        set(2, c.ushr(10).and(0x1F).shl(3).toByte())
    }

    fun getBgColorFromPalette(color: Byte): ByteArray {
        val index = color.toUByte().toInt()*2
        val color = palette.content.getShort(index)

        return color.toColor()
    }

    fun renderBgMode4() {
        val offset = vcount.ly*240

        (offset until offset+240).forEach {
            framebuffer.put(
                it * 3,
                getBgColorFromPalette(vram.content[it])
            )
        }
    }

    fun renderBgMode3() {
        val offset = vcount.ly*480

        (offset until offset + 480 step 2).forEach {
            framebuffer.put(
                (it / 2) * 3,
                vram.content.getShort(it).toColor()
            )
        }
    }

    @JvmInline
    value class BackgroundTextTile(val value: Int) {
        inline val tileNumber: Int get() = value and 0x3FF
        inline val horizontalFlip: Boolean get() = value bit 10
        inline val verticalFlip: Boolean get() = value bit 11
        inline val palette: Int get() = value.and(0xF000).ushr(12)
    }

    fun renderBgMode0() {  // BG 0-3
//        06000000-0600FFFF  64 KBytes shared for BG Map and Tiles
//        06010000-06017FFF  32 KBytes OBJ Tiles

//        Item        Depth     Required Memory
//        One Tile    4bit      20h bytes
//        One Tile    8bit      40h bytes
//        1024 Tiles  4bit      8000h (32K)
//        1024 Tiles  8bit      10000h (64K) - excluding some bytes for BG map
//        BG Map      32x32     800h (2K)
//        BG Map      64x64     2000h (8K)

//        The tiles may have 4bit or 8bit color depth, minimum map size is 32x32 tiles, maximum is 64x64 tiles, up to 1024 tiles can be used per map.

        listOf(
            0 to displayControl.isBg0,
            1 to displayControl.isBg1,
            2 to displayControl.isBg2,
            3 to displayControl.isBg3
        )
            .filter { it.second }
            .sortedBy { bgControl[it.first].priority }
            .forEach { (bg, _) ->
                val control = bgControl[bg]

                val tileMapOffset = control.characterBaseBlock * 16 * KiB
                val mapOffset = control.screenBaseBlock * 2 * KiB
                val bgTileSize = if (control.isSinglePalette) 64 else 32
                val rowSize = bgTileSize/8

                // tiles are 8x8

                val yOffset = (vcount.ly / 8) * 64  // TODO
                val yTileOffset = vcount.ly % 8
                val currentRow = vcount.ly*240*3
                var currentPixel = 0
                while (true) {
                    val tileEntryOffset = yOffset + (currentPixel/8)*2 + mapOffset
                    val entry = BackgroundTextTile(vram.content.getShort(tileEntryOffset).toInt())

                    val tileRowOffset = tileMapOffset + entry.tileNumber*bgTileSize + yTileOffset*rowSize

                    if (control.isSinglePalette) {
                        for (it in 0 until 8) {
                            framebuffer.put(
                                currentRow + currentPixel * 3,
                                getBgColorFromPalette(vram.content[tileRowOffset + it])
                            )
                            currentPixel += 1
                            if (currentPixel >= 240) return
                        }
                    } else {
                        val paletteOffset = 16*entry.palette

                        for (it in 0 until 4) {
                            val tilePixels = vram.content[tileRowOffset + it]

                            framebuffer.put(
                                currentRow + currentPixel * 3,
                                getBgColorFromPalette(tilePixels.and(0xF).plus(paletteOffset).toByte())
                            )
                            currentPixel += 1
                            if (currentPixel >= 240) return

                            framebuffer.put(
                                currentRow + currentPixel * 3,
                                getBgColorFromPalette(tilePixels.toUByte().toInt().ushr(4).and(0xF).plus(paletteOffset).toByte())
                            )
                            currentPixel += 1
                            if (currentPixel >= 240) return
                        }
                    }
                }
            }
    }

    private fun checkVCounter() {
        val vc = vcount.ly == displayStat.lyc

        if (vc && displayStat[DisplayStatFlag.VCOUNTER_IRQ] && !displayStat[DisplayStatFlag.VCOUNTER]) {
            mmio.ir[Interrupt.VCount] = true
        }

        displayStat[DisplayStatFlag.VCOUNTER] = vc
    }

    fun tick() {
        if (cyclesLeft > 0) {
            cyclesLeft -= 1
            return
        }

        when (state) {
            PPUState.HDraw -> {
                when (displayControl.bgMode) {
                    0 -> renderBgMode0()
                    3 -> renderBgMode3()
                    4 -> renderBgMode4()
                    else -> TODO("Background mode ${displayControl.bgMode} not implemented")
                }

                state = PPUState.HBlank
                cyclesLeft = HBLANK_CYCLES

                displayStat[DisplayStatFlag.HBLANK] = true
                if (displayStat[DisplayStatFlag.HBLANK_IRQ]) {
                    mmio.ir[Interrupt.HBlank] = true
                }
            }
            PPUState.HBlank -> {
                vcount.ly += 1
                checkVCounter()

                if (vcount.ly >= VDRAW_HEIGHT) {
                    state = PPUState.VBlank
                    cyclesLeft = SCANLINE_CYCLES

                    displayStat[DisplayStatFlag.HBLANK] = false
                    displayStat[DisplayStatFlag.VBLANK] = true

                    if (displayStat[DisplayStatFlag.VBLANK_IRQ]) {
                        mmio.ir[Interrupt.VBlank] = true
                    }
                } else {
                    state = PPUState.HDraw
                    cyclesLeft = HDRAW_CYCLES
                }
            }
            PPUState.VBlank -> {
                vcount.ly += 1

                if (vcount.ly >= TOTAL_HEIGHT) {
                    state = PPUState.HDraw
                    cyclesLeft = HDRAW_CYCLES

                    displayStat[DisplayStatFlag.VBLANK] = false
                    vcount.ly = 0
                    checkVCounter()
                } else {
                    cyclesLeft = SCANLINE_CYCLES
                    checkVCounter()
                }
            }
        }
    }
}