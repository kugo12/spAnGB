package spAnGB.memory.mmio

import spAnGB.Scheduler
import spAnGB.hw.Timer
import spAnGB.memory.Bus
import spAnGB.memory.Memory

class MMIO(
    val bus: Bus,
) : Memory {
    val keyInput = KeyInput()

    val ime = InterruptMasterEnable()
    val ir = InterruptRequest()
    val ie = InterruptEnable()
    val halt = Halt()

    val timers = run {
        val t3 = Timer(ir, bus.scheduler, Interrupt.Timer3)
        val t2 = Timer(ir, bus.scheduler, Interrupt.Timer2, t3::countUp)
        val t1 = Timer(ir, bus.scheduler, Interrupt.Timer1, t2::countUp)
        val t0 = Timer(ir, bus.scheduler, Interrupt.Timer0, t1::countUp)

        arrayOf(t0, t1, t2, t3)
    }

    override fun read8(address: Int) = get(address).read8(address)
    override fun read16(address: Int) = get(address).read16(address)
    override fun read32(address: Int) =
        read16(address).toUShort().toInt().or(
            read16(address + 2).toUShort().toInt().shl(16)
        )

    override fun write8(address: Int, value: Byte) = get(address).write8(address, value)
    override fun write16(address: Int, value: Short) = get(address).write16(address, value)
    override fun write32(address: Int, value: Int) {
        get(address).write16(address, value.toShort())
        get(address + 2).write16(address + 2, value.ushr(16).toShort())
    }

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

            0xB0, 0xB2 -> bus.dma[0].source
            0xB4, 0xB6 -> bus.dma[0].destination
            0xB8, 0xBA -> bus.dma[0]
            0xBC, 0xBE -> bus.dma[1].source
            0xC0, 0xC2 -> bus.dma[1].destination
            0xC4, 0xC6 -> bus.dma[1]
            0xC8, 0xCA -> bus.dma[2].source
            0xCC, 0xCE -> bus.dma[2].destination
            0xD0, 0xD2 -> bus.dma[2]
            0xD4, 0xD6 -> bus.dma[3].source
            0xD8, 0xDA -> bus.dma[3].destination
            0xDC, 0xDE -> bus.dma[3]

            0x100, 0x102 -> timers[0]
            0x104, 0x106 -> timers[1]
            0x108, 0x10A -> timers[2]
            0x10C, 0x10E -> timers[3]

            0x130 -> keyInput
            0x200 -> ie
            0x202 -> ir
            0x208 -> ime
            0x20A -> Memory.silentStub  // TODO
            0x301 -> halt
            else -> Memory.stub
        }
}

