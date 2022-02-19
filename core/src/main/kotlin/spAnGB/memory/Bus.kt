@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.memory

import spAnGB.Scheduler
import spAnGB.apu.APU
import spAnGB.cpu.CPU
import spAnGB.memory.dma.DMAManager
import spAnGB.memory.mmio.MMIO
import spAnGB.memory.mmio.WaitstateControl
import spAnGB.memory.ram.RAM
import spAnGB.memory.rom.Cartridge
import spAnGB.memory.rom.GamepakPrefetch
import spAnGB.ppu.PPU
import spAnGB.utils.KiB
import spAnGB.utils.uInt
import java.io.File
import java.nio.ByteBuffer

enum class AccessType { NonSequential, Sequential }

class Bus(
    framebuffer: ByteBuffer,
    blitFramebuffer: () -> Unit,
    val scheduler: Scheduler
) {
    val apu = APU()
    val mmio = MMIO(this)
    var bios: Memory = Memory.stub
    var unusedMemory: Memory = Memory.stub
    var cartridge: Memory = Memory.stub
    var cartridgePersistence: Memory = Memory.stub

    val wram = RAM(256 * KiB)
    val iwram = RAM(32 * KiB)

    val cpu = CPU(this)
    val dmaManager = DMAManager(this)
    val dma = dmaManager.dma
    val waitCnt = WaitstateControl()

    val ppu = PPU(framebuffer, blitFramebuffer, mmio, scheduler, dmaManager)
    val prefetch = GamepakPrefetch(waitCnt, cpu)

    var last = 0


    // This cursed stuff is for inlining
    fun read8(address: Int, access: AccessType = AccessType.NonSequential): Byte = get<Byte, Byte>(address, access) { read8(address).also { last = it.uInt } }
    fun read16(address: Int, access: AccessType = AccessType.NonSequential): Short = get<Short, Short>(address, access) { read16(address).also { last = it.uInt } }
    fun read32(address: Int, access: AccessType = AccessType.NonSequential): Int = get<Int, Int>(address, access) { read32(address).also { last = it } }

    fun write8(address: Int, value: Byte, access: AccessType = AccessType.NonSequential) = get<Byte, Unit>(address, access) { write8(address, value) }
    fun write16(address: Int, value: Short, access: AccessType = AccessType.NonSequential) = get<Short, Unit>(address, access) { write16(address, value) }
    fun write32(address: Int, value: Int, access: AccessType = AccessType.NonSequential) = get<Int, Unit>(address, access) { write32(address, value) }

    private inline fun <reified Size, reified Return> get(address: Int, access: AccessType, func: Memory.() -> Return): Return =
        when (address ushr 24) {
            0x0 -> {
                tick()
                bios.func()
            }
            0x2 -> {
                stepBy<Size>({ 6 }, { 3 })
                wram.func()
            }
            0x3 -> {
                tick()
                iwram.func()
            }
            0x4 -> {
                tick()
                mmio.func()
            }
            0x5 -> {
                stepBy<Size>({ 2 }, { 1 })
                ppu.palette.func()
            }
            0x6 -> {
                stepBy<Size>({ 2 }, { 1 })
                ppu.vram.func()
            }
            0x7 -> {
                tick()
                ppu.attributes.func()
            }
            in 0x8 .. 0xD -> {
                val a = if (address and 0x1FFFF == 0 && !dmaManager.isDmaActive) AccessType.NonSequential.ordinal else access.ordinal
                val waitState = (address.ushr(24) - 0x8).ushr(1)
                stepBy<Size>(
                    {
                        waitCnt.lut[waitState]
                            .let { it[a] + it[AccessType.Sequential.ordinal] }
                            .let { prefetch.prefetch<Return>(address, it, waitCnt.lut[waitState][1] * 2) }
                    },
                    {
                        waitCnt.lut[waitState][a]
                            .let { prefetch.prefetch<Return>(address, it, waitCnt.lut[waitState][1]) }
                    }
                )
                cartridge.func()
            }
            0xE, 0xF -> {
                // TODO: prefetch penalty?
                step(waitCnt.sram)
                cartridgePersistence.func()
            }
            else -> {
                tick()
                unusedMemory.func()
            }
        }

    inline fun <reified T> stepBy(int: () -> Int, other: () -> Int) {
        step(if (0 is T) int() else other())
    }

    fun step(cycles: Int) {
        for (it in 0 until cycles) tick()
    }

    inline fun tick() {
        prefetch.tick()
        scheduler.tick()
    }

    fun idle() = tick()

    fun loadCartridge(f: File) {
        val c = Cartridge(f, this)
        cartridge = c
        cartridgePersistence = c.persistence
    }
}