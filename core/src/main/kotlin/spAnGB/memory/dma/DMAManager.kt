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
    inline val firstActiveDMA get() = activeDma.indexOfFirst { it }

    var isRunning = false

    val vblankTask = scheduler.task { transfer(DMA.DMAStart.VBlank) }
    val hblankTask = scheduler.task { transfer(DMA.DMAStart.HBlank) }
    val immediateTask = scheduler.task { transfer(DMA.DMAStart.Immediate) }
    val videoTask = scheduler.task { transferVideo() }

    private fun transfer(timing: DMA.DMAStart) {
        for (it in dma) {
            if (it.enabled && it.startTiming == timing)
                activeDma[it.index] = true
        }

        start()
    }

    private fun start() {
        if (isRunning) {
            for (it in firstActiveDMA + 1..3)
                if (activeDma[it]) dma[it].earlyExit = true

            return
        }

        isRunning = true
        bus.idle()

        while (isDmaActive) dma[firstActiveDMA].transfer()
        isRunning = false

        bus.idle()
    }

    private fun transferVideo() {
        val dma = dma[3]
        if (dma.enabled && dma.startTiming == DMA.DMAStart.Special) {
            activeDma[3] = true
            start()
        }
    }

    fun stopVideoTransfer() {
        val dma = dma[3]

        if (dma.enabled && dma.startTiming == DMA.DMAStart.Special)
            dma.enabled = false
    }
}