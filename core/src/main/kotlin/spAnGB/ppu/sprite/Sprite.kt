package spAnGB.ppu.sprite

import spAnGB.ppu.*
import spAnGB.utils.bit
import spAnGB.utils.toInt
import spAnGB.utils.uInt

private typealias RenderFn = (PPU, Int, Int, () -> Int, () -> Int, (Int, Int) -> Int, (Int) -> Int) -> Unit

fun PPU.renderSprites() {
    spriteBuffer.clear()

    if (!displayControl.isObj) return

    for (it in 0 until 127) {
        SpriteData(sprites[it]).apply {
            if (shouldBeRendered()) render()
        }
    }
}

@JvmInline
value class SpriteData(val value: Long) { // TODO: rendering code deduplication
    inline val isMosaic get() = value bit 12
    inline val isDisabled: Boolean get() = value bit 9

    inline val isTransform: Boolean get() = value bit 8
    inline val isDoubleSize: Boolean get() = isTransform && value bit 9

    inline val enabled: Boolean get() = isTransform || !isDisabled

    inline val x: Int get() = value.ushr(16).and(0x1FF).toInt().let { if (it >= 240) it - 512 else it }
    inline val y: Int get() = value.and(0xFF).toInt().let { if (it >= 160) it - 256 else it }
    inline val is8Bit: Boolean get() = value bit 13
    inline val horizontalFlip: Boolean get() = value bit 28
    inline val verticalFlip: Boolean get() = value bit 29
    inline val tileNumber: Int get() = value.ushr(32).and(0x3FF).toInt()
    inline val paletteNumber: Int get() = value.ushr(44).and(0xF).toInt()
    inline val priority: Int get() = value.ushr(42).and(0x3).toInt()

    inline val mode: Int get() = value.ushr(10).and(0x3).toInt()

    inline val shape: Int get() = value.toInt().ushr(14).and(0x3)
    inline val size: Int get() = value.ushr(30).and(0x3).toInt()

    inline val affineParameters: Int get() = value.ushr(25).and(0x1F).toInt()

    fun PPU.render() {
        if (displayControl.bgMode in 3..5 && tileNumber <= 511)  // FIXME
            return

        if (isTransform) {
            internalRender(::renderAffine)
        } else {
            internalRender(::renderText)
        }
    }

    fun PPU.shouldBeRendered(): Boolean {
        val (width, height) = SpriteSizes[shape][size + 4 * isDoubleSize.toInt()]
        val lyc = vcount.ly
        val y = y
        val x = x

        return enabled &&
                shape < 3 &&
                lyc >= y &&
                lyc < y + height &&
                x < 240 &&
                x > -width
    }

    private inline fun PPU.internalRender(func: RenderFn) =
        if (is8Bit) {
            func(
                this, Tile8BitRow, Tile8BitSize,
                { if (displayControl.isOneDimensionalMapping) tileNumber else tileNumber.and(1.inv()) },
                { SpritePaletteOffset },
                { color, _ -> color },
                { it }
            )
        } else {
            func(
                this, Tile4BitRow, Tile4BitSize,
                { tileNumber },
                { 16 * paletteNumber + SpritePaletteOffset },
                { color, x -> color.ushr(x.and(1).times(4)).and(0xF) },
                { it / 2 }
            )
        }

    private inline fun renderText(
        ppu: PPU,
        rowSize: Int,
        tileSize: Int,
        getTileNumber: () -> Int,
        getPaletteOffset: () -> Int,
        getPixelColor: (Int, Int) -> Int,
        getXInTile: (Int) -> Int
    ) {
        val buffer = ppu.spriteBuffer
        val lyc = ppu.vcount.ly

        val isWindow = mode == 2
        val isMosaic = isMosaic
        val isSemiTransparent = mode == 1
        val priority = priority

        val (width, height) = SpriteSizes[shape][size]
        val x = x
        val y = y

        val start = if (x > 0) x else 0
        val end = if (start + width >= ScreenWidth) ScreenWidth else x + width
        val yOffset = lyc - y - if (isMosaic) ppu.mosaic.objY else 0

        val paletteOffset = getPaletteOffset()
        val tileRowSize =
            if (ppu.displayControl.isOneDimensionalMapping) (width / 8) * tileSize
            else TwoDimensionalTileRow * Tile4BitSize

        val tileRow = if (verticalFlip) {
            (7 - yOffset % 8) * rowSize + ((height - yOffset - 1) / 8) * tileRowSize
        } else {
            yOffset % 8 * rowSize + yOffset / 8 * tileRowSize
        }

        val tileRowOffset = SpriteTileOffset + getTileNumber() * Tile4BitSize + tileRow

        for (screenPixel in start until end) {
            val spriteX = if (horizontalFlip) width - (screenPixel - x + 1) else screenPixel - x

            val vramOffset = tileRowOffset +
                    ((spriteX / 8) * tileSize) +
                    getXInTile(spriteX % 8)

            val color = getPixelColor(ppu.vram.byteBuffer[vramOffset].uInt, spriteX)

            buffer[screenPixel].apply(
                color == 0,
                isWindow,
                priority,
                isMosaic,
                isSemiTransparent,
                ppu.palette.shortBuffer[color + paletteOffset]
            )
        }
    }

    private inline fun renderAffine(
        ppu: PPU,
        rowSize: Int,
        tileSize: Int,
        getTileNumber: () -> Int,
        getPaletteOffset: () -> Int,
        getPixelColor: (Int, Int) -> Int,
        getXInTile: (Int) -> Int
    ) {
        val buffer = ppu.spriteBuffer
        val lyc = ppu.vcount.ly

        val isMosaic = isMosaic
        val isSemiTransparent = mode == 1
        val isWindow = mode == 2
        val priority = priority

        val (width, height) = SpriteSizes[shape][size + 4 * isDoubleSize.toInt()]
        val (originalWidth, originalHeight) = SpriteSizes[shape][size]
        val x = x
        val y = y

        val paramOffset = affineParameters * 0x10 + 3
        val pa = ppu.attributes.shortBuffer[paramOffset].toLong()
        val pb = ppu.attributes.shortBuffer[paramOffset + 4].toLong()
        val pc = ppu.attributes.shortBuffer[paramOffset + 8].toLong()
        val pd = ppu.attributes.shortBuffer[paramOffset + 12].toLong()

        val paletteOffset = getPaletteOffset()
        val tileRowSize =
            if (ppu.displayControl.isOneDimensionalMapping) (originalWidth / 8) * tileSize
            else TwoDimensionalTileRow * Tile4BitSize

        val tileOffset = SpriteTileOffset + getTileNumber() * Tile4BitSize

        val yInSprite = lyc - y - (height / 2) - if (isMosaic) ppu.mosaic.objY else 0

        val start = if (x > 0) x else 0
        val end = if (start + width >= ScreenWidth) ScreenWidth else x + width

        val translationX = x + (width / 2)
        for (currentPixel in start until end) {
            val x1 = currentPixel - translationX
            val cX = (x1 * pa + yInSprite * pb).ushr(8).toInt() + originalWidth / 2
            val cY = (yInSprite * pd + x1 * pc).ushr(8).toInt() + originalHeight / 2

            if (cX < 0 || cY < 0 || cX >= originalWidth || cY >= originalHeight) continue

            val vramOffset = tileOffset +
                    ((cY % 8) * rowSize) +
                    ((cY / 8) * tileRowSize) +
                    ((cX / 8) * tileSize) +
                    getXInTile(cX % 8)

            val color = getPixelColor(ppu.vram.byteBuffer[vramOffset].toInt(), cX)

            buffer[currentPixel].apply(
                color == 0,
                isWindow,
                priority,
                isMosaic,
                isSemiTransparent,
                ppu.palette.shortBuffer[color + paletteOffset]
            )
        }
    }
}