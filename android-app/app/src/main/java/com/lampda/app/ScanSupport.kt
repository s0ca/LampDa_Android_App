package com.lampda.app

import java.util.UUID
import kotlin.math.exp

internal const val ScanRankIntervalMillis = 3_000L

internal fun smoothScanRssi(previous: Double, sample: Int, elapsedMillis: Long): Double {
    val weight = 1.0 - exp(-elapsedMillis.coerceAtLeast(0).toDouble() / 2_000.0)
    return previous + weight * (sample - previous)
}

internal data class ScanStrength(val address: String, val rssi: Double)

internal class ScanRanking {
    private var rankedAt: Long? = null
    private var order = emptyList<String>()

    fun update(devices: List<ScanStrength>, nowMillis: Long, force: Boolean = false): List<String> {
        val previousTime = rankedAt
        if (force || previousTime == null || nowMillis - previousTime >= ScanRankIntervalMillis) {
            val priorPositions = order.withIndex().associate { it.value to it.index }
            order = devices.sortedWith(compareByDescending<ScanStrength> { it.rssi }
                .thenBy { priorPositions[it.address] ?: Int.MAX_VALUE }).map { it.address }
            rankedAt = nowMillis
        } else {
            val available = devices.map { it.address }.toSet()
            val retained = order.filter { it in available }
            order = retained + devices.map { it.address }.filter { it !in retained }
        }
        return order
    }
}

internal val ElkServiceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
internal val UartServiceUuid: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

internal data class ScanIdentity(val name: String, val isLamp: Boolean)

internal enum class ScanListFilter(val label: String) {
    Known("Known lamps"), All("All BLE devices");

    fun accepts(isKnownLamp: Boolean) = this == All || isKnownLamp
}

internal fun defaultScanFilter(hasKnownLamps: Boolean) =
    if (hasKnownLamps) ScanListFilter.Known else ScanListFilter.All

internal fun mergeScanIdentity(
    previous: ScanIdentity?, names: List<String>, rawPayload: String, services: List<UUID>,
    knownLamp: Boolean = false,
): ScanIdentity {
    val isLamp = knownLamp || previous?.isLamp == true || (names + rawPayload).any { text ->
        text.filter { it.isLetterOrDigit() }.lowercase().contains("lampda")
    } || services.any { it == ElkServiceUuid || it == UartServiceUuid }
    val name = names.firstOrNull { it.isNotBlank() } ?: previous?.name
        ?: if (isLamp) "Lamp-Da candidate" else "Unknown BLE device"
    return ScanIdentity(name, isLamp)
}
