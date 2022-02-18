package spAnGB.memory.mmio

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.uInt

abstract class SimpleMMIO(  // TODO: Remove this in future
    var value: Int = 0,
    val mask: Int = 0xFFFF
) : Memory {

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short = (value and mask).toShort()

    override fun read32(address: Int): Int = 0

    override fun write8(address: Int, value: Byte) {
        if (address bit 0) {  // high
            this.value = (this.value and 0xFF00.inv()) or (value.uInt.shl(8))
        } else {
            this.value = (this.value and 0xFF.inv()) or (value.uInt)
        }
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.toInt() and mask
    }

    override fun write32(address: Int, value: Int) {}
}