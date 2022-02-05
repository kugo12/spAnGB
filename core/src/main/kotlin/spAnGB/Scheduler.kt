package spAnGB

typealias SchedulerTask = (Int) -> Unit

const val taskQueueSize = 16

class Scheduler {
    private var counter = 0L

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

    fun schedule(after: Long, task: SchedulerTask?) {
        val after = counter + after

        var index = 0
        while (true)
            if (taskCounters[index] == -1L) { // this throws if queue is too small
                taskCounters[index] = after
                tasks[index] = task

                return
            } else ++index
    }


    fun tick() {
        var index = 0
        while (index < taskQueueSize) {
            val taskCounter = taskCounters[index]
            when {
                taskCounter == -1L -> break
                taskCounter <= counter -> tasks[index]!!(index)
            }
            ++index
        }

        counter += 1  // TODO
    }
}