package spAnGB.cpu.thumb

import spAnGB.cpu.*
import spAnGB.cpu.arm.barrelShifterArithmeticRight
import spAnGB.cpu.arm.barrelShifterLogicalLeft
import spAnGB.cpu.arm.barrelShifterLogicalRight
import spAnGB.utils.bit
import spAnGB.utils.uLong

val thumbMovs = ThumbInstruction(   // TODO
    { "Movs" },
    {
        val src = registers[(instr ushr 3) and 0x7]
        val amount = (instr ushr 6) and 0x1F

        val value = when ((instr ushr 11) and 3) {
            0 -> barrelShifterLogicalLeft(src, amount)
            1 -> barrelShifterLogicalRight(src, if (amount == 0) 32 else amount)
            2 -> barrelShifterArithmeticRight(src, if (amount == 0) 32 else amount)
            else -> throw IllegalStateException()
        }

        registers[instr and 0x7] = value
        this[CPUFlag.C] = shifterCarry
        negativeAndZero(value)
    }
)

val thumbAddSub = ThumbInstruction(
    { "add/sub" },
    {
        val rn = if (instr bit 10) {
            (instr ushr 6) and 0x7
        } else {
            registers[(instr ushr 6) and 0x7]
        }

        val src = registers[(instr ushr 3) and 0x7]

        val result = if (instr bit 9) {
            (src - rn).also {
                subOverflow(it, src, rn)
                dumbBorrow(src.uLong - rn.uLong)
            }
        } else {
            (src + rn).also {
                overflow(it, src, rn)
                dumbCarry(src.uLong + rn.uLong)
            }
        }
        registers[instr and 0x7] = result

        negativeAndZero(result)
    }
)
