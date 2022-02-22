package spAnGB.apu.mmio

import spAnGB.memory.Memory
import spAnGB.utils.toInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val WaveRAMMask = 0xF

class WaveRAM(
    val control: WaveRamSelect
) : Memory {
    val memory = ByteBuffer.allocateDirect(32).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }

    private val shortBuffer = memory.asShortBuffer()
    private inline val currentBank get() = 16 * (!control.isSecondBank).toInt()

    override fun read8(address: Int): Byte =
        memory[(address and WaveRAMMask) + currentBank]

    override fun read16(address: Int): Short =
        shortBuffer[address.and(WaveRAMMask).plus(currentBank).ushr(1)]

    override fun write8(address: Int, value: Byte) {
        memory.put((address and WaveRAMMask) + currentBank, value)
    }

    override fun write16(address: Int, value: Short) {
        shortBuffer.put(address.and(WaveRAMMask).plus(currentBank).ushr(1), value)
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}
}
