package spAnGB.memory.rom

import spAnGB.cpu.CPU
import spAnGB.cpu.CPUState
import spAnGB.memory.mmio.WaitstateControl
import spAnGB.utils.toInt

class GamepakPrefetch(
    val waitCnt: WaitstateControl,
    val cpu: CPU
) {
    val bus = cpu.bus
    val dmaManager = cpu.bus.dmaManager
    inline val isEnabled get() = waitCnt.isPrefetchEnabled

    var mBytes = 4
    var mSize = 4
    var mCycles = waitCnt.lut[0][1] * 2
    var cyclesLeft = mCycles
    var headAddress = -1
    var size = 0
    var bubble = false

    var isActive = false

    fun tick() {
        if (isEnabled && isActive) {
            if (--cyclesLeft <= 0) {
                ++size
                cyclesLeft = mCycles
                if (size >= mSize) {
                    isActive = false
                    bubble = false
                    return
                }
            }
            bubble = cyclesLeft == 1 || (mBytes == 4 && cyclesLeft == mCycles / 2 + 1)
        }
    }

    inline fun <reified W> prefetch(address: Int, cycles: Int, sequential: Int): Int {
        // TODO: weird DMA and prefetch tick penalty

        if (!isEnabled || Unit is W || dmaManager.isDmaActive || address != cpu.pc) {
            return cycles +
                    (bubble && isEnabled && isActive).toInt()
                        .also { stop() }
        }

        if (address == headAddress && size > 0) {
            headAddress += mBytes
            --size
            return 1
        }

        if (isActive && address == headAddress + mBytes * size) {
            return cyclesLeft.also {
                headAddress += mBytes * (size + 1)
                size = 0
                cyclesLeft += sequential
            }
        }

        reset(sequential)
        cyclesLeft = sequential + cycles

        return cycles
    }

    fun reset(cycles: Int) {
        mBytes = if (cpu.state == CPUState.ARM) 4 else 2
        mSize = 16 / mBytes
        headAddress = cpu.pc + mBytes
        size = 0
        mCycles = cycles
        isActive = true
        bubble = false
    }

    fun stop() {
        bubble = false
        isActive = false
        size = 0
    }
}