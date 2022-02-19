package spAnGB.apu

import spAnGB.apu.channels.*
import spAnGB.apu.mmio.MainVolumeControl
import spAnGB.apu.mmio.SoundBias
import spAnGB.apu.mmio.SoundControl
import spAnGB.apu.mmio.SoundMasterControl

class APU {
    val sweep = SweepToneChannel()
    val tone = ToneChannel()
    val wave = WaveChannel()
    val noise = NoiseChannel()
    val fifoA = FIFOChannel()
    val fifoB = FIFOChannel()

    val master = SoundMasterControl()
    val control = SoundControl()
    val volume = MainVolumeControl()
    val bias = SoundBias()
}