package spAnGB.memory.rom

import spAnGB.memory.Memory
import spAnGB.utils.KiB
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FlashStub: Memory {
    val byteBuffer = ByteBuffer.allocateDirect(128 * KiB).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }
    val shortBuffer = byteBuffer.asShortBuffer()
    val intBuffer = byteBuffer.asIntBuffer()

    override fun read8(address: Int): Byte {
        val addr = address and 0xFFFF
        return when (addr) {
            0 -> 0xC2u.toByte()
            1 -> 0x09
            else -> byteBuffer[addr]
        }
    }

    override fun read16(address: Int): Short = shortBuffer[address and 0xFFFF]

    override fun read32(address: Int): Int = intBuffer[address and 0xFFFF]

    override fun write8(address: Int, value: Byte) {
        byteBuffer.put(address and 0xFFFF, value)
    }

    override fun write16(address: Int, value: Short) {
        shortBuffer.put(address and 0xFFFF, value)
    }

    override fun write32(address: Int, value: Int) {
        intBuffer.put(address and 0xFFFF, value)
    }
}