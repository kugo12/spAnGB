package spAnGB.apu.channels

import spAnGB.apu.mmio.FrequencyControl
import spAnGB.apu.mmio.WaveLengthVolume
import spAnGB.apu.mmio.WaveRAM
import spAnGB.apu.mmio.WaveRamSelect

class WaveChannel {
    val select = WaveRamSelect()
    val volume = WaveLengthVolume()
    val control = FrequencyControl()
    val ram = WaveRAM(select)
}