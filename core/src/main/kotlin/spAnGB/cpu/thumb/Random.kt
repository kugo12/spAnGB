package spAnGB.cpu.thumb

import spAnGB.cpu.CPUFlag
import spAnGB.cpu.CPUInstruction
import spAnGB.cpu.ThumbInstruction
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
        this[CPUFlag.Z] = value == 0
        this[CPUFlag.N] = value < 0
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

        val value = if (instr bit 9) {
            this[CPUFlag.V] = (src xor rn) and (rn xor (src - rn)).inv() < 0
            this[CPUFlag.C] = !((src.uLong - rn.uLong) bit 32)
            src - rn
        } else {
            this[CPUFlag.V] = (src xor rn).inv() and (rn xor (src + rn)) < 0
            this[CPUFlag.C] = (src.uLong + rn.uLong) bit 32
            src + rn
        }
        registers[instr and 0x7] = value

        this[CPUFlag.N] = value < 0
        this[CPUFlag.Z] = value == 0
    }
)
