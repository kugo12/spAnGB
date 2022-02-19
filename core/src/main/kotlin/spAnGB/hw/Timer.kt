package spAnGB.hw

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.memory.Memory
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.InterruptRequest
import spAnGB.utils.*

val prescalerLut = longArrayOf(1, 64, 256, 1024)

class Timer(  // FIXME
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


    inline val currentCounter: Int get() =
        if (isRunning)
            (counter + (scheduler.counter - start) / nextTick).toInt()
        else
            counter


    private val overflowTask: SchedulerTask = ::overflow

    fun reschedule(taskIndex: Int) {
        start = scheduler.counter
        scheduler.schedule((0x10000 - counter.toShort().uLong) * nextTick, overflowTask, taskIndex)
    }

    fun schedule() {
        start = scheduler.counter + 2
        isRunning = true
        task = scheduler.schedule((0x10000 - counter.toShort().uLong) * nextTick, overflowTask)
    }

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short =
        if (address bit 1) {  // Control
            control.toShort()
        } else {
            currentCounter.toShort().also {
                if (interrupt == Interrupt.Timer3) println(currentCounter.hex)
            }
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

            if (interrupt == Interrupt.Timer0) {
                control = control and 4.inv()
            }

            if (isRunning && (isCountUp || !isEnabled)) {
                isRunning = false
                scheduler.schedule(2, {
                    scheduler.clear(it)
                    counter += ((scheduler.counter - start) / wasTickingAt).toInt()
                    if (counter > 0xFFFF) onOverflow()
                }, task)
            }

            if (isEnabled) {
                if (!wasEnabled) counter = reload
                if (!isCountUp && !isRunning) schedule()
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
        counter = currentCounter
        onOverflow()
        reschedule(taskIndex)
    }

    fun onOverflow() {
        if (isIrqEnabled) ir[interrupt] = true
        while (counter >= 0x10000) {
            counter -= 0x10000 - reload
        }  // TODO

        incrementNextTimer?.invoke()
    }
}