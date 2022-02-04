package spAnGB.hw

import spAnGB.memory.Memory
import spAnGB.memory.mmio.InterruptRequest

class Timer(
    val ir: InterruptRequest
): Memory {
    override fun read8(address: Int): Byte {
        return 0
//        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short {
//        TODO("Not yet implemented")
        return 0
    }

    override fun read32(address: Int): Int { TODO() }

    override fun write8(address: Int, value: Byte) {
//        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
//        TODO("Not yet implemented")
    }

    override fun write32(address: Int, value: Int) { TODO() }

    fun tick() {

    }
}