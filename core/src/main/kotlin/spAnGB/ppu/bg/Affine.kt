package spAnGB.ppu.bg

import spAnGB.ppu.*
import spAnGB.utils.KiB
import spAnGB.utils.uInt


fun PPU.renderBgAffine(n: Int) {
    val control = bgControl[n]
    val buffer = lineBuffers[n]
    val (width, height) = AffineBackgroundSizes[control.size]
    val isWraparound = control.isWrapAround

    val tileMapOffset = control.characterBaseBlock * 16 * KiB
    val mapOffset = control.screenBaseBlock * 2 * KiB
    var xRef = bgReference[n - 2].internal
    var yRef = bgReference[n - 1].internal

    val pa = bgMatrix[n.and(1).shl(2)].param
    val pc = bgMatrix[n.and(1).shl(2) + 2].param

    val tilesInRow = width / 8
    val lyc = vcount.ly
    val mosaicOffset = if (control.isMosaic) mosaic.bgY else 0

    for (currentPixel in 0 until 240) {
        var localX = currentPixel + xRef ushr 8
        var localY = lyc + yRef ushr 8 - mosaicOffset
        xRef += pa
        yRef += pc

        if (isWraparound) {  // TODO
            localX = if (localX < 0) width + localX else localX%width
            localY = if (localY < 0) width + localY else localY%width
        }

        if (localX < 0 || localY < 0 || localX > width || localY > height) continue

        val tileOffset = mapOffset +
                (localY / 8) * tilesInRow +
                localX / 8
        val tileNumber = vram.byteBuffer[tileOffset].uInt

        val tileRowOffset = tileMapOffset +
                tileNumber * Tile8BitSize +
                (localY % 8) * Tile8BitRow +
                localX % 8
        val color = vram.byteBuffer[tileRowOffset].uInt

        if (color != 0)
            buffer[currentPixel] = palette.shortBuffer[color].toBufferColor()
    }
}