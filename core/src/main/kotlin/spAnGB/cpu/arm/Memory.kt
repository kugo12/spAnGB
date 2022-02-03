package spAnGB.cpu.arm

import spAnGB.cpu.ARMInstruction
import spAnGB.cpu.CPU
import spAnGB.cpu.CPUMode
import spAnGB.utils.bit
import spAnGB.utils.hex
import spAnGB.utils.uInt

class MemoryAccessDsl(
    val cpu: CPU,
    val instruction: Int,
    offsetCalc: CPU.(Int) -> Int = {
        when (instruction bit 25) {
            true -> cpu.operand(instruction ushr 4, cpu.registers[instruction and 0xF])
            false -> instruction and 0xFFF
        }
    }
) {
    val offset = offsetCalc(cpu, instruction)
    val base = (instruction ushr 16) and 0xF

    val addressWithOffset = when (instruction bit 23) {
        true -> cpu.registers[base] + offset
        false -> cpu.registers[base] - offset
    }

    val srcOrDst = (instruction ushr 12) and 0xF
    val pre = instruction bit 24
    val address = if (pre) addressWithOffset else cpu.registers[base]

    inline fun CPU.saveAddressWithOffsetIfEnabled() {
        if ((pre && instruction bit 21) || !pre)
            registers[base] = addressWithOffset
    }

    inline fun perform(func: CPU.() -> Unit) {
        cpu.func()
    }
}

private inline fun memoryInstruction(crossinline func: MemoryAccessDsl.() -> Unit): CPU.(op: Int) -> Unit =
    { instr ->
        MemoryAccessDsl(this, instr).func()
    }

private inline fun hsbMemoryInstruction(crossinline func: MemoryAccessDsl.() -> Unit): CPU.(Int) -> Unit =
    { instruction ->
        MemoryAccessDsl(
            this, instruction
        ) {
            when (instruction bit 22) {
                true -> instruction.ushr(4).and(0xF0).or(instruction and 0xF)
                false -> registers[instruction and 0xF]
            }
        }.func()
    }

val armSwp = ARMInstruction(
    { "Swp" },
    { instr ->
        val rn = registers[(instr ushr 16) and 0xF]
        val rm = registers[instr and 0xF]
        val dest = (instr ushr 12) and 0xF

        when (instr bit 22) {
            true -> {  // byte
                registers[dest] = bus.read8(rn).uInt
                bus.write8(rn, rm.toByte())
            }
            false -> {  // word
                registers[dest] = bus
                    .read32(rn)
                    .rotateRight((rn and 3) shl 3)
                bus.write32(rn, rm)
            }
        }
    }
)

val armLdr = ARMInstruction(
    { "Ldr" },
    memoryInstruction {
        perform {
            val value = when (instruction bit 22) {
                true -> bus.read8(address).uInt
                false -> bus.read32(address).rotateRight((address and 3) shl 3)
            }
            saveAddressWithOffsetIfEnabled()
            registers[srcOrDst] = value
        }
    }
)

val armStr = ARMInstruction(
    { "Str" },
    { instr ->
        pc += 4
        val offset = when (instr bit 25) {
            true -> operand(instr ushr 4, registers[instr and 0xF])
            false -> instr and 0xFFF
        }

        val base = (instr ushr 16) and 0xF
        val addr = when (instr bit 23) {
            true -> registers[base] + offset
            false -> registers[base] - offset
        }
        val src = registers[(instr ushr 12) and 0xF]

        if (instr bit 24) {  // pre
            when (instr bit 22) {
                true -> bus.write8(addr, src.toByte())
                false -> bus.write32(addr, src)
            }

            pc -= 4
            if (instr bit 21) {
                registers[base] = addr
            }
        } else {
            when (instr bit 22) {
                true -> bus.write8(registers[base], src.toByte())
                false -> bus.write32(registers[base], src)
            }

            pc -= 4
            registers[base] = addr
        }
    }
)

val armLdrhsb = ARMInstruction(
    { "Ldrhsb" },
    hsbMemoryInstruction { // LDRH LDRSH LDRB LDRSB
        perform {
            val value = when ((instruction ushr 5) and 0x3) {  // TODO
                0 -> bus.read8(address).uInt
                1 -> bus.read16(address).uInt.rotateRight((address and 1) shl 3)
                2 -> bus.read8(address).toInt()
                3 -> bus.read16(address).toInt() shr ((address and 1) shl 3)
                else -> throw IllegalStateException("Unreachable")
            }
            saveAddressWithOffsetIfEnabled()
            registers[srcOrDst] = value
        }
    }
)

val armStrhsb = ARMInstruction(
    { "Strhsb" },
    { instr ->
        pc += 4
        val offset = when (instr bit 22) {
            true -> ((instr ushr 4) and 0xF0) or (instr and 0xF)
            false -> registers[instr and 0xF]
        }

        val base = (instr ushr 16) and 0xF
        val addressWithOffset = when (instr bit 23) {
            true -> registers[base] + offset
            false -> registers[base] - offset
        }
        val pre = instr bit 24
        val src = registers[(instr ushr 12) and 0xF]

        val address = if (pre) addressWithOffset else registers[base]

        when ((instr ushr 5) and 0x3) {
            0 -> bus.write8(address, src.toByte())
            1 -> bus.write16(address, src.toShort())
            2 -> bus.write8(address, src.toByte())
            3 -> bus.write16(address, src.toShort())
        }

        pc -= 4
        if ((pre && instr bit 21) || !pre)
            registers[base] = addressWithOffset
    }
)

private inline fun registerList(instr: Int, reversed: Boolean): List<Int> =
    (0..15)
        .filter { instr bit it }
        .let { if (reversed) it.reversed() else it }


val armStm = ARMInstruction(
    { "stm" },
    { instr ->
        val pre = instr bit 24
        val up = instr bit 23
        val psrForceUser = instr bit 22
        val modeCopy = mode

        pc += 4

        val base = (instr ushr 16) and 0xF
        val regs = registerList(instr, !up)
        val first = (0..15).firstOrNull { instr bit it }
        var addr = registers[base]

        var baseInRegList: Int? = null

        if (pre) {
            when (up) {
                true -> addr += 4
                false -> addr -= 4
            }
        }

        if (psrForceUser) setCPUMode(CPUMode.User)

        if (regs.isEmpty()) {
            when {
                !up && !pre -> addr -= 0x3C
                !up && pre -> addr -= 0x3C
            }
            bus.write32(addr, pc)

            when {
                up && !pre -> addr += 0x40
                !up && !pre -> addr -= 4
                up && pre -> addr += 0x3C
            }
        } else {
            regs.forEach {
                bus.write32(addr, registers[it])
                if (it == base) {
                    baseInRegList = addr
                }

                when (up) {
                    true -> addr += 4
                    false -> addr -= 4
                }
            }
        }

        if (psrForceUser) setCPUMode(modeCopy)

        pc -= 4

        if (instr bit 21) {  // writeback
            if (pre && regs.isNotEmpty()) {
                when (up) {
                    true -> addr -= 4
                    false -> addr += 4
                }
            }

            if (baseInRegList != null && first != base)
                bus.write32(baseInRegList!!, addr)
            registers[base] = addr
        }

    }
)

val armLdm = ARMInstruction(
    { "ldm" },
    { instr ->
        val pre = instr bit 24
        val up = instr bit 23
        val psrForceUser = instr bit 22
        val modeCopy = mode

        val base = (instr ushr 16) and 0xF
        val regs = registerList(instr, !up)
        var addr = registers[base]

        var baseInRegList: Int? = null

        if (pre) {
            when (up) {
                true -> addr += 4
                false -> addr -= 4
            }
        }

        if (psrForceUser) {
            regs.find { it == 15 }?.let {
                TODO("reg 15 psrForceUser arm stm")
            }

            setCPUMode(CPUMode.User)
        }

        if (regs.isEmpty()) {
            registers[15] = bus.read32(addr)
            when (up) {
                true -> addr += 0x40
                false -> addr -= 0x40
            }
        } else {
            regs.forEach {
                registers[it] = bus.read32(addr)
                if (it == base) {
                    baseInRegList = addr
                }

                when (up) {
                    true -> addr += 4
                    false -> addr -= 4
                }
            }
        }

        if (psrForceUser) setCPUMode(modeCopy)

        if (instr bit 21) {  // writeback
            if (pre) {
                when (up) {
                    true -> addr -= 4
                    false -> addr += 4
                }
            }

            if (baseInRegList == null) {
                registers[base] = addr
            }
        }
    }
)