package spAnGB.cpu.arm

import spAnGB.cpu.ARMInstruction
import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag
import spAnGB.cpu.CPUMode
import spAnGB.utils.hex
import kotlin.math.absoluteValue

private inline fun extendOffset(offset: Int): Int {
    val unsigned = (offset and 0x7FFFFF) shl 2
    val sign = ((offset and 0x800000) shl 8) shr 6
    return unsigned or sign
}

val armBx = ARMInstruction(
    { "bx ${registers[it and 0xF].hex}" },
    {
        pc = registers[instr and 0xF]
        changeState(pc and 1)
    }
)

val armB = ARMInstruction(
    { "b ${pc.hex} + ${extendOffset(it)} = ${(pc+extendOffset(it)).hex}" },
    {
        pc += extendOffset(instr)
        armRefill()
    }
)

val armBl = ARMInstruction(
    { "bl ${pc.hex} + ${extendOffset(it)} = ${(pc+extendOffset(it)).hex}" },
    {
        lr = pc - 4
        pc += extendOffset(instr)
        armRefill()
    }
)

val armSwi = ARMInstruction(
    { "Swi" },
    {
        val cpsr = cpsr

        setCPUMode(CPUMode.Supervisor)

        lr = pc - 4
        pc = 0x08
        spsr = cpsr

        this[CPUFlag.I] = true
        armRefill()
    }
)

