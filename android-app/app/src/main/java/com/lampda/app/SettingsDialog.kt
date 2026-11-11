package com.lampda.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
internal fun SettingsDialog(
    lampType: LampType,
    connected: Boolean,
    onLampType: (LampType) -> Unit,
    onCommand: (String) -> Unit,
    advancedUartEnabled: Boolean,
    onAdvancedUartChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val theme = LocalAppTheme.current
    val changeTheme = LocalChangeTheme.current
    val language = LocalAppLanguage.current
    val changeLanguage = LocalChangeLanguage.current
    val fullscreen = LocalFullscreen.current
    val changeFullscreen = LocalChangeFullscreen.current
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { AppText("Settings") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TabRow(tab) {
                listOf("App", "Lamp", "About").forEachIndexed { index, title ->
                    Tab(tab == index, onClick = { tab = index }, text = {
                        Text(uiString(title), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    })
                }
            }
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> {
                        Row(Modifier.fillMaxWidth().toggleable(fullscreen, role = Role.Switch,
                            onValueChange = changeFullscreen), verticalAlignment = Alignment.CenterVertically) {
                            AppText("Fullscreen", Modifier.weight(1f))
                            Switch(fullscreen, onCheckedChange = null)
                        }
                        AppText("Language", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { option ->
                                androidx.compose.material3.FilterChip(
                                    selected = language == option,
                                    onClick = { changeLanguage(option) },
                                    label = { AppText(option.label) },
                                )
                            }
                        }
                        AppText("Theme", fontWeight = FontWeight.SemiBold)
                        AppTheme.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().selectable(theme == option, role = Role.RadioButton,
                                onClick = { changeTheme(option) }).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(selected = theme == option, onClick = null)
                                AppText(option.label, Modifier.weight(1f))
                                Box(Modifier.size(22.dp).background(option.colors().primary, CircleShape))
                                Box(Modifier.size(22.dp).background(option.colors().surfaceVariant, CircleShape))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppText("Advanced UART", Modifier.weight(1f))
                            Switch(advancedUartEnabled, onCheckedChange = onAdvancedUartChanged)
                        }
                    }
                    1 -> {
                        LampTypeSelector(lampType, onLampType)
                        FirmwareOptions(lampType, connected, onCommand)
                        Button(onClick = { context.startActivity(Intent(context, FirmwareUpdateActivity::class.java)) }) {
                            Text(if (language == AppLanguage.French) "Mise à jour USB" else "USB firmware update")
                        }
                    }
                    else -> {
                        AppText("Lamp-Da", style = MaterialTheme.typography.titleMedium)
                        AppText("App ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                        ManualAppUpdateCheck()
                        AppText("Developed by s0ca", style = MaterialTheme.typography.bodyMedium)
                        AppText("Firmware 1.9+ / Nordic UART", style = MaterialTheme.typography.bodyMedium)
                        ProjectSourceLink("App source (GitHub)", ProjectLinks.AppSource)
                        ProjectSourceLink("Lamp-Da firmware (GitHub)", ProjectLinks.FirmwareSource)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = onDismiss) { AppText("Done") } })
}

@Composable
private fun ManualAppUpdateCheck() {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AppUpdateResult?>(null) }
    TextButton(enabled = !checking, onClick = {
        checking = true
        result = null
        scope.launch {
            try {
                result = checkForAppUpdate(BuildConfig.VERSION_NAME.substringBefore('-'))
            } finally {
                checking = false
            }
        }
    }) { AppText(if (checking) "Checking for updates..." else "Check for updates") }
    when (val update = result) {
        is AppUpdateResult.Available -> {
            Text("${uiString("App update available")}: ${update.tag}", style = MaterialTheme.typography.bodyMedium)
            ProjectSourceLink("View release on GitHub", "${ProjectLinks.AppSource}/releases/tag/${update.tag}")
        }
        AppUpdateResult.UpToDate -> AppText("No newer stable version", style = MaterialTheme.typography.bodySmall)
        AppUpdateResult.Unavailable -> AppText("Could not check for updates.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        null -> Unit
    }
}

@Composable
private fun ProjectSourceLink(label: String, url: String) {
    val context = LocalContext.current
    val unavailable = uiString("No browser available")
    TextButton(onClick = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
        }
    }) { AppText(label) }
}
