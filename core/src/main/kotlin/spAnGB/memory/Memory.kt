package spAnGB.memory

import spAnGB.utils.hex

interface Memory {
    fun read8(address: Int): Byte
    fun read16(address: Int): Short
    fun read32(address: Int): Int

    fun write8(address: Int, value: Byte)
    fun write16(address: Int, value: Short)
    fun write32(address: Int, value: Int)

    companion object {
        val stub: Memory = MemoryStub()
    }
}

open class MemoryStub : Memory {
    private fun todo(address: Int, value: Int? = null) {
        println("Memory stub: ${address.hex}" + (value?.let { " write ${value.hex}" } ?: ""))
    }

    override fun read8(address: Int) = todo(address).let { 0.toByte() }
    override fun read16(address: Int) = todo(address).let { 0.toShort() }
    override fun read32(address: Int) = todo(address).let { 0 }
    override fun write8(address: Int, value: Byte) = todo(address, value.toInt())
    override fun write16(address: Int, value: Short) = todo(address, value.toInt())
    override fun write32(address: Int, value: Int) = todo(address, value)
}