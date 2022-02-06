package spAnGB.cpu

import spAnGB.utils.bit

enum class CPUFlag(val description: String, val mask: Int) {
    N("Negative", 0x80000000u.toInt()),  // sign
    Z("Zero", 0x40000000),    // zero
    C("Carry", 0x20000000),    // carry (0-borrow/no carry, 1-no borrow/carry)
    V("Overflow", 0x10000000),    // overflow
    I("IRQ disable", 0x80),          // IRQ disable
    F("FIQ disable", 0x40),          // FIQ disable
    T("Thumb", 0x20),          // state bit (1 - thumb, 0 - arm)
}

fun flagLutFactory(): BooleanArray {
    return BooleanArray(256) {
        val n = it bit 7
        val z = it bit 6
        val c = it bit 5
        val v = it bit 4

        when (it and 0xF) {
            0x0 -> z
            0x1 -> !z
            0x2 -> c
            0x3 -> !c
            0x4 -> n
            0x5 -> !n
            0x6 -> v
            0x7 -> !v
            0x8 -> c && !z
            0x9 -> !c || z
            0xA -> n == v
            0xB -> n != v
            0xC -> !z && (n == v)
            0xD -> z || (n != v)
            0xE -> true
            0xF -> false
            else -> throw IllegalStateException()
        }
    }
}