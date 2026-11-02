package com.lampda.app

internal class LatestControlSender<T : Any>(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancelScheduled: (Runnable) -> Unit,
    private val send: (T) -> Unit,
) {
    private var latest: T? = null
    private var scheduled = false
    private val task = Runnable {
        scheduled = false
        latest?.let(send)
    }

    fun update(value: T, commit: Boolean = false) {
        latest = value
        if (commit) this.commit()
        else if (!scheduled) {
            scheduled = true
            schedule(task, 150L)
        }
    }

    fun commit() {
        cancelScheduled(task)
        scheduled = false
        latest?.let(send)
    }

    fun cancel() {
        cancelScheduled(task)
        scheduled = false
        latest = null
    }
}
