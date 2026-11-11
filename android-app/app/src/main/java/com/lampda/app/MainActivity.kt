package com.lampda.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.UUID
import kotlin.math.roundToInt

private val UartRxCharacteristicUuid: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
private val UartTxCharacteristicUuid: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
private val BatteryServiceUuid: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BatteryLevelCharacteristicUuid: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private val ClientCharacteristicConfigUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private const val ReconnectDelayMs = 1_500L
private const val MaxReconnectAttempts = 12
private val ReconnectToken = Any()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppThemeHost {
                LampDaApp()
                AppUpdateNotice()
            }
        }
    }

}

@Composable
private fun LampDaApp() {
    val context = LocalContext.current
    val fleet = remember { LampFleetController(context.applicationContext) }
    val controller = fleet.active
    var hasPermissions by remember { mutableStateOf(context.hasBlePermissions()) }
    var sunsetRemainingSeconds by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = context.hasBlePermissions()
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredBlePermissions())
        }
    }

    DisposableEffect(Unit) {
        onDispose { fleet.close() }
    }

    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermissions = context.hasBlePermissions()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(fleet.activeAddress, controller.sunsetEndsAtMillis) {
        val endsAt = controller.sunsetEndsAtMillis
        if (endsAt == null) {
            sunsetRemainingSeconds = 0
            return@LaunchedEffect
        }

        while (true) {
            val remaining = ((endsAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            sunsetRemainingSeconds = remaining
            if (remaining == 0) break
            delay(1000)
        }
    }

    LaunchedEffect(fleet.selectedAddresses, fleet.sessions.map {
        Triple(it.device.address, it.controller.isConnected, it.controller.reportedType to it.controller.lampType)
    }) {
        fleet.refreshPrimary()
    }

    LampScreen(
        fleet = fleet,
        hasPermissions = hasPermissions,
        devices = fleet.scanner.devices,
        connectionState = if (fleet.selectedAddresses.isEmpty() && fleet.sessions.isNotEmpty())
            "No lamp selected" else controller.connectionState,
        connected = controller.isConnected,
        isScanning = fleet.scanner.isScanning,
        batteryLevel = controller.batteryLevel,
        connectedLampName = controller.connectedLampName
            ?: fleet.sessions.firstOrNull { it.device.address == fleet.activeAddress }?.device?.name,
        uartLogs = controller.uartLogs,
        diagnosticCommands = controller.diagnosticCommands.toSet(),
        selectedMode = controller.selectedMode,
        lampType = controller.lampType,
        rgbColor = controller.rgbColor,
        colorError = controller.colorError,
        onLampType = { fleet.chooseLampType(it) },
        onColor = { color -> fleet.dispatch { it.sendColor(color) } },
        onColorCommit = { color -> fleet.dispatch { it.sendColor(color, commit = true) } },
        brightness = controller.brightness,
        speed = controller.speed,
        sunsetMinutes = controller.sunsetMinutes,
        sunsetRemainingSeconds = sunsetRemainingSeconds,
        timerKind = controller.activeSunTimerKind,
        timerError = controller.sunTimerError,
        supportsSunrise = controller.supportsSunrise,
        onRequestPermissions = { permissionLauncher.launch(requiredBlePermissions()) },
        onToggleScan = { fleet.scanner.toggleScan() },
        onConnect = { fleet.connect(it) },
        onDisconnect = { fleet.disconnectActive() },
        onPower = { enabled -> fleet.dispatch { it.setPower(enabled) } },
        onBrightness = { value -> fleet.dispatch { it.sendBrightness(value) } },
        onBrightnessCommit = { fleet.dispatch { it.commitBrightness() } },
        onSpeed = { value -> fleet.dispatch { it.sendSpeed(value) } },
        onSpeedCommit = { fleet.dispatch { it.commitSpeed() } },
        onMode = { mode -> fleet.dispatch { it.setMode(mode) } },
        onSunsetMinutes = { controller.sunsetMinutes = it },
        onSyncTime = { fleet.dispatch { it.syncTime() } },
        onStartSunset = { kind, target ->
            val minutes = controller.sunsetMinutes
            fleet.dispatch { it.sunsetMinutes = minutes; it.startSunTimer(kind, target) }
        },
        onSendUart = { controller.sendUartLine(it) },
        onClearUart = { controller.clearUartLogs() },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LampScreen(
    fleet: LampFleetController,
    hasPermissions: Boolean,
    devices: List<LampDevice>,
    connectionState: String,
    connected: Boolean,
    isScanning: Boolean,
    batteryLevel: Int?,
    connectedLampName: String?,
    uartLogs: List<String>,
    diagnosticCommands: Set<String>,
    selectedMode: LampMode,
    lampType: LampType,
    rgbColor: RgbColor,
    colorError: String?,
    onLampType: (LampType) -> Unit,
    onColor: (RgbColor) -> Unit,
    onColorCommit: (RgbColor) -> Unit,
    brightness: Int,
    speed: Int,
    sunsetMinutes: Int,
    sunsetRemainingSeconds: Int,
    timerKind: SunTimerKind?,
    timerError: String?,
    supportsSunrise: Boolean,
    onRequestPermissions: () -> Unit,
    onToggleScan: () -> Unit,
    onConnect: (LampDevice) -> Unit,
    onDisconnect: () -> Unit,
    onPower: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onBrightnessCommit: (Int) -> Unit,
    onSpeed: (Int) -> Unit,
    onSpeedCommit: (Int) -> Unit,
    onMode: (LampMode) -> Unit,
    onSunsetMinutes: (Int) -> Unit,
    onSyncTime: () -> Unit,
    onStartSunset: (SunTimerKind, Calendar?) -> Unit,
    onSendUart: (String) -> Unit,
    onClearUart: () -> Unit,
) {
    val settingsContext = LocalContext.current
    val settingsPreferences = remember { settingsContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Control) }
    var advancedUartEnabled by rememberSaveable {
        mutableStateOf(settingsPreferences.getBoolean("advanced_uart", false))
    }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var sessionsVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(fleet.activeAddress) { settingsVisible = false }
    var colorsExpanded by rememberSaveable { mutableStateOf(false) }
    var modesExpanded by rememberSaveable { mutableStateOf(false) }
    var timerExpanded by rememberSaveable { mutableStateOf(false) }
    val tabs = if (advancedUartEnabled) {
        listOf(AppTab.Control, AppTab.Scan, AppTab.Uart)
    } else {
        listOf(AppTab.Control, AppTab.Scan)
    }

    LaunchedEffect(advancedUartEnabled) {
        if (!advancedUartEnabled && selectedTab == AppTab.Uart) {
            selectedTab = AppTab.Control
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText("Lamp-Da", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!connectedLampName.isNullOrBlank()) {
                        Text(
                            text = connectedLampName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    AppText(
                        text = "${connectionState}  |  ${batteryLevel?.let { "Battery $it%" } ?: "Battery --"}",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        onClick = {
                            if (fleet.selectedAddresses.isEmpty() && fleet.sessions.isNotEmpty()) sessionsVisible = true
                            else if (connected) onDisconnect()
                            else if (!hasPermissions) onRequestPermissions()
                            else {
                                selectedTab = AppTab.Scan
                                if (!isScanning) onToggleScan()
                            }
                        },
                    ) {
                        AppText(when {
                            fleet.selectedAddresses.isEmpty() && fleet.sessions.isNotEmpty() -> "Select lamps"
                            connected -> "Disconnect"
                            else -> "Connect"
                        }, style = MaterialTheme.typography.labelMedium)
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { AppText("Settings") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { settingsVisible = true }) {
                            Icon(Icons.Default.Settings, contentDescription = uiString("Settings"),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (!hasPermissions) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5DA))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText("Bluetooth permissions are required.")
                    Button(onClick = onRequestPermissions) {
                        AppText("Allow Bluetooth")
                    }
                }
            }
            return@Column
        }

        TabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { AppText(tab.title) }
                )
            }
        }

        fleet.error?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
        fleet.sessions.firstOrNull { it.device.address == fleet.activeAddress }?.let {
            ConnectionRepairNotice(fleet, it)
        }
        if (selectedTab == AppTab.Control && fleet.sessions.isNotEmpty()) {
            FleetTargetBar(fleet, onManage = { sessionsVisible = true })
        }

        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                AppTab.Control -> if (fleet.selectedAddresses.isEmpty() && fleet.sessions.isNotEmpty()) {
                    AppText("No lamp selected", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else ControlTab(
                    origins = fleet.origins,
                    canStopTimer = fleet.active.canStopSunTimer,
                    stoppingTimer = fleet.active.isStoppingSunTimer,
                    onStopTimer = fleet.active::stopSunTimer,
                    stateReadback = { StateReadbackControls(fleet.active) },
                    modesExpanded = modesExpanded,
                    onModesToggle = { modesExpanded = !modesExpanded },
                    timerExpanded = timerExpanded,
                    onTimerToggle = { timerExpanded = !timerExpanded },
                    brightness = brightness,
                    speed = speed,
                    selectedMode = selectedMode,
                    lampType = lampType,
                    rgbColor = rgbColor,
                    colorError = colorError,
                    colorsExpanded = colorsExpanded,
                    onColorsToggle = { colorsExpanded = !colorsExpanded },
                    onColor = onColor,
                    onColorCommit = onColorCommit,
                    onPower = onPower,
                    onBrightness = onBrightness,
                    onBrightnessCommit = onBrightnessCommit,
                    onSpeed = onSpeed,
                    onSpeedCommit = onSpeedCommit,
                    onMode = onMode,
                    sunsetMinutes = sunsetMinutes,
                    sunsetRemainingSeconds = sunsetRemainingSeconds,
                    timerKind = timerKind,
                    timerError = timerError,
                    supportsSunrise = supportsSunrise,
                    onSunsetMinutes = onSunsetMinutes,
                    onSyncTime = onSyncTime,
                    onStartSunset = onStartSunset,
                )
                AppTab.Scan -> ScanTab(
                    devices = devices,
                    sessionAddresses = fleet.sessions.map { it.device.address }.toSet(),
                    scanState = fleet.scanner.connectionState,
                    isScanning = isScanning,
                    onToggleScan = onToggleScan,
                    onConnect = {
                        onConnect(it)
                        selectedTab = AppTab.Control
                    },
                )
                AppTab.Uart -> UartTab(
                    targetAddress = fleet.activeAddress,
                    logs = uartLogs,
                    connected = connected,
                    shortcuts = uartShortcuts(lampType, supportsSunrise, diagnosticCommands),
                    onSend = onSendUart,
                    onClear = onClearUart,
                )
            }
        }

    }

    if (sessionsVisible) {
        LampSessionsDialog(fleet, onAdd = {
            sessionsVisible = false
            selectedTab = AppTab.Scan
            if (!isScanning) onToggleScan()
        }, onDismiss = { sessionsVisible = false })
    }

    if (settingsVisible) {
        SettingsDialog(
            lampType = lampType,
            connected = connected,
            onLampType = onLampType,
            onCommand = onSendUart,
            advancedUartEnabled = advancedUartEnabled,
            onAdvancedUartChanged = {
                advancedUartEnabled = it
                settingsPreferences.edit().putBoolean("advanced_uart", it).apply()
            },
            onDismiss = { settingsVisible = false },
        )
    }
}

private enum class AppTab(val title: String) {
    Control("Control"),
    Scan("Scan"),
    Uart("UART"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UartTab(
    targetAddress: String?,
    logs: List<String>,
    connected: Boolean,
    shortcuts: List<UartShortcut>,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by remember(targetAddress) { mutableStateOf("") }
    var shortcutsExpanded by remember(targetAddress) { mutableStateOf(false) }
    var shortcutCategory by remember(targetAddress) { mutableStateOf<UartCategory?>(null) }
    var selectedFilterName by rememberSaveable(key = "uart_log_filter_name") { mutableStateOf("All") }
    val selectedFilter = UartLogFilter.fromStored(selectedFilterName)
    val clipboardManager = LocalClipboardManager.current
    val logListState = rememberLazyListState()
    val visibleLogs = logs.filter(selectedFilter::includes)

    LaunchedEffect(visibleLogs.size, visibleLogs.lastOrNull(), selectedFilter) {
        if (visibleLogs.isNotEmpty()) {
            logListState.animateScrollToItem(visibleLogs.lastIndex)
        }
    }

    Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppText("Bluetooth UART", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        OutlinedButton(onClick = {
                            shortcutCategory = null
                            shortcutsExpanded = true
                        }) {
                            AppText("Command")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = shortcutsExpanded,
                            onDismissRequest = { shortcutsExpanded = false },
                            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp).heightIn(max = 360.dp),
                        ) {
                            val category = shortcutCategory
                            if (category == null) {
                                UartCategory.entries.filter { group -> shortcuts.any { it.category == group } }.forEach { group ->
                                    DropdownMenuItem(text = { AppText(group.label) },
                                        onClick = { shortcutCategory = group })
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { AppText("Back") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                                    onClick = { shortcutCategory = null },
                                )
                                shortcuts.filter { it.category == category }.forEach { shortcut ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                AppText(shortcut.label)
                                                Text(shortcut.command, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        enabled = shortcut.template || connected,
                                        onClick = {
                                            shortcutsExpanded = false
                                            if (shortcut.template) input = shortcut.command
                                            else onSend(shortcut.command)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(visibleLogs.joinToString("\n")))
                        },
                        enabled = visibleLogs.isNotEmpty()
                    ) {
                        AppText("Copy")
                    }
                    OutlinedButton(onClick = onClear) {
                        AppText("Clear")
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UartLogFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilterName = filter.name },
                        label = { AppText(filter.label) }
                    )
                }
            }

            LazyColumn(
                state = logListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (visibleLogs.isEmpty()) {
                    item {
                        AppText(
                            if (logs.isEmpty()) "No UART data yet." else "No matching UART data.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(visibleLogs) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { AppText("UART command") }
                )
                Button(
                    enabled = connected && input.isNotBlank(),
                    onClick = {
                        val line = input.trim()
                        if (line.isNotEmpty()) {
                            onSend(line)
                            input = ""
                        }
                    }
                ) {
                    AppText("Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateReadbackControls(lamp: LampBleController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(when (lamp.reportedPower) {
                true -> "Last reported output: On"
                false -> "Last reported output: Off"
                null -> "Power state not reported"
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lamp.isConnected) {
                val confirmed = listOfNotNull(
                    "Output status".takeIf { lamp.supportsOutputStatus },
                    "Sun timers".takeIf { lamp.supportsSunTimerStop },
                )
                AppText(
                    text = when {
                        lamp.isRefreshingState -> "Refreshing lamp state..."
                        lamp.stateRefreshFailed -> "The lamp did not return its full state."
                        confirmed.isEmpty() -> "Firmware features not confirmed"
                        else -> "Confirmed firmware features"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lamp.stateRefreshFailed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (confirmed.isNotEmpty()) {
                    Text(confirmed.map { uiString(it) }.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        if (lamp.isConnected) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { AppText("Refresh lamp state") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = lamp::refreshState, enabled = lamp.canRefreshState) {
                    Icon(Icons.Default.Refresh, contentDescription = uiString("Refresh lamp state"))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlTab(
    origins: ControlOrigins,
    canStopTimer: Boolean,
    stoppingTimer: Boolean,
    onStopTimer: () -> Unit,
    stateReadback: @Composable () -> Unit,
    modesExpanded: Boolean,
    onModesToggle: () -> Unit,
    timerExpanded: Boolean,
    onTimerToggle: () -> Unit,
    brightness: Int,
    speed: Int,
    selectedMode: LampMode,
    lampType: LampType,
    rgbColor: RgbColor,
    colorError: String?,
    colorsExpanded: Boolean,
    onColorsToggle: () -> Unit,
    onColor: (RgbColor) -> Unit,
    onColorCommit: (RgbColor) -> Unit,
    sunsetMinutes: Int,
    sunsetRemainingSeconds: Int,
    timerKind: SunTimerKind?,
    timerError: String?,
    supportsSunrise: Boolean,
    onPower: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onBrightnessCommit: (Int) -> Unit,
    onSpeed: (Int) -> Unit,
    onSpeedCommit: (Int) -> Unit,
    onMode: (LampMode) -> Unit,
    onSunsetMinutes: (Int) -> Unit,
    onSyncTime: () -> Unit,
    onStartSunset: (SunTimerKind, Calendar?) -> Unit,
) {
    var category by rememberSaveable(lampType) { mutableStateOf(lampType.availableModes().first().category) }
    val modeSummary = uiString(selectedMode.label) + " | " + uiString(origins.mode.label) +
        if (selectedMode.supportsSpeed) " | " + uiString(selectedMode.rampLabel) + " $speed/255 (" +
            uiString(origins.ramp.label) + ")" else ""
    val parameterLabel = uiString(selectedMode.label) + " | " + uiString(selectedMode.rampLabel)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onPower(true) }) {
                            AppText("On")
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onPower(false) }) {
                            AppText("Off")
                        }
                    }

                    stateReadback()

                    ControlSlider(
                        origin = origins.brightness,
                        label = "Brightness",
                        value = brightness,
                        onValueChange = onBrightness,
                        onValueChangeFinished = { onBrightnessCommit(brightness) },
                        maxValue = 1024,
                        presets = listOf(10, 256, 512, 768, 1024),
                        onPreset = {
                            onBrightness(it)
                            onBrightnessCommit(it)
                        },
                    )
                    if (selectedMode.supportsSpeed) {
                        ControlSlider(
                            origin = origins.ramp,
                            label = parameterLabel,
                            value = speed,
                            onValueChange = onSpeed,
                            onValueChangeFinished = { onSpeedCommit(speed) },
                            maxValue = 255,
                            presets = listOf(0, 64, 128, 192, 255),
                            onPreset = {
                                onSpeed(it)
                                onSpeedCommit(it)
                            },
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CollapsibleHeader(
                        title = "Modes",
                        detail = modeSummary,
                        expanded = modesExpanded,
                        onToggle = onModesToggle,
                    )
                    if (modesExpanded) {
                        if (lampType == LampType.Rgb) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                lampType.availableModes().map { it.category }.distinct().forEach { group ->
                                    FilterChip(selected = category == group,
                                        onClick = { category = group }, label = { AppText(group) })
                                }
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            maxItemsInEachRow = 2,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            lampType.availableModes().filter {
                                lampType == LampType.Simple || it.category == category
                            }.forEach { mode ->
                                ModeTile(
                                    mode = mode,
                                    speedSupported = mode.supportsSpeed,
                                    selected = selectedMode == mode && origins.mode in
                                        listOf(ControlOrigin.Requested, ControlOrigin.Reported),
                                    onClick = { onMode(mode) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (lampType == LampType.Rgb) item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                RgbColorControls(rgbColor, onColor, onColorCommit, colorsExpanded, onColorsToggle,
                    colorError, origins.color)
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SunTimerControls(
                    expanded = timerExpanded,
                    onToggle = onTimerToggle,
                    minutes = sunsetMinutes,
                    remainingSeconds = sunsetRemainingSeconds,
                    activeKind = timerKind,
                    canStop = canStopTimer,
                    stopping = stoppingTimer,
                    supportsSunrise = supportsSunrise,
                    error = timerError,
                    onMinutes = onSunsetMinutes,
                    onSyncTime = onSyncTime,
                    onStart = onStartSunset,
                    onStop = onStopTimer,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    detail: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, fontWeight = FontWeight.SemiBold)
            AppText(detail, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        AppText(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlSlider(
    origin: ControlOrigin,
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    presets: List<Int> = emptyList(),
    onPreset: (Int) -> Unit = {},
    maxValue: Int = 100,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                AppText(label, fontWeight = FontWeight.SemiBold)
                ControlOriginLabel(origin)
            }
            AppText("$value / $maxValue")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..maxValue.toFloat(),
            steps = 0
        )
        if (presets.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = value == preset && origin in
                            listOf(ControlOrigin.Requested, ControlOrigin.Reported),
                        onClick = { onPreset(preset) },
                        label = { AppText("${(preset * 100f / maxValue).roundToInt()}%") }
                    )
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ModeTile(
    mode: LampMode,
    speedSupported: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            AppText(
                mode.label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
            if (speedSupported) AppText(
                mode.rampLabel,
                color = contentColor.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanTab(
    devices: List<LampDevice>,
    sessionAddresses: Set<String>,
    scanState: String,
    isScanning: Boolean,
    onToggleScan: () -> Unit,
    onConnect: (LampDevice) -> Unit,
) {
    val scanContext = LocalContext.current
    var scanFilter by rememberSaveable {
        mutableStateOf(defaultScanFilter(scanContext.getSharedPreferences("lamp_profiles", Context.MODE_PRIVATE)
            .getStringSet("known_lamps", emptySet()).orEmpty().isNotEmpty()))
    }
    val knownDevices = devices.filter { it.isKnownLamp }
    val ranking = remember { ScanRanking() }
    var rankedAddresses by remember { mutableStateOf(emptyList<String>()) }
    var rankedSignals by remember { mutableStateOf(emptyMap<String, Int>()) }
    val latestDevices by rememberUpdatedState(devices.toList())
    LaunchedEffect(isScanning) {
        do {
            val samples = latestDevices.map { ScanStrength(it.address, it.smoothedRssi) }
            rankedAddresses = ranking.update(samples,
                android.os.SystemClock.elapsedRealtime(), force = !isScanning)
            rankedSignals = samples.associate { it.address to it.rssi.roundToInt() }
            if (!isScanning) break
            delay(ScanRankIntervalMillis)
        } while (true)
    }
    val rankPositions = rankedAddresses.withIndex().associate { it.value to it.index }
    val visibleDevices = devices.filter { scanFilter.accepts(it.isKnownLamp) }
        .sortedBy { rankPositions[it.address] ?: Int.MAX_VALUE }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText("Lamp scan", fontWeight = FontWeight.SemiBold)
                    AppText(
                        if (isScanning) {
                            "Scanning: ${devices.size} BLE / ${knownDevices.size} known"
                        } else {
                            "${devices.size} BLE / ${knownDevices.size} known"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = onToggleScan) {
                    AppText(if (isScanning) "Stop" else "Scan")
                }
            }
        }

        if (scanState != "Scanning" && scanState != "Disconnected") {
            AppText(scanState, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanListFilter.entries.forEach { filter ->
                FilterChip(scanFilter == filter, onClick = { scanFilter = filter }, label = { AppText(filter.label) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (visibleDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppText(
                                if (isScanning) "Looking for nearby devices..." else "No matching device",
                                fontWeight = FontWeight.SemiBold
                            )
                            AppText(
                                if (scanFilter == ScanListFilter.Known) "No known lamps found."
                                else "No Bluetooth advertisements received.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                items(visibleDevices, key = { it.address }) { device ->
                    LampDeviceRow(
                        device = device.copy(rssi = rankedSignals[device.address] ?: device.rssi),
                        alreadyAdded = device.address in sessionAddresses,
                        onConnect = { onConnect(device) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LampDeviceRow(
    device: LampDevice,
    alreadyAdded: Boolean,
    onConnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                AppText(
                    device.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (device.isKnownLamp) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { AppText("Known lamp") }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { AppText("${device.rssi} dBm") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { AppText(device.signalLabel) }
                    )
                }
            }
            Button(onClick = onConnect) {
                AppText(if (alreadyAdded) "Open" else "Connect")
            }
        }
    }
}

private val LampDevice.signalLabel: String
    get() = when {
        rssi >= -55 -> "Excellent"
        rssi >= -70 -> "Good"
        rssi >= -85 -> "Weak"
        else -> "Far"
    }

data class LampDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bluetoothDevice: BluetoothDevice,
    val isLikelyLamp: Boolean,
    val isKnownLamp: Boolean = false,
    val smoothedRssi: Double = rssi.toDouble(),
    val rssiUpdatedAtMillis: Long = 0,
)

@SuppressLint("MissingPermission")
internal class LampBleController(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences("lamp_profiles", Context.MODE_PRIVATE)

    var lampType by mutableStateOf(LampType.Simple)
        private set
    var reportedType by mutableStateOf<LampType?>(null)
        private set
    var colorReading by mutableStateOf(ControlReading(RgbColor()))
        private set
    val rgbColor get() = colorReading.value
    var colorError by mutableStateOf<String?>(null)
        private set

    val devices = mutableStateListOf<LampDevice>()
    var connectionState by mutableStateOf("Disconnected")
        private set
    var isConnected by mutableStateOf(false)
        private set
    var connectionIssue by mutableStateOf<String?>(null)
        private set
    var connectedLampName by mutableStateOf<String?>(null)
        private set
    val diagnosticCommands = mutableStateListOf<String>()
    var isScanning by mutableStateOf(false)
        private set
    var modeReading by mutableStateOf(ControlReading(LampMode.Static))
        private set
    val selectedMode get() = modeReading.value
    var brightnessReading by mutableStateOf(ControlReading(512))
        private set
    val brightness get() = brightnessReading.value
    var rampReading by mutableStateOf(ControlReading(128))
        private set
    val speed get() = rampReading.value
    var sunsetMinutes by mutableIntStateOf(30)
    var sunsetEndsAtMillis by mutableStateOf<Long?>(null)
        private set
    var activeSunTimerKind by mutableStateOf<SunTimerKind?>(null)
        private set
    var reportedPower by mutableStateOf<Boolean?>(null)
        private set
    var supportsOutputStatus by mutableStateOf(false)
        private set
    private var pendingStateRefresh by mutableStateOf<LampStateRefresh?>(null)
    val isRefreshingState get() = pendingStateRefresh != null
    val canRefreshState get() = isConnected && !isRefreshingState &&
        pendingSunTimer == null && !isStoppingSunTimer
    var stateRefreshFailed by mutableStateOf(false)
        private set
    var sunTimerError by mutableStateOf<String?>(null)
        private set
    private data class PendingSunTimer(val kind: SunTimerKind, val endsAt: Long)
    private var pendingSunTimer by mutableStateOf<PendingSunTimer?>(null)
    var supportsSunTimerStop by mutableStateOf(false)
        private set
    private var pendingSunTimerStop by mutableStateOf<Any?>(null)
    val isStoppingSunTimer get() = pendingSunTimerStop != null
    val canStopSunTimer get() = isConnected && supportsSunTimerStop &&
        activeSunTimerKind != null && pendingSunTimer == null && !isStoppingSunTimer && !isRefreshingState
    var batteryLevel by mutableStateOf<Int?>(null)
        private set
    val uartLogs = mutableStateListOf<String>()

    private var gatt: BluetoothGatt? = null
    private var uartRxCharacteristic: BluetoothGattCharacteristic? = null
    private var uartTxCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu = 23
    private var serviceDiscoveryStarted = false
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null
    private val notificationQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private data class PendingWrite(val characteristic: BluetoothGattCharacteristic, val payload: ByteArray)
    private val uartWriteQueue = ArrayDeque<PendingWrite>()
    private var notificationWriteInProgress = false
    private var uartWriteInProgress = false
    private var batteryReadInProgress = false
    private var postConnectSetupDone = false
    private var uartLineBuffer = ""
    var supportsSunrise by mutableStateOf(true)
        private set
    private var lastDevice: LampDevice? = null
    private var manualDisconnect = false
    private var reconnectAttempts = 0
    private fun <T : Any> controlSender(send: (T) -> Unit) = LatestControlSender(
        schedule = { task, delay -> mainHandler.postDelayed(task, delay); Unit },
        cancelScheduled = { mainHandler.removeCallbacks(it) }, send = send)
    private val brightnessSender = controlSender<Int> { sendUartLine(Firmware19.brightness(it)) }
    private val speedSender = controlSender<Int> { sendUartLine(Firmware19.ramp(it)) }
    private val colorSender = controlSender<RgbColor> { sendUartLine(Firmware19.color(it)) }
    private var editingBrightness = false
    private var editingRamp = false
    private val identityProbe = LampIdentityProbe(
        schedule = { task, delay -> mainHandler.postDelayed(task, delay); Unit },
        cancelScheduled = { mainHandler.removeCallbacks(it) },
        request = { if (postConnectSetupDone && gatt != null) sendUartLine("t") },
    )
    private var writeAdmissionRetries = 0
    private val retryUartWriteRunnable = Runnable { processUartWriteQueue() }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            @Suppress("SpellCheckingInspection")
            val names = listOfNotNull(result.scanRecord?.deviceName, result.device.name)
            val rawPayload = result.scanRecord?.bytes?.toString(Charsets.ISO_8859_1).orEmpty()
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
            onMain {
                val observedAt = android.os.SystemClock.elapsedRealtime()
                val existingIndex = devices.indexOfFirst { it.address == result.device.address }
                val previous = devices.getOrNull(existingIndex)
                val isKnownLamp = preferences.getStringSet("known_lamps", emptySet())
                    ?.contains(result.device.address) == true
                val identity = mergeScanIdentity(previous?.let { ScanIdentity(it.name, it.isLikelyLamp) },
                    names, rawPayload, serviceUuids,
                    knownLamp = isKnownLamp)
                val deviceName = identity.name
                val isLikelyLamp = identity.isLamp
                if (existingIndex >= 0) {
                    val existing = devices[existingIndex]
                    devices[existingIndex] = existing.copy(
                        name = deviceName,
                        rssi = result.rssi,
                        smoothedRssi = smoothScanRssi(existing.smoothedRssi, result.rssi,
                            observedAt - existing.rssiUpdatedAtMillis),
                        rssiUpdatedAtMillis = observedAt,
                        isLikelyLamp = isLikelyLamp,
                        isKnownLamp = isKnownLamp,
                    )
                    return@onMain
                }

                devices.add(
                    LampDevice(
                        name = deviceName,
                        address = result.device.address,
                        rssi = result.rssi,
                        bluetoothDevice = result.device,
                        rssiUpdatedAtMillis = observedAt,
                        isLikelyLamp = isLikelyLamp,
                        isKnownLamp = isKnownLamp,
                    )
                )

                val target = lastDevice
                if (!manualDisconnect && target?.address == result.device.address && gatt == null) {
                    connect(LampDevice(deviceName, result.device.address, result.rssi, result.device, isLikelyLamp))
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            onMain {
                isScanning = false
                connectionState = "Scan failed: $errorCode"
                appendUartLog("APP scan failed: $errorCode")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onServiceChanged(gatt: BluetoothGatt) {
            onMain {
                if (this@LampBleController.gatt != gatt) return@onMain
                // Discard outstanding operations before discovering the changed database on a new connection.
                retryConnection("Lamp services changed; reconnecting")
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (this@LampBleController.gatt != gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onMain { connectionState = "Connection error: $status" }
                closeGatt()
                scheduleReconnect()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    manualDisconnect = false
                    onMain { connectionState = "Connected, discovering services" }
                    if (!gatt.requestMtu(247)) discoverOnce(gatt)
                    mainHandler.postDelayed({ discoverOnce(gatt) }, 2_000)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    onMain { connectionState = "Disconnected" }
                    closeGatt()
                    scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            onMain {
                if (this@LampBleController.gatt != gatt) return@onMain
                if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
                discoverOnce(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (this@LampBleController.gatt != gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                retryConnection("Service discovery failed: $status")
                return
            }

            batteryCharacteristic =
                gatt.getService(BatteryServiceUuid)
                    ?.getCharacteristic(BatteryLevelCharacteristicUuid)

            val uartService = gatt.getService(UartServiceUuid)
            uartRxCharacteristic = uartService?.getCharacteristic(UartRxCharacteristicUuid)
                ?: gatt.services
                    .asSequence()
                    .flatMap { it.characteristics.asSequence() }
                    .firstOrNull { it.uuid == UartRxCharacteristicUuid && it.canWrite() }
            uartTxCharacteristic = uartService?.getCharacteristic(UartTxCharacteristicUuid)
                ?: gatt.services
                    .asSequence()
                    .flatMap { it.characteristics.asSequence() }
                    .firstOrNull { it.uuid == UartTxCharacteristicUuid }
            if (uartRxCharacteristic?.canWrite() != true || uartTxCharacteristic == null) {
                retryConnection("Nordic UART service not found: ${gatt.serviceSummary()}")
                return
            }
            // The official firmware starts pairing immediately. Wait before enabling UART.
            mainHandler.postDelayed({ beginNotificationSetup(gatt) }, 1_000)
        }

        @Deprecated("Deprecated in Android 13, kept for older devices")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (this@LampBleController.gatt != gatt) return
            if (characteristic.uuid == BatteryLevelCharacteristicUuid) {
                @Suppress("DEPRECATION")
                finishBatteryRead(gatt, characteristic.value, status)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (this@LampBleController.gatt != gatt) return
            if (characteristic.uuid == BatteryLevelCharacteristicUuid) {
                finishBatteryRead(gatt, value, status)
            }
        }

        @Deprecated("Deprecated in Android 13, kept for older devices")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (this@LampBleController.gatt != gatt) return
            if (characteristic.uuid == BatteryLevelCharacteristicUuid) {
                @Suppress("DEPRECATION")
                updateBatteryLevel(characteristic.value)
            } else if (characteristic.uuid == UartTxCharacteristicUuid) {
                @Suppress("DEPRECATION")
                appendUartRx(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (this@LampBleController.gatt != gatt) return
            if (characteristic.uuid == BatteryLevelCharacteristicUuid) {
                updateBatteryLevel(value)
            } else if (characteristic.uuid == UartTxCharacteristicUuid) {
                appendUartRx(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            onMain {
                if (this@LampBleController.gatt != gatt) return@onMain
                notificationWriteInProgress = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val message = "Notification setup failed: $status (${descriptor.characteristic.uuid})"
                    if (!optionalBleNotification(descriptor.characteristic.uuid)) {
                        if (requiresManualBleRepair(status)) {
                            appendUartLog("APP $message")
                            manualDisconnect = true
                            mainHandler.removeCallbacksAndMessages(ReconnectToken)
                            closeGatt()
                            connectionIssue = "Lamp notifications were refused. Retry the connection; if it persists, renew this lamp's pairing."
                            connectionState = "Connection needs attention"
                        } else retryConnection(message)
                        return@onMain
                    }
                    appendUartLog("APP $message; using battery reads only")
                    android.util.Log.w("LampDaBle", message)
                }
                processNotificationQueue(gatt)
                finishPostConnectSetupIfIdle(gatt)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (this@LampBleController.gatt != gatt) return

            onMain {
                if (this@LampBleController.gatt != gatt) return@onMain
                uartWriteInProgress = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    appendUartLog("APP BLE write failed: $status; remaining command discarded")
                    retryConnection("Write failed: $status")
                    return@onMain
                }
                processUartWriteQueue()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) return
        if (!context.hasBlePermissions()) {
            connectionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                "Allow Nearby devices permission" else "Allow location permission for Bluetooth scan"
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            connectionState = "Enable Bluetooth to scan"
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val locationManager = context.getSystemService(LocationManager::class.java)
            val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled == true
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
            }
            if (!locationEnabled) {
                connectionState = "Enable phone location services to scan"
                return
            }
        }
        val currentScanner = scanner
        if (currentScanner == null) {
            connectionState = "Bluetooth LE scanner unavailable"
            return
        }
        devices.clear()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        try {
            isScanning = true
            connectionState = "Scanning"
            currentScanner.startScan(null, settings, scanCallback)
        } catch (error: SecurityException) {
            isScanning = false
            connectionState = "Bluetooth scan permission denied"
        } catch (error: IllegalStateException) {
            isScanning = false
            connectionState = "Bluetooth unavailable for scanning"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (context.hasBlePermissions()) runCatching { scanner?.stopScan(scanCallback) }
        isScanning = false
        if (connectionState == "Scanning") connectionState = "Disconnected"
    }

    fun toggleScan() {
        if (isScanning) {
            stopScan()
        } else {
            startScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: LampDevice) {
        connectionIssue = null
        if (!context.hasBlePermissions()) return
        mainHandler.removeCallbacksAndMessages(ReconnectToken)
        reconnectAttempts = 0
        stopScan()
        closeGatt()
        lastDevice = device
        val savedType = preferences.getString("type_${device.address}", null)
        applyLampType(LampType.entries.firstOrNull { it.name == savedType } ?: LampType.Simple)
        manualDisconnect = false
        connectGatt(device.bluetoothDevice, device.name)
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice, name: String) {
        connectedLampName = name
        connectionState = "Connecting to $name"
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun setPower(enabled: Boolean) {
        val currentGatt = gatt
        reportedPower = null
        sunsetEndsAtMillis = null
        activeSunTimerKind = null
        pendingSunTimer = null
        sendUartLine(Firmware19.power(enabled))
        if (supportsOutputStatus && currentGatt != null) mainHandler.postDelayed({
            if (gatt == currentGatt && postConnectSetupDone) sendUartLine(Firmware19.StatusQuery)
        }, 500)
    }

    fun sendBrightness(value: Int, commit: Boolean = false) {
        editingBrightness = !commit
        brightnessReading = brightnessReading.request(value.coerceIn(0, 1024))
        brightnessSender.update(brightness, commit)
    }

    fun commitBrightness() {
        editingBrightness = false
        brightnessSender.commit()
    }

    fun sendSpeed(value: Int, commit: Boolean = false) {
        editingRamp = !commit
        rampReading = rampReading.request(value.coerceIn(0, 255))
        speedSender.update(speed, commit)
    }

    fun commitSpeed() {
        editingRamp = false
        speedSender.commit()
    }

    fun cancelContinuousControls() {
        editingBrightness = false
        editingRamp = false
        brightnessSender.cancel()
        speedSender.cancel()
        colorSender.cancel()
    }

    fun setMode(mode: LampMode) {
        if (mode.lampType != lampType) return
        speedSender.cancel()
        colorSender.cancel()
        if (mode == LampMode.RgbCustom) {
            sendColor(rgbColor, commit = true)
            return
        }
        modeReading = modeReading.request(mode)
        rampReading = rampReading.invalidate()
        colorReading = colorReading.invalidate()
        sendUartLine(Firmware19.mode(mode))
    }

    fun chooseLampType(type: LampType) {
        lastDevice?.let { preferences.edit().putString("type_${it.address}", type.name).apply() }
        applyLampType(type)
    }

    fun verifyLampType() {
        if (isConnected && postConnectSetupDone) identityProbe.start()
    }

    private fun applyLampType(type: LampType) {
        colorSender.cancel()
        speedSender.cancel()
        lampType = type
        colorError = null
        modeReading = ControlReading(type.availableModes().first())
        rampReading = rampReading.invalidate()
        colorReading = colorReading.invalidate()
    }

    fun sendColor(color: RgbColor, commit: Boolean = false) {
        if (lampType != LampType.Rgb) return
        if (colorTarget() == null) return
        speedSender.cancel()
        colorReading = colorReading.request(color)
        modeReading = modeReading.request(LampMode.RgbCustom)
        colorSender.update(color, commit)
    }

    private fun colorTarget(): BluetoothGattCharacteristic? {
        val error = when {
            gatt == null || !postConnectSetupDone -> "Connect to the lamp first."
            uartRxCharacteristic == null -> "UART not connected"
            else -> null
        }
        if (error != null && error != colorError) appendUartLog("APP $error")
        colorError = error
        return if (error == null) uartRxCharacteristic else null
    }

    fun syncTime() {
        sendUartLine(clockCommand("time", Calendar.getInstance()))
    }

    fun refreshState() {
        val currentGatt = gatt ?: return
        if (!canRefreshState || !postConnectSetupDone) return
        val request = LampStateRefresh()
        pendingStateRefresh = request
        stateRefreshFailed = false
        sendUartLine(Firmware19.StatusQuery)
        mainHandler.postDelayed({
            if (gatt == currentGatt && pendingStateRefresh === request) {
                pendingStateRefresh = null
                stateRefreshFailed = true
            }
        }, 6_000)
    }

    fun startSunTimer(kind: SunTimerKind, targetTime: Calendar? = null) {
        sunTimerError = null
        val currentGatt = gatt
        if (currentGatt == null || !postConnectSetupDone) {
            sunTimerError = "Connect to the lamp first."
            return
        }
        if (kind == SunTimerKind.Sunrise && !supportsSunrise) {
            sunTimerError = "Sunrise is not available on this firmware."
            return
        }
        if (pendingSunTimer != null || isStoppingSunTimer || isRefreshingState) {
            sunTimerError = "Waiting for the lamp to confirm the timer."
            return
        }
        val minutes = sunsetMinutes.coerceIn(1, 24 * 60)
        val now = Calendar.getInstance()
        val target = targetTime ?: (now.clone() as Calendar).apply {
            add(Calendar.MINUTE, minutes)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            sunTimerError = "Choose a future end time."
            return
        }

        val pending = PendingSunTimer(kind, target.timeInMillis)
        pendingSunTimer = pending
        sendUartLine(clockCommand("time", now))
        sendUartLine(clockCommand(kind.command, target))
        mainHandler.postDelayed({
            if (gatt == currentGatt && pendingSunTimer === pending) {
                pendingSunTimer = null
                sunTimerError = "No timer confirmation received from the lamp."
            }
        }, 8_000)
        appendUartLog("TX ${kind.label} ends at ${target.time}")
    }

    fun sendUartLine(line: String) {
        if (gatt == null || !postConnectSetupDone) {
            appendUartLog("APP Command not sent: UART is not connected")
            return
        }
        val payload = Firmware19.command(line.trimEnd()) + "\n"
        appendUartLog("TX ${payload.trimEnd()}")
        writeUart(payload.toByteArray(Charsets.UTF_8))
    }

    fun stopSunTimer() {
        val currentGatt = gatt ?: return
        if (!canStopSunTimer || !postConnectSetupDone || pendingSunTimer != null) return
        val request = Any()
        pendingSunTimerStop = request
        sunTimerError = null
        sendUartLine(Firmware19.SunTimerStop)
        mainHandler.postDelayed({
            if (gatt == currentGatt && pendingSunTimerStop === request) {
                pendingSunTimerStop = null
                sunTimerError = "The lamp did not confirm the timer stop."
            }
        }, 8_000)
    }

    fun clearUartLogs() {
        uartLogs.clear()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        manualDisconnect = true
        reconnectAttempts = 0
        mainHandler.removeCallbacksAndMessages(ReconnectToken)
        gatt?.disconnect()
        closeGatt()
        connectionState = "Disconnected"
    }

    fun close() {
        stopScan()
        manualDisconnect = true
        mainHandler.removeCallbacksAndMessages(ReconnectToken)
        disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        if (manualDisconnect) return
        val device = lastDevice ?: return
        if (!context.hasBlePermissions()) return
        if (reconnectAttempts >= MaxReconnectAttempts) {
            appendUartLog("APP Reconnection limit reached; reconnect manually to retry")
            onMain {
                connectionState = "Connection needs attention"
                connectionIssue = "Reconnection failed. Check that the lamp is available, then retry."
            }
            return
        }

        reconnectAttempts += 1
        val delayMs = ReconnectDelayMs * reconnectAttempts
        onMain { connectionState = "Reconnecting ${reconnectAttempts}/$MaxReconnectAttempts" }
        mainHandler.postAtTime({
            if (manualDisconnect || gatt != null) return@postAtTime
            connectGatt(device.bluetoothDevice, device.name)
        }, ReconnectToken, android.os.SystemClock.uptimeMillis() + delayMs)
    }

    private fun retryConnection(message: String) {
        appendUartLog("APP $message")
        android.util.Log.w("LampDaBle", message)
        onMain { connectionState = message }
        closeGatt()
        scheduleReconnect()
    }

    @SuppressLint("MissingPermission")
    private fun processUartWriteQueue() {
        if (uartWriteInProgress || batteryReadInProgress) return

        val currentGatt = gatt ?: return
        val pending = uartWriteQueue.firstOrNull() ?: return
        val characteristic = pending.characteristic
        val payload = pending.payload
        val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        uartWriteInProgress = true
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(
                characteristic,
                payload,
                writeType
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }

        if (!ok) {
            uartWriteInProgress = false
            writeAdmissionRetries++
            if (writeAdmissionRetries <= 3) {
                mainHandler.removeCallbacks(retryUartWriteRunnable)
                mainHandler.postDelayed(retryUartWriteRunnable, 150L)
            } else {
                appendUartLog("APP BLE write rejected repeatedly; connection reset")
                retryConnection("Write failed")
            }
            return
        }
        uartWriteQueue.removeFirst()
        writeAdmissionRetries = 0

        // Android reports completion for both write types; it owns queue advancement.
    }

    @SuppressLint("MissingPermission")
    private fun writeUart(payload: ByteArray) {
        val characteristic = uartRxCharacteristic
        if (gatt == null || characteristic == null) {
            connectionState = "UART not connected"
            return
        }

        if (!postConnectSetupDone) return
        // The firmware assembles a full line across packets, even at the default MTU.
        payload.toList().chunked(uartPayloadSize(negotiatedMtu)).forEach { chunk ->
            uartWriteQueue.add(PendingWrite(characteristic, chunk.toByteArray()))
        }
        processUartWriteQueue()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        pendingStateRefresh = null
        stateRefreshFailed = false
        supportsSunTimerStop = false
        pendingSunTimerStop = null
        reportedPower = null
        supportsOutputStatus = false
        identityProbe.stop()
        brightnessReading = brightnessReading.invalidate()
        rampReading = rampReading.invalidate()
        modeReading = modeReading.invalidate()
        colorReading = colorReading.invalidate()
        isConnected = false
        connectedLampName = null
        reportedType = null
        diagnosticCommands.clear()
        uartRxCharacteristic = null
        uartTxCharacteristic = null
        negotiatedMtu = 23
        serviceDiscoveryStarted = false
        colorError = null
        batteryCharacteristic = null
        batteryLevel = null
        sunsetEndsAtMillis = null
        activeSunTimerKind = null
        pendingSunTimer = null
        sunTimerError = null
        uartLineBuffer = ""
        supportsSunrise = true
        uartWriteQueue.clear()
        uartWriteInProgress = false
        batteryReadInProgress = false
        brightnessSender.cancel()
        speedSender.cancel()
        colorSender.cancel()
        mainHandler.removeCallbacks(retryUartWriteRunnable)
        editingBrightness = false
        editingRamp = false
        writeAdmissionRetries = 0
        notificationQueue.clear()
        notificationWriteInProgress = false
        postConnectSetupDone = false
        gatt?.close()
        gatt = null
    }

    private fun beginNotificationSetup(currentGatt: BluetoothGatt, attempts: Int = 0) {
        if (gatt != currentGatt) return
        if (currentGatt.device.bondState == BluetoothDevice.BOND_BONDING) {
            connectionState = "Pairing with lamp"
            if (attempts >= 100) {
                retryConnection("Pairing timed out")
            } else {
                mainHandler.postDelayed({ beginNotificationSetup(currentGatt, attempts + 1) }, 300)
            }
            return
        }
        uartTxCharacteristic?.let { enableCharacteristicUpdates(currentGatt, it) }
        batteryCharacteristic?.let { enableBatteryUpdates(currentGatt, it) }
        finishPostConnectSetupIfIdle(currentGatt)
    }

    private fun enableBatteryUpdates(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        enableCharacteristicUpdates(gatt, characteristic)
    }

    private fun enableCharacteristicUpdates(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val supportsIndicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (!supportsNotify && !supportsIndicate) return

        gatt.setCharacteristicNotification(characteristic, true)
        if (characteristic.getDescriptor(ClientCharacteristicConfigUuid) == null) return

        notificationQueue.add(characteristic)
        processNotificationQueue(gatt)
    }

    private fun processNotificationQueue(gatt: BluetoothGatt) {
        if (notificationWriteInProgress || notificationQueue.isEmpty()) return

        val characteristic = notificationQueue.removeFirst()
        val descriptor = characteristic.getDescriptor(ClientCharacteristicConfigUuid) ?: return
        val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val descriptorValue = if (supportsNotify) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        notificationWriteInProgress = true
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, descriptorValue) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = descriptorValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        if (!ok) {
            notificationWriteInProgress = false
            if (optionalBleNotification(characteristic.uuid)) {
                appendUartLog("APP Battery notification write rejected; using battery reads only")
                processNotificationQueue(gatt)
                finishPostConnectSetupIfIdle(gatt)
            } else retryConnection("Could not enable lamp notifications")
        }
    }

    private fun finishPostConnectSetupIfIdle(gatt: BluetoothGatt) {
        if (this.gatt != gatt) return
        if (postConnectSetupDone || notificationWriteInProgress || notificationQueue.isNotEmpty()) return

        postConnectSetupDone = true
        reconnectAttempts = 0
        isConnected = true
        onMain { connectionState = "Connected" }
        appendUartLog("UART notifications enabled")

        batteryCharacteristic?.let { characteristic ->
            batteryReadInProgress = gatt.readCharacteristic(characteristic)
        }

        mainHandler.postDelayed({
            if (this@LampBleController.gatt == gatt) {
                syncTime()
            }
        }, 300)

        mainHandler.postDelayed({
            if (this@LampBleController.gatt == gatt) {
                identityProbe.start()
                Firmware19.connectionQueries.filterNot { it == "t" }.forEach(::sendUartLine)
            }
        }, 700)
    }

    private fun appendUartRx(value: ByteArray?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            onMain { appendUartRx(value) }
            return
        }
        val text = value?.toString(Charsets.UTF_8) ?: return
        if (text.isEmpty()) return

        uartLineBuffer += text.replace("\r", "")
        while (true) {
            val lineEndIndex = uartLineBuffer.indexOf('\n')
            if (lineEndIndex < 0) return

            val line = uartLineBuffer.substring(0, lineEndIndex).trim()
            uartLineBuffer = uartLineBuffer.substring(lineEndIndex + 1)
            if (line.isNotEmpty()) {
                val content = firmwareLine(line)
                when (val feedback = firmwareFeedback(content)) {
                    FirmwareFeedback.OutputOff -> {
                        recordReportedPower(false)
                        brightnessReading = brightnessReading.invalidate()
                        modeReading = modeReading.invalidate()
                        rampReading = rampReading.invalidate()
                        colorReading = colorReading.invalidate()
                    }
                    is FirmwareFeedback.OutputStatus -> {
                        recordReportedPower(true)
                        applyReportedMode(feedback.group, feedback.mode)
                        if (!editingBrightness) brightnessReading = brightnessReading.receive(feedback.brightness)
                        if (!editingRamp) rampReading = rampReading.receive(feedback.ramp)
                    }
                    is FirmwareFeedback.Brightness -> if (!editingBrightness)
                        brightnessReading = brightnessReading.receive(feedback.value)
                    is FirmwareFeedback.Ramp -> if (!editingRamp)
                        rampReading = rampReading.receive(feedback.value)
                    is FirmwareFeedback.Mode -> applyReportedMode(feedback.group, feedback.mode)
                    is FirmwareFeedback.BleName -> connectedLampName = feedback.name
                    is FirmwareFeedback.Indicator, null -> Unit
                }
                updateSunTimerFromUart(content)
                if (content.startsWith("New ble name set to: ")) {
                    val currentGatt = gatt
                    mainHandler.postDelayed({
                        if (gatt == currentGatt && postConnectSetupDone) sendUartLine("sys ble_name")
                    }, 300)
                }
                reportedLampType(content)?.let { type ->
                    val firstTypeReply = reportedType == null
                    identityProbe.stop()
                    reportedType = type
                    val knownLamps = preferences.getStringSet("known_lamps", emptySet()).orEmpty().toMutableSet()
                    gatt?.device?.address?.let { knownLamps.add(it) }
                    preferences.edit().putStringSet("known_lamps", knownLamps).apply()
                    val deviceIndex = devices.indexOfFirst { it.address == gatt?.device?.address }
                    if (deviceIndex >= 0) devices[deviceIndex] = devices[deviceIndex].copy(isKnownLamp = true)
                    if (lampType != type) applyLampType(type)
                    lastDevice?.let { preferences.edit().putString("type_${it.address}", type.name).apply() }
                    if (firstTypeReply && postConnectSetupDone) sendUartLine(Firmware19.StatusQuery)
                }
                if (Firmware19.advertisesSunrise(content)) supportsSunrise = true
                advertisedDiagnostic(content)?.let { if (it !in diagnosticCommands) diagnosticCommands.add(it) }
                appendUartLog("RX $line")
            }
        }
    }

    private fun recordReportedPower(on: Boolean) {
        reportedPower = on
        val firstStatus = !supportsOutputStatus
        supportsOutputStatus = true
        val refresh = pendingStateRefresh
        if (if (refresh != null) refresh.receiveStatus() else firstStatus) {
            sendUartLine("sys sun")
        }
    }

    private fun applyReportedMode(group: Int, modeId: Int) {
        val mode = lampType.availableModes().firstOrNull { it.groupId == group && it.modeId == modeId }
        if (mode != selectedMode) rampReading = rampReading.invalidate()
        modeReading = mode?.let { modeReading.receive(it) } ?: modeReading.invalidate()
        if (mode != LampMode.RgbCustom) colorReading = colorReading.invalidate()
    }

    @SuppressLint("MissingPermission")
    private fun discoverOnce(currentGatt: BluetoothGatt) {
        onMain {
            if (gatt != currentGatt || serviceDiscoveryStarted) return@onMain
            serviceDiscoveryStarted = true
            if (!currentGatt.discoverServices()) retryConnection("Service discovery failed")
        }
    }

    private fun updateSunTimerFromUart(line: String) {
        sunTimerStatus(line)?.let { status ->
            supportsSunTimerStop = true
            if (pendingStateRefresh?.receiveTimer(status.kind) == true) pendingStateRefresh = null
            if (pendingSunTimer == null && !isStoppingSunTimer) {
                if (status.remainingSeconds != null) {
                    activeSunTimerKind = status.kind
                    sunsetEndsAtMillis = System.currentTimeMillis() + status.remainingSeconds * 1_000
                    sunTimerError = null
                } else if (status.kind == activeSunTimerKind) {
                    activeSunTimerKind = null
                    sunsetEndsAtMillis = null
                }
            }
            return
        }
        val pending = pendingSunTimer
        if (pending != null) {
            when (sunTimerReply(line, pending.kind)) {
                SunTimerReply.Accepted -> {
                    pendingSunTimer = null
                    activeSunTimerKind = pending.kind
                    sunsetEndsAtMillis = pending.endsAt
                    sunTimerError = null
                }
                SunTimerReply.Refused -> {
                    pendingSunTimer = null
                    if (pending.kind == SunTimerKind.Sunrise && line.startsWith("unknown command:", ignoreCase = true)) {
                        supportsSunrise = false
                    }
                    sunTimerError = when {
                        pending.kind == SunTimerKind.Sunrise && !supportsSunrise -> "Sunrise is not available on this firmware."
                        pending.kind == SunTimerKind.Sunrise -> "Sunrise refused: the lamp must be off with no active Sunset."
                        else -> "Timer refused by the lamp. Check UART for details."
                    }
                }
                null -> Unit
            }
        }
        val stopConfirmed = sunTimerStopConfirmed(line)
        if (stopConfirmed ||
            line.equals("${activeSunTimerKind?.label} timer finished !", ignoreCase = true) ||
            line.equals("Shutdown with sunset timer", ignoreCase = true)) {
            if (isStoppingSunTimer || stopConfirmed) sunTimerError = null
            pendingSunTimerStop = null
            sunsetEndsAtMillis = null
            activeSunTimerKind = null
        }
    }

    private fun appendUartLog(line: String) {
        onMain {
            uartLogs.add(line)
            while (uartLogs.size > 200) {
                uartLogs.removeAt(0)
            }
        }
    }

    private fun updateBatteryLevel(value: ByteArray?) {
        val level = value?.firstOrNull()?.toInt()?.and(0xFF) ?: return
        onMain { batteryLevel = level.coerceIn(0, 100) }
    }

    private fun finishBatteryRead(currentGatt: BluetoothGatt, value: ByteArray?, readStatus: Int) {
        onMain {
            if (gatt != currentGatt) return@onMain
            batteryReadInProgress = false
            if (readStatus == BluetoothGatt.GATT_SUCCESS) updateBatteryLevel(value)
            processUartWriteQueue()
        }
    }

    private fun BluetoothGatt.serviceSummary(): String {
        if (services.isEmpty()) return "no services"
        return services.joinToString(limit = 4, truncated = "...") { service ->
            service.uuid.toString().substring(4, 8)
        }
    }

    private fun BluetoothGattCharacteristic.canWrite(): Boolean =
        properties and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ) != 0

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

internal fun requiredBlePermissions(apiLevel: Int = Build.VERSION.SDK_INT): Array<String> =
    if (apiLevel >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.hasBlePermissions(): Boolean =
    requiredBlePermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
