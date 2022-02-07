package spAnGB.ppu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.ppu.toCoefficient

class BlendAlpha : SimpleMMIO() {
    inline val firstCoefficient get() = value.toCoefficient()
    inline val secondCoefficient get() = value.ushr(8).toCoefficient()
}