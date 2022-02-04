package spAnGB

typealias SchedulerTask = Pair<Long, () -> Unit>

class Scheduler {
    private var counter = 0L

    val tasks: MutableList<SchedulerTask> = ArrayDeque(64)

    fun schedule(after: Long, task: () -> Unit) {
        tasks.add(Pair(counter + after, task))
    }

    fun tick() {
        tasks.listIterator().apply {
            while (hasNext()) {
                val item = next()
                if (item.first <= counter) {
                    item.second()
                    remove()
                }
            }
        }

        counter += 1  // TODO
    }
}