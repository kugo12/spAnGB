package spAnGB.memory.dma.mmio

import spAnGB.memory.Memory

class DMAAddress: Memory {
    var value = 0

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short {
        TODO("Not yet implemented")
    }

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
        this.value = value and 0x0FFFFFFF  // TODO: 7/F?
    }
}