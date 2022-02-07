package spAnGB.ppu.bg

import spAnGB.ppu.PPU
import spAnGB.ppu.toBufferColor
import spAnGB.utils.uInt

fun PPU.renderBgMode4() {
    val buffer = lineBuffers[0]
    val offset = vcount.ly * 240 +
            displayControl.frameSelect * 0xA000

    for (it in 0 until 240) {
        val color = vram.byteBuffer[it + offset].uInt

        if (color != 0) buffer[it] = palette.shortBuffer[color].toBufferColor()
    }
}