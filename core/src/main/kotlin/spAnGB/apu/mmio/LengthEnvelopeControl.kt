package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class LengthEnvelopeControl: SimpleMMIO() {
    inline val length get() = value and 0x3F
    inline val envelopeStepTime get() = value.ushr(8).and(7)
    inline val isEnvelopeIncrease get() = value bit 11
    inline val initialEnvelopeVolume get() = value.ushr(12).and(0xF)
}