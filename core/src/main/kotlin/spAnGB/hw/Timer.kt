package spAnGB.hw

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.memory.Memory
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.InterruptRequest
import spAnGB.utils.bit
import spAnGB.utils.uInt
import spAnGB.utils.uLong

@JvmField
val prescalerLut = longArrayOf(1, 64, 256, 1024)

class Timer(
    val ir: InterruptRequest,
    val scheduler: Scheduler,
    val interrupt: Interrupt,
    val incrementNextTimer: (() -> Unit)? = null
) : Memory {
    var counter = 0

    var start = 0L
    var task = -1
    var isRunning = false

    var control = 0
    var reload = 0

    inline val isCountUp get() = control bit 2
    inline val nextTick get() = prescalerLut[control and 3]
    inline val isIrqEnabled get() = control bit 6
    inline val isEnabled get() = control bit 7


    private val overflowTask: SchedulerTask = ::overflow

    fun reschedule(taskIndex: Int) {
        start = scheduler.counter
        isRunning = true
        scheduler.schedule((0x10000 - counter.toShort().uLong) * nextTick, overflowTask, taskIndex)
    }

    fun schedule(wasEnabled: Boolean) {
        val x = if (!wasEnabled) 3 else 0
        start = scheduler.counter + x
        isRunning = true
        task = scheduler.schedule((0x10000 - counter.toShort().uLong) * nextTick + x, overflowTask)
    }

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short =
        if (address bit 1) {  // Control
            control.toShort()
        } else {
            if (isRunning)
                (counter + (scheduler.counter - start) / nextTick).toShort()
            else
                counter.toShort()
        }

    override fun read32(address: Int): Int {
        TODO()
    }

    override fun write8(address: Int, value: Byte) {
        TODO("Not yet implemented")
    }

    override fun write16(address: Int, value: Short) {
        if (address bit 1) {  // Control
            val wasEnabled = isEnabled
            val wasTickingAt = nextTick
            control = value.toInt()

            if (isRunning && (isCountUp || !isEnabled)) {
                scheduler.schedule(3, {
                    scheduler.clear(it)
                    counter += ((scheduler.counter - start) / wasTickingAt).toInt()
                    if (counter > 0xFFFF) onOverflow()
                    isRunning = false
                }, task)
            }

            if (interrupt == Interrupt.Timer0) {
                control = control and 4.inv()
            }

            if (isEnabled) {
                if (!wasEnabled) {
                    counter = reload
                }

                if (!isCountUp && !isRunning) {
                    schedule(wasEnabled)
                }
            }
        } else {
            reload = value.uInt
        }
    }

    override fun write32(address: Int, value: Int) {
        TODO()
    }

    fun countUp() {
        if (isEnabled && isCountUp) {
            counter += 1
            if (counter > 0xFFFF) onOverflow()
        }
    }


    private inline fun overflow(taskIndex: Int) {
        onOverflow()
        reschedule(taskIndex)
    }

    fun onOverflow() {
        if (isIrqEnabled) {
            ir[interrupt] = true
        }
        incrementNextTimer?.invoke()
        counter = reload
    }
}