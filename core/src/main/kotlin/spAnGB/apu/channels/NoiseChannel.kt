package spAnGB.apu.channels

import spAnGB.apu.mmio.LengthEnvelopeControl
import spAnGB.apu.mmio.NoiseControl

class NoiseChannel {
    val envelope = LengthEnvelopeControl()
    val control = NoiseControl()
}