@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.apu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class MainVolumeControl: SimpleMMIO(mask = 0xFF77) {
    inline val volumeRight get() = value and 0x7
    inline val volumeLeft get() = value.ushr(4).and(7)

    inline infix fun isRightEnabled(channel: Int) = value bit (7 + channel)
    inline infix fun isLeftEnabled(channel: Int) = value bit (12 + channel)
}