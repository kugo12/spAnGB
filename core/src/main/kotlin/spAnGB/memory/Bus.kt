package spAnGB.memory

import spAnGB.memory.dma.DMA
import spAnGB.memory.mmio.MMIO
import spAnGB.ppu.PPU
import spAnGB.utils.KiB
import spAnGB.utils.hex
import java.nio.ByteBuffer

class Bus(
    framebuffer: ByteBuffer,
    val bios: Bios = Bios("bios.bin"),
    val cartridge: Cartridge = Cartridge("suite.gba"),
//    val cartridge: Cartridge = Cartridge("irqDemo (1).gba"),
): Memory {
    val wram = RAM(256*KiB)
    val iwram = RAM(32*KiB)

    val dma = arrayOf(
        DMA(this),
        DMA(this),
        DMA(this),
        DMA(this, 0xFFFF)
    )
    val mmio = MMIO(this)

    val ppu = PPU(framebuffer, mmio)

    val memoryMapping: Array<Memory> = arrayOf(
        bios,         // 0 - BIOS
        Memory.stub,  // 1 - not used
        wram,  // 2 - WRAM
        iwram,  // 3 - IWRAM
        mmio,  // 4 - MMIO
        ppu.palette,  // 5 - PPU - palette
        ppu.vram,  // 6 - PPU - VRAM
        ppu.attributes,  // 7 - PPU - attributes
        cartridge,  // 8 - GamePak - wait state 0
        cartridge,  // 9 - GamePak - wait state 0
        cartridge,  // A - GamePak - wait state 1
        cartridge,  // B - GamePak - wait state 1
        cartridge,  // C - GamePak - wait state 2
        cartridge,  // D - GamePak - wait state 2
        cartridge,  // E - GamePak - SRAM
        cartridge,  // F - not used / SRAM mirror
    )

    override fun read8(address: Int): Byte = get(address).read8(address and 0x0FFFFFFF)
    override fun read16(address: Int): Short = get(address).read16(address and 0x0FFFFFFE)
    override fun read32(address: Int): Int = get(address).read32(address and 0x0FFFFFFC)

    override fun write8(address: Int, value: Byte) = get(address).write8(address and 0x0FFFFFFF, value)
    override fun write16(address: Int, value: Short) = get(address).write16(address and 0x0FFFFFFE, value)
    override fun write32(address: Int, value: Int) = get(address).write32(address and 0x0FFFFFFC, value)

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun get(address: Int): Memory = memoryMapping[(address ushr 24) and 0xF]  //.also { println(it) }

    fun tick() {
        ppu.tick()
    }
}