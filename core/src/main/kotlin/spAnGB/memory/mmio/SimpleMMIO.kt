package spAnGB.memory.mmio

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.read8From16
import spAnGB.utils.uInt
import spAnGB.utils.write8to16

abstract class SimpleMMIO(  // TODO: Remove this in future
    var value: Int = 0,
    val mask: Int = 0xFFFF
) : Memory {

    override fun read8(address: Int): Byte = read8From16(address, value)

    override fun read16(address: Int): Short = value.toShort()

    override fun read32(address: Int): Int = 0

    override fun write8(address: Int, value: Byte) {
        this.value = write8to16(address, this.value, value)
    }

    override fun write16(address: Int, value: Short) {
        this.value = (this.value and mask.inv()) or (value.toInt() and mask)
    }

    override fun write32(address: Int, value: Int) {}
}