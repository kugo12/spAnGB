package spAnGB

typealias SchedulerTask = (Int) -> Unit

const val taskQueueSize = 16

class Scheduler {
    var counter = 0L
    private var queueSize = 0

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
        val after = counter + after

        var index = 0
        while (true)
            if (taskCounters[index] == -1L) { // this throws if queue is too small
                taskCounters[index] = after
                tasks[index] = task
                if (index > queueSize) queueSize = index

                return index
            } else ++index
    }


    fun tick() {
        for (index in 0 .. queueSize) {
            val taskCounter = taskCounters[index]

            if (taskCounter == -1L) break
            if (taskCounter <= counter) tasks[index]!!(index)
        }

        counter += 1  // TODO
    }
}