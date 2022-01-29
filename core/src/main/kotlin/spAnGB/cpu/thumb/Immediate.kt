package spAnGB.cpu.thumb

import spAnGB.cpu.CPUFlag
import spAnGB.cpu.ThumbInstruction

private inline val Int.destination get() = ushr(8).and(0x7)
private inline val Int.immediate get() = and(0xFF)

val thumbMovImm = ThumbInstruction(
    { "MovImm" },
    { instr ->
        registers[instr.destination] = instr.immediate.apply {
            set(CPUFlag.N, false)
            set(CPUFlag.Z, this == 0)
        }
    }
)

val thumbCmpImm = ThumbInstruction(
    { "CmpImm" },
    { instr ->
        val rd = registers[instr.destination]
        val result = rd - instr.immediate

        this[CPUFlag.N] = result < 0
        this[CPUFlag.Z] = result == 0
        this[CPUFlag.C] = !(rd >= 0 && rd < instr.immediate)
        this[CPUFlag.V] = rd < 0 && result > 0
    }
)

val thumbSubImm = ThumbInstruction(
    { "SubImm" },
    { instr ->
        val rd = registers[instr.destination]
        val result = rd - instr.immediate
        registers[instr.destination] = result

        this[CPUFlag.N] = result < 0
        this[CPUFlag.Z] = result == 0
        this[CPUFlag.C] = !(rd >= 0 && rd < instr.immediate)
        this[CPUFlag.V] = rd < 0 && result > 0
    }
)

val thumbAddImm = ThumbInstruction(
    { "AddImm" },
    { instr ->
        val rd = registers[instr.destination]
        val result = rd + instr.immediate
        registers[instr.destination] = result

        this[CPUFlag.N] = result < 0
        this[CPUFlag.Z] = result == 0
        this[CPUFlag.C] =
            (rd.toUInt().toULong() + instr.immediate.toUInt().toULong()).shr(32) and 0x1uL != 0uL  // TODO: dumb carry
        this[CPUFlag.V] = rd > 0 && result < 0
    }
)