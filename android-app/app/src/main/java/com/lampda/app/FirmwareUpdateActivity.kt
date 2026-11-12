package com.lampda.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider

class FirmwareUpdateActivity : ComponentActivity() {
    private lateinit var model: UsbFirmwareUpdate
    private val permissionAction get() = "$packageName.USB_FIRMWARE_PERMISSION"
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            model.permissionResult()
        }
    }
    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            model.refreshUsb()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[UsbFirmwareUpdate::class.java]
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(permissionReceiver, IntentFilter(permissionAction))
        val devices = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(deviceReceiver, devices, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(deviceReceiver, devices)
        setContent {
            AppThemeHost {
                DisposableEffect(model.busy) {
                    if (model.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                BackHandler(model.busy) { }
                FirmwareUpdateScreen(model, { identify() }, { identify(assisted = true) }, ::finish)
            }
        }
    }

    private fun identify(assisted: Boolean = false) {
        if (model.busy || model.permissionDeviceId != null) return
        try {
            val device = model.runtimeDevice() ?: return
            if (model.hasPermission(device)) {
                if (assisted) model.prepareAssistedUpdate(device) else model.identify(device)
            }
            else {
                model.requestingPermission(device, assisted)
                getSystemService(UsbManager::class.java).requestPermission(device,
                    PendingIntent.getBroadcast(this, 0, Intent(permissionAction).setPackage(packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        } catch (error: Exception) { model.fail(error.message ?: "USB operation failed.") }
    }

    override fun onResume() {
        super.onResume()
        model.refreshUsb()
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        unregisterReceiver(deviceReceiver)
        super.onDestroy()
    }
}

@Composable
private fun usbLabel(english: String, french: String) =
    if (LocalAppLanguage.current == AppLanguage.French) french else english

@Composable
private fun FirmwareUpdateScreen(
    model: UsbFirmwareUpdate,
    identify: () -> Unit,
    startAssisted: () -> Unit,
    close: () -> Unit,
) {
    var confirmDfu by remember { mutableStateOf(false) }
    var confirmCopy by remember { mutableStateOf(false) }
    var chooseDownload by remember { mutableStateOf(false) }
    var downloadType by remember { mutableStateOf<LampType?>(null) }
    var correctVariant by remember(model.firmware) { mutableStateOf(false) }
    var bootType by remember(model.volume) { mutableStateOf<LampType?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.selectFile(uri)
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.selectVolume(uri)
    }
    val assistedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.selectVolume(uri, assisted = true)
    }
    LaunchedEffect(model.assistStage, model.busy) {
        if (model.assistStage == UsbAssistStage.OpenVolumePicker && !model.busy) {
            model.volumePickerOpened()
            assistedPicker.launch(null)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 32.dp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(usbLabel("USB Update", "Mise à jour USB"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = close, enabled = !model.busy) { AppText("Done") }
        }
        Text(usbLabel("Experimental · UF2", "Expérimental · UF2"), color = MaterialTheme.colorScheme.tertiary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(usbLabel("USB detection", "Détection USB"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = model::refreshUsb, enabled = !model.busy) { AppText("Refresh") }
        }
        val detectionText = when {
            !model.hostSupported -> usbLabel("USB host is not available.", "Le mode hôte USB est indisponible.")
            model.detection == UsbDetection.Empty -> usbLabel("Android detects no USB device.", "Android ne détecte aucun périphérique USB.")
            model.detection == UsbDetection.Runtime -> usbLabel("Lamp-Da USB detected", "Lamp-Da détectée en USB")
            model.detection == UsbDetection.Bootloader -> usbLabel("Lamp-Da bootloader detected", "Bootloader Lamp-Da détecté")
            model.detection == UsbDetection.MultipleLamps -> usbLabel("Multiple lamps detected", "Plusieurs lampes détectées")
            else -> usbLabel("Other USB device detected", "Autre périphérique USB détecté")
        }
        Text(detectionText, color = MaterialTheme.colorScheme.tertiary)
        if (model.usbInventory.isNotBlank()) SelectionContainer {
            Text(model.usbInventory, style = MaterialTheme.typography.bodySmall)
        }
        AppText(model.status, color = MaterialTheme.colorScheme.tertiary)
        if (model.assistStage != null) {
            if (model.assistStage == UsbAssistStage.SelectVolume) {
                Button(onClick = { assistedPicker.launch(null) }, enabled = !model.busy) {
                    Text(usbLabel("Select USB bootloader root", "Choisir la racine USB du bootloader"))
                }
            }
            OutlinedButton(onClick = model::cancelAssistedUpdate, enabled = !model.busy) {
                Text(usbLabel("Cancel assisted update", "Annuler la mise à jour assistée"))
            }
        } else if (model.permissionDeviceId == null) {
            Button(onClick = startAssisted, enabled = !model.busy &&
                model.detection == UsbDetection.Runtime && !model.copyAttempted) {
                Text(usbLabel("Assisted firmware update", "Mise à jour assistée"))
            }
            Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !model.busy) {
                Text(usbLabel("Select UF2 file", "Choisir le fichier UF2"))
            }
            OutlinedButton(onClick = {
                downloadType = model.updateLampType
                chooseDownload = true
            }, enabled = !model.busy) {
                Text(usbLabel("Download from GitHub", "Télécharger depuis GitHub"))
            }
            model.firmware?.let { selected ->
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                Text("${selected.metadata.variant} · ${selected.bytes.size / 1024} KiB")
                SelectionContainer { Text("SHA-256\n${selected.metadata.sha256}", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(correctVariant, { correctVariant = it }, enabled = !model.busy)
                    Text(usbLabel("I checked the source, lamp model and firmware variant.",
                        "J’ai vérifié la provenance, le modèle de lampe et la variante du firmware."), Modifier.weight(1f))
                }
            }
            OutlinedButton(onClick = identify, enabled = !model.busy && model.detection == UsbDetection.Runtime) {
                Text(usbLabel("Read lamp type / version over USB", "Lire le type / la version par USB"))
            }
            Button(onClick = { confirmDfu = true }, enabled = !model.busy && correctVariant && model.detection == UsbDetection.Runtime &&
                model.firmware != null && model.lampType == model.firmware?.metadata?.variant && !model.copyAttempted) {
                Text(usbLabel("Enter USB DFU", "Passer en DFU USB"))
            }
            OutlinedButton(onClick = { treePicker.launch(null) }, enabled = !model.busy && correctVariant && model.firmware != null && !model.copyAttempted) {
                Text(usbLabel("Select USB bootloader root", "Choisir la racine USB du bootloader"))
            }
            model.volume?.let { Text(it.name) }
            if (model.volume != null && model.updateLampType == null) {
                Text(usbLabel(
                    "The bootloader cannot report Simple / RGB. Confirm the physical lamp type; this is not automatic detection.",
                    "Le bootloader ne fournit pas le type Simple / RGB. Confirmer le type physique de la lampe ; ce choix n’est pas une détection automatique."),
                    color = MaterialTheme.colorScheme.tertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LampType.entries.forEach { type ->
                        FilterChip(selected = bootType == type, onClick = { bootType = type },
                            enabled = !model.busy, label = { AppText(type.label) })
                    }
                }
                OutlinedButton(onClick = { bootType?.let(model::confirmBootloaderType) }, enabled = !model.busy && correctVariant && bootType != null) {
                    Text(usbLabel("Confirm lamp type", "Confirmer le type de lampe"))
                }
            }
            val installBlocker = usbInstallBlocker(model.busy, model.firmware?.metadata?.variant,
                correctVariant, model.volume != null, model.updateLampType, model.copyAttempted)
            Button(onClick = { confirmCopy = true }, enabled = installBlocker == null) {
                Text(usbLabel("Install firmware", "Installer le firmware"))
            }
            if (installBlocker != null && !model.busy) AppText(installBlocker, style = MaterialTheme.typography.bodySmall)
        }
        if (model.busy && model.copyPhase == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        // Keep device diagnostics verbatim, as in the UART tab.
        if (model.report.isNotBlank()) SelectionContainer { Text(model.report, style = MaterialTheme.typography.bodySmall) }
    }
    if (model.assistStage == UsbAssistStage.ConfirmInstall && !model.busy) {
        model.firmware?.let { selected ->
            AssistedInstallDialog(selected, model.volume?.name.orEmpty(), model::cancelAssistedUpdate) {
                model.copyFirmware(assisted = true)
            }
        }
    }
    model.copyPhase?.let { phase ->
        FirmwareCopyDialog(phase, model.progress, model.status, model::dismissCopyResult)
    }
    if (chooseDownload) {
        AlertDialog(
            onDismissRequest = { chooseDownload = false },
            title = { Text(usbLabel("Official firmware", "Firmware officiel")) },
            text = {
                Column {
                    Text(usbLabel("Lamp model", "Modèle de lampe"))
                    LampType.entries.forEach { type ->
                        FilterChip(selected = downloadType == type, onClick = { downloadType = type },
                            enabled = model.updateLampType == null || model.updateLampType == type,
                            label = { AppText(type.label) })
                    }
                }
            },
            confirmButton = {
                Button(enabled = downloadType != null && !model.busy, onClick = {
                    chooseDownload = false
                    downloadType?.let(model::selectOfficialFirmware)
                }) { Text(usbLabel("Download", "Télécharger")) }
            },
            dismissButton = {
                OutlinedButton(onClick = { chooseDownload = false }) { Text(usbLabel("Cancel", "Annuler")) }
            },
        )
    }
    if (confirmDfu || confirmCopy) {
        val copying = confirmCopy
        AlertDialog(onDismissRequest = { confirmDfu = false; confirmCopy = false }, title = {
            Text(usbLabel("Confirm firmware update", "Confirmer la mise à jour"))
        }, text = {
            Text(if (copying) usbLabel(
                "Install this file on the selected lamp? Keep USB connected. Copy completion does not prove installation: read the version after reboot.",
                "Installer ce fichier sur la lampe sélectionnée ? Garder l’USB branché. Après la copie, lire la version au redémarrage pour vérifier l’installation.")
            else usbLabel(
                "Enter update mode? The lamp will stop running. Only continue with a trusted application UF2 for this lamp. If Android cannot mount its USB volume, do not format it; finish the update on a computer.",
                "Passer en mode mise à jour ? La lampe va s’arrêter. Continuer uniquement avec un UF2 fiable pour cette lampe. Si Android ne monte pas son volume USB, ne pas le formater : terminer sur ordinateur."))
        }, confirmButton = {
            Button(onClick = {
                confirmDfu = false; confirmCopy = false
                if (copying) model.copyFirmware() else model.enterDfu()
            }) { Text(usbLabel("Confirm", "Confirmer")) }
        }, dismissButton = {
            OutlinedButton(onClick = { confirmDfu = false; confirmCopy = false }) { Text(usbLabel("Cancel", "Annuler")) }
        })
    }
}

@Composable
private fun AssistedInstallDialog(firmware: LocalFirmware, volume: String, cancel: () -> Unit, install: () -> Unit) {
    val rgb = firmware.metadata.variant == LampType.Rgb
    var standardRgbConfirmed by remember(firmware, volume) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(usbLabel("Confirm firmware update", "Confirmer la mise à jour")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(firmware.name)
                Text(volume, style = MaterialTheme.typography.bodySmall)
                if (rgb) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(standardRgbConfirmed, { standardRgbConfirmed = it })
                    Text(usbLabel("This is a standard RGB lamp, not RGB HD.",
                        "C’est une lampe RGB standard, pas une RGB HD."), Modifier.weight(1f))
                }
                Text(usbLabel("Keep USB connected. Read the lamp version after reboot to verify the update.",
                    "Garde l’USB branché. Après le redémarrage, lis la version de la lampe pour vérifier la mise à jour."))
            }
        },
        confirmButton = {
            Button(onClick = install, enabled = !rgb || standardRgbConfirmed) {
                Text(usbLabel("Install firmware", "Installer le firmware"))
            }
        },
        dismissButton = { OutlinedButton(onClick = cancel) { Text(usbLabel("Cancel", "Annuler")) } },
    )
}

@Composable
private fun FirmwareCopyDialog(phase: UsbCopyPhase, progress: Float, status: String, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (phase.finished) dismiss() },
        properties = DialogProperties(dismissOnBackPress = phase.finished, dismissOnClickOutside = false),
        title = {
            Text(when (phase) {
                UsbCopyPhase.Complete -> usbLabel("Copy complete", "Copie terminée")
                UsbCopyPhase.Uncertain -> usbLabel("Update not verified", "Mise à jour non vérifiée")
                else -> usbLabel("Firmware update", "Mise à jour du firmware")
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (phase) {
                    UsbCopyPhase.Preparing -> Text(usbLabel("Preparing the USB volume…", "Préparation du volume USB…"))
                    UsbCopyPhase.Copying -> Text(usbLabel("Copying UF2", "Copie du fichier UF2") + " · ${(progress * 100).toInt()} %")
                    UsbCopyPhase.Finalizing -> Text(usbLabel("Finalizing the copy…", "Finalisation de la copie…"))
                    else -> AppText(status)
                }
                if (!phase.finished) {
                    if (phase == UsbCopyPhase.Copying) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(usbLabel("Keep USB connected.", "Ne débranche pas le câble USB."))
                }
            }
        },
        confirmButton = {
            if (phase.finished) Button(onClick = dismiss) { AppText("Done") }
        },
    )
}
