package spAnGB.memory.mmio

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.toInt

class Halt : Memory {
    var isHalted = false

    override fun read8(address: Int): Byte = isHalted.toInt().shl(7).toByte()

    override fun read16(address: Int): Short {
        TODO("Not yet implemented")
    }

    override fun read32(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun write8(address: Int, value: Byte) {
        isHalted = value.toUByte().toInt() bit 7
    }

    override fun write16(address: Int, value: Short) {
        TODO("Not yet implemented")
    }

    override fun write32(address: Int, value: Int) {
        TODO("Not yet implemented")
    }
}