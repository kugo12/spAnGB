package spAnGB.memory.dma

import spAnGB.Scheduler

class DMAManager(
    val dma: Array<DMA>,
    val scheduler: Scheduler
) {
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