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
            text.startsWith("timer end time is less than current time") ||
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

// Commands and feedback match official develop 7bb2044 (v1.9 command family).
internal object Firmware19 {
    // Keep startup traffic bounded: capability/help and diagnostic dumps are manual only.
    val connectionQueries: List<String> = listOf("t", "sys ble_name")
    const val StatusQuery = "sys status"
    const val SunTimerStop = "set sun_cancel"
    fun advertisesSunrise(line: String) = firmwareLine(line).startsWith("set sunrise ")
    fun command(line: String): String = when (line) {
        "bat", "time", "alerts" -> "sys $line"
        "ble", "states", "cinfo", "i2c", "stats" -> "debug $line"
        "PD" -> "debug pd"
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

internal sealed interface FirmwareFeedback {
    data object OutputOff : FirmwareFeedback
    data class OutputStatus(val group: Int, val mode: Int, val ramp: Int, val brightness: Int) : FirmwareFeedback
    data class Brightness(val value: Int) : FirmwareFeedback
    data class Ramp(val value: Int) : FirmwareFeedback
    data class Mode(val group: Int, val mode: Int) : FirmwareFeedback
    data class Indicator(val value: Int) : FirmwareFeedback
    data class BleName(val name: String) : FirmwareFeedback
}

private val OutputStatusLine = Regex("""[$]stat:g(\d{1,3}):m(\d{1,3}):r(\d{1,3}):[FX] brgt:(\d{1,4})""")

internal data class SunTimerStatus(val kind: SunTimerKind, val remainingSeconds: Long?)

internal fun sunTimerStopConfirmed(line: String): Boolean = when (firmwareLine(line).lowercase()) {
    "sunset timer canceled", "sunrise timer canceled", "no sun timer is running" -> true
    else -> false
}

private val SunTimerStatusLine = Regex("""(sunset|sunrise): (?:disabled|enabled, ends in (\d{1,8})m ([0-5]?\d)s)""")

internal fun sunTimerStatus(line: String): SunTimerStatus? {
    val match = SunTimerStatusLine.matchEntire(firmwareLine(line)) ?: return null
    val (timer, minutes, seconds) = match.destructured
    val kind = if (timer == "sunset") SunTimerKind.Sunset else SunTimerKind.Sunrise
    return SunTimerStatus(kind, minutes.toLongOrNull()?.let { it * 60 + seconds.toLong() })
}

internal fun firmwareFeedback(line: String): FirmwareFeedback? {
    val content = firmwareLine(line)
    if (content == "\$stat:OFF") return FirmwareFeedback.OutputOff
    OutputStatusLine.matchEntire(content)?.destructured?.let { (g, m, r, b) ->
        val group = g.toInt()
        val mode = m.toInt()
        val ramp = r.toInt()
        val brightness = b.toInt()
        return if (group <= 255 && mode <= 255 && ramp <= 255 && brightness <= 1024)
            FirmwareFeedback.OutputStatus(group, mode, ramp, brightness) else null
    }
    if (content.startsWith("ble_name: ")) {
        return content.removePrefix("ble_name: ").trim().takeIf { it.isNotEmpty() }
            ?.let(FirmwareFeedback::BleName)
    }
    val fields = content.split(':')
    val value = fields.getOrNull(1)?.toIntOrNull() ?: return null
    return when {
        fields.size == 2 && fields[0] == "\$brgt" && value in 0..1024 -> FirmwareFeedback.Brightness(value)
        fields.size == 2 && fields[0] == "\$ramp" && value in 0..255 -> FirmwareFeedback.Ramp(value)
        fields.size == 2 && fields[0] == "\$ind" && value in 0..2 -> FirmwareFeedback.Indicator(value)
        fields.size == 3 && fields[0] == "\$mode" && value in 0..255 ->
            fields[2].toIntOrNull()?.takeIf { it in 0..255 }?.let { FirmwareFeedback.Mode(value, it) }
        else -> null
    }
}

internal fun uartPayloadSize(mtu: Int): Int = (mtu.coerceIn(23, 517) - 3).coerceAtMost(244)
