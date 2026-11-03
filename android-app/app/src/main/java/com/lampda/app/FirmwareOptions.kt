package com.lampda.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun FirmwareOptions(type: LampType, connected: Boolean, onCommand: (String) -> Unit) {
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var slotMenu by rememberSaveable { mutableStateOf(false) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (type == LampType.Rgb) {
                AppText("Lamp favorites", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { slotMenu = true }) {
                        AppText("Slot ${slot + 1}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(slotMenu, onDismissRequest = { slotMenu = false }) {
                        (0..7).forEach { index ->
                            DropdownMenuItem(text = { AppText("Slot ${index + 1}") }, onClick = {
                                slot = index
                                slotMenu = false
                            })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = connected, onClick = {
                        onCommand(Firmware19.favorite(slot, save = false))
                        message = "Favorite ${slot + 1} recall requested"
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Star, contentDescription = null)
                        AppText("Recall", Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(enabled = connected, onClick = { confirmSave = true },
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        AppText("Save", Modifier.padding(start = 6.dp))
                    }
                }
            }
            // Re-enable after the firmware retains fragmented BLE UART commands.
            TextField(value = "", onValueChange = {}, singleLine = true, enabled = false,
                label = { AppText("Bluetooth name") }, modifier = Modifier.fillMaxWidth(),
                supportingText = { AppText("Renaming is disabled pending a firmware fix.") })
            Button(enabled = false, onClick = {}) {
                Icon(Icons.Default.Edit, contentDescription = null)
                AppText("Rename", Modifier.padding(start = 6.dp))
            }
            message?.let { AppText(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary) }
    }
    if (confirmSave) AlertDialog(
        onDismissRequest = { confirmSave = false },
        title = { AppText("Save favorite ${slot + 1}?") },
        text = { AppText("Replace this slot with the lamp's current mode and effect settings?") },
        confirmButton = {
            Button(enabled = connected, onClick = {
                onCommand(Firmware19.favorite(slot, save = true))
                message = "Favorite ${slot + 1} save requested"
                confirmSave = false
            }) { AppText("Save") }
        },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { AppText("Cancel") } },
    )
}
