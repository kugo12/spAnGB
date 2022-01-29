package spAnGB.cpu

//const CPU_MODE: [u32; 7] = [0b10010, 0b10011, 0b10111, 0b11011, 0b10001, 0b10000, 0b11111];
//#[derive(PartialEq, Copy, Clone)]
//pub enum CPU_mode {
//    irq,
//    svc,  // supervisor
//    abt,  // abort mode
//    und,  // undefined
//    fiq,
//    usr,  // user
//    sys,  // system
//}

enum class CPUMode(val mask: Int) {
    Interrupt(0b10010),  // irq
    Supervisor(0b10011),  // svc
    Abort(0b10111),  // abt
    Undefined(0b11011),  // und
    FastInterrupt(0b10001),  // fiq
    User(0b10000), // usr
    System(0b11111);  // sys

    companion object {
        val byMask: Map<Int, CPUMode> = values().associateBy { it.mask }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Array<T>.get(index: CPUMode) = get(index.ordinal)