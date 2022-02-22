package spAnGB.apu.channels

import spAnGB.apu.mmio.DutyEnvelopeControl
import spAnGB.apu.mmio.FrequencyControl
import spAnGB.apu.mmio.SweepControl
import spAnGB.cpu.CLOCK_SPEED

class SweepToneChannel: Channel, WithLength, WithSweep, WithFrequency, WithEnvelope {
    val sweep = SweepControl()
    val dutyEnvelope = DutyEnvelopeControl()
    val control = FrequencyControl(::onReset)

    var isEnabled = false
    var volume = 0
    var sweepStep = 0
    var frequency = 0
    var currentSample = 0

    inline val cyclesPerIncrement get() = CLOCK_SPEED / (GB_CLOCK_SPEED / (32 * frequency))

    override fun getSample(): Int {
        if (!isEnabled) return 0

        return dutyEnvelope.pattern[currentSample] * volume
    }

    override fun onReset() {
        isEnabled = true
        frequency = control.frequency

        sweepStep = sweep.time
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

    override fun stepSweep() {
        if (sweep.isEnabled) {
            if (sweepStep > 0) {
                sweepStep -= 1
            } else {
                frequency += (if (sweep.isDecrease) -(frequency ushr sweep.shift) else frequency ushr sweep.shift)

                if (frequency <= 0 || frequency >= 2048)
                    isEnabled = false

                sweepStep = sweep.time
            }
        }
    }

    override fun step(cycles: Long) {
        if (frequency == 0) return

        val n = cycles / cyclesPerIncrement
        currentSample = (currentSample + n.toInt()) and 7
    }
}