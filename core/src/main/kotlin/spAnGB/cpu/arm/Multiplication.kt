package spAnGB.cpu.arm

import spAnGB.cpu.ARMInstruction
import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag
import spAnGB.utils.bit

// TODO: refactoring + splitting accumulative instructions, maybe some dsl here too?
// TODO: better descriptions

val armMulMla = ARMInstruction(
    { "mul/mla" },
    { instr ->
        val destination = (instr ushr 16) and 0xF
        val rn = when (instr bit 21) {
            true -> registers[(instr ushr 12) and 0xF]
            false -> 0
        }

        val rs = registers[(instr ushr 8) and 0xF]
        val rm = registers[instr and 0xF]
        val result = rm * rs + rn
        registers[destination] = result

        if (instr bit 20) {
            this[CPUFlag.C] = false
            this[CPUFlag.Z] = result == 0
            this[CPUFlag.N] = result < 0
        }
    }
)

val armUmullUmlal = ARMInstruction(
    { "Umull/Umlal" },
    { instr ->
        val rdLow = (instr ushr 12) and 0xF
        val rdHigh = (instr ushr 16) and 0xF
        val rn: ULong = when (instr bit 21) {
            true -> registers[rdLow].toUInt().toULong() or (registers[rdHigh].toUInt().toULong() shl 32)
            false -> 0uL
        }

        val rm = registers[instr and 0xF].toUInt().toULong()
        val rs = registers[(instr ushr 8) and 0xF].toUInt().toULong()
        val result = rm * rs + rn
        registers[rdLow] = result.toUInt().toInt()
        registers[rdHigh] = (result shr 32).toUInt().toInt()

        if (instr bit 20) {
//            this[CPUFlag.V] = false
//            this[CPUFlag.C] = false
            this[CPUFlag.Z] = result == 0uL
            this[CPUFlag.N] = result and 0x8000000000000000uL != 0uL
        }
    }
)

val armSmullSmlal = ARMInstruction(
    { "Smull/Smlal" },
    { instr ->
        val rdLow = (instr ushr 12) and 0xF
        val rdHigh = (instr ushr 16) and 0xF
        val rn = when (instr bit 21) {
            true -> registers[rdLow].toUInt().toLong() or (registers[rdHigh].toUInt().toLong() shl 32)
            false -> 0L
        }

        val rm = registers[instr and 0xF].toLong()
        val rs = registers[(instr ushr 8) and 0xF].toLong()
        val result = rm * rs + rn
        registers[rdLow] = result.toInt()
        registers[rdHigh] = result.ushr(32).toInt()

        if (instr bit 20) {
//            this[CPUFlag.V] = false
//            this[CPUFlag.C] = false
            this[CPUFlag.Z] = result == 0L
            this[CPUFlag.N] = result < 0L
        }
    }
)