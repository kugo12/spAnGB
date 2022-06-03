package spAnGB.memory.rom

import spAnGB.memory.Bus
import spAnGB.memory.Memory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

val test = listOf("EEPROM_V", "SRAM_V", "FLASH_V", "FLASH512_V", "FLASH1M_V")

class Cartridge(
    file: File,
    val bus: Bus,
) : Memory {
    val byteBuffer: ByteBuffer
    val intBuffer: IntBuffer
    val shortBuffer: ShortBuffer

    val title: String
    val size: Int

    val persistence: Memory

    init {
        val rom = file.readBytes()

        if (calculateHeaderChecksum(rom) != rom[0xBD]) {
            throw IllegalStateException("Invalid rom file")
        }

        val persistenceType = rom.toString(Charset.defaultCharset()).findAnyOf(test)

        persistence = persistenceType?.let { (_, type) ->
            when (type) {
                "SRAM_V" -> SRAM()
                "FLASH1M_V" -> Flash()
                "FLASH_V" -> Flash(FlashType.Macronix64)
                "EEPROM_V" -> SRAM()
                else -> TODO("$type not supported rn")
            }
        } ?: Memory.silentStub

        title = rom
            .slice(0xA0..0xAB)
            .toByteArray()
            .run { String(this, StandardCharsets.UTF_8) }

        size = rom.size
        byteBuffer = ByteBuffer.wrap(rom).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
        shortBuffer = byteBuffer.asShortBuffer()
        intBuffer = byteBuffer.asIntBuffer()
    }

    private fun calculateHeaderChecksum(rom: ByteArray): Byte =
        rom.slice(0xA0..0xBC)
            .fold((-0x19).toByte()) { acc, i ->
                (acc - i).toByte()
            }


    override fun read8(address: Int): Byte {
        val addr = address and 0x1FFFFFF
        if (addr >= size) {
            return (addr ushr 1).toByte()
        }
        return byteBuffer[addr]
    }
    override fun read16(address: Int): Short {
        val addr = address and 0x1FFFFFF
        if (addr >= size - 2) {
            return (addr ushr 1).toShort()
        }
        return shortBuffer[addr ushr 1]
    }
    override fun read32(address: Int): Int {
        val addr = address and 0x1FFFFFF
        if (addr >= size - 4) {
            val low = addr.ushr(1).and(1.inv()) and 0xFFFF
            return low or (low + 1).shl(16)
        }
        return intBuffer[addr ushr 2]
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}