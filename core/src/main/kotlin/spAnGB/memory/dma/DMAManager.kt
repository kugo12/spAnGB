package spAnGB.memory.dma

import spAnGB.Scheduler

class DMAManager(
    val dma: Array<DMA>,
    val scheduler: Scheduler
) {
    val vblankTask = ::scheduleVblank
    val hblankTask = ::scheduleHblank

    private fun scheduleVblank(taskIndex: Int) {
        transfer(DMA.DMAStart.VBlank)
        scheduler.clear(taskIndex)
    }

    private fun scheduleHblank(taskIndex: Int) {
        transfer(DMA.DMAStart.HBlank)
        scheduler.clear(taskIndex)
    }

    private fun transfer(timing: DMA.DMAStart) {
        dma.forEach {
            if (it.enabled && it.startTiming == timing)
                it.transfer()
        }
    }
}