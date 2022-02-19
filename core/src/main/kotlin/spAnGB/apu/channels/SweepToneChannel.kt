package spAnGB.apu.channels

import spAnGB.apu.mmio.DutyEnvelopeControl
import spAnGB.apu.mmio.FrequencyControl
import spAnGB.apu.mmio.SweepControl

class SweepToneChannel {
    val sweep = SweepControl()
    val dutyEnvelope = DutyEnvelopeControl()
    val control = FrequencyControl()
}