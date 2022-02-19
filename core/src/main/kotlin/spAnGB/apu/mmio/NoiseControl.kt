package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class NoiseControl: SimpleMMIO() {
    inline val divider get() = value and 0x7
    inline val is7Bits get() = value bit 3
    inline val frequency get() = value.ushr(4).and(0xF)
    inline val shouldStopAfterExpiring get() = value bit 14
    inline val restart get() = value bit 15
}