package spAnGB.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder

class RAM(
    size: Int
): Memory {
    val mask = size - 1  // Warning: this is not universal at all
    val byteBuffer = ByteBuffer.allocate(size).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }
    val shortBuffer = byteBuffer.asShortBuffer()
    val intBuffer = byteBuffer.asIntBuffer()


    override fun read8(address: Int): Byte = byteBuffer[address and mask]
    override fun read16(address: Int): Short = shortBuffer[(address and mask) ushr 1]
    override fun read32(address: Int): Int = intBuffer[(address and mask) ushr 2]

    override fun write8(address: Int, value: Byte) {
        byteBuffer.put(address and mask, value)
    }

    override fun write16(address: Int, value: Short) {
        shortBuffer.put((address and mask) ushr 1, value)
    }

    override fun write32(address: Int, value: Int) {
        intBuffer.put((address and mask) ushr 2, value)
    }
}