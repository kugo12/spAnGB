package spAnGB.memory.mmio

import spAnGB.memory.Memory
import spAnGB.utils.*
import kotlin.experimental.inv

class InterruptMasterEnable: Memory {
    var enabled = false

    override fun read8(address: Int): Byte = enabled.toInt().toByte()

    override fun read16(address: Int): Short = enabled.toInt().toShort()

    override fun read32(address: Int): Int = enabled.toInt()

    override fun write8(address: Int, value: Byte) {
        enabled = value.toInt() bit 0
    }

    override fun write16(address: Int, value: Short) {
        enabled = value.toInt() bit 0
    }

    override fun write32(address: Int, value: Int) {
        enabled = value bit 0
    }

}

class InterruptEnable: SimpleMMIO() {
    inline operator fun get(flag: Interrupt) = (value and flag.mask) != 0
    inline operator fun set(flag: Interrupt, value: Boolean) {
        this.value = this.value and flag.mask.inv()
        if (value) this.value = this.value or flag.mask
    }
}

class InterruptRequest: Memory {
    var value = 0

    override fun read8(address: Int): Byte = read8From16(address, value)
    override fun read16(address: Int): Short = value.toShort()
    override fun read32(address: Int): Int = value and 0xFFFF

    override fun write8(address: Int, value: Byte) {
        if (address bit 0) {
            this.value = (this.value and 0xFF) or (this.value.and(0xFF00) and value.uInt.shl(8).inv())
        } else {
            this.value = (this.value and 0xFF00) or (this.value.and(0xFF) and value.uInt.inv())
        }
    }

    override fun write16(address: Int, value: Short) {
        this.value = this.value and value.uInt.inv()
    }

    override fun write32(address: Int, value: Int) {}

    inline operator fun get(flag: Interrupt) = (value and flag.mask) != 0
    inline operator fun set(flag: Interrupt, value: Boolean) {
        this.value = this.value and flag.mask.inv()
        if (value) this.value = this.value or flag.mask
    }
}

enum class Interrupt(val mask: Int) {
    VBlank(0x1),
    HBlank(0x2),
    VCount(0x4),
    Timer0(0x8),
    Timer1(0x10),
    Timer2(0x20),
    Timer3(0x40),
    Serial(0x80),
    DMA0(0x100),
    DMA1(0x200),
    DMA2(0x400),
    DMA3(0x800),
    Keypad(0x1000),
    GamePak(0x2000)
}