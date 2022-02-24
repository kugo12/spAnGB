package spAnGB.apu.channels

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.apu.mmio.LengthEnvelopeControl
import spAnGB.apu.mmio.NoiseControl
import spAnGB.cpu.CLOCK_SPEED

class NoiseChannel(
    val scheduler: Scheduler
) : Channel, WithLength, WithEnvelope {
    val envelope = LengthEnvelopeControl()
    val control = NoiseControl(::onReset)

    var isEnabled = false
    var volume = 0
    var lfsr = 0
    private var sample = 0

    inline val cyclesPerIncrement get() = control.cycles

    var taskIndex = -1
    val task = object: SchedulerTask {
        override fun invoke(p1: Int) {
            val carry = lfsr and 1

            if (carry != 0) {
                lfsr = lfsr.ushr(1).xor(if (control.is7Bits) 0x60 else 0x6000)
            }

            sample = carry * 2 - 1

            if (!isEnabled) {
                taskIndex = -1
                scheduler.clear(p1)
            } else {
                scheduler.schedule(cyclesPerIncrement.toLong(), this, p1)
            }
        }
    }

    override fun getSample(): Int {
        if (!isEnabled) return 0

        return sample * volume
    }

    override fun onReset() {
        isEnabled = true
        lfsr = if (control.is7Bits) 0x40 else 0x4000
        sample = 1

        if (envelope.length == 0) envelope.length = 0x3F

        volume = envelope.initEnvelope()

        if (taskIndex == -1) taskIndex = scheduler.schedule(cyclesPerIncrement.toLong(), task)
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
}