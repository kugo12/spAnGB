package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class FrequencyControl: SimpleMMIO() {
    inline val frequency get() = value and 0x7FF
    inline val shouldStopAfterExpiring get() = value bit 14
    inline val restart get() = value bit 15
}