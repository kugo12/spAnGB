package spAnGB.ppu

import spAnGB.Scheduler
import spAnGB.memory.RAM
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.mmio.*
import spAnGB.utils.KiB
import spAnGB.utils.bit
import spAnGB.utils.uInt
import java.nio.ByteBuffer

const val HDRAW_CYCLES = 960L
const val HBLANK_CYCLES = 272L
const val SCANLINE_CYCLES = HDRAW_CYCLES + HBLANK_CYCLES

const val VBLANK_HEIGHT = 68
const val VDRAW_HEIGHT = 160
const val TOTAL_HEIGHT = VBLANK_HEIGHT + VDRAW_HEIGHT

class PPU(
    framebuffer: ByteBuffer,
    val mmio: MMIO,
    val scheduler: Scheduler
) {
    val framebuffer = framebuffer.asIntBuffer()
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

    val priorityBuffers = Array(4) { IntArray(240) }

    init {
        scheduler.schedule(HDRAW_CYCLES, ::hdraw)
    }

    fun Short.toColor() = toInt().let {
        it.and(0x1F).shl(27)  // R
            .or(it.and(0x3E0).shl(14))  // G
            .or(it.and(0x7C00).shl(1))  // B
            .or(0xFF)  // A
    }

    fun renderBgMode4() {
        val offset = vcount.ly * 240
        val buffer = priorityBuffers[0]

        (0 until 240).forEach {
            val color = palette.shortBuffer[
                    vram.byteBuffer[it + offset].uInt
            ].toColor()

            buffer[it] = color
        }
    }

    fun renderBgMode3() {
        val offset = vcount.ly * 240
        val buffer = priorityBuffers[0]

        (0 until 240).forEach {
            buffer[it] = vram.shortBuffer[it + offset].toColor()
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
        val color = palette.shortBuffer[0].toColor()

        priorityBuffers.forEach {
            it.fill(color)
        }
    }


    fun renderJoinedBuffers() {
        val lyc = vcount.ly * 240
        val backdrop = palette.shortBuffer[0].toColor()

        for (pixel in 0 until 240) {
            val index = lyc + pixel
            for (buffer in priorityBuffers) {
                if (buffer[pixel] != 0) {
                    framebuffer.put(index, buffer[pixel])
                    break
                }
            }

            if (framebuffer[index] == 0) {
                framebuffer.put(index, backdrop)
            }
        }
    }

    @JvmInline
    value class SpriteData(val value: Long) {
        init {
            if (value bit 8) TODO("Rotation not supported rn")
        }

        inline val x: Int get() = value.ushr(9).toShort().toInt().shr(7)
        inline val y: Int get() = value.and(0xFF).toInt().let { if (it > 160) it.shl(24).shr(24) else it }
        inline val disabled: Boolean get() = !(value bit 8) && value bit 9
        inline val is8Bit: Boolean get() = value bit 13
        inline val horizontalFlip: Boolean get() = value bit 28
        inline val verticalFlip: Boolean get() = value bit 29
        inline val tileNumber: Int get() = value.ushr(32).and(0x3FF).toInt()
        inline val paletteNumber: Int get() = value.ushr(44).and(0xF).toInt()
        inline val priority: Int get() = value.ushr(42).and(0x3).toInt()

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
        val buffer = priorityBuffers[priority]
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
            val tilesInRow = 32

            val yOffset = lyc - y

            val tileRowOffset = 0x10000 +
                    tileNumber * tileSize +
                    (if (verticalFlip) 7 - (yOffset % 8) else (yOffset % 8)) * rowSize +
                    (if (verticalFlip) (height - yOffset - 1) / 8 else (yOffset / 8)) * tilesInRow * tileSize
            val screenPixelOffset = if (x > 0) x else 0
            val pixelsLeft = if (x >= 240 - width) 240 - x else if (x < 0) width + x else width
            val pixelsToSkipEnd = if (x >= 240 - width) ((240 - x + width) % 8) else 0
            val pixelsToSkipStart = if (x < 0) -x else 0

            var currentPixel = 0
            while (currentPixel < pixelsLeft) {
                val vramOffset = if (horizontalFlip) {
                    ((pixelsLeft - currentPixel - 1) + pixelsToSkipStart).div(2).let {
                        tileRowOffset + (it / rowSize) * tileSize
                    }
                } else {
                    (currentPixel + pixelsToSkipStart).div(2).let {
                        tileRowOffset + (it / rowSize) * tileSize
                    }
                }

                currentPixel += blit4BitTileRow(
                    buffer,
                    vramOffset,
                    currentPixel + screenPixelOffset,
                    if (x + currentPixel + pixelsToSkipEnd >= 240) 7 - pixelsToSkipEnd else 0,
                    paletteOffset,
                    horizontalFlip,
                    if (currentPixel == 0 && x < 0) 7 - (pixelsToSkipStart % 8) else 0
                )
            }
        }
    }

    fun SpriteData.shouldBeRendered(): Boolean {
        if (shape >= 3) {
            println("Invalid sprite shape size")
            return false
        }

        val (width, height) = spriteSizes[shape][size]
        val lyc = vcount.ly
        val y = y
        val x = x

        return !disabled &&
                lyc >= y &&
                lyc < y + height &&
                x < 240 &&
                x > -width
    }

    private val spriteIterator = 127 downTo 0
    fun renderSprites() {
        if (displayControl.isOneDimensionalMapping) TODO("One dimensional mapping not implemented rn")

        spriteIterator.forEach {
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
            3 to displayControl.isBg3,
            2 to displayControl.isBg2,
            1 to displayControl.isBg1,
            0 to displayControl.isBg0
        )
            .filter { it.second }
            .forEach { (bg, _) ->
                val control = bgControl[bg]
                val buffer = priorityBuffers[control.priority]
                val (bgWidth, bgHeight) = backgroundSizes[control.size]

                val tileMapOffset = control.characterBaseBlock * 16 * KiB
                val mapOffset = control.screenBaseBlock * 1 * KiB
                val bgYOffset = bgYOffset[bg].offset
                val bgXOffset = bgXOffset[bg].offset

                // tiles are 8x8
                val screenRowOffset = vcount.ly * 240

                var currentPixel = 0

                if (control.isSinglePalette) {
                    TODO()
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
                            buffer,
                            tileRowOffset,
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
        buffer: IntArray,
        tileRowOffset: Int,
        currentPixelX: Int,
        pixelsToSkipEnd: Int,
        paletteOffset: Int,
        flip: Boolean,
        pixelsToSkipStart: Int = 0
    ): Int {
        val pixelsFromRowToRender = if (7 - pixelsToSkipEnd + currentPixelX >= 240)
            239 - currentPixelX else 7 - pixelsToSkipEnd

        var x = 0

        if (flip) {
            var pix = pixelsFromRowToRender
            while (pix >= pixelsToSkipStart) {
                blit4BitPixel(
                    buffer,
                    tileRowOffset + pix / 2,
                    paletteOffset,
                    pix,
                    x + currentPixelX
                )

                x += 1
                pix -= 1
            }
        } else {
            var pix = pixelsToSkipStart
            while (pix <= pixelsFromRowToRender) {
                blit4BitPixel(
                    buffer,
                    tileRowOffset + pix / 2,
                    paletteOffset,
                    pix,
                    x + currentPixelX
                )

                x += 1
                pix += 1
            }
        }

        return x
    }

    fun blit4BitPixel(buffer: IntArray, vramOffset: Int, paletteOffset: Int, pixel: Int, pixelInBuffer: Int) {
        val color = vram.byteBuffer[vramOffset].toInt().let {
            if (pixel bit 0) {
                it.ushr(4).and(0xF)
            } else {
                it and 0xF
            }
        }

        if (color != 0)
            buffer[pixelInBuffer] = palette.shortBuffer[color + paletteOffset].toColor()
    }


    fun checkVCounter() {
        val vc = vcount.ly == displayStat.lyc

        if (vc && displayStat[DisplayStatFlag.VCOUNTER_IRQ] && !displayStat[DisplayStatFlag.VCOUNTER]) {
            mmio.ir[Interrupt.VCount] = true
        }

        displayStat[DisplayStatFlag.VCOUNTER] = vc
    }

    fun hdraw(taskIndex: Int) {
        priorityBuffers.forEach {
            it.fill(0)
        }

        when (displayControl.bgMode) {
            0 -> renderBgMode0()
            3 -> renderBgMode3()
            4 -> renderBgMode4()
            else -> TODO("Background mode ${displayControl.bgMode} not implemented")
        }

        renderSprites()
        renderJoinedBuffers()

        scheduler.schedule(HBLANK_CYCLES, ::hblank, taskIndex)

        displayStat[DisplayStatFlag.HBLANK] = true
        if (displayStat[DisplayStatFlag.HBLANK_IRQ]) {
            mmio.ir[Interrupt.HBlank] = true
        }
    }

    fun hblank(taskIndex: Int) {
        vcount.ly += 1
        checkVCounter()

        if (vcount.ly >= VDRAW_HEIGHT) {
            scheduler.schedule(SCANLINE_CYCLES, ::vblank, taskIndex)

            displayStat[DisplayStatFlag.HBLANK] = false
            displayStat[DisplayStatFlag.VBLANK] = true

            if (displayStat[DisplayStatFlag.VBLANK_IRQ]) {
                mmio.ir[Interrupt.VBlank] = true
            }
        } else {
            scheduler.schedule(HDRAW_CYCLES, ::hdraw, taskIndex)
        }
    }

    fun vblank(taskIndex: Int) {
        vcount.ly += 1

        if (vcount.ly >= TOTAL_HEIGHT) {
            scheduler.schedule(HDRAW_CYCLES, ::hdraw, taskIndex)

            displayStat[DisplayStatFlag.VBLANK] = false
            vcount.ly = 0
        } else {
            scheduler.schedule(SCANLINE_CYCLES, ::vblank, taskIndex)
        }

        checkVCounter()
    }
}