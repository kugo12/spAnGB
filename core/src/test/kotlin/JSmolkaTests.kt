import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.properties.Delegates


class JSmolkaTests : StringSpec({
    val passedFramebuffer = getClasspathFile("jsmolka_passed.data")
        .readBytes()

    withData(
        nameFn = Map.Entry<String, *>::key,
        findInClasspath("jsmolka_gba_tests") {
            it.extension == "gba" &&
                    !listOf("ppu", "unsafe").contains(it.parentFile.name)
        }.groupBy { it.parentFile.name }
            .asSequence()
    ) { (_, v) ->
        withData(
            nameFn = { it.nameWithoutExtension },
            v
        ) { rom ->
            val e = TestEmulator(rom)
            e.stepFrames(360)
            withClue(lazy {
                val image = e.framebuffer.toImage(240, 160)
                val file = File("build/test-results/${rom.parentFile.name}/${rom.nameWithoutExtension}.png")
                file.parentFile.mkdirs()
                ImageIO.write(image, "PNG", file)

                file.absolutePath
            }) {
                e.framebuffer.toArray() shouldBe passedFramebuffer
            }
        }
    }
})