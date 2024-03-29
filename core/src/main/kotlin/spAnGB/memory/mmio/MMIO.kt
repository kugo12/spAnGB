package spAnGB.memory.mmio

import spAnGB.hw.KeyInput
import spAnGB.hw.Serial
import spAnGB.hw.Timer
import spAnGB.memory.Bus
import spAnGB.memory.Memory
import spAnGB.utils.uInt

class MMIO(
    val bus: Bus,
) : Memory {
    val ime = InterruptMasterEnable()
    val ir = InterruptRequest()
    val ie = InterruptEnable()
    val halt = Halt()

    val serial = Serial(bus.scheduler, ir)
    val keyInput = KeyInput(ir)
    val apu = bus.apu

    val timers = run {
        val t3 = Timer(ir, bus.scheduler, Interrupt.Timer3)
        val t2 = Timer(ir, bus.scheduler, Interrupt.Timer2, t3::countUp)
        val t1 = Timer(ir, bus.scheduler, Interrupt.Timer1, t2::countUp)
        val t0 = Timer(ir, bus.scheduler, Interrupt.Timer0, t1::countUp)

        arrayOf(t0, t1, t2, t3)
    }

    override fun read8(address: Int) = getReadable(address).read8(address)
    override fun read16(address: Int) = getReadable(address).read16(address)
    override fun read32(address: Int) =
        read16(address).uInt.or(
            read16(address + 2).uInt.shl(16)
        )

    override fun write8(address: Int, value: Byte) = getWritable(address).write8(address, value)
    override fun write16(address: Int, value: Short) = getWritable(address).write16(address, value)
    override fun write32(address: Int, value: Int) {
        getWritable(address).write16(address, value.toShort())
        getWritable(address + 2).write16(address + 2, value.ushr(16).toShort())
    }

    inline fun getReadable(address: Int): Memory =
        when (address and 0xFFFFFF) {
            0x0, 0x1 -> bus.ppu.displayControl
            0x4, 0x5 -> bus.ppu.displayStat
            0x6, 0x7 -> bus.ppu.vcount
            0x8, 0x9 -> bus.ppu.bgControl[0]
            0xA, 0xB -> bus.ppu.bgControl[1]
            0xC, 0xD -> bus.ppu.bgControl[2]
            0xE, 0xF -> bus.ppu.bgControl[3]

            0x48, 0x49 -> bus.ppu.winIn
            0x4A, 0x4B -> bus.ppu.winOut

            0x50, 0x51 -> bus.ppu.blend
            0x52, 0x53 -> bus.ppu.alpha

            0x60, 0x61 -> apu.sweep.sweep
            0x62, 0x63 -> apu.sweep.dutyEnvelope
            0x64, 0x65 -> apu.sweep.control
            0x68, 0x69 -> apu.tone.dutyEnvelope
            0x6C, 0x6D -> apu.tone.control
            0x70, 0x71 -> apu.wave.select
            0x72, 0x73 -> apu.wave.volume
            0x74, 0x75 -> apu.wave.control
            0x78, 0x79 -> apu.noise.envelope
            0x7C, 0x7D -> apu.noise.control
            0x80, 0x81 -> apu.volume
            0x82, 0x83 -> apu.control
            0x84, 0x85 -> apu.master
            0x88, 0x89 -> apu.bias
            0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F -> apu.wave.ram

            0xB8, 0xBA -> bus.dma[0]
            0xC4, 0xC6 -> bus.dma[1]
            0xD0, 0xD2 -> bus.dma[2]
            0xDC, 0xDE -> bus.dma[3]

            0x100, 0x102 -> timers[0]
            0x104, 0x106 -> timers[1]
            0x108, 0x10A -> timers[2]
            0x10C, 0x10E -> timers[3]

            0x120, 0x121, 0x122, 0x123 -> serial.data32
            0x128, 0x129 -> serial
            0x12A -> serial.data8
            0x130, 0x131 -> keyInput
            0x132, 0x133 -> keyInput.irqControl
            0x134, 0x135 -> serial.rCnt
            0x200, 0x201 -> ie
            0x202, 0x203 -> ir
            0x204, 0x205 -> bus.waitCnt
            0x208 -> ime
            0x301 -> halt

            0x66, 0x67, 0x6E, 0x6F, 0x76, 0x77, 0x7A, 0x7B, 0x7E, 0x7F, 0x86, 0x87, 0x8A, 0x8B -> Memory.zero

            else -> bus.unusedMemory
        }

    inline fun getWritable(address: Int): Memory =
        when (address and 0xFFFFFF) {
            0x0, 0x1 -> bus.ppu.displayControl
            0x4, 0x5 -> bus.ppu.displayStat
            0x6, 0x7 -> bus.ppu.vcount
            0x8, 0x9 -> bus.ppu.bgControl[0]
            0xA, 0xB -> bus.ppu.bgControl[1]
            0xC, 0xD -> bus.ppu.bgControl[2]
            0xE, 0xF -> bus.ppu.bgControl[3]

            0x10, 0x11 -> bus.ppu.bgXOffset[0]
            0x12, 0x13 -> bus.ppu.bgYOffset[0]
            0x14, 0x15 -> bus.ppu.bgXOffset[1]
            0x16, 0x17 -> bus.ppu.bgYOffset[1]
            0x18, 0x19 -> bus.ppu.bgXOffset[2]
            0x1A, 0x1B -> bus.ppu.bgYOffset[2]
            0x1C, 0x1D -> bus.ppu.bgXOffset[3]
            0x1E, 0x1F -> bus.ppu.bgYOffset[3]

            0x20, 0x21 -> bus.ppu.bgMatrix[0]
            0x22, 0x23 -> bus.ppu.bgMatrix[1]
            0x24, 0x25 -> bus.ppu.bgMatrix[2]
            0x26, 0x27 -> bus.ppu.bgMatrix[3]
            0x28, 0x29, 0x2A, 0x2B -> bus.ppu.bgReference[0]
            0x2C, 0x2D, 0x2E, 0x2F -> bus.ppu.bgReference[1]
            0x30, 0x31 -> bus.ppu.bgMatrix[4]
            0x32, 0x33 -> bus.ppu.bgMatrix[5]
            0x34, 0x35 -> bus.ppu.bgMatrix[6]
            0x36, 0x37 -> bus.ppu.bgMatrix[7]
            0x38, 0x39, 0x3A, 0x3B -> bus.ppu.bgReference[2]
            0x3C, 0x3D, 0x3E, 0x3F -> bus.ppu.bgReference[3]

            0x40, 0x41 -> bus.ppu.windowHorizontal[0]
            0x42, 0x43 -> bus.ppu.windowHorizontal[1]
            0x44, 0x45 -> bus.ppu.windowVertical[0]
            0x46, 0x47 -> bus.ppu.windowVertical[1]
            0x48, 0x49 -> bus.ppu.winIn
            0x4A, 0x4B -> bus.ppu.winOut

            0x4C, 0x4D -> bus.ppu.mosaic
            0x50, 0x51 -> bus.ppu.blend
            0x52, 0x53 -> bus.ppu.alpha
            0x54, 0x55 -> bus.ppu.brightness

            0x60, 0x61 -> apu.sweep.sweep
            0x62, 0x63 -> apu.sweep.dutyEnvelope
            0x64, 0x65 -> apu.sweep.control
            0x68, 0x69 -> apu.tone.dutyEnvelope
            0x6C, 0x6D -> apu.tone.control
            0x70, 0x71 -> apu.wave.select
            0x72, 0x73 -> apu.wave.volume
            0x74, 0x75 -> apu.wave.control
            0x78, 0x79 -> apu.noise.envelope
            0x7C, 0x7D -> apu.noise.control
            0x80, 0x81 -> apu.volume
            0x82, 0x83 -> apu.control
            0x84, 0x85 -> apu.master
            0x88, 0x89 -> apu.bias
            0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F -> apu.wave.ram
            0xA0, 0xA1, 0xA2, 0xA3 -> apu.fifoA.buffer
            0xA4, 0xA5, 0xA6, 0xA7 -> apu.fifoB.buffer

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

            0x120, 0x121, 0x122, 0x123 -> serial.data32
            0x128, 0x129 -> serial
            0x12A -> serial.data8
            0x130, 0x131 -> keyInput
            0x132, 0x133 -> keyInput.irqControl
            0x134, 0x135 -> serial.rCnt
            0x200, 0x201 -> ie
            0x202, 0x203 -> ir
            0x204, 0x205 -> bus.waitCnt
            0x208 -> ime
            0x301 -> halt

            else -> Memory.stub
        }
}

