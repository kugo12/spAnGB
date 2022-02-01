package spAnGB.memory.dma

import spAnGB.memory.Bus
import spAnGB.memory.Memory
import spAnGB.memory.dma.mmio.DMAAddress
import spAnGB.utils.bit
import spAnGB.utils.hex
import spAnGB.utils.uInt
import kotlin.experimental.and

class DMA(  // It is dma control by itself
    val bus: Bus,
    val mask: Int = 0x3FFF
) : Memory {
    val destination = DMAAddress()
    val source = DMAAddress()

    enum class AddrControl {
        Increment, Decrement, Fixed, Reload;

        companion object {
            val values = values()
        }
    }

    enum class DMAStart {
        Immediate, VBlank, HBlank, Special;

        companion object {
            val values = values()
        }
    }

    //    Bit   Expl.
//    0-4   Not used
//    5-6   Dest Addr Control  (0=Increment,1=Decrement,2=Fixed,3=Increment/Reload)
//    7-8   Source Adr Control (0=Increment,1=Decrement,2=Fixed,3=Prohibited)
//    9     DMA Repeat                   (0=Off, 1=On) (Must be zero if Bit 11 set)
//    10    DMA Transfer Type            (0=16bit, 1=32bit)
//    11    Game Pak DRQ  - DMA3 only -  (0=Normal, 1=DRQ <from> Game Pak, DMA3)
//    12-13 DMA Start Timing  (0=Immediately, 1=VBlank, 2=HBlank, 3=Special)
//    The 'Special' setting (Start Timing=3) depends on the DMA channel:
//    DMA0=Prohibited, DMA1/DMA2=Sound FIFO, DMA3=Video Capture
//    14    IRQ upon end of Word Count   (0=Disable, 1=Enable)
//    15    DMA Enable                   (0=Off, 1=On)
    var value = 0

    val destAddrControl: AddrControl get() = AddrControl.values[(value ushr 5) and 3]
    val sourceAddrControl: AddrControl get() = AddrControl.values[(value ushr 7) and 3]
    val repeat: Boolean get() = value bit 9
    val is32Bit: Boolean get() = value bit 10
    val startTiming: DMAStart get() = DMAStart.values[(value ushr 12) and 3]
    val irqEnabled: Boolean get() = value bit 14
    val enabled: Boolean get() = value bit 15


    var count = 0

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short {
        return count.toShort()
//        TODO("Not yet implemented")
    }

    override fun read32(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        if (address bit 1) {
            this.value = value.toInt()
            if (enabled) transferImmediately()
        } else {
            count = value.toInt() and mask
            if (count == 0) count = mask + 1
        }
    }

    override fun write32(address: Int, value: Int) { TODO() }

    fun transferImmediately() {
        // 1220 memory tests
        // 4492 dma
        if (is32Bit) {
            destination.value = destination.value and 3.inv()
            source.value = source.value and 3.inv()

            (0 until count).forEach {
                bus.write32(
                    getDestination(it * 4),
                    bus.read32(getSource(it * 4))
                )
            }
        } else {
            destination.value = destination.value and 1.inv()
            source.value = source.value and 1.inv()

            (0 until count).forEach {
                bus.write16(
                    getDestination(it * 2),
                    bus.read16(getSource(it * 2))
                )
            }
        }
    }

    inline fun getDestination(offset: Int) = when (destAddrControl) {
        AddrControl.Increment -> destination.value + offset
        AddrControl.Decrement -> destination.value - offset
        AddrControl.Fixed -> destination.value
        AddrControl.Reload -> destination.value + offset  // TODO
    }

    inline fun getSource(offset: Int) = when (sourceAddrControl) {
        AddrControl.Increment -> source.value + offset
        AddrControl.Decrement -> source.value - offset
        AddrControl.Fixed -> source.value
        AddrControl.Reload -> source.value + offset  // TODO
    }
}