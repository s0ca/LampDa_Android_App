package com.lampda.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class LampSession(val device: LampDevice, val controller: LampBleController)
internal val LampSession.displayName: String get() = controller.connectedLampName ?: device.name

internal class LampFleetController(private val context: Context) {
    val scanner = LampBleController(context)
    val sessions = mutableStateListOf<LampSession>()
    var activeAddress by mutableStateOf<String?>(null)
        private set
    var selectedAddresses by mutableStateOf<Set<String>>(emptySet())
        private set
    val groupMode: Boolean get() = selectedAddresses.size > 1
    var error by mutableStateOf<String?>(null)
        private set

    val active: LampBleController get() = sessions.firstOrNull { it.device.address == activeAddress }?.controller ?: scanner
    private val targetStates: List<LampTargetState> get() = sessions.map {
        LampTargetState(it.device.address, it.controller.lampType, it.controller.isConnected,
            it.controller.reportedType == it.controller.lampType)
    }
    val targets: List<LampBleController> get() {
        val addresses = controlTargetAddresses(selectedAddresses, targetStates)
        return addresses.mapNotNull { address -> sessions.firstOrNull { it.device.address == address }?.controller }
    }
    val origins: ControlOrigins get() {
        val ready = targets
        val complete = ready.size == selectedAddresses.size
        return ControlOrigins(
            combinedControlOrigin(ready.map { it.brightnessReading }, complete),
            combinedControlOrigin(ready.map { it.rampReading }, complete),
            combinedControlOrigin(ready.map { it.modeReading }, complete),
            combinedControlOrigin(ready.map { it.colorReading }, complete),
        )
    }

    fun connect(device: LampDevice) {
        error = null
        if (sessions.any { it.device.address == device.address }) {
            focus(device.address)
            return
        }
        if (sessions.size >= 4) {
            error = "Four-lamp connection limit reached."
            return
        }
        scanner.stopScan()
        val controller = LampBleController(context)
        sessions.add(LampSession(device, controller))
        focus(device.address)
        controller.connect(device)
    }

    fun focus(address: String) {
        if (sessions.none { it.device.address == address }) return
        cancelPendingControls()
        activeAddress = address
        selectedAddresses = setOf(address)
        error = null
    }

    fun canSelect(address: String): Boolean =
        address in selectionAfterToggle(selectedAddresses, address, true, targetStates)

    fun select(address: String, selected: Boolean) {
        val next = selectionAfterToggle(selectedAddresses, address, selected, targetStates)
        if (next == selectedAddresses) return
        cancelPendingControls()
        selectedAddresses = next
        activeAddress = primarySelection(activeAddress, next)
        error = null
    }

    fun disconnect(address: String) {
        val session = sessions.firstOrNull { it.device.address == address } ?: return
        cancelPendingControls()
        session.controller.close()
        sessions.remove(session)
        selectedAddresses = selectedAddresses - address
        if (activeAddress == address) {
            activeAddress = selectedAddresses.firstOrNull()
        }
        error = null
    }

    fun disconnectActive() { activeAddress?.let(::disconnect) }

    fun refreshPrimary() {
        val next = readyPrimary(activeAddress, selectedAddresses, controlTargetAddresses(selectedAddresses, targetStates).toSet())
        if (next != activeAddress) {
            // Moving display focus does not change the checked recipients.
            activeAddress = next
        }
    }

    fun chooseLampType(type: LampType) {
        cancelPendingControls()
        selectedAddresses = activeAddress?.let(::setOf) ?: emptySet()
        active.chooseLampType(type)
    }

    fun reconnect(address: String) {
        sessions.firstOrNull { it.device.address == address }?.let { it.controller.connect(it.device) }
    }

    fun dispatch(action: (LampBleController) -> Unit) {
        targets.toList().forEach(action)
    }

    private fun cancelPendingControls() { sessions.forEach { it.controller.cancelContinuousControls() } }

    fun close() {
        scanner.close()
        sessions.toList().forEach { it.controller.close() }
        sessions.clear()
        selectedAddresses = emptySet()
        activeAddress = null
    }
}
