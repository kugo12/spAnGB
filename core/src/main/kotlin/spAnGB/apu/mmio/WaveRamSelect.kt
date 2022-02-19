package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class WaveRamSelect : SimpleMMIO(mask = 0xE0) {
    inline val isTwoBanks get() = value bit 5
    inline val isSecondBank get() = value bit 6
    inline val isEnabled get() = value bit 7
}