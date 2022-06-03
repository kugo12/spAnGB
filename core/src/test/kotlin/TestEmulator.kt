import spAnGB.spAnGB
import java.io.File
import java.nio.ByteBuffer

val bios = getClasspathFile("bios.bin")

class TestEmulator(
    rom: File
) {

    var frameCounter = 0

    val framebuffer = ByteBuffer.allocate(160*240*4)

    val emulator = spAnGB(
        framebuffer,
        { frameCounter += 1 },
        rom,
        bios,
//        skipBios = true,
        sampleConsumer = { a, b ->  }
    )

    fun stepFrames(count: Int) {
        (0 .. count).forEach {
            emulator.stepFrame()
        }
    }
}