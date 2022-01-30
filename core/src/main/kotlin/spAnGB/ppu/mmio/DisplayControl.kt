package spAnGB.ppu.mmio

import spAnGB.memory.Memory
import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

enum class DisplayControlFlag(val mask: Int) {
    DisplayFrameSelect(0x10),
    HBlankIntervalFree(0x20),
    ObjVRAMMapping(0x40),
    ForcedBlank(0x80),
    DisplayBG0(0x100),
    DisplayBG1(0x200),
    DisplayBG2(0x400),
    DisplayBG3(0x800),
    DisplayOBJ(0x1000),
    DisplayWin0(0x2000),
    DisplayWin1(0x4000),
    DisplayWinObj(0x8000)
}

class DisplayControl : SimpleMMIO() {
    inline val bgMode: Int get() = value and 7

    inline val isBg0: Boolean get() = value bit 8
    inline val isBg1: Boolean get() = value bit 9
    inline val isBg2: Boolean get() = value bit 10
    inline val isBg3: Boolean get() = value bit 11

    inline val isObj: Boolean get() = value bit 12
    inline val isWin0: Boolean get() = value bit 13
    inline val isWin1: Boolean get() = value bit 14
    inline val isWinObj: Boolean get() = value bit 15
}