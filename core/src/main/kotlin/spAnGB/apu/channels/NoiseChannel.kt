package spAnGB.apu.channels

import spAnGB.apu.mmio.LengthEnvelopeControl
import spAnGB.apu.mmio.NoiseControl

class NoiseChannel : Channel, WithLength, WithEnvelope, WithFrequency {
    val envelope = LengthEnvelopeControl()
    val control = NoiseControl(::onReset)

    var isEnabled = false
    var volume = 0
    var lfsr = 0
    private var sample = 0

    override fun getSample(): Int {
        if (!isEnabled) return 0

        return sample * volume
    }

    override fun onReset() {
        isEnabled = true
        lfsr = 0x7FFF

        volume = envelope.initEnvelope()
    }

    override fun stepLength() {
        if (envelope.length > 0) {
            envelope.length -= 1
        } else if (control.shouldStopAfterExpiring) {
            isEnabled = false
        }
    }

    override fun stepEnvelope() {
        if (envelope.envelopeStepTime != 0) {
            volume = envelope.stepEnvelope(volume)
        }
    }

    override fun step(cycles: Long) {
        val n = cycles / control.divider

        for (it in 0 until n) {  // TODO: funny LUT
            val xored = lfsr.xor(lfsr ushr 1) and 1
            lfsr = lfsr ushr 1

            lfsr = if (control.is7Bits) {
                (lfsr and 0x4000.inv()) or (xored shl 14)
            } else {
                (lfsr and 0x0040.inv()) or (xored shl 5)
            }

            sample = lfsr.and(1) * -2 - 1
        }
    }
}