package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class SoundMasterControl: SimpleMMIO(mask = 0x80) {  // TODO psg r/o flags
    inline val isEnabled get() = value bit 7
}