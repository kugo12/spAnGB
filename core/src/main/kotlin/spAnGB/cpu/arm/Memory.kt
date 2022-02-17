package spAnGB.cpu.arm

import spAnGB.cpu.ARMInstruction
import spAnGB.memory.AccessType.*
import spAnGB.cpu.CPUMode
import spAnGB.utils.bit
import spAnGB.utils.uInt


val armSwp = ARMInstruction(
    { "Swp" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        val rn = registers[(instr ushr 16) and 0xF]
        val rm = registers[instr and 0xF]
        val dest = (instr ushr 12) and 0xF

        when (instr bit 22) {
            true -> {  // byte
                setRegister(dest, bus.read8(rn, NonSequential).uInt)
                bus.write8(rn, rm.toByte(), NonSequential)
            }
            false -> {  // word
                setRegister(
                    dest,
                    bus
                        .read32(rn, NonSequential)
                        .rotateRight((rn and 3) shl 3)
                )
                bus.write32(rn, rm, NonSequential)
            }
        }
    }
)

val armLdr = ARMInstruction(
    { "Ldr" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        val base = (instruction ushr 16) and 0xF
        val srcOrDst = (instruction ushr 12) and 0xF
        val pre = instruction bit 24

        val offset = when (instruction bit 25) {
            true -> operand(instruction ushr 4, registers[instruction and 0xF])
            false -> instruction and 0xFFF
        }
        val addressWithOffset = when (instruction bit 23) {
            true -> registers[base] + offset
            false -> registers[base] - offset
        }
        val address = if (pre) addressWithOffset else registers[base]

        val value = when (instruction bit 22) {
            true -> bus.read8(address, NonSequential).uInt
            false -> bus.read32(address, NonSequential).rotateRight((address and 3) shl 3)
        }

        if ((pre && instruction bit 21) || !pre)
            setRegister(base, addressWithOffset)
        setRegister(srcOrDst, value)
    }
)

val armStr = ARMInstruction(
    { "Str" },
    {
        prefetchAccess = NonSequential
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
                true -> bus.write8(addr, src.toByte(), NonSequential)
                false -> bus.write32(addr, src, NonSequential)
            }

            pc -= 4
            if (instr bit 21) {
                setRegister(base, addr)
            }
        } else {
            when (instr bit 22) {
                true -> bus.write8(registers[base], src.toByte(), NonSequential)
                false -> bus.write32(registers[base], src, NonSequential)
            }

            pc -= 4
            setRegister(base, addr)
        }
    }
)

val armLdrhsb = ARMInstruction(
    { "Ldrhsb" }, // LDRH LDRSH LDRB LDRSB
    {
        bus.idle()
        prefetchAccess = NonSequential

        val base = (instruction ushr 16) and 0xF
        val srcOrDst = (instruction ushr 12) and 0xF
        val pre = instruction bit 24

        val offset = when (instruction bit 22) {
            true -> instruction.ushr(4).and(0xF0).or(instruction and 0xF)
            false -> registers[instruction and 0xF]
        }
        val addressWithOffset = when (instruction bit 23) {
            true -> registers[base] + offset
            false -> registers[base] - offset
        }
        val address = if (pre) addressWithOffset else registers[base]


        val value = when ((instruction ushr 5) and 0x3) {  // TODO
            0 -> bus.read8(address, NonSequential).uInt
            1 -> bus.read16(address, NonSequential).uInt.rotateRight((address and 1) shl 3)
            2 -> bus.read8(address, NonSequential).toInt()
            3 -> bus.read16(address, NonSequential).toInt() shr ((address and 1) shl 3)
            else -> throw IllegalStateException("Unreachable")
        }

        if ((pre && instruction bit 21) || !pre)
            setRegister(base, addressWithOffset)
        setRegister(srcOrDst, value)
    }
)

val armStrhsb = ARMInstruction(
    { "Strhsb" },
    {
        prefetchAccess = NonSequential
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
            0 -> bus.write8(address, src.toByte(), NonSequential)
            1 -> bus.write16(address, src.toShort(), NonSequential)
            2 -> bus.write8(address, src.toByte(), NonSequential)
            3 -> bus.write16(address, src.toShort(), NonSequential)
        }

        pc -= 4
        if ((pre && instr bit 21) || !pre)
            setRegister(base, addressWithOffset)
    }
)

@JvmField
val registerArray = IntArray(16) { -1 }
private inline fun registerList(instr: Int): IntArray {
    for (it in 0..15) if (instr bit it) registerArray[it] = it else registerArray[it] = -1

    return registerArray
}

val armStm = ARMInstruction(  // STM and LDM sequential stuff
    { "stm" },
    {
        prefetchAccess = NonSequential
        var pre = instr bit 24
        val increment = instr bit 23
        val psrForceUser = instr bit 22
        val modeCopy = mode

        pc += 4

        val base = (instr ushr 16) and 0xF
        val regs = registerList(instr)
        val n = regs.count { it != -1 }
        var newAddress = registers[base] + (if (increment) 4 else -4) * n
        var addr = registers[base] +
                (if (increment) 0 else -4 * n)

        val baseNotFirst = instr.and(1.shl(base).minus(1)) != 0
        var access = NonSequential
        if (!increment) pre = !pre

        if (psrForceUser) setCPUMode(CPUMode.User)

        if (instr and 0xFFFF == 0) {
            when {
                !increment && !pre -> newAddress -= 0x40
                !increment && pre -> newAddress -= 0x3C
                increment && pre -> newAddress += 4
            }
            bus.write32(newAddress, pc, access)

            when {
                !increment && pre -> newAddress -= 4
                increment && !pre -> newAddress += 0x40
                increment && pre -> newAddress += 0x3C
            }
        } else {
            for (it in regs) {
                if (it == -1) continue

                if (pre) addr += 4

                if (it == base && baseNotFirst) {
                    bus.write32(addr, newAddress, access)
                } else {
                    bus.write32(addr, registers[it], access)
                }

                access = Sequential
                if (!pre) addr += 4
            }
        }

        if (psrForceUser) setCPUMode(modeCopy)

        pc -= 4

        if (instr bit 21) {  // writeback
            setRegister(base, newAddress)
        }
    }
)

val armLdm = ARMInstruction(
    { "ldm" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        var pre = instr bit 24
        val increment = instr bit 23
        val psrForceUser = instr bit 22
        val modeCopy = mode

        val base = (instr ushr 16) and 0xF
        val regs = registerList(instr)
        val n = regs.count { it != -1 }
        var newAddress = registers[base] + (if (increment) 4 else -4) * n
        var addr = registers[base] +
                (if (increment) 0 else -4 * n)

        var access = NonSequential
        if (!increment) pre = !pre

        if (psrForceUser) {
            if (instr bit 15)
                TODO("reg 15 psrForceUser arm stm")

            setCPUMode(CPUMode.User)
        }

        if (instr and 0xFFFF == 0) {
            setRegister(15, bus.read32(addr, access))
            when (increment) {
                true -> newAddress += 0x40
                false -> newAddress -= 0x40
            }
        } else {
            for (it in regs) {
                if (it == -1) continue

                if (pre) addr += 4

                setRegister(it, bus.read32(addr, access))
                access = Sequential

                if (!pre) addr += 4
            }
        }

        if (psrForceUser) setCPUMode(modeCopy)

        if (instr bit 21 && !(instr bit base)) {
            setRegister(base, newAddress)
        }
    }
)