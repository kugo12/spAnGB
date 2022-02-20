package spAnGB.ppu

import spAnGB.Scheduler
import spAnGB.memory.dma.DMAManager
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.MMIO
import spAnGB.memory.ram.Palette
import spAnGB.memory.ram.OAM
import spAnGB.memory.ram.VRAM
import spAnGB.ppu.bg.*
import spAnGB.ppu.mmio.*
import spAnGB.ppu.mmio.bg.BackgroundControl
import spAnGB.ppu.mmio.bg.BackgroundCoordinate
import spAnGB.ppu.mmio.bg.BackgroundOffset
import spAnGB.ppu.mmio.bg.BackgroundParameter
import spAnGB.ppu.mmio.blend.BlendAlpha
import spAnGB.ppu.mmio.blend.BlendBrightness
import spAnGB.ppu.mmio.blend.BlendControl
import spAnGB.ppu.mmio.win.WindowDimension
import spAnGB.ppu.mmio.win.WindowInsideControl
import spAnGB.ppu.mmio.win.WindowOutsideControl
import spAnGB.utils.KiB
import spAnGB.utils.bit
import spAnGB.utils.uInt
import java.nio.ByteBuffer

// TODO: BIG REFACTORING

const val HDRAW_CYCLES = 1006L
const val HBLANK_CYCLES = 226L
const val SCANLINE_CYCLES = HDRAW_CYCLES + HBLANK_CYCLES

const val VBLANK_HEIGHT = 68
const val VDRAW_HEIGHT = 160
const val TOTAL_HEIGHT = VBLANK_HEIGHT + VDRAW_HEIGHT

private const val LUT_OBJ = 4
private const val LUT_SPECIAL = 5

class PPU(
    framebuffer: ByteBuffer,
    val blitFramebuffer: () -> Unit,
    val mmio: MMIO,
    val scheduler: Scheduler,
    val dmaManager: DMAManager
) {
    val displayControl = DisplayControl()
    val displayStat = DisplayStat()
    val vcount = VCount()

    val palette = Palette()
    val vram = VRAM(displayControl)
    val attributes = OAM()

    val bgControl = Array(4) { BackgroundControl(it) }
    val bgXOffset = Array(4) { BackgroundOffset() }
    val bgYOffset = Array(4) { BackgroundOffset() }
    val bgMatrix = Array(8) { BackgroundParameter() }

    // 2x 2y 3x 3y
    val bgReference = Array(4) { BackgroundCoordinate() }

    val windowHorizontal = Array(2) { WindowDimension() }
    val windowVertical = Array(2) { WindowDimension() }
    val winIn = WindowInsideControl()
    val winOut = WindowOutsideControl()

    val mosaic = MosaicControl()
    val alpha = BlendAlpha()
    val blend = BlendControl()
    val brightness = BlendBrightness()

    // Bg - 0-3  Sprites - 4-7  Obj window - 8
    val lineBuffers = Array(9) { IntArray(240) }  // TODO
    val finalBuffer = IntArray(240)

    val joinedSprites = IntArray(240)
    val spritePriorities = IntArray(240)

    val framebuffer = framebuffer.asIntBuffer()
    val sprites = attributes.byteBuffer.asLongBuffer()

    val hdrawRef = ::hdraw
    val hblankRef = ::hblank
    val vblankRef = ::vblank
    val vblhbl = ::hblankInVblank

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

    fun joinSprites() {  // FIXME: this is a quick fix for an issue that needs more code refactoring in future
        for (pixel in 0 until 240) {
            for (it in 4 until 8) {
                if (lineBuffers[it][pixel] != 0) {
                    joinedSprites[pixel] = lineBuffers[it][pixel]
                    spritePriorities[pixel] = it - 4
                    break
                }
            }
        }
    }

    fun shouldBlend(bg: Int, isBd: Boolean, isObj: Boolean): Boolean { // FIXME
        return (bg != -1 && blend isFirstBg bg) || (isBd && blend.firstBd) || (isObj && blend.firstObj)
    }

    fun Int.brightnessBlend(shouldBlend: Boolean) = // TODO: alpha blending needs more refactoring
        if ((blend.mode == 2 || blend.mode == 3) && shouldBlend) {
            val coefficient = brightness.coefficient
            when (blend.mode) {
                2 -> {
                    transformColors { it + ((31 - it) * coefficient).ushr(4) }
                }
                3 -> {
                    transformColors { it - (it * coefficient).ushr(4) }
                }
                else -> -1
            }
        } else this

    fun renderMixedBuffers() {
        joinSprites()

        val sortedBackgrounds = bgControl
            .filter { displayControl isBg it.index }
            .sortedBy { it.priority }
            .toTypedArray()
        val backdrop = palette.shortBuffer[0].toInt().brightnessBlend(shouldBlend(-1, true, false)).toColor()

        if (displayControl.isWin0 || displayControl.isWin1 || displayControl.isWinObj) {
            fillWindowLut()
            for (pixel in 0 until 240) {
                mixBuffers(sortedBackgrounds, backdrop, pixel)
            }
        } else {
            for (pixel in 0 until 240) {

                var color = 0

                for (bg in sortedBackgrounds) {
                    if (spritePriorities[pixel] <= bg.priority) {
                        color = joinedSprites[pixel].brightnessBlend(shouldBlend(-1, false, true)).toColor()
                        break
                    }
                    if (lineBuffers[bg.index][pixel] != 0) {
                        color =
                            lineBuffers[bg.index][pixel].brightnessBlend(shouldBlend(bg.index, false, false)).toColor()
                        break
                    }
                }

                if (color == 0) {
                    color = backdrop
                }

                finalBuffer[pixel] = color
            }
        }

        framebuffer.put(vcount.ly * 240, finalBuffer, 0, 240)
    }

    fun mixBuffers(bgs: Array<BackgroundControl>, backdrop: Int, pixel: Int) {
        val isEnabled = mixingIsEnabled[getCurrentWindow(pixel)]

        var value = 0
        for (bg in bgs) {
            if (isEnabled[bg.index] && lineBuffers[bg.index][pixel] != 0) {
                value = if (isEnabled[LUT_OBJ] && spritePriorities[pixel] <= bg.priority) {
                    joinedSprites[pixel].brightnessBlend(shouldBlend(-1, false, true) && isEnabled[LUT_SPECIAL])
                        .toColor()
                } else {
                    lineBuffers[bg.index][pixel].brightnessBlend(
                        shouldBlend(
                            bg.index,
                            false,
                            false
                        ) && isEnabled[LUT_SPECIAL]
                    ).toColor()
                }

                break
            } else if (isEnabled[LUT_OBJ] && spritePriorities[pixel] <= bg.priority) {
                value = joinedSprites[pixel].brightnessBlend(shouldBlend(-1, false, true) && isEnabled[LUT_SPECIAL])
                    .toColor()
                break
            }
        }

        if (value == 0) {
            value = backdrop
        }

        finalBuffer[pixel] = value
    }

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
            buffer[pixelInBuffer] = palette.shortBuffer[color].toBufferColor()
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
            buffer[pixelInBuffer] = palette.shortBuffer[color + paletteOffset].toBufferColor()
    }

    fun clearLineBuffers() {
        for (it in lineBuffers) it.fill(0)
        finalBuffer.fill(0)
        joinedSprites.fill(0)
        spritePriorities.fill(255)
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
            2 -> renderBgMode2()
            3 -> renderBgMode3()
            4 -> renderBgMode4()
            5 -> renderBgMode5()
            else -> TODO("Background mode ${displayControl.bgMode} not implemented")
        }

        renderSprites()
        renderMixedBuffers()

        scheduler.schedule(2, dmaManager.hblankTask)
        scheduler.schedule(HBLANK_CYCLES, hblankRef, taskIndex)

        displayStat[DisplayStatFlag.HBLANK] = true
        if (displayStat[DisplayStatFlag.HBLANK_IRQ]) {
            mmio.ir[Interrupt.HBlank] = true
        }
    }

    fun hblank(taskIndex: Int) {
        displayStat[DisplayStatFlag.HBLANK] = false
        vcount.ly += 1
        checkVCounter()

        if (vcount.ly >= 2) scheduler.schedule(2, dmaManager.videoTask)

        if (vcount.ly >= VDRAW_HEIGHT) {
            bgReference.lock()

            scheduler.schedule(2, dmaManager.vblankTask)
            scheduler.schedule(HDRAW_CYCLES, vblankRef, taskIndex)
            blitFramebuffer()

            displayStat[DisplayStatFlag.VBLANK] = true
            if (displayStat[DisplayStatFlag.VBLANK_IRQ]) {
                mmio.ir[Interrupt.VBlank] = true
            }
        } else {
            bgReference.increment()
            scheduler.schedule(HDRAW_CYCLES, hdrawRef, taskIndex)
        }
    }

    fun vblank(taskIndex: Int) {
        displayStat[DisplayStatFlag.HBLANK] = true
        if (displayStat[DisplayStatFlag.HBLANK_IRQ]) {
            mmio.ir[Interrupt.HBlank] = true
        }

        scheduler.schedule(HBLANK_CYCLES, vblhbl, taskIndex)
    }


    fun hblankInVblank(taskIndex: Int) {
        displayStat[DisplayStatFlag.HBLANK] = false
        vcount.ly += 1

        if (vcount.ly < 162) scheduler.schedule(2, dmaManager.videoTask)
        else if (vcount.ly == 162) dmaManager.stopVideoTransfer()

        if (vcount.ly >= TOTAL_HEIGHT) {
            bgReference.unlock()
            bgReference.copyToInternal()
            vcount.ly = 0
            scheduler.schedule(HDRAW_CYCLES, hdrawRef, taskIndex)
        } else {
            if (vcount.ly == 227) {
                displayStat[DisplayStatFlag.VBLANK] = false
            }
            scheduler.schedule(HDRAW_CYCLES, vblankRef, taskIndex)
        }

        checkVCounter()
    }

    fun Array<BackgroundCoordinate>.increment() {
        this[0].internal += bgMatrix[1].param
        this[1].internal += bgMatrix[3].param
        this[2].internal += bgMatrix[5].param
        this[3].internal += bgMatrix[7].param
    }
}