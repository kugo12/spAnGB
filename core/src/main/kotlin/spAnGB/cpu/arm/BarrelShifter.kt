@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu.arm

import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag
import spAnGB.utils.bit
import spAnGB.utils.toInt

fun CPU.barrelShifterLogicalLeft(value: Int, amount: Int): Int = when {
    amount > 31 -> 0.also { shifterCarry = (amount == 32) && (value and 1 != 0) }
    amount == 0 -> value.also { shifterCarry = this[CPUFlag.C] }
    else -> value.shl(amount).also { shifterCarry = value bit (32 - amount) }
}

fun CPU.barrelShifterLogicalRight(value: Int, amount: Int): Int = when {
    amount > 31 -> 0.also { shifterCarry = (amount == 32) && (value < 0) }
    amount == 0 -> value.also { shifterCarry = this[CPUFlag.C] }
    else -> value.ushr(amount).also { shifterCarry = value bit (amount - 1) }
}

fun CPU.barrelShifterArithmeticRight(value: Int, amount: Int): Int = when {
    amount > 31 -> value.shr(31).also { shifterCarry = value < 0 }
    amount == 0 -> value.also { shifterCarry = this[CPUFlag.C] }
    else -> value.shr(amount).also { shifterCarry = value.bit(amount - 1) }
}

fun CPU.barrelShifterRotateRight(value: Int, amount: Int): Int = when (amount) {
    0 -> value.also { shifterCarry = this[CPUFlag.C] }
    else -> value.rotateRight(amount).also { shifterCarry = it < 0 }
}

fun CPU.barrelShifterRotateRightExtended(value: Int): Int =
    (value ushr 1)
        .or(this[CPUFlag.C].toInt() shl 31)
        .also { shifterCarry = value and 1 != 0 }

fun CPU.operand(operand: Int, value: Int): Int {
    var shiftType = operand ushr 1 and 0x3
    val amount = when (operand bit 0) {
        true -> registers[operand ushr 4 and 0xF] and 0xFF
        false -> {
            var imm = operand ushr 3 and 0x1F

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