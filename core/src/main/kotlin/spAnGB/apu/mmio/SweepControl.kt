package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class SweepControl: SimpleMMIO(mask = 0x7F) {
    inline val shift get() = value and 0x7
    inline val isDecrease get() = value bit 3
    inline val time get() = (value ushr 4) and 0x7
}