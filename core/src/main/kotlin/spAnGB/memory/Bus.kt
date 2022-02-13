@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.memory

import spAnGB.Scheduler
import spAnGB.memory.dma.DMA
import spAnGB.memory.dma.DMAManager
import spAnGB.memory.mmio.MMIO
import spAnGB.memory.ram.RAM
import spAnGB.memory.rom.Cartridge
import spAnGB.ppu.PPU
import spAnGB.utils.KiB
import spAnGB.utils.uInt
import java.io.File
import java.nio.ByteBuffer

class Bus(
    framebuffer: ByteBuffer,
    blitFramebuffer: () -> Unit,
    val scheduler: Scheduler
): Memory {
    var bios: Memory = Memory.stub
    var unusedMemory: Memory = Memory.stub
    var cartridge: Memory = Memory.stub
    var cartridgePersistence: Memory = Memory.stub

    private val wram = RAM(256 * KiB)
    private val iwram = RAM(32 * KiB)

    val dma = arrayOf(
        DMA(this, 0),
        DMA(this, 1),
        DMA(this, 2),
        DMA(this, 3)
    )
    val dmaManager = DMAManager(dma, scheduler)
    @JvmField
    val mmio = MMIO(this)

    val ppu = PPU(framebuffer, blitFramebuffer, mmio, scheduler, dmaManager)

    var last = 0


    // This cursed stuff is for inlining
    override fun read8(address: Int): Byte = get(address) { read8(address).also { last = it.uInt } }
    override fun read16(address: Int): Short = get(address) { read16(address).also { last = it.uInt } }
    override fun read32(address: Int): Int = get(address) { read32(address).also { last = it } }

    override fun write8(address: Int, value: Byte) = get(address) { write8(address, value) }
    override fun write16(address: Int, value: Short) = get(address) { write16(address, value) }
    override fun write32(address: Int, value: Int) = get(address) { write32(address, value) }

    private inline fun <T> get(address: Int, func: Memory.() -> T): T =
        when (address ushr 24) {
            0x0 -> bios.func()         // 0 - BIOS
            0x2 -> wram.func()  // 2 - WRAM
            0x3 -> iwram.func()  // 3 - IWRAM
            0x4 -> mmio.func()  // 4 - MMIO
            0x5 -> ppu.palette.func()  // 5 - PPU - palette
            0x6 -> ppu.vram.func()  // 6 - PPU - VRAM
            0x7 -> ppu.attributes.func()  // 7 - PPU - attributes
            0x8 -> cartridge.func()  // 8 - GamePak - wait state 0
            0x9 -> cartridge.func()  // 9 - GamePak - wait state 0
            0xA -> cartridge.func()  // A - GamePak - wait state 1
            0xB -> cartridge.func()  // B - GamePak - wait state 1
            0xC -> cartridge.func()  // C - GamePak - wait state 2
            0xD -> cartridge.func()  // D - GamePak - wait state 2
            0xE -> cartridgePersistence.func()  // E - GamePak - SRAM
            0xF -> cartridgePersistence.func()  // F - not used / SRAM mirror
            else -> unusedMemory.func()
        }

    fun loadCartridge(f: File) {
        val c = Cartridge(f, this)
        cartridge = c
        cartridgePersistence = c.persistence
    }
}