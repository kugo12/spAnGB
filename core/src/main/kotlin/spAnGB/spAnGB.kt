package spAnGB

import spAnGB.cpu.CPU
import spAnGB.memory.Bus
import spAnGB.ppu.PPU
import java.nio.ByteBuffer

@Suppress("ClassName")
class spAnGB(
    framebuffer: ByteBuffer
) {
    val bus = Bus(framebuffer)
    val cpu = CPU(bus)

    fun tick() {
        cpu.tick()
        bus.tick()
    }
}