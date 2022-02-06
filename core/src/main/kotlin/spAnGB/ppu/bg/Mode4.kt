package spAnGB.ppu.bg

import spAnGB.ppu.PPU
import spAnGB.utils.toColor
import spAnGB.utils.uInt

fun PPU.renderBgMode4() {
    val offset = vcount.ly * 240
    val buffer = lineBuffers[0]

    for (it in 0 until 240) {
        buffer[it] = palette.shortBuffer[
                vram.byteBuffer[it + offset].uInt
        ].toColor()
    }
}