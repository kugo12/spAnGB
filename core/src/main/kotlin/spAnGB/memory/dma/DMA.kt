@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.memory.dma

import spAnGB.memory.AccessType
import spAnGB.memory.Memory
import spAnGB.memory.dma.mmio.DMAAddress
import spAnGB.utils.bit
import spAnGB.utils.hex
import spAnGB.utils.uInt

class DMALatch {
    var count = 0
    var source = 0
    var destination = 0
    var last = 0
}

class DMA(
    // It is dma control by itself
    val manager: DMAManager,
    val index: Int
) : Memory {
    val cntMask = if (index == 3) 0xFFE0 else 0xF7E0
    val bus = manager.bus
    val cpu = bus.cpu
    val ir = bus.mmio.ir
    val scheduler = bus.scheduler
    val mask = if (index == 3) 0xFFFF else 0x3FFF

    val destination = DMAAddress(if (index == 3) 0xFFF else 0x7FF)
    val source = DMAAddress(if (index == 0) 0x7FF else 0xFFF)

    val latch = DMALatch()

    var earlyExit = false

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
    var value = 0

    val destAddrControl: AddrControl get() = AddrControl.values[(value ushr 5) and 3]
    val sourceAddrControl: AddrControl get() = AddrControl.values[(value ushr 7) and 3]
    val repeat: Boolean get() = value bit 9
    var is32Bit: Boolean
        get() = value bit 10
        set(newValue) {
            value = value and (1.shl(10).inv())
            if (newValue) value = value or (1.shl(10))
        }
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

    override fun read16(address: Int): Short =
        if (address bit 1) value.toShort() else 0

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        if (address bit 1) {
            val wasEnabled = enabled
            this.value = value.toInt() and cntMask

            if (!wasEnabled && enabled) {
                latch.source = source.value
                latch.destination = destination.value
                latch.count = count

                if (startTiming == DMAStart.Immediate) {
                    scheduler.schedule(2, manager.immediateTask)
                }
            }
        } else {
            count = value.toInt() and mask
            if (count == 0) count = mask + 1
        }
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}

    fun transfer() {
        earlyExit = false

        val isSourceInRom = latch.source ushr 24 in 0x8..0xD
        if (isSourceInRom) {
            value = value and ((3 shl 7).inv())  // force increment
        }

        if (is32Bit) transferWords() else transferHalfWords()

        cpu.prefetchAccess = AccessType.NonSequential
    }

    private fun transferWords() {
        transferX(3.inv(), 4, bus::read32, bus::write32)
    }

    private fun transferHalfWords() {
        transferX(
            1.inv(),
            2,
            { address, access ->
                bus.read16(address, access).uInt.let { it or it.shl(16) }
            },
            { address, value, access ->
                bus.write16(address, value.ushr(address.and(2) shl 3).toShort(), access)
            }
        )
    }

    private inline fun transferX(
        mask: Int,
        size: Int,
        read: (Int, AccessType) -> Int,
        write: (Int, Int, AccessType) -> Unit
    ) {
        latch.destination = latch.destination and mask
        latch.source = latch.source and mask

        val destinationOffset = getDestinationOffset(size)
        val sourceOffset = getSourceOffset(size)
        var accessSource = AccessType.Sequential
        var accessDestination = AccessType.Sequential
        var wasRomAccessed = false

        while (latch.count > 0) {
            if (earlyExit) return

            if (!wasRomAccessed) {
                when {
                    latch.source >= 0x8000000 -> {
                        accessSource = AccessType.NonSequential
                        wasRomAccessed = true
                    }
                    latch.destination >= 0x8000000 -> {
                        accessDestination = AccessType.NonSequential
                        wasRomAccessed = true
                    }
                }
            }

            if (latch.source >= 0x2000000)
                latch.last = read(latch.source, accessSource)
            else bus.idle()

            write(latch.destination, latch.last, accessDestination)

            accessSource = AccessType.Sequential
            accessDestination = AccessType.Sequential
            latch.destination += destinationOffset
            latch.source += sourceOffset
            --latch.count
        }

        endTransfer()
    }

    private fun endTransfer() {
        manager.activeDma[index] = false
        if (irqEnabled) {
            ir.value = ir.value or (1 shl (8 + index))
        }

        if (repeat && startTiming != DMAStart.Immediate) {
            latch.count = count
            if (destAddrControl == AddrControl.Reload) {
                latch.destination = destination.value
            }
            return
        }

        enabled = false
    }

    private inline fun getDestinationOffset(size: Int) =
        when (destAddrControl) {
            AddrControl.Decrement -> -size
            AddrControl.Fixed -> 0
            else -> size
        }

    private inline fun getSourceOffset(size: Int) =
        when (sourceAddrControl) {
            AddrControl.Increment -> size
            AddrControl.Decrement -> -size
            else -> 0
        }
}