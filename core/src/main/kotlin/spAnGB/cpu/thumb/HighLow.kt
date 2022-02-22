package spAnGB.cpu.thumb

import spAnGB.cpu.*
import spAnGB.utils.bit
import spAnGB.utils.toInt
import spAnGB.utils.uLong

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
    {
        val op = getOperands(instr)

        setRegister(op.destinationLocation, op.second)
    }
)

val thumbHiLoAdd = ThumbInstruction(
    { "HiLoAdd" },
    {
        val op = getOperands(instr)

        setRegister(op.destinationLocation, op.first + op.second)
    }
)

val thumbHiLoBx = ThumbInstruction(
    { "HiLoBx" },
    {
        val op = getOperands(instr)

        pc = op.second
        changeState(op.second and 1)
    }
)

val thumbHiLoCmp = ThumbInstruction(
    { "HiLoCmp" },
    {
        val op = getOperands(instr)

        val result = op.first - op.second

        negativeAndZero(result)
        subOverflow(result, op.first, op.second)
        dumbBorrow(op.first.uLong - op.second.uLong)
    }
)