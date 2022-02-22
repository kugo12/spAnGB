package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit
import spAnGB.utils.override
import spAnGB.utils.toInt

val WaveDuty = intArrayOf(0x80, 0xC0, 0xF0, 0xFC).map { d ->
    IntArray(8) { (d bit it).toInt() * 2 - 1 }.reversed().toIntArray()
}

class DutyEnvelopeControl: SimpleMMIO() {
    inline var length
        get() = value and 0x3F
        set(newValue) { value = value.override(0x3F, newValue) }

    inline val pattern get() = WaveDuty[value.ushr(6).and(3)]
    inline val envelopeStepTime get() = value.ushr(8).and(7)
    inline val isEnvelopeIncrease get() = value bit 11
    inline val initialEnvelopeVolume get() = value.ushr(12).and(0xF)

    var envelopeCounter = 0

    fun initEnvelope(): Int {
        return if (envelopeStepTime == 0) {
            0xF
        } else {
            envelopeCounter = envelopeStepTime
            initialEnvelopeVolume
        }
    }

    fun stepEnvelope(currentVolume: Int): Int {
        return if (envelopeCounter > 0) {
            envelopeCounter -= 1
            currentVolume
        } else {
            envelopeCounter = envelopeStepTime
            (currentVolume + if (isEnvelopeIncrease) 1 else -1).coerceIn(0, 0xF)  // TODO
        }
    }
}