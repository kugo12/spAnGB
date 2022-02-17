package spAnGB.cpu.thumb

import spAnGB.cpu.CPU
import spAnGB.cpu.ThumbInstruction
import spAnGB.memory.AccessType
import spAnGB.memory.AccessType.NonSequential
import spAnGB.memory.AccessType.Sequential
import spAnGB.utils.bit
import spAnGB.utils.uInt

val thumbLdrPcrelImm = ThumbInstruction(
    { "LdrPcrelImm" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        val immediate = instr.and(0xFF).shl(2)
        val address = pc.and(3.inv()) + immediate
        registers[(instr ushr 8) and 0x7] = bus.read32(address).rotateRight(address.and(0x3).shl(3))
    }
)

private inline fun CPU.getAddress(instr: Int): Int =
    registers[(instr ushr 6) and 0x7] + registers[(instr ushr 3) and 0x7]

val thumbLdrStrRegOff = ThumbInstruction(
    { "LdrStrRegOff" },
    {
        prefetchAccess = NonSequential
        val address = getAddress(instr)
        val rd = instr and 0x7

        if (instr bit 11) {  // load
            bus.idle()
            registers[rd] = when (instr bit 10) {
                true -> bus.read8(address).uInt
                false -> bus.read32(address).rotateRight(address.and(3).shl(3))
            }
        } else {  // store
            when (instr bit 10) {
                true -> bus.write8(address, registers[rd].toByte())
                false -> bus.write32(address, registers[rd])
            }
        }
    }
)

val thumbStrh = ThumbInstruction(
    { "Strh" },
    {
        prefetchAccess = NonSequential
        val address = getAddress(instr)
        bus.write16(address, registers[instr and 0x7].toShort())
    }
)

val thumbLdrh = ThumbInstruction(
    { "Ldrh" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        val address = getAddress(instr)
        registers[instr and 0x7] = bus.read16(address).uInt.rotateRight(address.and(0x1).shl(3))
    }
)

val thumbLdsb = ThumbInstruction(
    { "Ldsb" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        val address = getAddress(instr)
        registers[instr and 0x7] = bus.read8(address).toInt()
    }
)

val thumbLdsh = ThumbInstruction(
    { "Ldsh" },
    {
        bus.idle()
        prefetchAccess = NonSequential

        val address = getAddress(instr)
        registers[instr and 0x7] = bus.read16(address).toInt().shr((address.and(1).shl(3)))
    }
)

val thumbLdrStrImmOff = ThumbInstruction(
    { "LdrStrImmOff" },
    {
        prefetchAccess = NonSequential
        val offset = (instr ushr 4) and 0x7C
        val address = registers[(instr ushr 3) and 0x7]
        val rd = instr and 0x7

        if (instr bit 11) {
            bus.idle()
            registers[rd] = when (instr bit 12) {
                true -> bus.read8(address + offset.ushr(2)).uInt
                false -> bus.read32(address + offset).rotateRight(address.plus(offset).and(0x3).shl(3))
            }
        } else {
            when (instr bit 12) {
                true -> bus.write8(address + offset.ushr(2), registers[rd].toByte())
                false -> bus.write32(address + offset, registers[rd])
            }
        }
    }
)

val thumbLdrhStrhImmOff = ThumbInstruction(
    { "LdrhStrhImmOff" },
    {
        prefetchAccess = NonSequential
        val address = registers[(instr ushr 3) and 0x7] + ((instr ushr 5) and 0x3E)
        val rd = instr and 0x7

        if (instr bit 11) {
            bus.idle()
            registers[rd] = bus.read16(address).uInt.rotateRight((address and 1) shl 3)
        } else {
            bus.write16(address, registers[rd].toShort())
        }
    }
)

val thumbLdrStrSpRel = ThumbInstruction(
    { "LdrStrSpRel" },
    {
        prefetchAccess = NonSequential
        val address = registers[13] + ((instr and 0xFF) shl 2)
        val rd = (instr ushr 8) and 0x7

        if (instr bit 11) {
            bus.idle()
            registers[rd] = bus.read32(address).rotateRight((address and 0x3) shl 3)
        } else {
            bus.write32(address, registers[rd])
        }
    }
)

val thumbLdPcSp = ThumbInstruction(
    { "LdPcSp" },
    {
        val offset = (instr and 0xFF) shl 2
        val rd = (instr ushr 8) and 0x7

        registers[rd] = when (instr bit 11) {
            true -> registers[13] + offset
            false -> (pc and (2.inv())) + offset
        }
    }
)

val thumbSpOff = ThumbInstruction(
    { "SpOff" },
    {
        val offset = (instr and 0x7F) shl 2

        when (instr bit 7) {
            true -> registers[13] -= offset
            false -> registers[13] += offset
        }
    }
)

val thumbPush = ThumbInstruction(
    { "Push" },
    {
        prefetchAccess = NonSequential

        val n = instr.and(0x1FF).countOneBits()
        val startAddress = registers[13] - n * 4

        var address = startAddress
        var access = NonSequential

        for (it in 0..7) {
            if (instr bit it) {
                bus.write32(address, registers[it], access)
                address += 4
                access = Sequential
            }
        }

        if (instr bit 8) {
            bus.write32(address, lr, access)
        }

        registers[13] = startAddress
    }
)

val thumbPop = ThumbInstruction(
    { "Pop" },
    {
        bus.idle()
        prefetchAccess = NonSequential
        var spUpdate = 0
        var access = NonSequential

        for (it in 0..7) {
            if (instr bit it) {
                registers[it] = bus.read32(registers[13] + spUpdate, access)
                access = Sequential
                spUpdate += 4
            }
        }

        if (instr bit 8) {
            pc = bus.read32(registers[13] + spUpdate, access)
            spUpdate += 4

            thumbRefill()
        }

        registers[13] += spUpdate
    }
)

val thumbStmia = ThumbInstruction(
    { "Stmia" },
    {
        prefetchAccess = NonSequential
        var update = 0

        val rb = (instr ushr 8) and 0x7
        val address = registers[rb]
        var access = NonSequential
        val n = instr.and(0xFF).countOneBits()

        if (n == 0) {
            bus.write32(address, (pc + 2).and(2.inv()), access)
            update += 0x40
        } else {
            for (it in 0..7) {
                if (!(instr bit it)) continue

                if (it == rb && instr.and(1.shl(rb).minus(1)) != 0) {
                    bus.write32(address + update, address + n * 4, access)
                } else {
                    bus.write32(address + update, registers[it], access)
                }

                access = Sequential
                update += 4
            }
        }

        registers[rb] += update
    }
)

val thumbLdmia = ThumbInstruction(
    { "Ldmia" },
    {
        bus.idle()
        prefetchAccess = NonSequential
        var update = 0
        val rb = (instr ushr 8) and 0x7
        val address = registers[rb]
        var access = NonSequential

        if (instr and 0xFF == 0) {
            setRegister(15, bus.read32(address, access))
            update += 0x40
        } else {
            for (it in 0..7) {
                if (instr bit it) {
                    registers[it] = bus.read32(address + update, access)
                    access = Sequential
                    update += 4
                }
            }
        }

        registers[rb] += update
    }
)