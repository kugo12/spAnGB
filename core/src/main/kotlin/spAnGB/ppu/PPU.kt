package spAnGB.ppu

import spAnGB.Scheduler
import spAnGB.memory.RAM
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.bg.renderBgMode0
import spAnGB.ppu.mmio.*
import spAnGB.utils.KiB
import spAnGB.utils.bit
import spAnGB.utils.toColor
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

    val lineBuffers = Array(5) { IntArray(240) }

    val hdrawRef = ::hdraw
    val hblankRef = ::hblank
    val vblankRef = ::vblank

    init {
        scheduler.schedule(HDRAW_CYCLES, hdrawRef)
    }


    fun renderBgMode4() {
        val offset = vcount.ly * 240
        val buffer = lineBuffers[0]

        for (it in 0 until 240) {
            buffer[it] = palette.shortBuffer[
                    vram.byteBuffer[it + offset].uInt
            ].toColor()
        }
    }

    fun renderBgMode3() {
        val offset = vcount.ly * 240
        val buffer = lineBuffers[0]

        for (it in 0 until 240) {
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

    fun renderJoinedBuffers() {
        val backdrop = palette.shortBuffer[0].toColor()
        val finalBuffer = lineBuffers[4]

        for (pixel in 0 until 240) {
            for (buffer in lineBuffers) {
                if (buffer[pixel] != 0) {
                    finalBuffer[pixel] = buffer[pixel]
                    break
                }
            }

            if (finalBuffer[pixel] == 0) {
                finalBuffer[pixel] = backdrop
            }
        }

        framebuffer.put(vcount.ly * 240, finalBuffer, 0, 240)
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
        val buffer = lineBuffers[priority]
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

    fun renderSprites() {
        if (displayControl.isOneDimensionalMapping) TODO("One dimensional mapping not implemented rn")

        for (it in 127 downTo 0) {
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

    fun clearLineBuffers() {
        for (it in lineBuffers) it.fill(0)
    }


    fun checkVCounter() {
        val vc = vcount.ly == displayStat.lyc

        if (vc && displayStat[DisplayStatFlag.VCOUNTER_IRQ] && !displayStat[DisplayStatFlag.VCOUNTER]) {
            mmio.ir[Interrupt.VCount] = true
        }

        displayStat[DisplayStatFlag.VCOUNTER] = vc
    }

    fun hdraw(taskIndex: Int) {
        clearLineBuffers()

        when (displayControl.bgMode) {
            0 -> renderBgMode0()
            3 -> renderBgMode3()
            4 -> renderBgMode4()
            else -> TODO("Background mode ${displayControl.bgMode} not implemented")
        }

        renderSprites()
        renderJoinedBuffers()

        scheduler.schedule(HBLANK_CYCLES, hblankRef, taskIndex)

        displayStat[DisplayStatFlag.HBLANK] = true
        if (displayStat[DisplayStatFlag.HBLANK_IRQ]) {
            mmio.ir[Interrupt.HBlank] = true
        }
    }

    fun hblank(taskIndex: Int) {
        vcount.ly += 1
        checkVCounter()

        if (vcount.ly >= VDRAW_HEIGHT) {
            scheduler.schedule(SCANLINE_CYCLES, vblankRef, taskIndex)

            displayStat[DisplayStatFlag.HBLANK] = false
            displayStat[DisplayStatFlag.VBLANK] = true

            if (displayStat[DisplayStatFlag.VBLANK_IRQ]) {
                mmio.ir[Interrupt.VBlank] = true
            }
        } else {
            scheduler.schedule(HDRAW_CYCLES, hdrawRef, taskIndex)
        }
    }

    fun vblank(taskIndex: Int) {
        vcount.ly += 1

        if (vcount.ly >= TOTAL_HEIGHT) {
            scheduler.schedule(HDRAW_CYCLES, hdrawRef, taskIndex)

            displayStat[DisplayStatFlag.VBLANK] = false
            vcount.ly = 0
        } else {
            scheduler.schedule(SCANLINE_CYCLES, vblankRef, taskIndex)
        }

        checkVCounter()
    }
}