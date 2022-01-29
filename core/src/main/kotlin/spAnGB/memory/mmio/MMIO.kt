package spAnGB.memory.mmio

import spAnGB.memory.Bus
import spAnGB.memory.Memory
import spAnGB.memory.MemoryStub

class MMIO(
    val bus: Bus
): Memory {
    val keyInput = KeyInput()

    override fun read8(address: Int) = get(address).read8(address)
    override fun read16(address: Int) = get(address).read16(address)
    override fun read32(address: Int) = get(address).read32(address)

    override fun write8(address: Int, value: Byte) = get(address).write8(address, value)
    override fun write16(address: Int, value: Short) = get(address).write16(address, value)
    override fun write32(address: Int, value: Int) = get(address).write32(address, value)

    inline operator fun get(address: Int): Memory {
        val addr = address and 0xFFFFFF

        return when (address and 0xFFFFFF) {
            0x4 -> bus.ppu.displayStat
            0x6 -> bus.ppu.vcount
            0x130 -> keyInput
            else -> Memory.stub
        }
    }
}