package spAnGB.memory.dma

import spAnGB.memory.AccessType
import spAnGB.memory.Bus
import spAnGB.memory.Memory
import spAnGB.memory.dma.mmio.DMAAddress
import spAnGB.utils.bit
import spAnGB.utils.uInt

class DMALatch {
    var count = 0
    var source = 0
    var destination = 0
    var last = 0
}

class DMA(
    // It is dma control by itself
    val bus: Bus,
    val index: Int
) : Memory {
    val cpu = bus.cpu
    val scheduler = bus.scheduler
    var active: Boolean = false
    val mask = if (index == 3) 0xFFFF else 0x3FFF

    val destination = DMAAddress(if (index == 3) 0xFFF else 0x7FF)
    val source = DMAAddress(if (index == 0) 0x7FF else 0xFFF)

    val latch = DMALatch()

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

// TODO
//    11    Game Pak DRQ  - DMA3 only -  (0=Normal, 1=DRQ <from> Game Pak, DMA3)
//    12-13 DMA Start Timing  (0=Immediately, 1=VBlank, 2=HBlank, 3=Special)
//    The 'Special' setting (Start Timing=3) depends on the DMA channel:
//    DMA0=Prohibited, DMA1/DMA2=Sound FIFO, DMA3=Video Capture
//    14    IRQ upon end of Word Count   (0=Disable, 1=Enable)
    var value = 0

    val destAddrControl: AddrControl get() = AddrControl.values[(value ushr 5) and 3]
    val sourceAddrControl: AddrControl get() = AddrControl.values[(value ushr 7) and 3]
    val repeat: Boolean get() = value bit 9
    val is32Bit: Boolean get() = value bit 10
    val startTiming: DMAStart get() = DMAStart.values[(value ushr 12) and 3]
    val irqEnabled: Boolean get() = value bit 14
    var enabled: Boolean
        get() = value bit 15
        set(newValue) {
            value = value and (1.shl(15).inv())
            if (newValue) value = value or (1.shl(15))
        }


    var count = 0

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short {  // FIXME
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
            val wasEnabled = enabled
            this.value = value.toInt()
            if (!wasEnabled && enabled) {
                latch.count = count
                latch.source = source.value
                latch.destination = destination.value

                if (startTiming == DMAStart.Immediate) transfer()
            }
        } else {
            count = value.toInt() and mask
            if (count == 0) count = mask + 1
        }
    }

    override fun write32(address: Int, value: Int) {
        TODO()
    }

    fun transfer() {
        active = true
        bus.idle()  // FIXME
        bus.idle()

        if (latch.source ushr 24 in 0x8 .. 0xD) {
            value = value and ((3 shl 7).inv())
            if (latch.destination ushr 24 in 0x8 .. 0xD) {
                bus.idle()
                bus.idle()
            }
        }
        if (is32Bit) transferWords() else transferHalfWords()
        cpu.prefetchAccess = AccessType.NonSequential
        active = false
    }

    fun transferWords() {
        latch.destination = latch.destination and 3.inv()
        latch.source = latch.source and 3.inv()

        var access = AccessType.NonSequential
        for (it in 0 until latch.count) {
            val source = getSource(it * 4)
            if (source >= 0x2000000)
                latch.last = bus.read32(source, access)

            bus.write32(
                getDestination(it * 4),
                latch.last,
                access
            )
            access = AccessType.Sequential
        }

        endTransfer()
    }

    fun transferHalfWords() {
        latch.destination = latch.destination and 1.inv()
        latch.source = latch.source and 1.inv()

        var access = AccessType.NonSequential
        for (it in 0 until latch.count) {
            val source = getSource(it * 2)
            if (source >= 0x2000000)
                latch.last = bus.read16(source, access).uInt.let { it or it.shl(16) }

            val dest = getDestination(it * 2)
            bus.write16(
                dest,
                latch.last.ushr(dest.and(2) shl 3).toShort(),
                access
            )
            access = AccessType.Sequential
        }

        endTransfer()
    }

    fun endTransfer() {
        if (!repeat || startTiming == DMAStart.Immediate) {
            enabled = false
        } else {
            latch.count = count
            if (destAddrControl == AddrControl.Reload) {
                latch.destination = destination.value
            }
        }
    }

    inline fun getDestination(offset: Int) = when (destAddrControl) {
        AddrControl.Increment -> latch.destination + offset
        AddrControl.Decrement -> latch.destination - offset
        AddrControl.Fixed -> latch.destination
        AddrControl.Reload -> latch.destination + offset
    }

    inline fun getSource(offset: Int) = when (sourceAddrControl) {
        AddrControl.Increment -> latch.source + offset
        AddrControl.Decrement -> latch.source - offset
        else -> latch.source
    }
}