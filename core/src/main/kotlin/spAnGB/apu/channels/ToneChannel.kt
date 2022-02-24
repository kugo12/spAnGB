package spAnGB.apu.channels

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.apu.mmio.DutyEnvelopeControl
import spAnGB.apu.mmio.FrequencyControl

class ToneChannel(
    val scheduler: Scheduler
): Channel, WithEnvelope, WithLength {
    val dutyEnvelope = DutyEnvelopeControl()
    val control = FrequencyControl(::onReset)

    var currentSample = 0
    var isEnabled = false
    var volume = 0
    var envelopeCounter = 0

    var taskIndex = -1
    val task = object: SchedulerTask {
        override fun invoke(p1: Int) {
            currentSample = (currentSample + 1) and 7

            if (!isEnabled || control.frequency == 0) {
                taskIndex = -1
                scheduler.clear(p1)
            } else {
                scheduler.schedule(control.cyclesPerIncrement.toLong(), this, p1)
            }
        }
    }

    override fun getSample(): Int {
        if (!isEnabled || control.frequency == 0) return 0

        return dutyEnvelope.pattern[currentSample] * volume
    }

    override fun onReset() {
        if (control.frequency == 0) return
        isEnabled = true
        currentSample = 0

        if (dutyEnvelope.length == 0) dutyEnvelope.length = 0x3F

        volume = dutyEnvelope.initEnvelope()

        if (taskIndex == -1) taskIndex = scheduler.schedule(control.cyclesPerIncrement.toLong(), task)
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
}