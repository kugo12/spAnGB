package spAnGB.hw

import spAnGB.Scheduler
import spAnGB.cpu.CLOCK_SPEED
import spAnGB.memory.Memory
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.InterruptRequest
import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.*

const val Serial2MHz = CLOCK_SPEED / 2_000_000
const val Serial256KHz = CLOCK_SPEED / 256_000

class SerialData8 : Memory {
    var value: Byte = 0

    override fun read8(address: Int): Byte = value
    override fun read16(address: Int): Short = value.uShort
    override fun write8(address: Int, value: Byte) {
        this.value = value
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.toByte()
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}
}

class SerialData32 : Memory {
    var value: Int = 0

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short =
        if (address bit 1) {
            (value ushr 16).toShort()
        } else {
            value.toShort()
        }

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        if (address bit 1) {
            this.value = (this.value and 0xFFFF) or (value.uInt shl 16)
        } else {
            this.value = (this.value and 0xFFFF.inv()) or value.uInt
        }
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}
}

class SerialRControl : SimpleMMIO()  // FIXME

class Serial(  // FIXME
    val scheduler: Scheduler,
    val ir: InterruptRequest
) : Memory {
    val rCnt = SerialRControl()
    val data32 = SerialData32()
    val data8 = SerialData8()

    var control = 0

    inline val is2MHz get() = control bit 1
    inline val is32Bit get() = control bit 12
    inline val isIrqEnabled get() = control bit 14
    inline val isEnabled get() = control bit 7

    inline val tickRate get() = if (is2MHz) Serial2MHz else Serial256KHz
    inline val tickRequired get() = if (is32Bit) 32L else 8L
    inline val transferTime get() = tickRate * tickRequired

    val transferTask = scheduler.task {
        stop()

        if (isIrqEnabled) ir[Interrupt.Serial] = true
    }

    override fun read8(address: Int): Byte = read8From16(address, control)

    override fun read16(address: Int): Short = control.toShort()

    override fun write8(address: Int, value: Byte) {
        control = write8to16(address, control, value)
    }

    override fun write16(address: Int, value: Short) {
        control = value.uInt

        if (isEnabled) {
            scheduler.schedule(transferTime, transferTask)
        }
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}

    fun stop() {
        control = control and (1 shl 7).inv()
    }
}