package spAnGB.ppu.mmio.bg

import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.uInt

class BackgroundCoordinate: Memory {
    var value: Int = 0
    var internal: Int = 0
    var lock: Boolean = false

    override fun read8(address: Int) = 0.toByte()
    override fun read16(address: Int) = 0.toShort()
    override fun read32(address: Int) = 0

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        if (address bit 1) {  // upper
            this.value = (this.value and 0xFFFF) or (value.uInt.shl(20).shr(4))
//            this.value = (this.value and 0xFFFF) or (value.uInt.shl(16))
        } else {
            this.value = (this.value and 0xFFFF.inv()) or value.uInt
        }
        if (!lock) {
            internal = this.value
        }
    }

    override fun write32(address: Int, value: Int) {}
}