package spAnGB.ppu.mmio

import spAnGB.memory.mmio.SimpleMMIO

class BackgroundOffset: SimpleMMIO() {
    inline val offset: Int get() = value and 0x1FF
}