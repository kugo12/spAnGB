package spAnGB.ppu

import spAnGB.utils.bit
import spAnGB.utils.uLong

fun PPU.renderSprites() {
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
        intArrayOf(64, 64)
    ),
    arrayOf(  // horizontal
        intArrayOf(16, 8),
        intArrayOf(32, 8),
        intArrayOf(32, 16),
        intArrayOf(64, 32)
    ),
    arrayOf(  // vertical
        intArrayOf(8, 16),
        intArrayOf(8, 32),
        intArrayOf(16, 32),
        intArrayOf(32, 64)
    )
)

@JvmInline
value class SpriteData(val value: Long) {
    init {
//        if (value bit 8) TODO("Rotation not supported rn")
//        if (value bit 12) TODO("Mosaic not supported rn")
    }

    inline val isDisabled: Boolean get() = value bit 9

    inline val isTransform: Boolean get() = value bit 8
    inline val isDoubleSize: Boolean get() = value bit 9

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

    @JvmInline
    value class TransformParameters(val value: Long) {
        inline val pa: Int get() = value.and(0xFFFF).toInt()
        inline val pb: Int get() = value.ushr(16).and(0xFFFF).toInt()
        inline val pc: Int get() = value.ushr(32).and(0xFFFF).toInt()
        inline val pd: Int get() = value.ushr(48).and(0xFFFF).toInt()
    }

    fun PPU.getParameters(group: Int): TransformParameters {
        val offset = group * 0x20 + 6
        val pa = attributes.byteBuffer[offset].uLong
        val pb = attributes.byteBuffer[offset + 8].uLong
        val pc = attributes.byteBuffer[offset + 16].uLong
        val pd = attributes.byteBuffer[offset + 24].uLong

        return TransformParameters(pa.or(pb shl 16).or(pc shl 32).or(pd shl 48))
    }

    fun PPU.render() {
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

        val (width, height) = spriteSizes[shape][size]
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