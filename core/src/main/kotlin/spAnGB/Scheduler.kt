package spAnGB

typealias SchedulerTask = Pair<Long, () -> Unit>

class Scheduler {
    private var counter = 0L

    val tasks: MutableList<SchedulerTask> = ArrayDeque(64)

    fun schedule(after: Long, task: () -> Unit) {
        val after = counter+after

        tasks.forEachIndexed { index, t ->
            if (after <= t.first) {
                tasks.add(index, Pair(after, task))
                return
            }
        }

        tasks.add(Pair(after, task))
    }


    fun tick() {
        var head = tasks.firstOrNull()
        while (head != null && head.first <= counter) {
            tasks.removeFirst()
            head.second()
            head = tasks.firstOrNull()
        }

        counter += 1  // TODO
    }
}