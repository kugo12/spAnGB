package spAnGB.apu.mmio

import spAnGB.memory.Memory
import java.nio.ByteBuffer

class FIFOBuffer: Memory {
    // FIXME
    val buffer = ByteBuffer.allocateDirect(32)
    val shortBuffer = buffer.asShortBuffer()

    override fun read8(address: Int): Byte = buffer[address and 0x3]
    override fun read16(address: Int): Short = shortBuffer[address.ushr(1).and(1)]

    override fun write8(address: Int, value: Byte) {
        buffer.put(address and 0x3, value)
    }
    override fun write16(address: Int, value: Short) {
        shortBuffer.put(address.ushr(1).and(1), value)
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}
}