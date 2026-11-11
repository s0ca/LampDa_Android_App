package com.lampda.app

internal class LampStateRefresh {
    private var statusReceived = false
    private val timers = mutableSetOf<SunTimerKind>()

    // A status reply starts exactly one timer query per refresh.
    fun receiveStatus(): Boolean {
        if (statusReceived) return false
        statusReceived = true
        return true
    }

    fun receiveTimer(kind: SunTimerKind): Boolean {
        if (!statusReceived) return false
        timers.add(kind)
        return timers.size == SunTimerKind.entries.size
    }
}
