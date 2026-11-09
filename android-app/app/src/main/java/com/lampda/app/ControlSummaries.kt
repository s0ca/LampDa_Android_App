package com.lampda.app

import java.util.Locale

internal fun timerRemainingText(seconds: Int): String {
    val value = seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
}

internal fun sunTimerSummary(
    activeKind: SunTimerKind?, remainingSeconds: Int, selectedKind: SunTimerKind,
    minutes: Int, endTime: Pair<Int, Int>? = null,
): String {
    if (activeKind != null && remainingSeconds > 0)
        return "${activeKind.label} ${timerRemainingText(remainingSeconds)}"
    val schedule = endTime?.let { (hour, minute) ->
        "Ends at " + String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    } ?: "${minutes.coerceIn(1, 1440)} min"
    return "${selectedKind.label} | $schedule"
}
