package spAnGB

import spAnGB.apu.AudioManager
import spAnGB.apu.SampleConsumer
import spAnGB.cpu.CLOCK_SPEED
import spAnGB.memory.rom.Bios
import spAnGB.memory.Bus
import spAnGB.memory.UnusedMemory
import java.io.File
import java.nio.ByteBuffer

const val CYCLES_PER_FRAME = CLOCK_SPEED / 60

@Suppress("ClassName")
class spAnGB(
    framebuffer: ByteBuffer,
    blitFramebuffer: () -> Unit,
    rom: File = File("flash128.gba"),
    bios: File = File("bios.bin"),
    skipBios: Boolean = false,
    sampleConsumer: SampleConsumer = AudioManager()
) {
    val scheduler = Scheduler()
    val bus = Bus(
        framebuffer,
        blitFramebuffer,
        sampleConsumer,
        scheduler = scheduler
    )
    val cpu = bus.cpu

    init {
        val unused = UnusedMemory(cpu, bus)
        bus.bios = Bios(bios, bus, cpu, unused)
        bus.unusedMemory = unused
        bus.loadCartridge(rom)
        cpu.setUp(skipBios)
    }

    fun tick() {
        if(cpu.tick())
            scheduler.tick()
    }

    fun stepFrame() {
        val target = scheduler.counter + CYCLES_PER_FRAME
        while (scheduler.counter < target) tick()
    }
}