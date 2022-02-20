package spAnGB.memory.dma.mmio

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.uInt
import spAnGB.utils.write8to32
import kotlin.experimental.and

class DMAAddress(
    val mask: Int
): Memory {
    var value = 0

    override fun read8(address: Int): Byte = 0
    override fun read16(address: Int): Short = 0
    override fun read32(address: Int): Int = 0

    override fun write8(address: Int, value: Byte) {
        this.value = write8to32(address, this.value, value) and (mask.shl(16) or 0xFFFF)
    }

    override fun write16(address: Int, value: Short) {
        if (address bit 1) {
            this.value = (this.value and 0xFFFF) or (value.uInt.and(mask).shl(16))
        } else {
            this.value = (this.value and mask.shl(16)) or value.uInt
        }
    }

    override fun write32(address: Int, value: Int) { TODO() }
}