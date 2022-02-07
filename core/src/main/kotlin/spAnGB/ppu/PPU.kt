package spAnGB.ppu

import spAnGB.Scheduler
import spAnGB.memory.RAM
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.bg.renderBgMode0
import spAnGB.ppu.bg.renderBgMode1
import spAnGB.ppu.bg.renderBgMode3
import spAnGB.ppu.bg.renderBgMode4
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

private const val LUT_OBJ = 4
private const val LUT_SPECIAL = 5

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

    val windowHorizontal = Array(2) { WindowDimension() }
    val windowVertical = Array(2) { WindowDimension() }
    val winIn = WindowInsideControl()
    val winOut = WindowOutsideControl()

    // Bg - 0-3  Sprites - 4-7  Obj window - 8  Final - 9
    val lineBuffers = Array(10) { IntArray(240) }

    val hdrawRef = ::hdraw
    val hblankRef = ::hblank
    val vblankRef = ::vblank

    init {
        scheduler.schedule(HDRAW_CYCLES, hdrawRef)
    }

    fun getCurrentWindow(x: Int): Int {
        val lyc = vcount.ly

        val wy0 = windowVertical[0]
        val wx0 = windowHorizontal[0]
        if (
            displayControl.isWin0 &&
            wy0.top <= lyc && lyc <= wy0.bottom &&
            wx0.left <= x && x <= wx0.right
        ) return 0

        val wy1 = windowVertical[1]
        val wx1 = windowHorizontal[1]
        if (
            displayControl.isWin1 &&
            wy1.top <= lyc && lyc <= wy1.bottom &&
            wx1.left <= x && x <= wx1.right
        ) return 1

        if (displayControl.isWinObj && lineBuffers[8][x] != 0) return 3

        return 2
    }

    @JvmField
    val mixingIsEnabled = Array(4) { BooleanArray(6) }

    fun fillWindowLut() {  // FIXME: cursed
        mixingIsEnabled[0][0] = winIn.isWin0Bg0
        mixingIsEnabled[0][1] = winIn.isWin0Bg1
        mixingIsEnabled[0][2] = winIn.isWin0Bg2
        mixingIsEnabled[0][3] = winIn.isWin0Bg3
        mixingIsEnabled[0][LUT_OBJ] = winIn.isWin0Obj
        mixingIsEnabled[0][LUT_SPECIAL] = winIn.isWin0SpecialEffects

        mixingIsEnabled[1][0] = winIn.isWin1Bg0
        mixingIsEnabled[1][1] = winIn.isWin1Bg1
        mixingIsEnabled[1][2] = winIn.isWin1Bg2
        mixingIsEnabled[1][3] = winIn.isWin1Bg3
        mixingIsEnabled[1][LUT_OBJ] = winIn.isWin1Obj
        mixingIsEnabled[1][LUT_SPECIAL] = winIn.isWin1SpecialEffects

        mixingIsEnabled[2][0] = winOut.isBg0
        mixingIsEnabled[2][1] = winOut.isBg1
        mixingIsEnabled[2][2] = winOut.isBg2
        mixingIsEnabled[2][3] = winOut.isBg3
        mixingIsEnabled[2][LUT_OBJ] = winOut.isObj
        mixingIsEnabled[2][LUT_SPECIAL] = winOut.isSpecialEffects

        mixingIsEnabled[3][0] = winOut.isObjWinBg0
        mixingIsEnabled[3][1] = winOut.isObjWinBg1
        mixingIsEnabled[3][2] = winOut.isObjWinBg2
        mixingIsEnabled[3][3] = winOut.isObjWinBg3
        mixingIsEnabled[3][LUT_OBJ] = winOut.isObjWinObj
        mixingIsEnabled[3][LUT_SPECIAL] = winOut.isObjWinSpecialEffects
    }

    fun renderMixedBuffers() {
        val backdrop = palette.shortBuffer[0].toColor()
        val finalBuffer = lineBuffers[9]
        fillWindowLut()

        for (pixel in 0 until 240) {
            mixBuffers(finalBuffer, backdrop, pixel)
        }

        framebuffer.put(vcount.ly * 240, finalBuffer, 0, 240)
    }

    fun mixBuffers(into: IntArray, backdrop: Int, pixel: Int) {
        val isEnabled = mixingIsEnabled[getCurrentWindow(pixel)]

        var value = 0
        for (bufferIndex in 0 until 4) {
            if (isEnabled[bufferIndex] && lineBuffers[bufferIndex][pixel] != 0) {
                value = if (isEnabled[LUT_OBJ] && lineBuffers[bufferIndex + 4][pixel] != 0) {
                    lineBuffers[bufferIndex + 4][pixel]
                } else {
                    lineBuffers[bufferIndex][pixel]
                }

                break
            } else if (isEnabled[LUT_OBJ] && lineBuffers[bufferIndex + 4][pixel] != 0) {
                value = lineBuffers[bufferIndex + 4][pixel]
                break
            }
        }

        if (value == 0) {
            value = backdrop
        }

        into[pixel] = value
    }

    @JvmField  // WxH
    val backgroundSizes = listOf(
        256 to 256,
        512 to 256,
        256 to 512,
        512 to 512,
    )

    fun blit8BitTileRow(
        buffer: IntArray,
        tileRowOffset: Int,
        currentPixelX: Int,
        pixelsToSkipEnd: Int,
        flip: Boolean,
        pixelsToSkipStart: Int = 0
    ): Int {
        val pixelsFromRowToRender = if (7 - pixelsToSkipEnd + currentPixelX >= 240)
            239 - currentPixelX else 7 - pixelsToSkipEnd

        var x = 0

        if (flip) {
            for (it in pixelsFromRowToRender downTo pixelsToSkipStart) {
                blit8BitPixel(
                    buffer,
                    tileRowOffset + it,
                    x + currentPixelX
                )

                x += 1
            }
        } else {
            for (it in pixelsToSkipStart..pixelsFromRowToRender) {
                blit8BitPixel(
                    buffer,
                    tileRowOffset + it,
                    x + currentPixelX
                )

                x += 1
            }
        }

        return x
    }

    fun blit8BitPixel(buffer: IntArray, vramOffset: Int, pixelInBuffer: Int) {
        val color = vram.byteBuffer[vramOffset].uInt

        if (color != 0)
            buffer[pixelInBuffer] = palette.shortBuffer[color].toColor()
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
            for (it in pixelsFromRowToRender downTo pixelsToSkipStart) {
                blit4BitPixel(
                    buffer,
                    tileRowOffset + it / 2,
                    paletteOffset,
                    it,
                    x + currentPixelX
                )

                x += 1
            }
        } else {
            for (it in pixelsToSkipStart..pixelsFromRowToRender) {
                blit4BitPixel(
                    buffer,
                    tileRowOffset + it / 2,
                    paletteOffset,
                    it,
                    x + currentPixelX
                )

                x += 1
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
            1 -> renderBgMode1()
            3 -> renderBgMode3()
            4 -> renderBgMode4()
            else -> TODO("Background mode ${displayControl.bgMode} not implemented")
        }

        renderSprites()
        renderMixedBuffers()

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