@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.ppu.bg

import spAnGB.ppu.PPU
import spAnGB.utils.KiB
import spAnGB.utils.bit


@JvmInline
value class BackgroundTextTile(val value: Int) {
    inline val tileNumber: Int get() = value and 0x3FF
    inline val horizontalFlip: Boolean get() = value bit 10
    inline val verticalFlip: Boolean get() = value bit 11
    inline val palette: Int get() = value.and(0xF000).ushr(12)
}

inline fun PPU.renderBgText(n: Int) {
    val control = bgControl[n]
    val buffer = lineBuffers[n]
    val (bgWidth, bgHeight) = backgroundSizes[control.size]

    val tileMapOffset = control.characterBaseBlock * 16 * KiB
    val mapOffset = control.screenBaseBlock * 1 * KiB
    val bgYOffset = bgYOffset[n].offset
    val bgXOffset = bgXOffset[n].offset

    var currentPixel = 0

    if (control.isSinglePalette) {
        val rowSize = 8
        val bgTileSize = rowSize*8
        val yTileOffset = if (bgHeight == 512)
            (((vcount.ly + bgYOffset) / 256) % 2) * 32 * 32 + (((vcount.ly + bgYOffset) % 256) / 8) * 32
        else (((vcount.ly + bgYOffset) % 256) / 8) * 32

        val yOffset = (vcount.ly + bgYOffset) % 8
        val xOffset = bgXOffset % 8

        while (currentPixel < 240) {
            val xTileOffset = if (bgWidth == 512)
                (((currentPixel + bgXOffset) / 256) % 2) * 32 * 32 + ((currentPixel + bgXOffset) % 256) / 8
            else
                ((currentPixel + bgXOffset) % 256) / 8

            val entry = BackgroundTextTile(
                vram.shortBuffer[xTileOffset + yTileOffset + mapOffset].toInt()
            )

            val tileRowOffset = tileMapOffset +
                    entry.tileNumber * bgTileSize +
                    (if (entry.verticalFlip) 7 - yOffset else yOffset) * rowSize

            currentPixel += blit8BitTileRow(
                buffer,
                tileRowOffset,
                currentPixel,
                if (currentPixel + xOffset >= 240) 7 - xOffset else 0,
                entry.horizontalFlip,
                if (currentPixel == 0) xOffset else 0
            )
        }
    } else {
        val rowSize = 4
        val bgTileSize = rowSize*8
        val yTileOffset = if (bgHeight == 512)
            (((vcount.ly + bgYOffset) / 256) % 2) * 32 * 32 + (((vcount.ly + bgYOffset) % 256) / 8) * 32
        else (((vcount.ly + bgYOffset) % 256) / 8) * 32

        val yOffset = (vcount.ly + bgYOffset) % 8
        val xOffset = bgXOffset % 8

        while (currentPixel < 240) {
            val xTileOffset = if (bgWidth == 512)
                (((currentPixel + bgXOffset) / 256) % 2) * 32 * 32 + ((currentPixel + bgXOffset) % 256) / 8
            else
                ((currentPixel + bgXOffset) % 256) / 8

            val entry = BackgroundTextTile(
                vram.shortBuffer[xTileOffset + yTileOffset + mapOffset].toInt()
            )

            val tileRowOffset = tileMapOffset +
                    entry.tileNumber * bgTileSize +
                    (if (entry.verticalFlip) 7 - yOffset else yOffset) * rowSize

            currentPixel += blit4BitTileRow(
                buffer,
                tileRowOffset,
                currentPixel,
                if (currentPixel + xOffset >= 240) 7 - xOffset else 0,
                16 * entry.palette,
                entry.horizontalFlip,
                if (currentPixel == 0) xOffset else 0
            )
        }
    }
}

fun PPU.renderBgMode0() {
    if (displayControl.isBg0) renderBgText(0)
    if (displayControl.isBg1) renderBgText(1)
    if (displayControl.isBg2) renderBgText(2)
    if (displayControl.isBg3) renderBgText(3)
}