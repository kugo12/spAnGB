package spAnGB.memory

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class Cartridge(
    path: String
) : Memory {
    val content: ByteBuffer
    val title: String
    val size: Int

    init {
        val rom = File(path)
            .readBytes()

        if (calculateHeaderChecksum(rom) != rom[0xBD]) {
            throw IllegalStateException("Invalid rom file")
        }

        title = rom
            .slice(0xA0..0xAB)
            .toByteArray()
            .run { String(this, StandardCharsets.UTF_8) }

        size = rom.size
        content = ByteBuffer.wrap(rom).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    private fun calculateHeaderChecksum(rom: ByteArray): Byte =
        rom.slice(0xA0..0xBC)
            .fold((-0x19).toByte()) { acc, i ->
                (acc - i).toByte()
            }


    override fun read8(address: Int): Byte {
        val addr = address and 0x1FFFFFF
        if (addr >= size) {  // TODO
            return 0
        }
        return content[addr]
    }
    override fun read16(address: Int): Short {
        val addr = address and 0x1FFFFFF
        if (addr >= size - 2) {  // TODO
            return 0
        }
        return content.getShort(addr)
    }
    override fun read32(address: Int): Int {
        val addr = address and 0x1FFFFFF
        if (addr >= size - 4) {  // TODO
            return 0
        }
        return content.getInt(addr)
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}