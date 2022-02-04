package spAnGB.ppu

import spAnGB.Scheduler
import spAnGB.memory.RAM
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.mmio.*
import spAnGB.utils.KiB
import spAnGB.utils.bit
import spAnGB.utils.toInt
import java.nio.ByteBuffer

const val HDRAW_CYCLES = 960L
const val HBLANK_CYCLES = 272L
const val SCANLINE_CYCLES = HDRAW_CYCLES + HBLANK_CYCLES

const val VBLANK_HEIGHT = 68
const val VDRAW_HEIGHT = 160
const val TOTAL_HEIGHT = VBLANK_HEIGHT + VDRAW_HEIGHT

class PPU(
    val framebuffer: ByteBuffer,
    val mmio: MMIO,
    val scheduler: Scheduler
) {
    val palette = RAM(1 * KiB)
    val vram = VRAM()
    val attributes = RAM(1 * KiB)
    val sprites = attributes.byteBuffer.asLongBuffer()

    val displayControl = DisplayControl()
    val displayStat = DisplayStat()
    val vcount = VCount()
    val bgControl = Array(4) { BackgroundControl() }
    val bgXOffset = Array(4) { BackgroundOffset() }
    val bgYOffset = Array(4) { BackgroundOffset() }

    init {
        scheduler.schedule(HDRAW_CYCLES, ::hdraw)
    }

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

        inline val x: Int get() = value.ushr(9).toShort().toInt().shr(7)
        inline val y: Int get() = value.and(0xFF).toByte().toInt()
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

            val yOffset = lyc - y

            val tileRowOffset = 0x10000 +
                    (if (verticalFlip) 7 - (yOffset % 8) else (yOffset % 8)) * rowSize +
                    tileNumber * tileSize +
                    (if (verticalFlip) (height - yOffset)/8 else (yOffset / 8)) * 32 * tileSize
            val screenPixelOffset = if (x > 0) (lyc * 240 + x) * 3 else lyc * 240 * 3
            val pixelsLeft = if (x > 240 - width) 240 - x else if (x < 0) width + x else width
            val pixelsToSkipEnd = if (x > 240 - width) (240 - x) % 8 else 0
            val pixelsToSkipStart = if (x < 0) -x else 0

            var currentPixel = 0
            while (currentPixel < pixelsLeft) {
                val vramOffset = if (horizontalFlip) {
                    ((pixelsLeft - currentPixel - 1) + pixelsToSkipStart).div(2).let {
                        tileRowOffset +
//                            (it % rowSize) +
                                (it / rowSize) * tileSize
                    }
                } else {
                    (currentPixel + pixelsToSkipStart).div(2).let {
                        tileRowOffset +
//                            (it % rowSize) +
                                (it / rowSize) * tileSize
                    }
                }

                currentPixel += blit4BitTileRow(
                    vramOffset,
                    screenPixelOffset,
                    currentPixel,
                    if (x + currentPixel + pixelsToSkipEnd >= 240) pixelsToSkipEnd else 0,
                    paletteOffset,
                    horizontalFlip,
                    if (currentPixel == 0 && x < 0) 7 - (pixelsToSkipStart % 8) else 0
                )
            }
        }
    }

    fun SpriteData.shouldBeRendered(): Boolean {
        if (shape >= 3) return false

        val (width, height) = spriteSizes[shape][size]
        val lyc = vcount.ly
        val y = y
        val x = x

        return !disabled && lyc >= y && lyc < y + height && x < 240 && x > -width
    }

    fun renderSprites() {
        if (displayControl.isOneDimensionalMapping) TODO("One dimensional mapping not implemented rn")

        (127 downTo 0).forEach {
            SpriteData(sprites[it]).apply {
                if (shouldBeRendered()) render()
            }
        }
    }

    @JvmField  // WxH
    val backgroundSizes = listOf(
        256 to 256,
        512 to 256,
        256 to 512,
        512 to 512,
    )

    fun renderBgMode0() {  // BG 0-3
        listOf(
            0 to displayControl.isBg0,
            1 to displayControl.isBg1,
            2 to displayControl.isBg2,
            3 to displayControl.isBg3
        )
            .filter { it.second }
            .sortedByDescending { bgControl[it.first].priority }
            .forEach { (bg, _) ->
                val control = bgControl[bg]
                val (bgWidth, bgHeight) = backgroundSizes[control.size]

                val tileMapOffset = control.characterBaseBlock * 16 * KiB
                val mapOffset = control.screenBaseBlock * 1 * KiB
                val bgYOffset = bgYOffset[bg].offset
                val bgXOffset = bgXOffset[bg].offset

                // tiles are 8x8
                val screenRowOffset = vcount.ly * 240 * 3

                var currentPixel = 0

                if (control.isSinglePalette) {
                    val yOffset = (vcount.ly / 8) * 32 // TODO
                    val yTileOffset = vcount.ly % 8
                    val bgTileSize = 64
                    val rowSize = 8

                    while (true) {
                        val tileEntryOffset = yOffset + (currentPixel / 8) + mapOffset
                        val entry = BackgroundTextTile(vram.shortBuffer[tileEntryOffset].toInt())

                        val tileRowOffset = tileMapOffset +
                                entry.tileNumber * bgTileSize +
                                (if (entry.verticalFlip) 7 - yTileOffset else yTileOffset) * rowSize

                        for (it in (if (entry.horizontalFlip) 7 downTo 0 else 0 until 8)) {
                            val color = vram.byteBuffer[tileRowOffset + it].toInt()

                            if (color != 0)
                                palette.shortBuffer[color]
                                    .putToBuffer(screenRowOffset + currentPixel * 3)
                            currentPixel += 1
                            if (currentPixel >= 240) return@forEach
                        }

                        TODO()
                    }
                } else {
                    val bgTileSize = 32
                    val rowSize = 4
                    val yTileOffset = (((vcount.ly + bgYOffset) % 256) / 8) * 32

                    val yOffset = (vcount.ly + bgYOffset) % 8

                    while (currentPixel < 240) {
                        val xTileOffset =
                            (((currentPixel + bgXOffset) / 256) % 2) * 32 * 32 + ((currentPixel + bgXOffset) % 256) / 8

                        val entry = BackgroundTextTile(
                            vram.shortBuffer[xTileOffset + yTileOffset + mapOffset].toInt()
                        )
                        val xOffset = bgXOffset % 8

                        val tileRowOffset = tileMapOffset +
                                entry.tileNumber * bgTileSize +
                                (if (entry.verticalFlip) 7 - yOffset else yOffset) * rowSize

                        val paletteOffset = 16 * entry.palette

                        currentPixel += blit4BitTileRow(
                            tileRowOffset,
                            screenRowOffset,
                            currentPixel,
                            if (currentPixel + xOffset >= 240) 7 - xOffset else 0,
                            paletteOffset,
                            entry.horizontalFlip,
                            if (currentPixel == 0) xOffset else 0
                        )
                    }
                }
            }
    }

    fun blit4BitTileRow(
        tileRowOffset: Int,
        screenOffset: Int,
        currentPixelX: Int,
        pixelsToSkipEnd: Int,
        paletteOffset: Int,
        flip: Boolean,
        pixelsToSkipStart: Int = 0
    ): Int {
        val pixelsFromRowToRender = if (7 - pixelsToSkipEnd + currentPixelX >= 240)
            239 - currentPixelX else 7 - pixelsToSkipEnd

        val iter = if (flip)
            pixelsFromRowToRender downTo pixelsToSkipStart
        else
            pixelsToSkipStart..pixelsFromRowToRender


        var x = 0
        for (pix in iter) {
            val aaaa = (pix + pixelsFromRowToRender) and 1 != 0
            val color = vram.byteBuffer[tileRowOffset + pix / 2].toInt().let {
                if ((flip && !aaaa) || (!aaaa && !flip)) {
                    it.ushr(4).and(0xF)
                } else {
                    it and 0xF
                }
            }

            if (color != 0)
                palette.shortBuffer[color + paletteOffset]
                    .putToBuffer(screenOffset + (x + currentPixelX) * 3)

            x += 1
        }

        return x
    }

    fun checkVCounter() {
        val vc = vcount.ly == displayStat.lyc

        if (vc && displayStat[DisplayStatFlag.VCOUNTER_IRQ] && !displayStat[DisplayStatFlag.VCOUNTER]) {
            mmio.ir[Interrupt.VCount] = true
        }

        displayStat[DisplayStatFlag.VCOUNTER] = vc
    }

    fun hdraw() {
        renderBackDrop()

        when (displayControl.bgMode) {
            0 -> renderBgMode0()
            3 -> renderBgMode3()
            4 -> renderBgMode4()
            else -> TODO("Background mode ${displayControl.bgMode} not implemented")
        }

        renderSprites()

        scheduler.schedule(HBLANK_CYCLES, ::hblank)

        displayStat[DisplayStatFlag.HBLANK] = true
        if (displayStat[DisplayStatFlag.HBLANK_IRQ]) {
            mmio.ir[Interrupt.HBlank] = true
        }
    }

    fun hblank() {
        vcount.ly += 1
        checkVCounter()

        if (vcount.ly >= VDRAW_HEIGHT) {
            scheduler.schedule(SCANLINE_CYCLES, ::vblank)

            displayStat[DisplayStatFlag.HBLANK] = false
            displayStat[DisplayStatFlag.VBLANK] = true

            if (displayStat[DisplayStatFlag.VBLANK_IRQ]) {
                mmio.ir[Interrupt.VBlank] = true
            }
        } else {
            scheduler.schedule(HDRAW_CYCLES, ::hdraw)
        }
    }

    fun vblank() {
        vcount.ly += 1

        if (vcount.ly >= TOTAL_HEIGHT) {
            scheduler.schedule(HDRAW_CYCLES, ::hdraw)

            displayStat[DisplayStatFlag.VBLANK] = false
            vcount.ly = 0
        } else {
            scheduler.schedule(SCANLINE_CYCLES, ::vblank)
        }

        checkVCounter()
    }
}