package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

val WaveDuty = intArrayOf(
    0x808080,
    0xC0C0C0,
    0xF0F0F0,
    0xFCFCFC
)

class DutyEnvelopeControl: SimpleMMIO() {
    inline val length get() = value and 0x3F
    inline val pattern get() = WaveDuty[value.ushr(6).and(3)]
    inline val envelopeStepTime get() = value.ushr(8).and(7)
    inline val isEnvelopeIncrease get() = value bit 11
    inline val initialEnvelopeVolume get() = value.ushr(12).and(0xF)
}