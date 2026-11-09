package com.lampda.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

internal enum class ControlOrigin(val label: String) {
    Local("Local value"), Requested("Requested, unconfirmed"), Reported("Received from lamp"),
    Mixed("Different values"), Unavailable("Not synchronized"),
}

internal data class ControlReading<T>(val value: T, val origin: ControlOrigin = ControlOrigin.Local) {
    fun request(value: T) = ControlReading(value, ControlOrigin.Requested)
    fun receive(value: T) = ControlReading(value, ControlOrigin.Reported)
    fun invalidate() = copy(origin = ControlOrigin.Local)
}

internal fun <T> combinedControlOrigin(readings: List<ControlReading<T>>, complete: Boolean): ControlOrigin = when {
    !complete || readings.isEmpty() -> ControlOrigin.Unavailable
    readings.any { it.origin == ControlOrigin.Requested } -> ControlOrigin.Requested
    readings.any { it.origin != ControlOrigin.Reported } -> ControlOrigin.Local
    readings.map { it.value }.distinct().size > 1 -> ControlOrigin.Mixed
    else -> ControlOrigin.Reported
}

internal data class ControlOrigins(
    val brightness: ControlOrigin,
    val ramp: ControlOrigin,
    val mode: ControlOrigin,
    val color: ControlOrigin,
)

@Composable
internal fun ControlOriginLabel(origin: ControlOrigin) {
    AppText(origin.label, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
