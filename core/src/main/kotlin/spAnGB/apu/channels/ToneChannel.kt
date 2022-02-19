package spAnGB.apu.channels

import spAnGB.apu.mmio.DutyEnvelopeControl
import spAnGB.apu.mmio.FrequencyControl

class ToneChannel {
    val dutyEnvelope = DutyEnvelopeControl()
    val control = FrequencyControl()
}