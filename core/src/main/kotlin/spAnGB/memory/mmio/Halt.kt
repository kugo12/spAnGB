package spAnGB.memory.mmio

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.toInt

// TODO: stop mode
class Halt : Memory {
    var isHalted = false

    override fun read8(address: Int): Byte = 0
    override fun read16(address: Int): Short = 0
    override fun read32(address: Int): Int = 0

    override fun write8(address: Int, value: Byte) {
        isHalted = true
    }

    override fun write16(address: Int, value: Short) {
        isHalted = true
    }

    override fun write32(address: Int, value: Int) {
        TODO("Not yet implemented")
    }
}