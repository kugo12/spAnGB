package spAnGB.ppu

import spAnGB.memory.RAM
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.mmio.*
import spAnGB.utils.KiB
import spAnGB.utils.bit
import java.nio.ByteBuffer

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
        val c = toInt()
        set(0, c.and(0x1F).shl(3).toByte())
        set(1, c.ushr(5).and(0x1F).shl(3).toByte())
        set(2, c.ushr(10).and(0x1F).shl(3).toByte())
    }

    fun Short.putToBuffer(at: Int) {
        val c = toInt()

        framebuffer.put(at, c.and(0x1F).shl(3).toByte())
        framebuffer.put(at + 1, c.ushr(5).and(0x1F).shl(3).toByte())
        framebuffer.put(at + 2, c.ushr(10).and(0x1F).shl(3).toByte())
    }

    fun putToBuffer(pos: Int, color: ByteArray) {
        framebuffer.put(pos, color[0])
        framebuffer.put(pos + 1, color[1])
        framebuffer.put(pos + 2, color[2])
    }

    fun renderBgMode4() {
        val offset = vcount.ly * 240

        (offset until offset + 240).forEach {
            palette.shortBuffer[vram.byteBuffer[it].toInt() and 0xFF]
                .putToBuffer(it * 3)
        }
    }

    fun renderBgMode3() {
        val offset = vcount.ly * 240

        (offset until offset + 240).forEach {
            vram.shortBuffer[it ushr 1]
                .putToBuffer(it * 3)
        }
    }

    @JvmInline
    value class BackgroundTextTile(val value: Int) {
        inline val tileNumber: Int get() = value and 0x3FF
        inline val horizontalFlip: Boolean get() = value bit 10
        inline val verticalFlip: Boolean get() = value bit 11
        inline val palette: Int get() = value.and(0xF000).ushr(12)
    }

    fun renderBackDrop() {
        val offset = vcount.ly * 240
        val color = palette.shortBuffer[0].toColor()

        for (it in offset until offset + 240) {
            putToBuffer(it * 3, color)
        }
    }

    @JvmInline
    value class SpriteData(val value: Long) {
        init {
            if (value bit 8) TODO("Rotation not supported rn")
        }

        inline val x: Int get() = value.ushr(16).and(0x1FF).toInt()
        inline val y: Int get() = value.and(0xFF).toInt()
        inline val disabled: Boolean get() = !(value bit 8) && value bit 9
        inline val is8Bit: Boolean get() = value bit 13
        inline val horizontalFlip: Boolean get() = value bit 28
        inline val verticalFlip: Boolean get() = value bit 29
        inline val tileNumber: Int get() = value.ushr(32).and(0x3FF).toInt()
        inline val paletteNumber: Int get() = value.ushr(44).and(0xF).toInt()

        inline val shape: Int get() = value.toInt().ushr(14).and(0x3)
        inline val size: Int get() = value.ushr(30).and(0x3).toInt()
    }

    @JvmField  // width to height, [shape][size]
    val spriteSizes: Array<Array<Pair<Int, Int>>> = arrayOf(
        arrayOf(  // square
            8 to 8,
            16 to 16,
            32 to 32,
            64 to 64
        ),
        arrayOf(  // horizontal
            16 to 8,
            32 to 8,
            32 to 16,
            64 to 32
        ),
        arrayOf(  // vertical
            8 to 16,
            8 to 32,
            16 to 32,
            32 to 64
        )
    )

    fun SpriteData.render() {
        val lyc = vcount.ly
        val (width, height) = spriteSizes[shape][size]
        val x = x
        val y = y

        if (is8Bit) {
            TODO("8 bit sprites not implemented rn")
        } else {
            val paletteOffset = 16 * paletteNumber + 0x100
            val rowSize = 4
            val tileSize = rowSize * 8

            val tileRowOffset = 0x10000 +
                    ((lyc - y) % 8) * rowSize +
                    tileNumber * tileSize +
                    ((lyc - y) / 8) * 32 * tileSize
            val screenPixelOffset = (lyc * 240 + x) * 3
            var pixelsLeft = if (x > 240 - width) 240 - x else width
            val pix = pixelsLeft

            while (pixelsLeft > 0) {
                val currentTilePixel = pix - pixelsLeft
                val twoPixels = vram.byteBuffer[
                        tileRowOffset +
                                (currentTilePixel / 2) % rowSize +
                                ((currentTilePixel / 2) / rowSize) * tileSize
                ]
                val firstColor = twoPixels.toInt() and 0xF
                val secondColor = twoPixels.toInt().ushr(4).and(0xF)

                if (firstColor != 0)
                    palette.shortBuffer[firstColor + paletteOffset]
                        .putToBuffer(screenPixelOffset + currentTilePixel * 3)
                pixelsLeft -= 1
                if (pixelsLeft <= 0) return

                if (secondColor != 0)
                    palette.shortBuffer[secondColor + paletteOffset]
                        .putToBuffer(screenPixelOffset + currentTilePixel * 3 + 3)
                pixelsLeft -= 1
            }
        }
    }

    fun SpriteData.shouldBeRendered(): Boolean {
        val height = spriteSizes[shape][size].second
        val lyc = vcount.ly
        val y = y
        val x = x

        return !disabled && lyc >= y && lyc < y+height && x < 240
    }

    fun renderSprites() {
        if (displayControl.isOneDimensionalMapping) TODO("One dimensional mapping not implemented rn")

        val lyc = vcount.ly

        (0 until 128)
            .forEach {
                val sprite = SpriteData(attributes.byteBuffer.getLong(it shl 3))
                if (sprite.shouldBeRendered())
                    sprite.render()
            }
    }

    fun renderBgMode0() {  // BG 0-3
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
                val mapOffset = control.screenBaseBlock * 1 * KiB
                val bgTileSize = if (control.isSinglePalette) 64 else 32
                val rowSize = bgTileSize / 8

                // tiles are 8x8

                val yOffset = (vcount.ly / 8) * 32 // TODO
                val yTileOffset = vcount.ly % 8
                val currentRow = vcount.ly * 240 * 3
                var currentPixel = 0
                while (true) {
                    val tileEntryOffset = yOffset + (currentPixel / 8) + mapOffset
                    val entry = BackgroundTextTile(vram.shortBuffer[tileEntryOffset].toInt())

                    val tileRowOffset = tileMapOffset +
                            entry.tileNumber * bgTileSize +
                            (if (entry.verticalFlip) 7 - yTileOffset else yTileOffset) * rowSize

                    if (control.isSinglePalette) {
                        for (it in (if (entry.horizontalFlip) 7 downTo 0 else 0 until 8)) {
                            val color = vram.byteBuffer[tileRowOffset + it].toInt()

                            if (color != 0)
                                palette.shortBuffer[color]
                                    .putToBuffer(currentRow + currentPixel * 3)
                            currentPixel += 1
                            if (currentPixel >= 240) return
                        }
                    } else {
                        val paletteOffset = 16 * entry.palette

                        for (it in (if (entry.horizontalFlip) 3 downTo 0 else 0 until 4)) {
                            val tilePixels = vram.byteBuffer[tileRowOffset + it].toInt().let {
                                if (entry.horizontalFlip)
                                    ((it ushr 4) and 0xF) or ((it shl 4) and 0xF0)
                                else it
                            }
                            val firstColor = tilePixels.and(0xF)
                            val secondColor = tilePixels.ushr(4).and(0xF)

                            if (firstColor != 0)
                                palette.shortBuffer[firstColor + paletteOffset]
                                    .putToBuffer(currentRow + currentPixel * 3)
                            currentPixel += 1
                            if (currentPixel >= 240) return

                            if (secondColor != 0)
                                palette.shortBuffer[secondColor + paletteOffset]
                                    .putToBuffer(currentRow + currentPixel * 3)
                            currentPixel += 1
                            if (currentPixel >= 240) return
                        }
                    }
                }
            }
    }

    fun checkVCounter() {
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
                renderBackDrop()

                when (displayControl.bgMode) {
                    0 -> renderBgMode0()
                    3 -> renderBgMode3()
                    4 -> renderBgMode4()
                    else -> TODO("Background mode ${displayControl.bgMode} not implemented")
                }

                renderSprites()

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