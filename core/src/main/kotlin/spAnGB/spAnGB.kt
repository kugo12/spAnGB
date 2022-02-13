package spAnGB

import spAnGB.cpu.CPU
import spAnGB.memory.Bios
import spAnGB.memory.Bus
import spAnGB.memory.UnusedMemory
import spAnGB.memory.rom.Cartridge
import java.io.File
import java.nio.ByteBuffer

@Suppress("ClassName")
class spAnGB(
    framebuffer: ByteBuffer,
    blitFramebuffer: () -> Unit,
    rom: File = File("suite.gba"),
    bios: File = File("bios.bin")
) {
    val scheduler = Scheduler()
    val bus = Bus(
        framebuffer,
        blitFramebuffer,
        scheduler = scheduler
    )
    val cpu = CPU(bus)

    init {
        val unused = UnusedMemory(cpu, bus)
        bus.bios = Bios(bios, bus, cpu, unused)
        bus.unusedMemory = unused
        bus.loadCartridge(rom)
        cpu.reset()
    }

    fun tick() {
        cpu.tick()
        scheduler.tick()
    }
}