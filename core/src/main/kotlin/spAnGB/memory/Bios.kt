package spAnGB.memory

import spAnGB.cpu.CPU
import spAnGB.utils.KiB
import spAnGB.utils.hex
import spAnGB.utils.uInt
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Bios(
    file: File,
    val bus: Bus,
    val cpu: CPU,
    val unusedMemory: UnusedMemory
): Memory {
    val size: Int
    val byteBuffer: ByteBuffer = file.readBytes().let {
        assert(it.size == 16*KiB) { "Invalid bios file" }
        if (it.size != 16*KiB) {
            throw IllegalStateException("Invalid bios file")
        }
        size = it.size

        ByteBuffer.wrap(it).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
    }
    val shortBuffer = byteBuffer.asShortBuffer()
    val intBuffer = byteBuffer.asIntBuffer()
    var last = intBuffer[228]

    override fun read8(address: Int): Byte {
        if (address >= size) return unusedMemory.read8(address)

        return if (isPcInBios) {
            byteBuffer.get(address)
        } else {
            last.toByte()
        }
    }
    override fun read16(address: Int): Short {
        if (address >= size - 2) return unusedMemory.read16(address)

        return if (isPcInBios) {
            shortBuffer.get(address.ushr(1))
                .also { last = it.uInt }
        } else {
            last.toShort()
        }
    }
    override fun read32(address: Int): Int {
        if (address >= size - 4) return unusedMemory.read32(address)

        return if (isPcInBios) {
            intBuffer.get(address.ushr(2))
                .also { last = it }
        } else {
            last
        }
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}

    inline val isPcInBios get() = cpu.pc in 0..0x3FFF
}