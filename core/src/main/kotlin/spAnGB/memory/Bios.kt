package spAnGB.memory

import spAnGB.utils.KiB
import spAnGB.utils.hex
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Bios(
    path: String
): Memory {
    val size: Int
    val rom: ByteBuffer = File(path).readBytes().let {
        assert(it.size == 16*KiB) { "Invalid bios file" }
        if (it.size != 16*KiB) {
            throw IllegalStateException("Invalid bios file")
        }
        size = it.size

        ByteBuffer.wrap(it).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    override fun read8(address: Int): Byte = rom[address]
    override fun read16(address: Int): Short = rom.getShort(address)
    override fun read32(address: Int): Int = rom.getInt(address)

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}