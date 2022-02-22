package spAnGB.cpu.thumb

import spAnGB.cpu.*
import spAnGB.utils.bit
import spAnGB.utils.uLong

private inline val Int.destination get() = ushr(8).and(0x7)
private inline val Int.immediate get() = and(0xFF)

val thumbMovImm = ThumbInstruction(
    { "MovImm" },
    {
        registers[instr.destination] = instr.immediate.apply {
            negativeAndZero(this)
        }
    }
)

val thumbCmpImm = ThumbInstruction(
    { "CmpImm" },
    {
        val imm = instr.immediate
        val rd = registers[instr.destination]
        val result = rd - imm

        negativeAndZero(result)
        dumbBorrow(rd.uLong - imm.uLong)
        subOverflow(result, rd, imm)
    }
)

val thumbSubImm = ThumbInstruction(
    { "SubImm" },
    {
        val imm = instr.immediate
        val rd = registers[instr.destination]
        val result = rd - imm
        registers[instr.destination] = result

        negativeAndZero(result)
        dumbBorrow(rd.uLong - imm.uLong)
        subOverflow(result, rd, imm)
    }
)

val thumbAddImm = ThumbInstruction(
    { "AddImm" },
    {
        val imm = instr.immediate
        val rd = registers[instr.destination]
        val result = rd + imm
        registers[instr.destination] = result

        negativeAndZero(result)
        dumbCarry(rd.uLong + imm.uLong)
        overflow(result, rd, imm)
    }
)