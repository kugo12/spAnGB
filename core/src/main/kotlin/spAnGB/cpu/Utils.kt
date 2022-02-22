@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu

import spAnGB.utils.bit

fun CPU.idleMul(operand: Int) {
    var o = operand
    do {
        bus.idle()
        o = o ushr 8
    } while (o != 0)
}

fun CPU.idleSmul(operand: Int) {
    var o = operand
    var helper = 0xFFFFFFFFu.toInt()
    do {
        bus.idle()
        o = o ushr 8
        helper = helper ushr 8
    } while (o != 0 && o != helper)
}

inline fun CPU.subOverflow(result: Int, op1: Int, op2: Int) {
    this[CPUFlag.V] = (op1 xor op2) and (op1 xor result) < 0
}

inline fun CPU.overflow(result: Int, op1: Int, op2: Int) {
    this[CPUFlag.V] = (op1 xor op2).inv() and (op1 xor result) < 0
}

inline fun CPU.negativeAndZero(result: Int) {
    this[CPUFlag.N] = result < 0
    this[CPUFlag.Z] = result == 0
}

inline fun CPU.dumbBorrow(result: Long) {
    this[CPUFlag.C] = !(result bit 32)
}

inline fun CPU.dumbCarry(result: Long) {
    this[CPUFlag.C] = result bit 32
}