package spAnGB.apu.mmio

import spAnGB.cpu.CLOCK_SPEED
import spAnGB.utils.*

val DIVISOR = intArrayOf(8, 16, 32, 48, 64, 80, 96, 112)

class NoiseControl(
    onRestart: () -> Unit
) : FrequencyControl(onRestart) {
    inline val divider get() = value and 0x7
    inline val is7Bits get() = value bit 3
    inline val noiseFrequency get() = value.ushr(4).and(0xF)

    inline val cycles get() = CLOCK_SPEED / (524288.let {
        if (divider == 0) it*2 else it/divider
            }.ushr(noiseFrequency + 1))

}