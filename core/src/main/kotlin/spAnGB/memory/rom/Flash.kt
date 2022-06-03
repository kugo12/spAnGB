package spAnGB.memory.rom

import spAnGB.memory.Memory
import spAnGB.memory.rom.FlashCommandState.*
import spAnGB.utils.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class FlashType(val id: Int, val size: Int) {
    SST(0xD4BF, 64 * KiB),
    Macronix64(0x1CC2, 64 * KiB),
    Panasonic(0x1B32, 64 * KiB),
    Atmel(0x3D1F, 64 * KiB),
    Sanyo(0x1362, 128 * KiB),
    Macronix128(0x09C2, 128 * KiB);

    inline val isBanked get() = size == 128 * KiB
}

enum class FlashCommandState { None, First, Second }

enum class FlashState { Ready, Write, Erase, ChangeBank }

class Flash(
    val type: FlashType = FlashType.Macronix128
) : Memory {
    var isIdentification = false
    var state = FlashState.Ready
    var commandState = None

    var offset = 0

    val byteBuffer = ByteBuffer.allocateDirect(type.size).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }

    init {
        erase()
    }

    override fun read8(address: Int): Byte {
        val addr = address and 0xFFFF

        return when {
            addr <= 1 && isIdentification -> {
                (type.id ushr addr.shl(3)).toByte()
            }
            else -> byteBuffer[addr + offset].also { println("Read ${it.uInt.hex} to ${(addr + offset).hex}") }
        }
    }

    override fun write8(address: Int, value: Byte) {
        val addr = address and 0xFFFF

        when {
            state == FlashState.Write -> {
                state = FlashState.Ready
                byteBuffer[addr + offset] = value
                println("write to: ${(addr + offset).hex} v=${value.uInt.hex}, addr=${addr.hex}")
            }
            state == FlashState.ChangeBank && addr == 0 && value.uInt < 2 -> {
                state = FlashState.Ready
                offset = 0x10000 * value
                println("ChangeBank to $value")
            }
            state == FlashState.Erase && commandState == Second && addr and 0xFFF == 0 && value == 0x30.toByte() -> {
                state = FlashState.Ready
                erase(addr, addr + 0x1000)
            }
            addr == 0x5555 && value == 0xAA.toByte() && commandState == None -> {
                commandState = First
                println("CMD1")
            }
            addr == 0x2AAA && value == 0x55.toByte() && commandState == First -> {
                commandState = Second
                println("CMD2")
            }
            addr == 0x5555 && commandState == Second -> {
                println("CMD Dispatch ${value.uInt.hex}")
                commandState = None
                dispatchCommand(value.uInt)
            }
        }
    }

    override fun read16(address: Int): Short = byteBuffer[address and SRAM_MASK + offset].uShort.times(0x101).toShort()

    override fun read32(address: Int): Int = byteBuffer[address and SRAM_MASK + offset].uInt.times(0x1010101)

    override fun write16(address: Int, value: Short) {
        byteBuffer.put(address and SRAM_MASK + offset, value.uInt.rotateRight(address shl 3).toByte())
    }

    override fun write32(address: Int, value: Int) {
        byteBuffer.put(address and SRAM_MASK + offset, value.rotateRight(address shl 3).toByte())
    }

    fun dispatchCommand(command: Int) {
        when {
            command == 0x90 -> isIdentification = true
            command == 0xF0 -> isIdentification = false
            command == 0x80 -> {
                state = FlashState.Erase
                return
            }
            command == 0xA0 -> {
                state = FlashState.Write
                return
            }
            command == 0x10 && state == FlashState.Erase -> erase(offset = 0)
            command == 0xB0 && type.isBanked -> {
                state = FlashState.ChangeBank
                return
            }
        }

        state = FlashState.Ready
    }

    fun erase(start: Int = 0, end: Int = type.size, offset: Int = this.offset) {  // TODO: timings?
        for (it in start + offset until end + offset)
            byteBuffer[it] = 0xFF.toByte()
    }
}