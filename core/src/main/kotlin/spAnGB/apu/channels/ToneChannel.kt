package spAnGB.apu.channels

import spAnGB.apu.mmio.DutyEnvelopeControl
import spAnGB.apu.mmio.FrequencyControl

class ToneChannel: Channel, WithEnvelope, WithLength, WithFrequency {
    val dutyEnvelope = DutyEnvelopeControl()
    val control = FrequencyControl(::onReset)

    var currentSample = 0
    var isEnabled = false
    var volume = 0
    var envelopeCounter = 0

    override fun getSample(): Int {
        if (!isEnabled) return 0

        return dutyEnvelope.pattern[currentSample] * volume
    }

    override fun onReset() {
        isEnabled = true

        volume = dutyEnvelope.initEnvelope()
    }

    override fun stepLength() {
        if (dutyEnvelope.length > 0) {
            dutyEnvelope.length -= 1
        } else if (control.shouldStopAfterExpiring) {
            isEnabled = false
        }
    }

    override fun stepEnvelope() {
        if (dutyEnvelope.envelopeStepTime != 0) {
            volume = dutyEnvelope.stepEnvelope(volume)
        }
    }

    override fun step(cycles: Long) {
        if (control.frequency == 0) return

        val n = cycles / control.cyclesPerIncrement
        currentSample = (currentSample + n.toInt()) and 7
    }
}