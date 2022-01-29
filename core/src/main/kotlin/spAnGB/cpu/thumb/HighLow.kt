package spAnGB.cpu.thumb

import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag
import spAnGB.cpu.ThumbInstruction
import spAnGB.utils.bit
import spAnGB.utils.toInt

private class HiLoOperands(val first: Int, val second: Int, val destinationLocation: Int)

private fun CPU.getOperands(instr: Int): HiLoOperands {
    val h1 = (instr bit 7).toInt() * 8
    val h2 = (instr bit 6).toInt() * 8

    return HiLoOperands(
        registers[(instr and 0x7) + h1],
        registers[((instr ushr 3) and 0x7) + h2],
        (instr and 0x7) + h1
    )
}

val thumbHiLoMov = ThumbInstruction(
    { "HiLoMov" },
    { instr ->
        val op = getOperands(instr)

        registers[op.destinationLocation] = op.second
    }
)

val thumbHiLoAdd = ThumbInstruction(
    { "HiLoAdd" },
    { instr ->
        val op = getOperands(instr)

        registers[op.destinationLocation] = op.first + op.second
    }
)

val thumbHiLoBx = ThumbInstruction(
    { "HiLoBx" },
    { instr ->
        val op = getOperands(instr)

        pc = op.second
        changeState(op.second and 1)
    }
)

val thumbHiLoCmp = ThumbInstruction(
    { "HiLoCmp" },
    { instr ->
        val op = getOperands(instr)

        val tmp = op.first - op.second

        this[CPUFlag.N] = tmp < 0
        this[CPUFlag.Z] = tmp == 0
        this[CPUFlag.V] = (op.second xor op.first) and (op.first xor tmp).inv() < 0
        this[CPUFlag.C] = op.first.toUInt().toULong() - op.second.toUInt().toULong() <= UInt.MAX_VALUE.toULong()
    }
)