package spAnGB

typealias SchedulerTask = (Int) -> Unit

const val taskQueueSize = 16

class Scheduler {
    var counter = 0L

    val taskCounters: LongArray = LongArray(taskQueueSize) { -1L }
    val tasks: Array<SchedulerTask?> = arrayOfNulls(taskQueueSize)

    fun schedule(after: Long, task: SchedulerTask?, index: Int) {
        taskCounters[index] = counter + after
        tasks[index] = task
    }

    fun clear(index: Int) {
        tasks[index] = null
        taskCounters[index] = -1
    }

    fun schedule(after: Long, task: SchedulerTask?): Int {
        val c = counter + after

        var index = 0
        while (true)
            if (taskCounters[index] == -1L) { // this throws if queue is too small
                taskCounters[index] = c
                tasks[index] = task

                return index
            } else ++index
    }


    fun tick() {
        for (index in 0 until taskQueueSize) {
            val taskCounter = taskCounters[index]

            if (taskCounter == -1L) continue
            if (taskCounter <= counter) tasks[index]!!(index)
        }

        counter += 1
    }

    inline fun task(crossinline func: () -> Unit): SchedulerTask = {
        clear(it)
        func()
    }
}