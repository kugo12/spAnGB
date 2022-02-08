package spAnGB.ppu.mmio.blend

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.ppu.toCoefficient

class BlendBrightness: SimpleMMIO() {
    inline val coefficient get() = value.toCoefficient()
}