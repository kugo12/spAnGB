package spAnGB.ppu

import spAnGB.utils.*

fun PPU.renderSprites() {
    if (!displayControl.isObj) return

    for (it in 127 downTo 0) {
        SpriteData(sprites[it]).apply {
            if (shouldBeRendered()) render()
        }
    }
}

@JvmField  // width to height, [shape][size]
val spriteSizes: Array<Array<IntArray>> = arrayOf(
    arrayOf(  // square
        intArrayOf(8, 8),
        intArrayOf(16, 16),
        intArrayOf(32, 32),
        intArrayOf(64, 64),

        intArrayOf(16, 16),  // double size
        intArrayOf(32, 32),
        intArrayOf(64, 64),
        intArrayOf(128, 128)
    ),
    arrayOf(  // horizontal
        intArrayOf(16, 8),
        intArrayOf(32, 8),
        intArrayOf(32, 16),
        intArrayOf(64, 32),

        intArrayOf(32, 16),  // double size
        intArrayOf(64, 16),
        intArrayOf(64, 32),
        intArrayOf(128, 64)
    ),
    arrayOf(  // vertical
        intArrayOf(8, 16),
        intArrayOf(8, 32),
        intArrayOf(16, 32),
        intArrayOf(32, 64),

        intArrayOf(16, 32),  // double size
        intArrayOf(16, 64),
        intArrayOf(32, 64),
        intArrayOf(64, 128)
    )
)

@JvmInline
value class SpriteData(val value: Long) {
    init {
//        if (value bit 12) TODO("Mosaic not supported rn")
    }

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

    fun PPU.renderAffine() {
        val buffer = if (mode == 2) lineBuffers[8] else lineBuffers[priority + 4]
        val lyc = vcount.ly

        val (width, height) = spriteSizes[shape][size + 4 * isDoubleSize.toInt()]
        val (originalWidth, originalHeight) = spriteSizes[shape][size]
        val x = x
        val y = y

        val paramOffset = affineParameters * 0x10 + 3
        val pa = attributes.shortBuffer[paramOffset].toLong()
        val pb = attributes.shortBuffer[paramOffset + 4].toLong()
        val pc = attributes.shortBuffer[paramOffset + 8].toLong()
        val pd = attributes.shortBuffer[paramOffset + 12].toLong()

        assert(!is8Bit) { "8 bit mode not implemented rn" }


        val paletteOffset = 16 * paletteNumber + SpritePaletteOffset
        val tileRowSize =
            if (displayControl.isOneDimensionalMapping) (originalWidth / 8) * Tile4BitSize else TwoDimensionalTileRow * Tile4BitSize
        val tileOffset = SpriteTileOffset + (tileNumber * Tile4BitSize)

        val yInSprite = lyc - y - (height / 2)

        val start = if (x > 0) x else 0
        val end = if (start + width >= ScreenWidth) ScreenWidth else x + width

        val translationX = x + (width / 2)
        for (currentPixel in start until end) {
            val x1 = currentPixel - translationX
            val cX = (x1 * pa + yInSprite * pb).ushr(8).toInt() + originalWidth / 2
            val cY = (yInSprite * pd + x1 * pc).ushr(8).toInt() + originalHeight / 2

            if (cX < 0 || cY < 0 || cX >= originalWidth || cY >= originalHeight) continue

            val vramOffset = tileOffset +
                    ((cY % 8) * Tile4BitRow) +
                    ((cY / 8) * tileRowSize) +
                    (((cX / 2) / Tile4BitRow) * Tile4BitSize) +
                    ((cX / 2) % Tile4BitRow)

            val color = vram.byteBuffer[vramOffset].toInt().let {
                if (cX bit 0) {
                    it.ushr(4).and(0xF)
                } else {
                    it and 0xF
                }
            }
            if (color != 0)
                buffer[currentPixel] = palette.shortBuffer[color + paletteOffset].toBufferColor()
        }
    }

    fun PPU.render() {
        if (isTransform) {
            renderAffine()
            return
        }
        val buffer = if (mode == 2) lineBuffers[8] else lineBuffers[priority + 4]
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
            val tileRowSize =
                if (displayControl.isOneDimensionalMapping) (width / 8) * tileSize else tilesInRow * tileSize

            val yOffset = lyc - y

            val aaaa = if (verticalFlip) {
                (7 - yOffset % 8) * rowSize + ((height - yOffset - 1) / 8) * tileRowSize
            } else {
                yOffset % 8 * rowSize + yOffset / 8 * tileRowSize
            }

            val tileRowOffset = 0x10000 + tileNumber * tileSize + aaaa
            val screenPixelOffset = if (x > 0) x else 0
            val pixelsLeft = if (x >= 240 - width) 240 - x else if (x < 0) width + x else width
            val pixelsToSkipEnd = if (x >= 240 - width) ((240 - x + width) % 8) else 0
            val pixelsToSkipStart = if (x < 0) -x else 0

            var currentPixel = 0
            while (currentPixel < pixelsLeft) {
                val vramOffset = if (horizontalFlip) {
                    (pixelsLeft - currentPixel - 1 + pixelsToSkipStart).div(2).let {
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

    fun PPU.shouldBeRendered(): Boolean {
        if (shape >= 3) {
            println("Invalid sprite shape size")
            return false
        }

        val (width, height) = spriteSizes[shape][size + 4 * isDoubleSize.toInt()]
        val lyc = vcount.ly
        val y = y
        val x = x

        return enabled &&
                lyc >= y &&
                lyc < y + height &&
                x < 240 &&
                x > -width
    }
}