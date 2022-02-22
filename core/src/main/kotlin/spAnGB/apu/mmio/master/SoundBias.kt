package spAnGB.apu.mmio.master

import spAnGB.memory.mmio.SimpleMMIO

class SoundBias: SimpleMMIO() {
    inline val level get() = value.ushr(1).and(0x1FF)
    inline val samplingRate get() = value.ushr(14).and(3)  // TODO
}