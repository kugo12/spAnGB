package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit
import spAnGB.utils.setBit

class WaveRamSelect : SimpleMMIO(mask = 0xE0) {
    inline val isTwoBanks get() = value bit 5
    inline val isSecondBank get() = value bit 6
    inline var isEnabled
        get() = value bit 7
        set(newValue) { value = value.setBit(7, newValue) }

    inline val counterMask get() = if (isTwoBanks) 0x3F else 0x1F
}