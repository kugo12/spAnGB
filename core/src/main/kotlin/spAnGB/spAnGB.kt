package spAnGB

import spAnGB.cpu.CPU
import spAnGB.memory.Bus
import spAnGB.memory.Cartridge
import spAnGB.ppu.PPU
import java.io.File
import java.nio.ByteBuffer

@Suppress("ClassName")
class spAnGB(
    framebuffer: ByteBuffer,
    rom: File = File("kirbynightmare.gba")
) {
    val bus = Bus(
        framebuffer,
        cartridge = Cartridge(rom)
    )
    val cpu = CPU(bus)

    fun tick() {
        cpu.tick()
        bus.tick()
    }
}