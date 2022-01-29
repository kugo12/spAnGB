package spAnGB.cpu

enum class CPUFlag(val description: String, val mask: Int) {
    N("Negative", 0x80000000u.toInt()),  // sign
    Z("Zero", 0x40000000),    // zero
    C("Carry", 0x20000000),    // carry (0-borrow/no carry, 1-no borrow/carry)
    V("Overflow", 0x10000000),    // overflow
    I("IRQ disable", 0x80),          // IRQ disable
    F("FIQ disable", 0x40),          // FIQ disable
    T("Thumb", 0x20),          // state bit (1 - thumb, 0 - arm)
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun BooleanArray.get(index: CPUFlag) = get(index.ordinal)

@Suppress("NOTHING_TO_INLINE")
inline operator fun BooleanArray.set(index: CPUFlag, value: Boolean) = set(index.ordinal, value)