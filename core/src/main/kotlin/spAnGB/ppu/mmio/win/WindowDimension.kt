package spAnGB.ppu.mmio.win

import spAnGB.memory.Memory
import spAnGB.utils.uInt

class WindowDimension: Memory {
    var value = 0

    inline val left: Int get() = value ushr 8
    inline val right: Int get() = value and 0xFF
    inline val top: Int get() = value ushr 8
    inline val bottom: Int get() = value and 0xFF

    override fun read8(address: Int): Byte = 0
    override fun read16(address: Int): Short = 0
    override fun read32(address: Int): Int = 0

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.uInt
    }

    override fun write32(address: Int, value: Int) {
        TODO("Not yet implemented")
    }
}