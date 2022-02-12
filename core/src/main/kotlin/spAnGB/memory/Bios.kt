package spAnGB.memory

import spAnGB.utils.KiB
import spAnGB.utils.hex
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Bios(
    file: File
): Memory {
    val size: Int
    val rom: ByteBuffer = file.readBytes().let {
        assert(it.size == 16*KiB) { "Invalid bios file" }
        if (it.size != 16*KiB) {
            throw IllegalStateException("Invalid bios file")
        }
        size = it.size

        ByteBuffer.wrap(it).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    override fun read8(address: Int): Byte = 0 //rom[address]
    override fun read16(address: Int): Short {
        if (address >= size - 2) return 0

        return rom.getShort(address)
    }
    override fun read32(address: Int): Int {
        if (address >= size - 4) return 0

        return rom.getInt(address)
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}