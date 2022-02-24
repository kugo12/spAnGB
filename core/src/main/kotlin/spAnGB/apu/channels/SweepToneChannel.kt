package spAnGB.apu.channels

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.apu.mmio.DutyEnvelopeControl
import spAnGB.apu.mmio.FrequencyControl
import spAnGB.apu.mmio.SweepControl
import spAnGB.cpu.CLOCK_SPEED

class SweepToneChannel(
    val scheduler: Scheduler
): Channel, WithLength, WithSweep, WithEnvelope {
    val sweep = SweepControl()
    val dutyEnvelope = DutyEnvelopeControl()
    val control = FrequencyControl(::onReset)

    var isEnabled = false
    var volume = 0
    var sweepStep = 0
    var frequency = 0
    var currentSample = 0
    var isSweepEnabled = false

    inline val cyclesPerIncrement get() = CLOCK_SPEED / (131072/(2048-frequency)) / 8

    var taskIndex = -1
    val task = object: SchedulerTask {
        override fun invoke(p1: Int) {
            currentSample = (currentSample + 1) and 7

            if (!isEnabled || frequency == 0) {
                taskIndex = -1
                scheduler.clear(p1)
            } else {
                scheduler.schedule(cyclesPerIncrement.toLong(), this, p1)
            }
        }
    }

    override fun getSample(): Int {
        if (!isEnabled || frequency == 0) return 0

        return dutyEnvelope.pattern[currentSample] * volume
    }

    override fun onReset() {
        if (control.frequency == 0) return
        currentSample = 0
        isEnabled = true
        frequency = control.frequency
        isSweepEnabled = sweep.isEnabled

        if (dutyEnvelope.length == 0) dutyEnvelope.length = 0x3F

        if (isSweepEnabled) calculateSweep()

        volume = dutyEnvelope.initEnvelope()

        if (taskIndex == -1) taskIndex = scheduler.schedule(cyclesPerIncrement.toLong(), task)
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
        if (isSweepEnabled) {
            if (sweepStep > 0) {
                sweepStep -= 1
            } else {
                calculateSweep()
            }
        }
    }

    fun calculateSweep() {
        frequency += (if (sweep.isDecrease) -(frequency ushr sweep.shift) else frequency ushr sweep.shift)

        if (frequency <= 0 || frequency >= 2048) {
            isSweepEnabled = false
            frequency = frequency.coerceIn(0, 2047)
        }

        sweepStep = sweep.time
    }
}