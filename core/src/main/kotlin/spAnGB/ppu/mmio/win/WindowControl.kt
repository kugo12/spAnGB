package spAnGB.ppu.mmio.win

import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit

class WindowInsideControl: SimpleMMIO(mask = 0x3F3F) {
    inline val isWin0Bg0 get() = value bit 0
    inline val isWin0Bg1 get() = value bit 1
    inline val isWin0Bg2 get() = value bit 2
    inline val isWin0Bg3 get() = value bit 3
    inline val isWin0Obj get() = value bit 4
    inline val isWin0SpecialEffects get() = value bit 5

    inline val isWin1Bg0 get() = value bit 8
    inline val isWin1Bg1 get() = value bit 9
    inline val isWin1Bg2 get() = value bit 10
    inline val isWin1Bg3 get() = value bit 11
    inline val isWin1Obj get() = value bit 12
    inline val isWin1SpecialEffects get() = value bit 13

    inline infix fun isWin0Bg(index: Int) = value bit index
    inline infix fun isWin1Bg(index: Int) = value bit (index + 8)
}

class WindowOutsideControl: SimpleMMIO(mask = 0x3F3F) {
    inline val isBg0 get() = value bit 0
    inline val isBg1 get() = value bit 1
    inline val isBg2 get() = value bit 2
    inline val isBg3 get() = value bit 3
    inline val isObj get() = value bit 4
    inline val isSpecialEffects get() = value bit 5

    inline val isObjWinBg0 get() = value bit 8
    inline val isObjWinBg1 get() = value bit 9
    inline val isObjWinBg2 get() = value bit 10
    inline val isObjWinBg3 get() = value bit 11
    inline val isObjWinObj get() = value bit 12
    inline val isObjWinSpecialEffects get() = value bit 13

    inline infix fun isBg(index: Int) = value bit index
    inline infix fun isObjWinBg(index: Int) = value bit (index + 8)
}