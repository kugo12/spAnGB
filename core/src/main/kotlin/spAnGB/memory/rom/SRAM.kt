package spAnGB.memory.rom

import spAnGB.memory.Memory
import spAnGB.utils.KiB
import spAnGB.utils.uInt
import spAnGB.utils.uShort
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SRAM_MASK = 0x7FFF

class SRAM: Memory {
    val byteBuffer = ByteBuffer.allocateDirect(32 * KiB).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }

    override fun read8(address: Int): Byte {
        return byteBuffer[address and SRAM_MASK]
    }

    override fun read16(address: Int): Short = byteBuffer[address and SRAM_MASK].uShort.times(0x101).toShort()

    override fun read32(address: Int): Int = byteBuffer[address and SRAM_MASK].uInt.times(0x1010101)

    override fun write8(address: Int, value: Byte) {
        byteBuffer.put(address and SRAM_MASK, value)
    }

    override fun write16(address: Int, value: Short) {
        byteBuffer.put(address and SRAM_MASK, value.uInt.rotateRight(address shl 3).toByte())
    }

    override fun write32(address: Int, value: Int) {
        byteBuffer.put(address and SRAM_MASK, value.rotateRight(address shl 3).toByte())
    }
}