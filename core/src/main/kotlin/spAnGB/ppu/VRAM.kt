package spAnGB.ppu

import spAnGB.memory.Memory
import spAnGB.utils.KiB
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VRAM: Memory {
    val mask = 0x1FFFF
    val byteBuffer = ByteBuffer.allocate(96 * KiB).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }
    val shortBuffer = byteBuffer.asShortBuffer()
    val intBuffer = byteBuffer.asIntBuffer()

    private inline fun parseAddress(address: Int) =
        address.and(mask).run {
            if (this > 0x17FFF) minus(0x8000) else this
        }

    override fun read8(address: Int): Byte = byteBuffer[parseAddress(address)]
    override fun read16(address: Int): Short = shortBuffer[parseAddress(address) ushr 1]
    override fun read32(address: Int): Int = intBuffer[parseAddress(address) ushr 2]

    override fun write8(address: Int, value: Byte) {
        byteBuffer.put(parseAddress(address), value)
    }

    override fun write16(address: Int, value: Short) {
        shortBuffer.put(parseAddress(address) ushr 1, value)
    }

    override fun write32(address: Int, value: Int) {
        intBuffer.put(parseAddress(address) ushr 2, value)
    }
}