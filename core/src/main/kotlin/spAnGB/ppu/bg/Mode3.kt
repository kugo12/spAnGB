package spAnGB.ppu.bg

import spAnGB.ppu.PPU
import spAnGB.ppu.toBufferColor

fun PPU.renderBgMode3() {
    val offset = vcount.ly * 240
    val buffer = lineBuffers[0]

    for (it in 0 until 240) {
        buffer[it] = vram.shortBuffer[it + offset].toBufferColor()
    }
}