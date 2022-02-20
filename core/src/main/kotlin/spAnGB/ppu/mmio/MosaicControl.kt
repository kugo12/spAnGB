package spAnGB.ppu.mmio

import spAnGB.memory.mmio.SimpleMMIO

class MosaicControl: SimpleMMIO() {
    inline val backgroundHorizontal get() = value and 0xF
    inline val backgroundVertical get() = value.ushr(4).and(0xF)
    inline val objectHorizontal get() = value.ushr(8).and(0xF)
    inline val objectVertical get() = value.ushr(12).and(0xF)
}