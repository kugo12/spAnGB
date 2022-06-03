package spAnGB.memory

import spAnGB.utils.hex
import spAnGB.utils.uLong

interface Memory {
    fun read8(address: Int): Byte
    fun read16(address: Int): Short
    fun read32(address: Int): Int

    fun write8(address: Int, value: Byte)
    fun write16(address: Int, value: Short)
    fun write32(address: Int, value: Int)

    companion object {
        val silentStub = object : Memory {
            override fun read8(address: Int): Byte = 0xFF.toByte()
            override fun read16(address: Int): Short = 0xFFFF.toShort()
            override fun read32(address: Int): Int = 0xFFFFFFFF.toInt()
            override fun write8(address: Int, value: Byte) {}
            override fun write16(address: Int, value: Short) {}
            override fun write32(address: Int, value: Int) {}
        }
        val stub: Memory = silentStub // MemoryStub()
        val zero: Memory = silentStub
    }
}

open class MemoryStub : Memory {
    private fun todo(address: Int, value: Long) {
        println("Memory stub: ${address.hex}" + (if (value != -1L) " write ${value.toInt().hex}" else ""))
    }

    override fun read8(address: Int) = todo(address, -1L).let { 0.toByte() }
    override fun read16(address: Int) = todo(address, -1L).let { 0.toShort() }
    override fun read32(address: Int) = todo(address, -1L).let { 0 }
    override fun write8(address: Int, value: Byte) = todo(address, value.uLong)
    override fun write16(address: Int, value: Short) = todo(address, value.uLong)
    override fun write32(address: Int, value: Int) = todo(address, value.uLong)
}