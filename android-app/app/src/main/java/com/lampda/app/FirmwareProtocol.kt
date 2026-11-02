package com.lampda.app

import java.util.Calendar

internal fun clockCommand(action: String, calendar: Calendar): String {
    require(action in listOf("time", "sunset", "sunrise"))
    return "set $action ${calendar.get(Calendar.HOUR_OF_DAY)} ${calendar.get(Calendar.MINUTE)} " +
        "${calendar.get(Calendar.SECOND)} ${calendar.get(Calendar.DAY_OF_WEEK) - 1}"
}

enum class SunTimerKind(val label: String, val command: String) {
    Sunset("Sunset", "sunset"), Sunrise("Sunrise", "sunrise");
}

internal enum class SunTimerReply { Accepted, Refused }

internal fun sunTimerReply(line: String, kind: SunTimerKind): SunTimerReply? {
    val text = firmwareLine(line).lowercase()
    return when {
        kind == SunTimerKind.Sunset && (text.startsWith("sunset timer set to ") ||
            text.startsWith("sunset timer updated to ")) -> SunTimerReply.Accepted
        text.startsWith("sun timer set to ") || text.startsWith("sun timer updated to ") -> SunTimerReply.Accepted
        text.startsWith("sun timer cannot be used") || text.startsWith("refusing ${kind.command} command") ||
            text.startsWith("shutdown time is less than current time") ||
            text.startsWith("unknown command:") || text.startsWith("invalid call:") -> SunTimerReply.Refused
        else -> null
    }
}

internal fun nextSunTimerTime(now: Calendar, hour: Int, minute: Int): Calendar {
    require(hour in 0..23 && minute in 0..59)
    return (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
}

// Commands and mode addresses are pinned to official tag v1.9 (ee1d79f).
internal object Firmware19 {
    // Keep startup traffic bounded: capability/help and diagnostic dumps are manual only.
    val connectionQueries: List<String> = listOf("t")
    fun advertisesSunrise(line: String) = firmwareLine(line).startsWith("set sunrise ")
    fun command(line: String): String = when (line) {
        "bat", "ble", "states", "time", "cinfo", "alerts", "i2c", "stats" -> "sys $line"
        "PD" -> "sys pd"
        else -> line
    }
    fun power(on: Boolean) = "set on ${if (on) 1 else 0}"
    fun brightness(value: Int) = "set brgt ${value.coerceIn(0, 1024)}"
    fun ramp(value: Int) = "set ramp ${value.coerceIn(0, 255)}"
    fun color(color: RgbColor) = "set color ${color.red} ${color.green} ${color.blue}"
    fun mode(mode: LampMode): String {
        require(mode != LampMode.RgbCustom)
        return "set mode ${mode.groupId} ${mode.modeId}"
    }
    fun favorite(index: Int, save: Boolean): String {
        require(index in 0..7)
        return "set ${if (save) "new_favorite" else "favorite"} $index"
    }
    fun rename(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9-]{1,30}")))
        return "set ble_name $name"
    }
}

internal fun firmwareLine(line: String): String =
    line.trim().replaceFirst(Regex("""^\d+>\s*"""), "")
