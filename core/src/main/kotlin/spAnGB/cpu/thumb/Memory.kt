package spAnGB.cpu.thumb

import spAnGB.cpu.CPU
import spAnGB.cpu.ThumbInstruction
import spAnGB.utils.bit
import spAnGB.utils.uInt

val thumbLdrPcrelImm = ThumbInstruction(
    { "LdrPcrelImm" },
    { instr ->
        val immediate = instr.and(0xFF).shl(2)
        val address = pc.and(3.inv()) + immediate
        registers[(instr ushr 8) and 0x7] = bus.read32(address).rotateRight(address.and(0x3).shl(3))
    }
)

private inline fun CPU.getAddress(instr: Int): Int =
    registers[(instr ushr 6) and 0x7] + registers[(instr ushr 3) and 0x7]

val thumbLdrStrRegOff = ThumbInstruction(
    { "LdrStrRegOff" },
    { instr ->
        val address = getAddress(instr)
        val rd = instr and 0x7

        if (instr bit 11) {  // load
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
    { instr ->
        val address = getAddress(instr)
        bus.write16(address, registers[instr and 0x7].toShort())
    }
)

val thumbLdrh = ThumbInstruction(
    { "Ldrh" },
    { instr ->
        val address = getAddress(instr)
        registers[instr and 0x7] = bus.read16(address).uInt.rotateRight(address.and(0x1).shl(3))
    }
)

val thumbLdsb = ThumbInstruction(
    { "Ldsb" },
    { instr ->
        val address = getAddress(instr)
        registers[instr and 0x7] = bus.read8(address).toInt()
    }
)

val thumbLdsh = ThumbInstruction(
    { "Ldsh" },
    { instr ->
        val address = getAddress(instr)
        registers[instr and 0x7] = bus.read16(address).toInt().shr((address.and(1).shl(3)))
    }
)

val thumbLdrStrImmOff = ThumbInstruction(
    { "LdrStrImmOff" },
    { instr ->
        val offset = (instr ushr 4) and 0x7C
        val address = registers[(instr ushr 3) and 0x7]
        val rd = instr and 0x7

        if (instr bit 11) {
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
    { instr ->
        val address = registers[(instr ushr 3) and 0x7] + ((instr ushr 5) and 0x3E)
        val rd = instr and 0x7

        if (instr bit 11) {
            registers[rd] = bus.read16(address).uInt.rotateRight((address and 1) shl 3)
        } else {
            bus.write16(address, registers[rd].toShort())
        }
    }
)

val thumbLdrStrSpRel = ThumbInstruction(
    { "LdrStrSpRel" },
    { instr ->
        val address = registers[13] + ((instr and 0xFF) shl 2)
        val rd = (instr ushr 8) and 0x7

        if (instr bit 11) {
            registers[rd] = bus.read32(address).rotateRight((address and 0x3) shl 3)
        } else {
            bus.write32(address, registers[rd])
        }
    }
)

val thumbLdPcSp = ThumbInstruction(
    { "LdPcSp" },
    { instr ->
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
    { instr ->
        val offset = (instr and 0x7F) shl 2

        when (instr bit 7) {
            true -> registers[13] -= offset
            false -> registers[13] += offset
        }
    }
)

val thumbPush = ThumbInstruction(
    { "Push" },
    { instr ->
        var spUpdate = 0

        if (instr bit 8) {
            spUpdate += 4
            bus.write32(registers[13] - spUpdate, lr)
        }

        (7 downTo 0).forEach {
            if (instr bit it) {
                spUpdate += 4
                bus.write32(registers[13] - spUpdate, registers[it])
            }
        }

        registers[13] -= spUpdate
    }
)

val thumbPop = ThumbInstruction(
    { "Pop" },
    { instr ->
        var spUpdate = 0

        (0..7).forEach {
            if (instr bit it) {
                registers[it] = bus.read32(registers[13] + spUpdate)
                spUpdate += 4
            }
        }

        if (instr bit 8) {
            pc = bus.read32(registers[13] + spUpdate)
            spUpdate += 4

            thumbRefill()
        }

        registers[13] += spUpdate
    }
)

val thumbStmia = ThumbInstruction(
    { "Stmia" },
    { instr ->
        var update = 0

        val rb = (instr ushr 8) and 0x7
        val address = registers[rb]
        var rbAddress: Int? = null
        val regs = (0..7).filter { instr bit it }


        if (instr and 0xFF == 0) {
            bus.write32(address, (pc + 2).and(2.inv()))
            update += 0x40
        } else {
            regs.forEach {
                if (it == rb) {
                    rbAddress = address + update
                }

                bus.write32(address + update, registers[it])
                update += 4
            }
        }

        registers[rb] += update
        if (rbAddress != null && regs.firstOrNull() != rb) {
            bus.write32(rbAddress!!, registers[rb])
        }
    }
)

val thumbLdmia = ThumbInstruction(
    { "Ldmia" },
    { instr ->
        var update = 0
        val rb = (instr ushr 8) and 0x7
        val address = registers[rb]

        if (instr and 0xFF == 0) {
            setRegister(15, bus.read32(address))
            update += 0x40
        } else {
            (0..7).forEach {
                if (instr bit it) {
                    registers[it] = bus.read32(address + update)
                    update += 4
                }
            }
        }

        registers[rb] += update
    }
)