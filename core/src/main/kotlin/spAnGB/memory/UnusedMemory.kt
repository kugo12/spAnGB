package spAnGB.memory

import spAnGB.cpu.CPU
import spAnGB.cpu.CPUState
import spAnGB.utils.bit
import spAnGB.utils.uInt

class UnusedMemory(
    val cpu: CPU,
    val bus: Bus
): Memory {
    override fun read8(address: Int): Byte {
        val r = read()

        return r.ushr(address.and(3) shl 3).toByte()
    }

    override fun read16(address: Int): Short {
        val r = read()

        return r.ushr(address.and(2) shl 3).toShort()
    }

    override fun read32(address: Int): Int = read()

    fun read(): Int {
        if (cpu.state == CPUState.ARM) return cpu.pipeline[0]

        return when (cpu.pc ushr 24) {  // TODO
            // bios, oam
            0x0, 0x7 -> {
                if (cpu.pc bit 1) {  // not aligned
                    cpu.pipeline[1] or cpu.pipeline[0].shl(16)
                } else {
                    cpu.pipeline[0] or bus.read16(cpu.pc + 2).uInt.shl(16)
                }
            }
            // iwram
            0x3 -> {
                val old = cpu.pipeline[1]
                if (cpu.pc bit 1) {  // not aligned
                    cpu.pipeline[0] or old.shl(16)
                } else {
                    old or cpu.pipeline[0].shl(16)
                }
            }
            // wram, palette, vram, cartridge
            0x2, 0x5, 0x6, 0x8, 0x9, 0xA, 0xB, 0xC, 0xD -> {
                cpu.pipeline[0] or cpu.pipeline[0].shl(16)
            }
            else -> bus.last
        }
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}