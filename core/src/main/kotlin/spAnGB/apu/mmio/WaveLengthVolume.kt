package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

val WaveVolume = intArrayOf(0, 100, 50, 25, 75)

class WaveLengthVolume: SimpleMMIO() {  // TODO: some better volume representation
    inline val length get() = value and 0xFF
    inline val volume get() = if (value bit 15) 4 else value.ushr(13).and(3)
}