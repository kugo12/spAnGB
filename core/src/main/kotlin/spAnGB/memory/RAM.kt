package spAnGB.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder

class RAM(
    size: Int
): Memory {
    val mask = size - 1  // Warning: this is not universal at all
    val content = ByteBuffer.allocate(size).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }


    override fun read8(address: Int): Byte = content[address and mask]
    override fun read16(address: Int): Short = content.getShort(address and mask)
    override fun read32(address: Int): Int = content.getInt(address and mask)

    override fun write8(address: Int, value: Byte) {
        content.put(address and mask, value)
    }

    override fun write16(address: Int, value: Short) {
        content.putShort(address and mask, value)
    }

    override fun write32(address: Int, value: Int) {
        content.putInt(address and mask, value)
    }
}