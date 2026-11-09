package com.lampda.app

internal enum class UartLogFilter(val label: String) {
    All("All"), Rx("RX"), Tx("TX");

    fun includes(line: String): Boolean = when (this) {
        All -> true
        Rx -> line.startsWith("RX ")
        Tx -> line.startsWith("TX ")
    }

    companion object {
        fun fromStored(name: String?): UartLogFilter = entries.firstOrNull { it.name == name } ?: All
    }
}

internal enum class UartCategory(val label: String) { General("General"), Sys("sys"), Set("set"), Act("act") }

internal data class UartShortcut(val label: String, val command: String, val template: Boolean = false) {
    val category: UartCategory get() = when (command.substringBefore(' ')) {
        "sys" -> UartCategory.Sys
        "set" -> UartCategory.Set
        "act" -> UartCategory.Act
        else -> UartCategory.General
    }
}

internal fun uartShortcuts(type: LampType, sunrise: Boolean, diagnostics: Set<String>): List<UartShortcut> = buildList {
    addAll(listOf(
        UartShortcut("Help", "h"), UartShortcut("Control commands", "set h"),
        UartShortcut("System commands", "sys h"), UartShortcut("Action commands", "act h"),
        UartShortcut("Summary", "sum"), UartShortcut("Version", "v"),
        UartShortcut("Lamp type", "t"), UartShortcut("Serial number", "id"),
        UartShortcut("Battery", "sys bat"), UartShortcut("Bluetooth", "sys ble"),
        UartShortcut("Bluetooth name", "sys ble_name"),
        UartShortcut("States", "sys states"), UartShortcut("Clock", "sys time"),
        UartShortcut("Charger", "sys cinfo"), UartShortcut("Alerts", "sys alerts"),
        UartShortcut("I2C", "sys i2c"), UartShortcut("USB power", "sys pd"),
        UartShortcut("Statistics", "sys stats"), UartShortcut("Tasks", "sys tasks"),
        UartShortcut("Serial ports", "sys serial"), UartShortcut("ADC measurements", "sys adc"),
    ))
    diagnostics.filter { it in setOf("recovery", "rtrace") }.sorted().forEach {
        add(UartShortcut(if (it == "recovery") "Recovery diagnostics" else "Startup trace", "sys $it"))
    }
    addAll(listOf(
        UartShortcut("Power on", "set on 1", true), UartShortcut("Power off", "set on 0", true),
        UartShortcut("Brightness", "set brgt 512", true), UartShortcut("Effect parameter", "set ramp 128", true),
        UartShortcut("Mode", "set mode 0 0", true),
        UartShortcut("Sync time", clockCommand("time", java.util.Calendar.getInstance()), true),
        UartShortcut("Sunset duration", "set sunset_min 5", true),
        UartShortcut("Sunset end time", "set sunset 22 0 0 0", true),
        UartShortcut("Bluetooth name", "set ble_name Lamp-Da", true),
    ))
    if (type == LampType.Rgb) addAll(listOf(
        UartShortcut("RGB color", "set color 255 128 0", true),
        UartShortcut("Recall favorite", "set favorite 0", true),
        UartShortcut("Save favorite", "set new_favorite 0", true),
    ))
    if (sunrise) add(UartShortcut("Sunrise end time", "set sunrise 7 0 0 0", true))
}

internal fun advertisedDiagnostic(line: String): String? = when {
    firmwareLine(line).startsWith("sys recovery:") -> "recovery"
    firmwareLine(line).startsWith("sys rtrace:") -> "rtrace"
    else -> null
}
