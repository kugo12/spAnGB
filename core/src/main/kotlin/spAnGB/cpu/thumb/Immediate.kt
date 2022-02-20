package spAnGB.cpu.thumb

import spAnGB.cpu.CPUFlag
import spAnGB.cpu.ThumbInstruction
import spAnGB.utils.bit
import spAnGB.utils.uLong

private inline val Int.destination get() = ushr(8).and(0x7)
private inline val Int.immediate get() = and(0xFF)

val thumbMovImm = ThumbInstruction(
    { "MovImm" },
    {
        registers[instr.destination] = instr.immediate.apply {
            set(CPUFlag.N, false)
            set(CPUFlag.Z, this == 0)
        }
    }
)

val thumbCmpImm = ThumbInstruction(
    { "CmpImm" },
    {
        val imm = instr.immediate
        val rd = registers[instr.destination]
        val result = rd - imm

        this[CPUFlag.N] = result < 0
        this[CPUFlag.Z] = result == 0
        this[CPUFlag.C] = !((rd.uLong - imm.uLong) bit 32)
        this[CPUFlag.V] = (rd xor imm) and (imm xor result).inv() < 0
    }
)

val thumbSubImm = ThumbInstruction(
    { "SubImm" },
    {
        val imm = instr.immediate
        val rd = registers[instr.destination]
        val result = rd - imm
        registers[instr.destination] = result

        this[CPUFlag.N] = result < 0
        this[CPUFlag.Z] = result == 0
        this[CPUFlag.C] = !((rd.uLong - imm.uLong) bit 32)
        this[CPUFlag.V] = (rd xor imm) and (imm xor result).inv() < 0
    }
)

val thumbAddImm = ThumbInstruction(
    { "AddImm" },
    {
        val imm = instr.immediate
        val rd = registers[instr.destination]
        val result = rd + imm
        registers[instr.destination] = result

        this[CPUFlag.N] = result < 0
        this[CPUFlag.Z] = result == 0
        this[CPUFlag.C] = (rd.uLong + imm.uLong) bit 32  // TODO: dumb carry
        this[CPUFlag.V] = (rd xor imm).inv() and (rd xor result) < 0
    }
)