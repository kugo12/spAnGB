package spAnGB.ppu.mmio.blend

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.ppu.toCoefficient

class BlendAlpha : SimpleMMIO(mask = 0x1F1F) {
    inline val firstCoefficient get() = value.toCoefficient()
    inline val secondCoefficient get() = value.ushr(8).toCoefficient()
}