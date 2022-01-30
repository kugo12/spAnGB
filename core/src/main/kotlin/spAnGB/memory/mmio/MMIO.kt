package spAnGB.memory.mmio

import spAnGB.memory.Bus
import spAnGB.memory.Memory

class MMIO(
    val bus: Bus
) : Memory {
    val keyInput = KeyInput()

    val ime = InterruptMasterEnable()
    val ir = InterruptRequest()
    val ie = InterruptEnable()
    val halt = Halt()

    override fun read8(address: Int) = get(address).read8(address)
    override fun read16(address: Int) = get(address).read16(address)
    override fun read32(address: Int) = get(address).read32(address)

    override fun write8(address: Int, value: Byte) = get(address).write8(address, value)
    override fun write16(address: Int, value: Short) = get(address).write16(address, value)
    override fun write32(address: Int, value: Int) = get(address).write32(address, value)

    inline operator fun get(address: Int): Memory =
        when (address and 0xFFFFFF) {
            0x0 -> bus.ppu.displayControl
            0x4 -> bus.ppu.displayStat
            0x6 -> bus.ppu.vcount
            0x8 -> bus.ppu.bgControl[0]
            0xA -> bus.ppu.bgControl[1]
            0xC -> bus.ppu.bgControl[2]
            0xE -> bus.ppu.bgControl[3]
            0x10 -> bus.ppu.bgXOffset[0]  // TODO: offsets should be write only
            0x12 -> bus.ppu.bgYOffset[0]
            0x14 -> bus.ppu.bgXOffset[1]
            0x16 -> bus.ppu.bgYOffset[1]
            0x18 -> bus.ppu.bgXOffset[2]
            0x1A -> bus.ppu.bgYOffset[2]
            0x1C -> bus.ppu.bgXOffset[3]
            0x1E -> bus.ppu.bgYOffset[3]
            0x130 -> keyInput
            0x200 -> ie
            0x202 -> ir
            0x208 -> ime
            0x301 -> halt
            else -> Memory.stub
        }
}