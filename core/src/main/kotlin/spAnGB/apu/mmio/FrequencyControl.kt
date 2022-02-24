package spAnGB.apu.mmio

import spAnGB.cpu.CLOCK_SPEED
import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.read8From16
import spAnGB.utils.unsetBit
import spAnGB.utils.write8to16

open class FrequencyControl(
    val onRestart: () -> Unit
): Memory {
    var value = 0
    val mask = 0xFFFF

    inline val frequency get() = value and 0x7FF
    inline val shouldStopAfterExpiring get() = value bit 14
    private inline val restart get() = value bit 15

    private fun clearRestart() { value = value unsetBit 15 }

    inline val cyclesPerIncrement get() = CLOCK_SPEED / (131072/(2048-frequency)) / 8

    override fun read8(address: Int): Byte = read8From16(address, value)
    override fun read16(address: Int): Short = value.toShort()

    override fun write8(address: Int, value: Byte) {
        this.value = write8to16(address, this.value, value)

        restart()
    }

    override fun write16(address: Int, value: Short) {
        this.value = (this.value and mask.inv()) or (value.toInt() and mask)

        restart()
    }

    private fun restart() {
        if (restart) {
            clearRestart()
            onRestart()
        }
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}
}