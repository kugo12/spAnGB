package spAnGB.cpu.arm

import spAnGB.cpu.ARMInstruction
import spAnGB.cpu.CPU
import spAnGB.cpu.CPUMode
import spAnGB.utils.bit
import spAnGB.utils.uInt


val armSwp = ARMInstruction(
    { "Swp" },
    {
        val rn = registers[(instr ushr 16) and 0xF]
        val rm = registers[instr and 0xF]
        val dest = (instr ushr 12) and 0xF

        when (instr bit 22) {
            true -> {  // byte
                setRegister(dest, bus.read8(rn).uInt)
                bus.write8(rn, rm.toByte())
            }
            false -> {  // word
                setRegister(
                    dest,
                    bus
                        .read32(rn)
                        .rotateRight((rn and 3) shl 3)
                )
                bus.write32(rn, rm)
            }
        }
    }
)

val armLdr = ARMInstruction(
    { "Ldr" },
    {
        val instruction = pipelineHead

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
            true -> bus.read8(address).uInt
            false -> bus.read32(address).rotateRight((address and 3) shl 3)
        }

        if ((pre && instruction bit 21) || !pre)
            setRegister(base, addressWithOffset)
        setRegister(srcOrDst, value)
    }
)

val armStr = ARMInstruction(
    { "Str" },
    {
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
                setRegister(base, addr)
            }
        } else {
            when (instr bit 22) {
                true -> bus.write8(registers[base], src.toByte())
                false -> bus.write32(registers[base], src)
            }

            pc -= 4
            setRegister(base, addr)
        }
    }
)

val armLdrhsb = ARMInstruction(
    { "Ldrhsb" }, // LDRH LDRSH LDRB LDRSB
    {
        val instruction = pipelineHead

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
            0 -> bus.read8(address).uInt
            1 -> bus.read16(address).uInt.rotateRight((address and 1) shl 3)
            2 -> bus.read8(address).toInt()
            3 -> bus.read16(address).toInt() shr ((address and 1) shl 3)
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
            setRegister(base, addressWithOffset)
    }
)

@JvmField
val registerArray = IntArray(16) { -1 }
private inline fun registerList(instr: Int, reversed: Boolean): IntArray {
    if (reversed) {
        for (it in 0 .. 15) if (instr bit it) registerArray[15 - it] = it else registerArray[15 - it] = -1
    } else {
        for (it in 0 .. 15) if (instr bit it) registerArray[it] = it else registerArray[it] = -1
    }

    return registerArray
}

val armStm = ARMInstruction(
    { "stm" },
    {
        val pre = instr bit 24
        val up = instr bit 23
        val psrForceUser = instr bit 22
        val modeCopy = mode

        pc += 4

        val base = (instr ushr 16) and 0xF
        val regs = registerList(instr, !up)
        var addr = registers[base]

        var baseInRegList = -1

        if (pre) {
            when (up) {
                true -> addr += 4
                false -> addr -= 4
            }
        }

        if (psrForceUser) setCPUMode(CPUMode.User)

        if (instr and 0xFFFF == 0) {
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
            for (it in regs) {
                if (it == -1) continue

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
            if (pre && instr and 0xFFFF != 0) {
                when (up) {
                    true -> addr -= 4
                    false -> addr += 4
                }
            }

            if (
                baseInRegList != -1 &&
                instr.and(1.shl(base).minus(1)) != 0  // not first in the list (TODO: what if reverse?)
            ) bus.write32(baseInRegList, addr)

            setRegister(base, addr)
        }

    }
)

val armLdm = ARMInstruction(
    { "ldm" },
    {
        val pre = instr bit 24
        val up = instr bit 23
        val psrForceUser = instr bit 22
        val modeCopy = mode

        val base = (instr ushr 16) and 0xF
        val regs = registerList(instr, !up)
        var addr = registers[base]

        var baseInRegList = -1

        if (pre) {
            when (up) {
                true -> addr += 4
                false -> addr -= 4
            }
        }

        if (psrForceUser) {
            if (instr bit 15)
                TODO("reg 15 psrForceUser arm stm")

            setCPUMode(CPUMode.User)
        }

        if (instr and 0xFFFF == 0) {
            setRegister(15, bus.read32(addr))
            when (up) {
                true -> addr += 0x40
                false -> addr -= 0x40
            }
        } else {
            for (it in regs) {
                if (it == -1) continue

                setRegister(it, bus.read32(addr))
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

            if (baseInRegList == -1) {
                setRegister(base, addr)
            }
        }
    }
)