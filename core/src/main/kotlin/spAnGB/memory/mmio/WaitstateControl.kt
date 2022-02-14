package spAnGB.memory.mmio

import spAnGB.memory.AccessType
import spAnGB.memory.Memory
import spAnGB.utils.bit
import spAnGB.utils.uInt

private val CYCLES = intArrayOf(5, 4, 3, 9)
private val WS0SA = intArrayOf(3, 2)
private val WS1SA = intArrayOf(5, 2)
private val WS2SA = intArrayOf(9, 2)
private val SA = arrayOf(WS0SA, WS1SA, WS2SA)

class WaitstateControl : Memory {
    var value: Int = 0
    var sram: Int = CYCLES[0]
    var lut: Array<IntArray> = arrayOf(
        intArrayOf(5, 3),
        intArrayOf(5, 5),
        intArrayOf(5, 9),
    )

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short = value.toShort()


    override fun write8(address: Int, value: Byte) {
        if (address bit 0) {  // high
            this.value = (this.value and 0xFF00.inv()) or (value.uInt.shl(8))
        } else {
            this.value = (this.value and 0xFF.inv()) or (value.uInt)
        }
        generateLut()
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.uInt
        generateLut()
    }

    override fun write32(address: Int, value: Int) {}
    override fun read32(address: Int): Int = 0

    fun generateLut() {
        sram = CYCLES[value and 3]

        for (it in 0 .. 2) {
            val x = value.ushr(2 + 3*it)
            lut[it][AccessType.NonSequential.ordinal] = CYCLES[x and 3]
            lut[it][AccessType.Sequential.ordinal] = SA[it][x.ushr(2).and(1)]
        }
    }
}