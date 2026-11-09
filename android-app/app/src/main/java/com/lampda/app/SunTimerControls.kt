package com.lampda.app

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.roundToInt

private enum class TimerSchedule { Duration, Clock }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SunTimerControls(
    expanded: Boolean,
    onToggle: () -> Unit,
    minutes: Int,
    remainingSeconds: Int,
    activeKind: SunTimerKind?,
    supportsSunrise: Boolean,
    error: String?,
    onMinutes: (Int) -> Unit,
    onSyncTime: () -> Unit,
    onStart: (SunTimerKind, Calendar?) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(SunTimerKind.Sunset) }
    var schedule by rememberSaveable { mutableStateOf(TimerSchedule.Duration) }
    var input by rememberSaveable { mutableStateOf(minutes.toString()) }
    var hour by rememberSaveable { mutableStateOf(7) }
    var minute by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(minutes) {
        if (input.toIntOrNull() != minutes) input = minutes.toString()
    }
    LaunchedEffect(supportsSunrise) {
        if (!supportsSunrise) kind = SunTimerKind.Sunset
    }
    val summary = sunTimerSummary(activeKind, remainingSeconds, kind, minutes,
        if (schedule == TimerSchedule.Clock) hour to minute else null)

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onToggle),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                AppText("Sunrise / Sunset", fontWeight = FontWeight.SemiBold)
                AppText(summary,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            AppText(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
        }
        error?.let { AppText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SunTimerKind.entries.forEach { option ->
                    FilterChip(selected = kind == option, onClick = { kind = option },
                        enabled = option != SunTimerKind.Sunrise || supportsSunrise,
                        label = { AppText(if (option == SunTimerKind.Sunrise && !supportsSunrise)
                            "Sunrise (unavailable)" else option.label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(schedule == TimerSchedule.Duration, onClick = { schedule = TimerSchedule.Duration },
                    label = { AppText("Duration") })
                FilterChip(schedule == TimerSchedule.Clock, onClick = { schedule = TimerSchedule.Clock },
                    label = { AppText("End time") })
            }
            if (schedule == TimerSchedule.Duration) {
                Slider(minutes.coerceIn(1, 120).toFloat(), onValueChange = { onMinutes(it.roundToInt()) },
                    valueRange = 1f..120f, steps = 118)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15, 30, 45, 60).forEach { value ->
                        FilterChip(minutes == value, onClick = { onMinutes(value) }, label = { AppText("${value}m") })
                    }
                }
                TextField(value = input, onValueChange = { value ->
                    input = value.filter { it.isDigit() }.take(4)
                    input.toIntOrNull()?.takeIf { it in 1..1440 }?.let(onMinutes)
                }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { AppText("Minutes until end") }, isError = input.toIntOrNull() !in 1..1440)
            } else {
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }, modifier = Modifier.fillMaxWidth()) { AppText("Ends at %02d:%02d".format(hour, minute)) }
            }
            if (kind == SunTimerKind.Sunrise) {
                AppText("Sunrise requires the lamp to be off.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSyncTime, modifier = Modifier.weight(1f)) { AppText("Sync time") }
                Button(onClick = {
                    onStart(kind, if (schedule == TimerSchedule.Clock)
                        nextSunTimerTime(Calendar.getInstance(), hour, minute) else null)
                }, enabled = schedule == TimerSchedule.Clock || input.toIntOrNull() in 1..1440,
                    modifier = Modifier.weight(1f)) { AppText("Start ${kind.label}") }
            }
        }
    }
}
