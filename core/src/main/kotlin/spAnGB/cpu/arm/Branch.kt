@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu.arm

import spAnGB.cpu.ARMInstruction
import spAnGB.cpu.CPUFlag
import spAnGB.cpu.CPUMode
import spAnGB.utils.hex

private inline fun Int.extend() = this shl 8 shr 6

val armBx = ARMInstruction(
    { "bx ${registers[it and 0xF].hex}" },
    {
        pc = registers[instr and 0xF]
        changeState(pc and 1)
    }
)

val armB = ARMInstruction(
    { "b ${pc.hex} + ${it.extend()} = ${(pc + it.extend()).hex}" },
    {
        pc += instr.extend()
        armRefill()
    }
)

val armBl = ARMInstruction(
    { "bl ${pc.hex} + ${it.extend()} = ${(pc + it.extend()).hex}" },
    {
        lr = pc - 4
        pc += instr.extend()
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

