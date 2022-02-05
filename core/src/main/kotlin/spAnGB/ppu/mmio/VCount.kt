package spAnGB.ppu.mmio

import spAnGB.memory.Memory

class VCount: Memory {  // Read only
    @JvmField
    var ly = 0

    override fun read8(address: Int): Byte = ly.toByte()

    override fun read16(address: Int): Short = ly.toShort()

    override fun read32(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}