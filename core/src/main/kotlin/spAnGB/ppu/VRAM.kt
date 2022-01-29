package spAnGB.ppu

import spAnGB.memory.Memory
import spAnGB.utils.KiB
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VRAM: Memory {
    val mask = 0x1FFFF
    val content = ByteBuffer.allocate(96 * KiB).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }

    private inline fun parseAddress(address: Int) =
        address.and(mask).run {
            if (this > 0x17FFF) minus(0x8000) else this
        }

    override fun read8(address: Int): Byte = content[parseAddress(address)]
    override fun read16(address: Int): Short = content.getShort(parseAddress(address))
    override fun read32(address: Int): Int = content.getInt(parseAddress(address))

    override fun write8(address: Int, value: Byte) {
        content.put(parseAddress(address), value)
    }

    override fun write16(address: Int, value: Short) {
        content.putShort(parseAddress(address), value)
    }

    override fun write32(address: Int, value: Int) {
        content.putInt(parseAddress(address), value)
    }
}