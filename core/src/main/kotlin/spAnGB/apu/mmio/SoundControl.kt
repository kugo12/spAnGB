package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO

class SoundControl: SimpleMMIO(mask = 0x770F)  // TODO