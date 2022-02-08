package spAnGB.memory.mmio

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.uInt

abstract class SimpleMMIO(  // TODO: Remove this in future
    var value: Int = 0
) : Memory {

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short = value.toShort()

    override fun read32(address: Int): Int = value

    override fun write8(address: Int, value: Byte) {
        if (address bit 0) {  // high
            this.value = (this.value and 0xFF00.inv()) or (value.uInt.shl(8))
        } else {
            this.value = (this.value and 0xFF.inv()) or (value.uInt)
        }
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.uInt
    }

    override fun write32(address: Int, value: Int) {
        this.value = value and 0xFFFF
    }
}