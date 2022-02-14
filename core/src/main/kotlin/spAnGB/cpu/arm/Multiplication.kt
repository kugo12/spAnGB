package spAnGB.cpu.arm

import spAnGB.cpu.*
import spAnGB.memory.AccessType
import spAnGB.utils.bit
import spAnGB.utils.uLong

// TODO: refactoring + splitting accumulative instructions, maybe some dsl here too?
// TODO: better descriptions

val armMulMla = ARMInstruction(
    { "mul/mla" },
    {
        prefetchAccess = AccessType.NonSequential
        val destination = (instr ushr 16) and 0xF
        val rn = when (instr bit 21) {
            true -> {
                bus.idle()
                registers[(instr ushr 12) and 0xF]
            }
            false -> 0
        }

        val rs = registers[(instr ushr 8) and 0xF]
        val rm = registers[instr and 0xF]
        val result = rm * rs + rn
        idleSmul(rs)
        setRegister(destination, result)

        if (instr bit 20) {
            this[CPUFlag.C] = false
            this[CPUFlag.Z] = result == 0
            this[CPUFlag.N] = result < 0
        }
    }
)

val armUmullUmlal = ARMInstruction(
    { "Umull/Umlal" },
    {
        bus.idle()
        prefetchAccess = AccessType.NonSequential
        val rdLow = (instr ushr 12) and 0xF
        val rdHigh = (instr ushr 16) and 0xF
        val rn: Long = when (instr bit 21) {
            true -> {
                bus.idle()
                registers[rdLow].uLong or (registers[rdHigh].uLong shl 32)
            }
            false -> 0L
        }

        val rm = registers[instr and 0xF].uLong
        val rs = registers[(instr ushr 8) and 0xF].uLong
        val result = rm * rs + rn
        idleMul(rs.toInt())
        setRegister(rdLow, result.toInt())
        setRegister(rdHigh, (result shr 32).toInt())

        if (instr bit 20) {
            this[CPUFlag.Z] = result == 0L
            this[CPUFlag.N] = result bit 63
        }
    }
)

val armSmullSmlal = ARMInstruction(
    { "Smull/Smlal" },
    {
        bus.idle()
        prefetchAccess = AccessType.NonSequential
        val rdLow = (instr ushr 12) and 0xF
        val rdHigh = (instr ushr 16) and 0xF
        val rn = when (instr bit 21) {
            true -> {
                bus.idle()
                registers[rdLow].uLong or (registers[rdHigh].uLong shl 32)
            }
            false -> 0L
        }

        val rm = registers[instr and 0xF].toLong()
        val rs = registers[(instr ushr 8) and 0xF].toLong()
        val result = rm * rs + rn
        idleSmul(rs.toInt())
        setRegister(rdLow, result.toInt())
        setRegister(rdHigh, (result ushr 32).toInt())

        if (instr bit 20) {
            this[CPUFlag.Z] = result == 0L
            this[CPUFlag.N] = result < 0L
        }
    }
)