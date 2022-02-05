package spAnGB.hw

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.memory.Memory
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.InterruptRequest
import spAnGB.utils.bit
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

    private val tickTask: SchedulerTask = ::tick

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short =
        if (address bit 1) {  // Control
            control.toShort()
        } else {
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
            control = value.toInt()

            if (interrupt == Interrupt.Timer0) {
                control = control and 4.inv()
            }

            if (isEnabled) {
                if (!wasEnabled) counter = reload

                if (!isRunning && !isCountUp) {
                    isRunning = true
                    scheduler.schedule(nextTick, tickTask)
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
        if (isEnabled && isCountUp) increment()
    }

    fun increment() {
        counter = if (counter and 0xFFFF == 0xFFFF) {
            if (isIrqEnabled) { ir[interrupt] = true }
            incrementNextTimer?.invoke()

            reload
        } else {
            counter + 1
        }
    }

    private inline fun tick(taskIndex: Int) {
        if (isEnabled && !isCountUp) {
            increment()
            scheduler.schedule(nextTick, tickTask, taskIndex)
        } else {
            scheduler.clear(taskIndex)
            isRunning = false
        }
    }
}