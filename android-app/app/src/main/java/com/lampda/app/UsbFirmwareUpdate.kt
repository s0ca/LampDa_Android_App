package com.lampda.app

import android.app.Application
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal data class LocalFirmware(val name: String, val bytes: ByteArray, val metadata: Uf2Metadata)
internal data class BootVolume(val uri: Uri, val name: String)

private fun UsbDevice.lampIdentity() = UsbIdentity(vendorId, productId,
    (0 until interfaceCount).map { getInterface(it).interfaceClass }.toSet())

internal class UsbFirmwareUpdate(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val usb = app.getSystemService(UsbManager::class.java)
    var firmware by mutableStateOf<LocalFirmware?>(null); private set
    var volume by mutableStateOf<BootVolume?>(null); private set
    var lampType by mutableStateOf<LampType?>(null); private set
    private var dfuLampType by mutableStateOf<LampType?>(null)
    private var manualBootloaderType by mutableStateOf<LampType?>(null)
    val updateLampType get() = lampType ?: dfuLampType ?: manualBootloaderType
    var report by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var copyAttempted by mutableStateOf(false); private set
    var status by mutableStateOf("Choose a local UF2 file."); private set
    var detection by mutableStateOf(UsbDetection.Empty); private set
    var usbInventory by mutableStateOf(""); private set
    val hostSupported = app.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    var permissionDeviceId: Int? = null
    private var runtimeId: Int? = null

    fun refreshUsb() {
        val devices = usb.deviceList.values.sortedBy { it.deviceId }
        detection = detectUsb(devices.map { it.lampIdentity() })
        usbInventory = devices.joinToString("\n\n") { device ->
            val product = runCatching { device.productName }.getOrNull().orEmpty()
            val interfaces = (0 until device.interfaceCount).joinToString { index ->
                val intf = device.getInterface(index)
                "${intf.interfaceClass}/${intf.interfaceSubclass}/${intf.interfaceProtocol}"
            }
            "%04X:%04X %s\nInterfaces: %s\nPermission: %s".format(
                device.vendorId, device.productId, product, interfaces, usb.hasPermission(device))
        }
        if (runtimeId != null && devices.none { it.deviceId == runtimeId }) {
            runtimeId = null
            lampType = null
        }
        if (detection != UsbDetection.Bootloader) manualBootloaderType = null
    }

    fun runtimeDevice(): UsbDevice? {
        refreshUsb()
        val devices = usb.deviceList.values.filter { it.lampIdentity().isLamp }
        require(detection == UsbDetection.Runtime) {
            when (detection) {
                UsbDetection.Empty -> "Android detects no USB device."
                UsbDetection.Bootloader -> "The lamp is already in bootloader mode; its application cannot reply."
                UsbDetection.Other -> "USB devices detected, but no supported Lamp-Da USB identifier."
                else -> "Connect exactly one Lamp-Da using a USB data cable."
            }
        }
        return devices.single()
    }

    fun hasPermission(device: UsbDevice) = usb.hasPermission(device)

    private fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                status = error.message ?: "USB operation failed."
            } finally { busy = false }
        }
    }

    fun fail(message: String) { status = message }

    fun requestingPermission(device: UsbDevice) {
        permissionDeviceId = device.deviceId
        status = "Waiting for Android USB permission."
    }

    fun permissionResult() {
        val pending = permissionDeviceId ?: return
        permissionDeviceId = null
        refreshUsb()
        val device = usb.deviceList.values.firstOrNull { it.deviceId == pending }
        // Verify the actual permission rather than relying solely on broadcast extras.
        if (device != null && usb.hasPermission(device)) identify(device)
        else fail("USB permission was denied.")
    }

    fun selectFile(uri: Uri) = task {
        firmware = null
        volume = null
        manualBootloaderType = null
        copyAttempted = false
        firmware = withContext(Dispatchers.IO) {
            val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: error("Cannot read the filename.")
            val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(result.size() + read <= LampUf2.MaxFileBytes) { "Invalid or oversized UF2 file." }
                    result.write(buffer, 0, read)
                }
                result.toByteArray()
            } ?: error("Cannot open the firmware file.")
            LocalFirmware(name, bytes, LampUf2.inspect(name, bytes))
        }
        status = "UF2 file checked. Identify the lamp over USB."
    }

    private fun <T> serial(device: UsbDevice, block: (UsbSerialPort) -> T): T {
        require(usb.hasPermission(device)) { "USB permission is required." }
        val connection = usb.openDevice(device) ?: error("Cannot open the USB device.")
        val port = CdcAcmSerialDriver(device).ports.firstOrNull() ?: error("USB serial is unavailable.")
        try {
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            return block(port)
        } finally {
            runCatching { port.close() }
            connection.close()
        }
    }

    private fun response(port: UsbSerialPort, command: String): String {
        port.write("$command\r\n".toByteArray(Charsets.US_ASCII), 1500)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        val deadline = android.os.SystemClock.elapsedRealtime() + 2500
        while (android.os.SystemClock.elapsedRealtime() < deadline && output.size() < 16384) {
            val read = port.read(buffer, 200)
            if (read > 0) output.write(buffer, 0, read)
        }
        return output.toString("UTF-8")
    }

    fun identify(device: UsbDevice) = task {
        status = "Reading lamp type and version over USB."
        report = ""
        lampType = null
        dfuLampType = null
        manualBootloaderType = null
        runtimeId = null
        require(device.lampIdentity().isLamp &&
            usb.deviceList.values.any { it.deviceId == device.deviceId }) { "USB lamp unavailable." }
        val result = withContext(Dispatchers.IO) {
            serial(device) { port ->
                // Allow the firmware loop to observe DTR before accepting its first command.
                Thread.sleep(300)
                response(port, "t") to response(port, "v")
            }
        }
        report = result.first + result.second
        lampType = parseUsbLampType(result.first) ?: error("The lamp did not report a supported type over USB.")
        runtimeId = device.deviceId
        status = "Lamp identified. Check the firmware variant before entering DFU."
    }

    fun enterDfu() = task {
        val selected = firmware ?: error("Choose a local UF2 file.")
        require(selected.metadata.variant == lampType) { "The firmware filename does not match the lamp type." }
        val device = runtimeDevice() ?: error("USB lamp unavailable.")
        require(device.deviceId == runtimeId) { "Identify the lamp again after reconnecting USB." }
        // USB detach can arrive before the DFU write returns. Preserve the verified type
        // before requesting the transition, independently of the live runtime connection.
        dfuLampType = lampType
        withContext(Dispatchers.IO) {
            serial(device) { port -> port.write("act dfu\r\n".toByteArray(Charsets.US_ASCII), 1500) }
        }
        runtimeId = null
        status = "DFU requested. Select the root of the bootloader USB volume."
    }

    private fun bootRoot(uri: Uri): DocumentFile {
        require(uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(uri)) {
            "Select a removable USB volume, not phone storage or a cloud folder."
        }
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val id = treeId.substringBefore(':')
        require(treeId == "$id:" && !id.equals("primary", true)) { "Select the root of the USB volume." }
        val storage = app.getSystemService(StorageManager::class.java)
        require(storage.storageVolumes.any { it.isRemovable && it.uuid.equals(id, true) }) {
            "The selected removable volume is unavailable."
        }
        require(detectUsb(usb.deviceList.values.map { it.lampIdentity() }) == UsbDetection.Bootloader) {
            "Connect exactly one Lamp-Da in USB bootloader mode."
        }
        val root = DocumentFile.fromTreeUri(app, uri) ?: error("USB volume unavailable.")
        val info = root.findFile("INFO_UF2.TXT") ?: error("INFO_UF2.TXT was not found on this volume.")
        val content = app.contentResolver.openInputStream(info.uri)?.use {
            val bytes = ByteArray(4097)
            val read = it.read(bytes)
            require(read in 1..4096) { "Invalid bootloader information." }
            String(bytes, 0, read, Charsets.UTF_8)
        } ?: error("Cannot read bootloader information.")
        LampUf2.validateBootInfo(content)
        require(root.canWrite()) { "The USB volume is read-only." }
        return root
    }

    fun selectVolume(uri: Uri) = task {
        volume = null
        manualBootloaderType = null
        val root = withContext(Dispatchers.IO) { bootRoot(uri) }
        volume = BootVolume(uri, root.name ?: "USB UF2")
        status = "USB volume checked. Ready for confirmation."
    }

    fun confirmBootloaderType(type: LampType) = task {
        val target = volume ?: error("Select and validate the bootloader USB volume.")
        require(firmware?.metadata?.variant == type) { "The firmware filename does not match the lamp type." }
        withContext(Dispatchers.IO) { bootRoot(target.uri) }
        manualBootloaderType = type
        status = "Lamp type confirmed manually for this bootloader session."
    }

    fun copyFirmware() = task {
        require(!copyAttempted) { "Verify the lamp before attempting another update." }
        val selected = firmware ?: error("Choose a local UF2 file.")
        val target = volume ?: error("Select the bootloader USB volume.")
        require(updateLampType == selected.metadata.variant) { "Identify the lamp and check its firmware variant first." }
        progress = 0f
        // A failed write can still have programmed part of the application. Never retry automatically.
        copyAttempted = true
        status = "Copying firmware. Do not disconnect USB."
        try {
            withContext(Dispatchers.IO) {
                val root = bootRoot(target.uri)
                require(root.findFile("UPDATE.UF2") == null) { "UPDATE.UF2 already exists. No file was overwritten." }
                val outputFile = root.createFile("application/octet-stream", "UPDATE.UF2")
                    ?: error("Cannot create UPDATE.UF2 on the USB volume.")
                require(outputFile.name.equals("UPDATE.UF2", true)) { "The USB provider changed the destination filename." }
                app.contentResolver.openOutputStream(outputFile.uri, "w")?.use { output ->
                    var offset = 0
                    while (offset < selected.bytes.size) {
                        val count = minOf(4096, selected.bytes.size - offset)
                        output.write(selected.bytes, offset, count)
                        offset += count
                        withContext(Dispatchers.Main) { progress = offset.toFloat() / selected.bytes.size }
                    }
                    output.flush()
                } ?: error("Cannot open the destination file.")
            }
            status = "Copy complete. Reconnect the lamp and read its version to verify the update."
        } catch (error: Exception) {
            status = "Update outcome uncertain. Do not retry automatically. Reconnect and read the lamp version."
            report = error.message.orEmpty()
        } finally {
            volume = null
            dfuLampType = null
            manualBootloaderType = null
        }
    }
}

internal fun parseUsbLampType(response: String): LampType? {
    val types = response.lineSequence().map { it.substringAfter('>').trim().lowercase() }.toSet()
    return when {
        "simple" in types && "indexable" !in types -> LampType.Simple
        "indexable" in types && "simple" !in types -> LampType.Rgb
        else -> null
    }
}
