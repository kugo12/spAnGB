package spAnGB.ppu.mmio

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class BackgroundControl : SimpleMMIO() {
    inline val priority: Int get() = value and 0x3
    inline val characterBaseBlock: Int get() = value.and(0xC).ushr(2)
    inline val mosaic: Boolean get() = value bit 6
    inline val isSinglePalette: Boolean get() = value bit 7
    inline val screenBaseBlock: Int get() = value.and(0x1F00).ushr(8)
    inline val areaOverflow: Boolean get() = value bit 13  // only bg 2+3
    inline val size: Int get() = value.and(0xC000).ushr(14)
}