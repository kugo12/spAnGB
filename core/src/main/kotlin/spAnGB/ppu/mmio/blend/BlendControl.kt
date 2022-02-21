package spAnGB.ppu.mmio.blend

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class BlendControl: SimpleMMIO(mask = 0x3FFF) {
    inline val firstBg0 get() = value bit 0
    inline val firstBg1 get() = value bit 1
    inline val firstBg2 get() = value bit 2
    inline val firstBg3 get() = value bit 3
    inline val firstObj get() = value bit 4
    inline val firstBd get() = value bit 5

    inline val mode get() = value.ushr(6).and(3)

    inline val secondBg0 get() = value bit 8
    inline val secondBg1 get() = value bit 9
    inline val secondBg2 get() = value bit 10
    inline val secondBg3 get() = value bit 11
    inline val secondObj get() = value bit 12
    inline val secondBd get() = value bit 13

    inline infix fun isFirst(index: Int) = value bit index
    inline infix fun isSecond(index: Int) = value bit (index + 8)
}