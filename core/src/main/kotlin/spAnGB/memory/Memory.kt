package spAnGB.memory

import spAnGB.utils.hex
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface Memory {
    fun read8(address: Int): Byte
    fun read16(address: Int): Short
    fun read32(address: Int): Int

    fun write8(address: Int, value: Byte)
    fun write16(address: Int, value: Short)
    fun write32(address: Int, value: Int)

    companion object {
        val stub: Memory = MemoryStub()
        val silentStub = object: Memory {
            override fun read8(address: Int): Byte = 0
            override fun read16(address: Int): Short = 0
            override fun read32(address: Int): Int = 0
            override fun write8(address: Int, value: Byte) {}
            override fun write16(address: Int, value: Short) {}
            override fun write32(address: Int, value: Int) {}
        }
    }
}

open class MemoryStub : Memory {
    private fun todo(address: Int, value: Int? = null) {
        println("Memory stub: ${address.hex}" + (value?.let {
            val b = ByteBuffer.allocate(4).run {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(value)
                array().decodeToString()
            }

            " write ${value.hex} (" + b + ")"
        } ?: ""))
    }

    override fun read8(address: Int) = todo(address).let { 0.toByte() }
    override fun read16(address: Int) = todo(address).let { 0.toShort() }
    override fun read32(address: Int) = todo(address).let { 0 }
    override fun write8(address: Int, value: Byte) = todo(address, value.toInt())
    override fun write16(address: Int, value: Short) = todo(address, value.toInt())
    override fun write32(address: Int, value: Int) = todo(address, value)
}