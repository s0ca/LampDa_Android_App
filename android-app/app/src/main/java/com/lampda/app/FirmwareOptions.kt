package com.lampda.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FirmwareOptions(type: LampType, connected: Boolean, onCommand: (String) -> Unit) {
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var slotMenu by rememberSaveable { mutableStateOf(false) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val validName = name.matches(Regex("[A-Za-z0-9-]{1,30}"))

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
                    val recallLabel = uiString("Recall") + " - " + uiString("Slot ${slot + 1}")
                    val saveLabel = uiString("Save") + " - " + uiString("Slot ${slot + 1}")
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(recallLabel) } }, state = rememberTooltipState(),
                    ) {
                        OutlinedIconButton(enabled = connected, onClick = {
                            onCommand(Firmware19.favorite(slot, save = false))
                            message = "Favorite ${slot + 1} recall requested"
                        }, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = recallLabel)
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(saveLabel) } }, state = rememberTooltipState(),
                    ) {
                        OutlinedIconButton(enabled = connected, onClick = { confirmSave = true },
                            modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Check, contentDescription = saveLabel)
                        }
                    }
                }
            }
            TextField(value = name, onValueChange = { name = it }, singleLine = true, enabled = connected,
                label = { AppText("Bluetooth name") }, modifier = Modifier.fillMaxWidth(),
                isError = name.isNotEmpty() && !validName,
                supportingText = { AppText("1-30 letters, digits or hyphens. Applies after restarting the lamp.") })
            Button(enabled = connected && validName, onClick = {
                onCommand(Firmware19.rename(name))
            }) {
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
