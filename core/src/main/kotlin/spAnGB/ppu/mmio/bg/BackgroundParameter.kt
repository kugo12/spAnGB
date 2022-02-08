package spAnGB.ppu.mmio.bg

import spAnGB.memory.mmio.SimpleMMIO

class BackgroundParameter: SimpleMMIO() {
    inline val param get() = value.toShort().toInt()
}