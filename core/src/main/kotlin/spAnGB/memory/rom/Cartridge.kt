package spAnGB.memory.rom

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
    file: File
) : Memory {
    val byteBuffer: ByteBuffer
    val intBuffer: IntBuffer
    val shortBuffer: ShortBuffer

    val title: String
    val size: Int

    val persistence: Memory = FlashStub()

    init {
        val rom = file.readBytes()

        if (calculateHeaderChecksum(rom) != rom[0xBD]) {
            throw IllegalStateException("Invalid rom file")
        }

        rom.toString(Charset.defaultCharset()).findAnyOf(test)?.let { (_, type) ->
//            TODO("$type not supported rn")
        }

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
        if (addr >= size) {  // TODO
            return 0
        }
        return byteBuffer[addr]
    }
    override fun read16(address: Int): Short {
        val addr = address and 0x1FFFFFF
        if (addr >= size - 2) {  // TODO
            return 0
        }
        return shortBuffer[addr ushr 1]
    }
    override fun read32(address: Int): Int {
        val addr = address and 0x1FFFFFF
        if (addr >= size - 4) {  // TODO
            return 0
        }
        return intBuffer[addr ushr 2]
    }

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}