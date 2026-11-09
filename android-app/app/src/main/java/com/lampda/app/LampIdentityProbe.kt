package com.lampda.app

internal class LampIdentityProbe(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancelScheduled: (Runnable) -> Unit,
    private val request: () -> Unit,
) {
    private var attempts = 0
    private var active = false
    private val task = Runnable { attempt() }

    fun start() {
        stop()
        active = true
        attempts = 0
        attempt()
    }

    private fun attempt() {
        if (!active) return
        attempts++
        request()
        if (active && attempts < 3) schedule(task, 2_000L)
    }

    fun stop() {
        active = false
        cancelScheduled(task)
    }
}
