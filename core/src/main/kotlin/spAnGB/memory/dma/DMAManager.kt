package spAnGB.memory.dma

import spAnGB.Scheduler
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
    var isDmaActive = false
    val vblankTask = ::scheduleVblank
    val hblankTask = ::scheduleHblank

    private fun scheduleVblank(taskIndex: Int) {
        scheduler.clear(taskIndex)
        transfer(DMA.DMAStart.VBlank)
    }

    private fun scheduleHblank(taskIndex: Int) {
        scheduler.clear(taskIndex)
        transfer(DMA.DMAStart.HBlank)
    }

    private fun transfer(timing: DMA.DMAStart) {
        dma.forEach {
            if (it.enabled && it.startTiming == timing)
                it.transfer()
        }
    }
}