package spAnGB.memory.ram

import spAnGB.memory.Memory
import spAnGB.ppu.mmio.DisplayControl
import spAnGB.utils.KiB
import spAnGB.utils.uShort
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val VRAM_MASK = 0x1FFFF

class VRAM(
    private val control: DisplayControl
): Memory {
    val byteBuffer = ByteBuffer.allocate(96 * KiB).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }
    val shortBuffer = byteBuffer.asShortBuffer()
    val intBuffer = byteBuffer.asIntBuffer()

    private inline fun parseAddress(address: Int) =
        address.and(VRAM_MASK).run {
            if (this > 0x17FFF) minus(0x8000) else this
        }

    override fun read8(address: Int): Byte = byteBuffer[parseAddress(address)]
    override fun read16(address: Int): Short = shortBuffer[parseAddress(address) ushr 1]
    override fun read32(address: Int): Int = intBuffer[parseAddress(address) ushr 2]

    override fun write8(address: Int, value: Byte) {
        val addr = parseAddress(address)
        val max = when (control.bgMode) {
            3, 4 -> 0x14000
            else -> 0x10000
        }

        if (addr < max) {
            shortBuffer.put(addr ushr 1, value.uShort.times(0x101).toShort())
        }
    }

    override fun write16(address: Int, value: Short) {
        shortBuffer.put(parseAddress(address) ushr 1, value)
    }

    override fun write32(address: Int, value: Int) {
        intBuffer.put(parseAddress(address) ushr 2, value)
    }
}