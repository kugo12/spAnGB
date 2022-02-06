@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.ppu.bg

import spAnGB.ppu.PPU
import spAnGB.utils.KiB

inline fun PPU.renderBgText(n: Int) {
    val control = bgControl[n]
    val buffer = priorityBuffers[control.priority]
    val (bgWidth, bgHeight) = backgroundSizes[control.size]

    val tileMapOffset = control.characterBaseBlock * 16 * KiB
    val mapOffset = control.screenBaseBlock * 1 * KiB
    val bgYOffset = bgYOffset[n].offset
    val bgXOffset = bgXOffset[n].offset

    var currentPixel = 0

    if (control.isSinglePalette) {
        TODO()
    } else {
        val bgTileSize = 32
        val rowSize = 4
        val yTileOffset = (((vcount.ly + bgYOffset) % 256) / 8) * 32

        val yOffset = (vcount.ly + bgYOffset) % 8

        while (currentPixel < 240) {
            val xTileOffset =
                (((currentPixel + bgXOffset) / 256) % 2) * 32 * 32 + ((currentPixel + bgXOffset) % 256) / 8

            val entry = PPU.BackgroundTextTile(
                vram.shortBuffer[xTileOffset + yTileOffset + mapOffset].toInt()
            )
            val xOffset = bgXOffset % 8

            val tileRowOffset = tileMapOffset +
                    entry.tileNumber * bgTileSize +
                    (if (entry.verticalFlip) 7 - yOffset else yOffset) * rowSize

            val paletteOffset = 16 * entry.palette

            currentPixel += blit4BitTileRow(
                buffer,
                tileRowOffset,
                currentPixel,
                if (currentPixel + xOffset >= 240) 7 - xOffset else 0,
                paletteOffset,
                entry.horizontalFlip,
                if (currentPixel == 0) xOffset else 0
            )
        }
    }
}

fun PPU.renderBgMode0() {
    if (displayControl.isBg3) renderBgText(3)
    if (displayControl.isBg2) renderBgText(2)
    if (displayControl.isBg1) renderBgText(1)
    if (displayControl.isBg0) renderBgText(0)
}