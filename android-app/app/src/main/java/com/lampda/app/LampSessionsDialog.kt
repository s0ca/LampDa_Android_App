package com.lampda.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun FleetTargetBar(fleet: LampFleetController, onManage: () -> Unit) {
    val targets = fleet.targets.toSet()
    val selected = fleet.sessions.filter { it.device.address in fleet.selectedAddresses }
    val ready = selected.filter { it.controller in targets }
    val waiting = selected.filter { it.controller !in targets }
    val displayed = fleet.sessions.firstOrNull { it.device.address == fleet.activeAddress }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                AppText("Controlling", style = MaterialTheme.typography.titleSmall)
                if (fleet.selectedAddresses.isNotEmpty()) {
                    Text(uiString("Ready") + ": " + ready.size + "/" + selected.size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onManage) {
                Icon(Icons.Default.List, contentDescription = null)
                AppText("Select lamps", Modifier.padding(start = 6.dp))
            }
        }
        Text(if (ready.isEmpty()) uiString("No lamp ready") else ready.joinToString(" + ") { it.displayName },
            style = MaterialTheme.typography.bodyMedium)
        if (waiting.isNotEmpty()) Text(uiString("Not controlled") + ": " +
            waiting.joinToString(" + ") { it.displayName }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        displayed?.let {
            Text(uiString("Display and UART") + ": " + it.displayName,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (fleet.targets.size != fleet.selectedAddresses.size) {
            val mixed = selected.filter { it.controller.reportedType != null }
                .map { it.controller.reportedType }.distinct().size > 1
            AppText(if (mixed) "Different lamp type" else "Some group lamps are unavailable.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        if (fleet.groupMode && fleet.sessions.any { it.device.address in fleet.selectedAddresses &&
                it.device.address != fleet.activeAddress &&
                (it.controller.colorError != null || it.controller.sunTimerError != null) }) {
            AppText("A group lamp reported an error.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun LampSessionsDialog(fleet: LampFleetController, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val targets = fleet.targets.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("Lamps") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(fleet.sessions.toList(), key = { it.device.address }) { session ->
                    val controller = session.controller
                    val selected = session.device.address in fleet.selectedAddresses
                    val active = session.device.address == fleet.activeAddress
                    val enabled = selected || fleet.canSelect(session.device.address)
                    val selectionLabel = uiString("Select lamps") + ": " + session.displayName
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = selectionLabel },
                                onCheckedChange = { fleet.select(session.device.address, it) })
                            Column(Modifier.weight(1f).clickable(enabled = enabled) {
                                fleet.select(session.device.address, !selected)
                            }) {
                                Text(session.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(session.device.address, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!controller.isConnected) IconButton(onClick = { fleet.reconnect(session.device.address) }) {
                                Icon(Icons.Default.Refresh, contentDescription = uiString("Reconnect"))
                            }
                            IconButton(onClick = { fleet.disconnect(session.device.address) }) {
                                Icon(Icons.Default.Close, contentDescription = uiString("Disconnect"))
                            }
                        }
                        AppText(controller.connectionState + " | " + controller.lampType.label + " | " +
                            (controller.batteryLevel?.let { "Battery " + it + "%" } ?: "Battery --"),
                            style = MaterialTheme.typography.bodySmall)
                        AppText(if (selected) "Selected" else "Not selected",
                            style = MaterialTheme.typography.bodySmall)
                        ConnectionRepairNotice(fleet, session)
                        if (selected) AppText(if (controller in targets) "Receiving controls" else "Not controlled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (active) AppText("Display and UART", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        if (controller.isConnected && controller.reportedType != controller.lampType) {
                            AppText("Unverified lamp type", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { controller.verifyLampType() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                AppText("Verify lamp type", Modifier.padding(start = 6.dp))
                            }
                        }
                        if (!enabled && controller.isConnected) AppText(
                            when {
                                controller.reportedType != controller.lampType -> "Unverified lamp type"
                                controller.lampType != fleet.active.lampType -> "Different lamp type"
                                else -> "Some group lamps are unavailable."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        controller.sunTimerError?.let {
                            AppText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        controller.colorError?.let {
                            AppText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { AppText("Done") } },
        dismissButton = { TextButton(enabled = fleet.sessions.size < 4, onClick = onAdd) { AppText("Add lamp") } },
    )
}
