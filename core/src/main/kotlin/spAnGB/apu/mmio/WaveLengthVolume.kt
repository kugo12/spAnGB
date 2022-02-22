package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit
import spAnGB.utils.override

val WaveVolume = intArrayOf(4, 0, 1, 2, 3)

class WaveLengthVolume: SimpleMMIO() {
    inline var length
        get() = value and 0xFF
        set(newValue) { value = value.override(0xFF, newValue) }

    inline val volume get() = WaveVolume[if (value bit 15) 4 else value.ushr(13).and(3)]
}