import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.File
import java.nio.ByteBuffer

fun getClasspathFile(name: String) =
    Thread.currentThread().contextClassLoader
        .getResource(name)!!.file.let(::File)

fun findInClasspath(
    dir: String,
    filter: (File) -> Boolean
) = getClasspathFile(dir).walkTopDown().filter {
    it.isFile && filter(it)
}.toList()

fun ByteBuffer.toArray() = ByteArray(capacity()).apply { get(this); this@toArray.rewind() }

fun ByteBuffer.toImage(width: Int, height: Int) = run {
    val bytesPerPixel = 4

    val buffer = DataBufferByte(toArray(), 240*160*4)
    val raster = Raster.createInterleavedRaster(
        buffer,
        width,
        height,
        bytesPerPixel * width,
        bytesPerPixel,
        IntArray(bytesPerPixel) { it },
        null
    )

    val colorModel = ComponentColorModel(
        ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB),
        true,
        false,
        Transparency.TRANSLUCENT,
        DataBuffer.TYPE_BYTE
    )

    BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied, null)
}
