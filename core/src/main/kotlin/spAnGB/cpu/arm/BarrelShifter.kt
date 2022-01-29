@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu.arm

import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag
import spAnGB.utils.bit
import spAnGB.utils.toInt
import java.lang.IllegalStateException

inline fun CPU.barrelShifterLogicalLeft(value: Int, amount: Int): Pair<Int, Boolean> =
    if (amount > 31) {
        Pair(
            0,
            (amount == 32) && (value and 1 != 0)
        )
    } else if (amount == 0) {
        Pair(
            value,
            this[CPUFlag.C]
        )
    } else {
        Pair(
            value shl amount,
            value bit (32 - amount)
        )
    }

inline fun CPU.barrelShifterLogicalRight(value: Int, amount: Int): Pair<Int, Boolean> =
    if (amount > 31) {
        Pair(
            0,
            (amount == 32) && (value < 0)
        )
    } else if (amount == 0) {
        Pair(
            value,
            this[CPUFlag.C]
        )
    } else {
        Pair(
            value ushr amount,
            value bit (amount - 1)
        )
    }

inline fun CPU.barrelShifterArithmeticRight(value: Int, amount: Int): Pair<Int, Boolean> =
    if (amount > 31) {
        Pair(
            value shr 31,
            value < 0
        )
    } else if (amount == 0) {
        Pair(
            value,
            this[CPUFlag.C]
        )
    } else {
        Pair(
            value shr amount,
            value.bit(amount - 1)
        )
    }

inline fun CPU.barrelShifterRotateRight(value: Int, amount: Int): Pair<Int, Boolean> =
    if (amount == 0) {
        Pair(value, this[CPUFlag.C])
    } else {
        val tmp = value.rotateRight(amount)
        Pair(tmp, tmp < 0)
    }

inline fun CPU.barrelShifterRotateRightExtended(value: Int): Pair<Int, Boolean> =
    Pair(
        (value ushr 1) or (this[CPUFlag.C].toInt().shl(31)),
        value and 1 != 0
    )

inline fun CPU.operand(operand: Int, value: Int): Pair<Int, Boolean> {
    var shiftType = (operand ushr 1) and 0x3
    val amount = when (operand bit 0) {
        true -> registers[(operand ushr 4) and 0xF] and 0xFF
        false -> {
            var imm = (operand ushr 3) and 0x1F

            if (imm == 0) {
                when (shiftType) {
                    1, 2 -> imm = 32
                    3 -> shiftType = 4
                }
            }

            imm
        }
    }

    return when (shiftType) {
        0 -> barrelShifterLogicalLeft(value, amount)
        1 -> barrelShifterLogicalRight(value, amount)
        2 -> barrelShifterArithmeticRight(value, amount)
        3 -> barrelShifterRotateRight(value, amount)
        4 -> barrelShifterRotateRightExtended(value)
        else -> throw IllegalStateException("operand shiftType == $shiftType")
    }
}