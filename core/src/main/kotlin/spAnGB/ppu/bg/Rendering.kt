package spAnGB.ppu.bg

import spAnGB.ppu.PPU
import spAnGB.ppu.toBufferColor
import spAnGB.utils.uInt

fun PPU.renderBgMode0() {
    if (displayControl.isBg0) renderBgText(0)
    if (displayControl.isBg1) renderBgText(1)
    if (displayControl.isBg2) renderBgText(2)
    if (displayControl.isBg3) renderBgText(3)
}

fun PPU.renderBgMode1() {
    if (displayControl.isBg0) renderBgText(0)
    if (displayControl.isBg1) renderBgText(1)
    if (displayControl.isBg2) renderBgAffine(2)
}

fun PPU.renderBgMode2() {
    if (displayControl.isBg2) renderBgAffine(2)
    if (displayControl.isBg3) renderBgAffine(3)
}

fun PPU.renderBgMode3() {
    val offset = vcount.ly * 240
    val buffer = lineBuffers[2]

    for (it in 0 until 240) {
        buffer[it] = vram.shortBuffer[it + offset].toBufferColor()
    }
}

fun PPU.renderBgMode4() {
    val buffer = lineBuffers[2]
    val offset = vcount.ly * 240 +
            displayControl.frameSelect * 0xA000

    for (it in 0 until 240) {
        val color = vram.byteBuffer[it + offset].uInt

        if (color != 0) buffer[it] = palette.shortBuffer[color].toBufferColor()
    }
}