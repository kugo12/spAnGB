package spAnGB.ppu.mmio

import spAnGB.memory.Memory
import spAnGB.utils.hex
import spAnGB.utils.read8From16
import spAnGB.utils.uInt


enum class DisplayStatFlag(val mask: Int) {
    VBLANK(0x1),
    HBLANK(0x2),
    VCOUNTER(0x4),
    VBLANK_IRQ(0x8),
    HBLANK_IRQ(0x10),
    VCOUNTER_IRQ(0x20)
}

class DisplayStat: Memory {
    var flags = 0
    var lyc = 0

    val value: Int get() = flags.or(lyc.shl(8))

    override fun read8(address: Int): Byte = read8From16(address, value)

    override fun read16(address: Int): Short {
        return value.toShort()
    }

    override fun read32(address: Int): Int = value

    override fun write8(address: Int, value: Byte) {
        flags = value.toUByte().toInt()
//        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        flags = value.toInt() and 0xFF
        lyc = value.uInt.ushr(8).and(0xFF)
    }

    override fun write32(address: Int, value: Int) {
        TODO("Not yet implemented")
    }

    inline operator fun get(flag: DisplayStatFlag): Boolean = flags and flag.mask != 0
    inline operator fun set(flag: DisplayStatFlag, value: Boolean) {
        flags = flags and (flag.mask.inv())
        if (value) flags = flags or flag.mask
    }
}