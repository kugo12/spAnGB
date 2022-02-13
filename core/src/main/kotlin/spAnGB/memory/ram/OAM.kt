package spAnGB.memory.ram

import spAnGB.memory.Memory
import spAnGB.utils.KiB
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val OAM_MASK = 1*KiB - 1

class OAM: Memory {
    val byteBuffer = ByteBuffer.allocate(1*KiB).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }
    val shortBuffer = byteBuffer.asShortBuffer()
    val intBuffer = byteBuffer.asIntBuffer()


    override fun read8(address: Int): Byte = byteBuffer[address and OAM_MASK]
    override fun read16(address: Int): Short = shortBuffer[(address and OAM_MASK) ushr 1]
    override fun read32(address: Int): Int = intBuffer[(address and OAM_MASK) ushr 2]

    override fun write8(address: Int, value: Byte) {}

    override fun write16(address: Int, value: Short) {
        shortBuffer.put((address and OAM_MASK) ushr 1, value)
    }

    override fun write32(address: Int, value: Int) {
        intBuffer.put((address and OAM_MASK) ushr 2, value)
    }
}

