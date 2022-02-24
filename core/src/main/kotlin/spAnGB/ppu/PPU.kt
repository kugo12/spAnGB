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

object PixelType {
    const val Sprite = 4
    const val Backdrop = 5
}

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

    // Bg - 0-3
    val lineBuffers = Array(4) { IntArray(240) }  // TODO
    val spriteBuffer = Array(240) { SpritePixel() }
    val finalBuffer = IntArray(240)

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

        if (displayControl.isWinObj && spriteBuffer[x].isWindow) return 3

        return 2
    }

    @JvmField
    val windowIsEnabled = Array(4) { BooleanArray(6) }

    fun fillWindowLut() {  // FIXME: cursed
        windowIsEnabled[0][0] = winIn.isWin0Bg0
        windowIsEnabled[0][1] = winIn.isWin0Bg1
        windowIsEnabled[0][2] = winIn.isWin0Bg2
        windowIsEnabled[0][3] = winIn.isWin0Bg3
        windowIsEnabled[0][LUT_OBJ] = winIn.isWin0Obj
        windowIsEnabled[0][LUT_SPECIAL] = winIn.isWin0SpecialEffects

        windowIsEnabled[1][0] = winIn.isWin1Bg0
        windowIsEnabled[1][1] = winIn.isWin1Bg1
        windowIsEnabled[1][2] = winIn.isWin1Bg2
        windowIsEnabled[1][3] = winIn.isWin1Bg3
        windowIsEnabled[1][LUT_OBJ] = winIn.isWin1Obj
        windowIsEnabled[1][LUT_SPECIAL] = winIn.isWin1SpecialEffects

        windowIsEnabled[2][0] = winOut.isBg0
        windowIsEnabled[2][1] = winOut.isBg1
        windowIsEnabled[2][2] = winOut.isBg2
        windowIsEnabled[2][3] = winOut.isBg3
        windowIsEnabled[2][LUT_OBJ] = winOut.isObj
        windowIsEnabled[2][LUT_SPECIAL] = winOut.isSpecialEffects

        windowIsEnabled[3][0] = winOut.isObjWinBg0
        windowIsEnabled[3][1] = winOut.isObjWinBg1
        windowIsEnabled[3][2] = winOut.isObjWinBg2
        windowIsEnabled[3][3] = winOut.isObjWinBg3
        windowIsEnabled[3][LUT_OBJ] = winOut.isObjWinObj
        windowIsEnabled[3][LUT_SPECIAL] = winOut.isObjWinSpecialEffects
    }

    fun getBackdropColor() = palette.shortBuffer[0].toInt().let {
        if (blend.firstBd && blend.mode > 1) brightnessBlend(it) else it
    }

    fun alphaBlend(first: Int, second: Int) = transformColors(first, second) { a, b ->
        (a * alpha.firstCoefficient + b * alpha.secondCoefficient).ushr(4)
            .coerceAtMost(31)
    }

    fun brightnessBlend(first: Int): Int {
        val coefficient = brightness.coefficient

        return when (blend.mode) {
            2 -> first.transformColors { it + ((31 - it) * coefficient).ushr(4) }
            3 -> first.transformColors { it - (it * coefficient).ushr(4) }
            else -> -1
        }
    }

    fun renderMixedBuffers() {  // TODO: refactoring
        val sortedBackgrounds = bgControl
            .filter { displayControl isBg it.index }
            .sortedBy { it.priority }
            .toTypedArray()
        val backdrop = getBackdropColor()

        processSpriteMosaic()
        processBackgroundMosaic()

        if (displayControl.isWin0 || displayControl.isWin1 || displayControl.isWinObj) {
            fillWindowLut()
            for (pixel in 0 until 240) {
                mixBuffers(sortedBackgrounds, backdrop, pixel)
            }
        } else {
            for (pixel in 0 until 240) {
                var bottomColor = backdrop
                var topColor = backdrop
                var bottomPriority = TransparentPriority
                var topPriority = TransparentPriority
                var topIndex = PixelType.Backdrop
                var bottomIndex = PixelType.Backdrop

                for (it in sortedBackgrounds) {
                    if (lineBuffers[it.index][pixel] != 0) {
                        if (it.priority < topPriority) {
                            topPriority = it.priority
                            topIndex = it.index
                            topColor = lineBuffers[it.index][pixel]
                        } else {
                            bottomPriority = it.priority
                            bottomIndex = it.index
                            bottomColor = lineBuffers[it.index][pixel]

                            break
                        }
                    }
                }

                val spritePixel = spriteBuffer[pixel]
                if (spritePixel.priority != TransparentPriority) {
                    if (spritePixel.priority <= topPriority) {
                        bottomIndex = topIndex
                        bottomColor = topColor
                        topIndex = PixelType.Sprite
                        topColor = spritePixel.color.toInt()
                    } else if (spritePixel.priority <= bottomPriority) {
                        bottomIndex = PixelType.Sprite
                        bottomColor = spritePixel.color.toInt()
                    }
                }

                val isTopFirstTarget = blend isFirst topIndex
                val isBottomSecondTarget = blend isSecond bottomIndex
                finalBuffer[pixel] = when {
                    isTopFirstTarget && isBottomSecondTarget && blend.mode == 1 ||
                            topIndex == PixelType.Sprite && spritePixel.isSemiTransparent && isBottomSecondTarget -> alphaBlend(
                        topColor,
                        bottomColor
                    ).toColor()
                    blend.mode > 1 && isTopFirstTarget -> brightnessBlend(topColor).toColor()
                    else -> topColor.toColor()
                }
            }
        }

        framebuffer.put(vcount.ly * 240, finalBuffer, 0, 240)
    }


    fun mixBuffers(bgs: Array<BackgroundControl>, backdrop: Int, pixel: Int) {
        val isEnabled = windowIsEnabled[getCurrentWindow(pixel)]

        var bottomColor = backdrop
        var topColor = backdrop
        var bottomPriority = TransparentPriority
        var topPriority = TransparentPriority
        var topIndex = PixelType.Backdrop
        var bottomIndex = PixelType.Backdrop

        for (it in bgs) {
            if (isEnabled[it.index] && lineBuffers[it.index][pixel] != 0) {
                if (it.priority < topPriority) {
                    topPriority = it.priority
                    topIndex = it.index
                    topColor = lineBuffers[it.index][pixel]
                } else {
                    bottomPriority = it.priority
                    bottomIndex = it.index
                    bottomColor = lineBuffers[it.index][pixel]

                    break
                }
            }
        }

        val spritePixel = spriteBuffer[pixel]
        if (isEnabled[LUT_OBJ] && spritePixel.priority != TransparentPriority) {
            if (spritePixel.priority <= topPriority) {
                bottomIndex = topIndex
                bottomColor = topColor
                topIndex = PixelType.Sprite
                topColor = spritePixel.color.toInt()
            } else if (spritePixel.priority <= bottomPriority) {
                bottomIndex = PixelType.Sprite
                bottomColor = spritePixel.color.toInt()
            }
        }

        val isTopFirstTarget = blend isFirst topIndex
        val isBottomSecondTarget = blend isSecond bottomIndex
        val isSpecial = isEnabled[LUT_SPECIAL]
        finalBuffer[pixel] = when {
            isSpecial &&
                    ((isTopFirstTarget && isBottomSecondTarget && blend.mode == 1) ||
                            (topIndex == PixelType.Sprite && spritePixel.isSemiTransparent && isBottomSecondTarget)) -> {
                alphaBlend(topColor, bottomColor).toColor()
            }
            isSpecial && blend.mode > 1 && isTopFirstTarget -> {
                brightnessBlend(topColor).toColor()
            }
            else -> {
                topColor.toColor()
            }
        }
    }

    fun processBackgroundMosaic() {  // TODO
        if (mosaic.backgroundHorizontal == 0) return

        for (it in 0..3) {
            if (bgControl[it].isMosaic) {
                val buffer = lineBuffers[it]

                for (pixel in 0 until 240) {
                    if (mosaic.bgX != 0) {
                        buffer[pixel] = buffer[pixel - 1]
                    }
                    mosaic.advanceBgX()
                }
                mosaic.bgX = 0
            }
        }
    }

    fun processSpriteMosaic() {
        if (mosaic.objectHorizontal == 0) return

        for (it in 0 until 240) {
            val pixel = spriteBuffer[it]
            if (pixel.isMosaic) {
                if (mosaic.objX != 0) {
                    val previous = spriteBuffer[it - 1]
                    pixel.color = previous.color
                    pixel.priority = previous.priority
                }

                mosaic.advanceObjX()
            } else {
                mosaic.objX = 0
            }
        }
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
        mosaic.advanceCounters()

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
            mosaic.resetCounters()
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