package spAnGB.ppu.bg

import spAnGB.ppu.*
import spAnGB.ppu.mmio.bg.BackgroundControl
import spAnGB.utils.KiB
import spAnGB.utils.bit
import spAnGB.utils.uInt


fun PPU.renderBgText(n: Int) {
    val control = bgControl[n]

    if (control.isSinglePalette) {
        render(
            n, control,
            Tile8BitRow, Tile8BitSize,
            { it },
            { color, _ -> color },
            { color, _ -> color }
        )
    } else {
        render(
            n, control,
            Tile4BitRow, Tile4BitSize,
            { it/2 },
            { color, tX -> color.ushr(tX.and(1).times(4)).and(0xF) },
            { color, entry -> color + entry.palette }
        )
    }
}


@JvmInline
private value class BackgroundTextTile(val value: Int) {
    inline val tileNumber: Int get() = value and 0x3FF
    inline val horizontalFlip: Boolean get() = value bit 10
    inline val verticalFlip: Boolean get() = value bit 11
    inline val palette: Int get() = value.and(0xF000).ushr(8)
}

private inline fun PPU.render(
    n: Int,
    control: BackgroundControl,
    rowSize: Int,
    tileSize: Int,
    getTileRowPixel: (Int) -> Int,
    getColor: (Int, Int) -> Int,
    getPaletteOffset: (Int, BackgroundTextTile) -> Int
) {
    val buffer = lineBuffers[n]
    val (bgWidth, bgHeight) = TextBackgroundSizes[control.size]

    val tileMapOffset = control.characterBaseBlock * 16 * KiB
    val mapOffset = control.screenBaseBlock * 1 * KiB
    val bgYOffset = bgYOffset[n].offset - if (control.isMosaic) mosaic.bgY else 0
    val bgXOffset = bgXOffset[n].offset

    val yTileOffset = if (bgHeight == 512)  // TODO
        (((vcount.ly + bgYOffset) / 256) % 2) * 32 * 32 + (((vcount.ly + bgYOffset) % 256) / 8) * 32
    else (((vcount.ly + bgYOffset) % 256) / 8) * 32

    val yOffset = (vcount.ly + bgYOffset) % 8

    for (it in 0 until 240) {
        val lX = it + bgXOffset
        val xTileOffset = if (bgWidth == 512)  // TODO
            ((lX / 256) % 2) * 32 * 32 + (lX % 256) / 8
        else
            (lX % 256) / 8

        val entry = BackgroundTextTile(
            vram.shortBuffer[xTileOffset + yTileOffset + mapOffset].toInt()
        )

        val tX = (if (entry.horizontalFlip) 7 - lX % 8 else lX % 8)
        val tileRowOffset = tileMapOffset +
                entry.tileNumber * tileSize +
                (if (entry.verticalFlip) 7 - yOffset else yOffset) * rowSize +
                getTileRowPixel(tX)

        val color = getColor(vram.byteBuffer[tileRowOffset].uInt, tX)
        if (color != 0)
            buffer[it] = palette.shortBuffer[getPaletteOffset(color, entry)].toBufferColor()
    }
}