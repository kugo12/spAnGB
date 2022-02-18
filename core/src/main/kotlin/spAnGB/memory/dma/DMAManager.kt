package spAnGB.memory.dma

import spAnGB.memory.Bus

class DMAManager(
    val bus: Bus,
) {
    val scheduler = bus.scheduler

    val dma = arrayOf(
        DMA(this, 0),
        DMA(this, 1),
        DMA(this, 2),
        DMA(this, 3)
    )

    var activeDma = booleanArrayOf(false, false, false, false)

    inline val isDmaActive get() = activeDma.any { it }  // TODO

    val vblankTask = scheduler.task { transfer(DMA.DMAStart.VBlank) }
    val hblankTask = scheduler.task { transfer(DMA.DMAStart.HBlank) }
    val immediateTask = scheduler.task { transfer(DMA.DMAStart.Immediate) }

    private fun transfer(timing: DMA.DMAStart) {
        for (it in dma) {
            if (it.enabled && it.startTiming == timing)
                it.start()
        }
    }

    private fun DMA.start() {  // FIXME
        activeDma[index] = true
        earlyExit = false

        for (it in 0 until index)
            if (activeDma[it]) return

        for (it in index+1 .. 3)
            if (activeDma[it]) dma[it].earlyExit = true

        transfer()

        if (!earlyExit) {
            for (it in index+1 .. 3)
                if (activeDma[it] && dma[it].enabled) dma[it].start()
        } else {
            earlyExit = false
        }
    }
}