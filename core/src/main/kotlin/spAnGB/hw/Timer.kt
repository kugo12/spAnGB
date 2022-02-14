package spAnGB.hw

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.memory.Memory
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.InterruptRequest
import spAnGB.utils.bit
import spAnGB.utils.hex
import spAnGB.utils.uInt

@JvmField
val prescalerLut = longArrayOf(1, 64, 256, 1024)

class Timer(
    @JvmField
    val ir: InterruptRequest,
    @JvmField
    val scheduler: Scheduler,
    @JvmField
    val interrupt: Interrupt,
    @JvmField
    val incrementNextTimer: (() -> Unit)? = null
) : Memory {
    @JvmField
    var counter = 0

    var start = 0L
    var task = -1

    @JvmField
    var control = 0

    @JvmField
    var reload = 0

    inline val isCountUp get() = control bit 2
    inline val nextTick get() = prescalerLut[control and 3]
    inline val isIrqEnabled get() = control bit 6
    inline val isEnabled get() = control bit 7

    @JvmField
    var isRunning = false

    private val overflowTask: SchedulerTask = ::overflow

    fun reschedule(taskIndex: Int) {
        println("reschedule at " +((0x10000 - counter).hex).toString() )
        start = scheduler.counter
        scheduler.schedule((0x10000 - counter).toLong() * nextTick, overflowTask, taskIndex)
    }

    fun schedule() {
        isRunning = true
        start = scheduler.counter + 3
        println("schedule at " +((0x10000 - counter).hex).toString() )

        task = scheduler.schedule((0x10000 - counter).toLong() * nextTick + 3, overflowTask)
    }

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short =
        if (address bit 1) {  // Control
            control.toShort()
        } else {
//            counter += ((scheduler.counter - start) / nextTick).toInt()
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
            if (isRunning) {
                counter += ((scheduler.counter - start) / nextTick).toInt()
                start = scheduler.counter
                if (counter > 0xFFFF) onOverflow()
                scheduler.clear(task)
                isRunning = false
            }
            val wasEnabled = isEnabled
            control = value.toInt()

            if (interrupt == Interrupt.Timer0) {
                control = control and 4.inv()
            }

            if (isEnabled) {
                if (!wasEnabled) {
                    counter = reload
                }

                if (!isCountUp) {
                    if (!isRunning) schedule()
                    return
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