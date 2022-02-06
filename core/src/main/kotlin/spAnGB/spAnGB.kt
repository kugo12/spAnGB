package spAnGB

import spAnGB.cpu.CPU
import spAnGB.memory.Bus
import spAnGB.memory.rom.Cartridge
import java.io.File
import java.nio.ByteBuffer

@Suppress("ClassName")
class spAnGB(
    framebuffer: ByteBuffer,
    rom: File = File("firered.gba")
) {
    val scheduler = Scheduler()
    val bus = Bus(
        framebuffer,
        cartridge = Cartridge(rom),
        scheduler = scheduler
    )
    val cpu = CPU(bus)

    fun tick() {
        cpu.tick()
        scheduler.tick()
    }
}