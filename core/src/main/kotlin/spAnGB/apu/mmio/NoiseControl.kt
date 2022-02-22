package spAnGB.apu.mmio

import spAnGB.apu.channels.GB_CLOCK_SPEED
import spAnGB.cpu.CLOCK_SPEED
import spAnGB.utils.*

val DIVISOR = intArrayOf(8, 16, 32, 48, 64, 80, 96, 112)

class NoiseControl(
    onRestart: () -> Unit
) : FrequencyControl(onRestart) {
    inline val divider get() = DIVISOR[value and 0x7].shl(noiseFrequency)
    inline val is7Bits get() = value bit 3
    inline val noiseFrequency get() = value.ushr(4).and(0xF)
}