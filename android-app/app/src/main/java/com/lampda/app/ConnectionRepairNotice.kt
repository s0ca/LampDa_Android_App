package com.lampda.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectionRepairNotice(fleet: LampFleetController, session: LampSession) {
    val issue = session.controller.connectionIssue ?: return
    var showRepair by remember(session.device.address) { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(issue)
        TextButton(onClick = { showRepair = true }) { AppText("Repair connection") }
    }
    if (showRepair) AlertDialog(
        onDismissRequest = { showRepair = false },
        title = { AppText("Repair connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.displayName)
                Text(session.device.address)
                AppText("Try reconnecting first. If it still fails after a firmware update, forget only this lamp in Android Bluetooth settings, then pair it again from Lamp-Da.")
            }
        },
        confirmButton = {
            Button(onClick = {
                showRepair = false
                fleet.reconnect(session.device.address)
            }) { AppText("Reconnect") }
        },
        dismissButton = {
            TextButton(onClick = {
                showRepair = false
                fleet.disconnect(session.device.address)
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }) { AppText("Bluetooth settings") }
        },
    )
}
