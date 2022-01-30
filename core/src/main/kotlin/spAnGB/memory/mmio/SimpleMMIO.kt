package spAnGB.memory.mmio

import spAnGB.memory.Memory

abstract class SimpleMMIO(  // TODO: Remove this in future
    var value: Int = 0
): Memory {

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short = value.toShort()

    override fun read32(address: Int): Int = value

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.toUShort().toInt()
    }

    override fun write32(address: Int, value: Int) {
        this.value = value and 0xFFFF
    }

}