package spAnGB.memory.dma

import spAnGB.memory.Bus
import spAnGB.memory.Memory
import spAnGB.memory.dma.mmio.DMAAddress

class DMA(  // It is dma control by itself
    val bus: Bus,
    val mask: Int = 0x3FFF
): Memory {
    val destination = DMAAddress()
    val source = DMAAddress()

    var value = 0

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short {
        TODO("Not yet implemented")
    }

    override fun read32(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        TODO("Not yet implemented")
    }

    override fun write32(address: Int, value: Int) {
        this.value = value and mask
        if (this.value == 0) this.value = mask + 1
        transferImmediately()
    }

    fun transferImmediately() {
        val src = source.value
        val dest = destination.value

        (0 .. value).forEach {
            bus.write16(
                dest + it*2,
                bus.read16(
                    src + it*2
                )
            )
        }
    }
}