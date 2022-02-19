package spAnGB.apu.mmio

import spAnGB.memory.Memory
import spAnGB.utils.toInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val WaveRAMMask = 0xF

class WaveRAM(
    val control: WaveRamSelect
) : Memory {
    val memory = Array(2) {
        ByteBuffer.allocateDirect(16).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    private val shortBuffers = memory.map(ByteBuffer::asShortBuffer).toTypedArray()
    private inline val currentBank get() = (!control.isSecondBank).toInt()

    override fun read8(address: Int): Byte = memory[currentBank][address and WaveRAMMask]
    override fun read16(address: Int): Short = shortBuffers[currentBank][address.and(WaveRAMMask).ushr(1)]

    override fun write8(address: Int, value: Byte) { memory[currentBank].put(address and WaveRAMMask, value) }
    override fun write16(address: Int, value: Short) { shortBuffers[currentBank].put(address.and(WaveRAMMask).ushr(1), value) }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}
}
