package spAnGB.cpu

enum class CPUMode(val mask: Int) {
    Interrupt(0b10010),  // irq
    Supervisor(0b10011),  // svc
    Abort(0b10111),  // abt
    Undefined(0b11011),  // und
    FastInterrupt(0b10001),  // fiq
    User(0b10000), // usr
    System(0b11111);  // sys

    companion object {
        @JvmField
        val byMask: Map<Int, CPUMode> = values().associateBy { it.mask }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Array<T>.get(index: CPUMode) = get(index.ordinal)