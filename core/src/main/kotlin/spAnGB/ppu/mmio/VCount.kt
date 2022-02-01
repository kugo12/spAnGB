package spAnGB.ppu.mmio

import spAnGB.memory.Memory

class VCount: Memory {
    var ly = 0

    override fun read8(address: Int): Byte = ly.toByte()

    override fun read16(address: Int): Short = ly.toShort()

    override fun read32(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        TODO("Not yet implemented")
    }

    override fun write32(address: Int, value: Int) {
        TODO("Not yet implemented")
    }
}