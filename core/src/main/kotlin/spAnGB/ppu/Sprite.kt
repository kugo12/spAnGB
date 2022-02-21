package spAnGB.ppu

import spAnGB.utils.*

const val TransparentPriority = 4

class SpritePixel(
    var priority: Int = TransparentPriority,
    var isMosaic: Boolean = false,
    var isSemiTransparent: Boolean = false,
    var color: Short = 0,

    var isWindow: Boolean = false
) {
    fun clear() {
        priority = TransparentPriority
        isSemiTransparent = false
        isMosaic = false
        isWindow = false
    }

    inline fun apply(
        isTransparent: Boolean,
        isWindow: Boolean,
        priority: Int,
        isMosaic: Boolean,
        isSemiTransparent: Boolean,
        color: Short
    ) {
        if (isTransparent) {
            this.isMosaic = isMosaic
        } else if (isWindow) {
            this.isWindow = true
        } else if (priority < this.priority) {
            this.priority = priority
            this.isMosaic = isMosaic
            this.isSemiTransparent = isSemiTransparent
            this.color = color
        }
    }

    inline fun isTransparent() = priority == TransparentPriority
}

inline fun Array<SpritePixel>.clear() = forEach(SpritePixel::clear)

fun PPU.renderSprites() {
    spriteBuffer.clear()

    if (!displayControl.isObj) return

    for (it in 0 until 127) {
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

    fun PPU.renderAffine() {
        val buffer = spriteBuffer
        val lyc = vcount.ly

        val isMosaic = isMosaic
        val isSemiTransparent = mode == 1
        val isWindow = mode == 2
        val priority = priority

        val (width, height) = spriteSizes[shape][size + 4 * isDoubleSize.toInt()]
        val (originalWidth, originalHeight) = spriteSizes[shape][size]
        val x = x
        val y = y

        val paramOffset = affineParameters * 0x10 + 3
        val pa = attributes.shortBuffer[paramOffset].toLong()
        val pb = attributes.shortBuffer[paramOffset + 4].toLong()
        val pc = attributes.shortBuffer[paramOffset + 8].toLong()
        val pd = attributes.shortBuffer[paramOffset + 12].toLong()

        if (is8Bit) {
            val tileRowSize =
                if (displayControl.isOneDimensionalMapping) (originalWidth / 8) * Tile8BitSize
                else TwoDimensionalTileRow * Tile8BitSize / 2

            val n = if (displayControl.isOneDimensionalMapping) tileNumber else tileNumber and 1.inv()
            val tileOffset = SpriteTileOffset + n * Tile4BitSize

            val yInSprite = lyc - y - (height / 2) - if (isMosaic) mosaic.objY else 0

            val start = if (x > 0) x else 0
            val end = if (start + width >= ScreenWidth) ScreenWidth else x + width

            val translationX = x + (width / 2)
            for (currentPixel in start until end) {
                val x1 = currentPixel - translationX
                val cX = (x1 * pa + yInSprite * pb).ushr(8).toInt() + originalWidth / 2
                val cY = (yInSprite * pd + x1 * pc).ushr(8).toInt() + originalHeight / 2

                if (cX < 0 || cY < 0 || cX >= originalWidth || cY >= originalHeight) continue

                val vramOffset = tileOffset +
                        ((cY % 8) * Tile8BitRow) +
                        ((cY / 8) * tileRowSize) +
                        ((cX / Tile8BitRow) * Tile8BitSize) +
                        (cX % Tile8BitRow)

                val color = vram.byteBuffer[vramOffset].uInt

                buffer[currentPixel].apply(
                    color == 0,
                    isWindow,
                    priority,
                    isMosaic,
                    isSemiTransparent,
                    palette.shortBuffer[color + SpritePaletteOffset]
                )
            }
        } else {
            val paletteOffset = 16 * paletteNumber + SpritePaletteOffset
            val tileRowSize =
                if (displayControl.isOneDimensionalMapping) (originalWidth / 8) * Tile4BitSize
                else TwoDimensionalTileRow * Tile4BitSize

            val tileOffset = SpriteTileOffset + (tileNumber * Tile4BitSize)

            val yInSprite = lyc - y - (height / 2) - if (isMosaic) mosaic.objY else 0

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

                buffer[currentPixel].apply(
                    color == 0,
                    isWindow,
                    priority,
                    isMosaic,
                    isSemiTransparent,
                    palette.shortBuffer[color + paletteOffset]
                )
            }
        }
    }

    fun PPU.render() {
        if (displayControl.bgMode in 3..5 && tileNumber <= 511) return
        if (isTransform) {
            renderAffine()
            return
        }

        val buffer = spriteBuffer
        val lyc = vcount.ly

        val isWindow = mode == 2
        val isMosaic = isMosaic
        val isSemiTransparent = mode == 1
        val priority = priority

        val (width, height) = spriteSizes[shape][size]
        val x = x
        val y = y

        if (is8Bit) {  // TODO: refactoring
            val tileRowSize =
                if (displayControl.isOneDimensionalMapping) (width / 8) * Tile8BitSize
                else TwoDimensionalTileRow * Tile8BitSize / 2

            val yOffset = lyc - y - if (isMosaic) mosaic.objY else 0

            val tileRow = if (verticalFlip) {
                (7 - yOffset % 8) * Tile8BitRow + ((height - yOffset - 1) / 8) * tileRowSize
            } else {
                yOffset % 8 * Tile8BitRow + yOffset / 8 * tileRowSize
            }

            val n = if (displayControl.isOneDimensionalMapping) tileNumber else tileNumber.and(1.inv())
            val tileRowOffset = SpriteTileOffset + n * Tile4BitSize + tileRow

            val start = if (x > 0) x else 0
            val end = if (start + width >= ScreenWidth) ScreenWidth else x + width

            for (screenPixel in start until end) {
                val spriteX = if (horizontalFlip) width - (screenPixel - x + 1) else screenPixel - x

                val vramOffset = tileRowOffset +
                        ((spriteX / 8) * Tile8BitSize) +
                        (spriteX % 8)

                val color = vram.byteBuffer[vramOffset].uInt

                buffer[screenPixel].apply(
                    color == 0,
                    isWindow,
                    priority,
                    isMosaic,
                    isSemiTransparent,
                    palette.shortBuffer[color + SpritePaletteOffset]
                )
            }
        } else {
            val paletteOffset = 16 * paletteNumber + SpritePaletteOffset
            val tileRowSize =
                if (displayControl.isOneDimensionalMapping) (width / 8) * Tile4BitSize
                else TwoDimensionalTileRow * Tile4BitSize

            val yOffset = lyc - y - if (isMosaic) mosaic.objY else 0

            val tileRow = if (verticalFlip) {
                (7 - yOffset % 8) * Tile4BitRow + ((height - yOffset - 1) / 8) * tileRowSize
            } else {
                yOffset % 8 * Tile4BitRow + yOffset / 8 * tileRowSize
            }

            val tileRowOffset = SpriteTileOffset + tileNumber * Tile4BitSize + tileRow

            val start = if (x > 0) x else 0
            val end = if (start + width >= ScreenWidth) ScreenWidth else x + width

            for (screenPixel in start until end) {
                val spriteX = if (horizontalFlip) width - (screenPixel - x + 1) else screenPixel - x

                val vramOffset = tileRowOffset +
                        (((spriteX / 2) / Tile4BitRow) * Tile4BitSize) +
                        ((spriteX / 2) % Tile4BitRow)

                val color = vram.byteBuffer[vramOffset].toInt().let {
                    if (spriteX bit 0) {
                        it.ushr(4).and(0xF)
                    } else {
                        it and 0xF
                    }
                }

                buffer[screenPixel].apply(
                    color == 0,
                    isWindow,
                    priority,
                    isMosaic,
                    isSemiTransparent,
                    palette.shortBuffer[color + paletteOffset]
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