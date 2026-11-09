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

internal enum class UartCategory(val label: String) { General("General"), Sys("sys"), Set("set"), Act("act"), Debug("debug") }

internal data class UartShortcut(val label: String, val command: String, val template: Boolean = false) {
    val category: UartCategory get() = when (command.substringBefore(' ')) {
        "sys" -> UartCategory.Sys
        "set" -> UartCategory.Set
        "act" -> UartCategory.Act
        "debug" -> UartCategory.Debug
        else -> UartCategory.General
    }
}

internal fun uartShortcuts(type: LampType, sunrise: Boolean, diagnostics: Set<String>): List<UartShortcut> = buildList {
    addAll(listOf(
        UartShortcut("Help", "h"), UartShortcut("Control commands", "set h"),
        UartShortcut("System commands", "sys h"), UartShortcut("Action commands", "act h"),
        UartShortcut("Debug commands", "debug h"),
        UartShortcut("Summary", "sum"), UartShortcut("Version", "v"),
        UartShortcut("Lamp type", "t"), UartShortcut("Serial number", "id"),
        UartShortcut("Battery", "sys bat"), UartShortcut("Bluetooth", "debug ble"),
        UartShortcut("Bluetooth name", "sys ble_name"),
        UartShortcut("States", "debug states"), UartShortcut("Clock", "sys time"),
        UartShortcut("Charger", "debug cinfo"), UartShortcut("Alerts", "sys alerts"),
        UartShortcut("I2C", "debug i2c"), UartShortcut("USB power", "debug pd"),
        UartShortcut("Statistics", "debug stats"), UartShortcut("Tasks", "debug tasks"),
        UartShortcut("Serial ports", "debug serial"), UartShortcut("ADC measurements", "debug adc"),
        UartShortcut("Output status", Firmware19.StatusQuery),
        UartShortcut("Sun timers", "sys sun"),
        UartShortcut("Battery level flashes", "sys bat_blink"),
        UartShortcut("Button bindings", "sys bindings"),
        UartShortcut("Indicator", "debug ind"),
        UartShortcut("Microphone sensitivity", "sys mic"),
    ))
    diagnostics.filter { it in setOf("sys recovery", "sys rtrace", "debug recovery", "debug rtrace") }.sorted().forEach {
        add(UartShortcut(if (it.endsWith("recovery")) "Recovery diagnostics" else "Startup trace", it))
    }
    addAll(listOf(
        UartShortcut("Power on", "set on 1", true), UartShortcut("Power off", "set on 0", true),
        UartShortcut("Brightness", "set brgt 512", true), UartShortcut("Effect parameter", "set ramp 128", true),
        UartShortcut("Mode", "set mode 0 0", true),
        UartShortcut("Sync time", clockCommand("time", java.util.Calendar.getInstance()), true),
        UartShortcut("Sunset duration", "set sunset_min 5", true),
        UartShortcut("Sunset end time", "set sunset 22 0 0 0", true),
        UartShortcut("Bluetooth name", "set ble_name Lamp-Da", true),
        UartShortcut("Cancel sun timer", Firmware19.SunTimerStop, true),
        UartShortcut("Battery level flashes", "set bat_blink 1", true),
        UartShortcut("Indicator brightness", "set ind_lvl 1", true),
        UartShortcut("Indicator color", "set ind_color 255 128 0", true),
        UartShortcut("Restore indicator color", "set ind_color", true),
        UartShortcut("Microphone sensitivity", "set mic_sens 50", true),
        UartShortcut("Button bindings", "set bind nav 3", true),
        UartShortcut("Reset button bindings", "set bind_reset", true),
    ))
    if (type == LampType.Rgb) addAll(listOf(
        UartShortcut("RGB color", "set color 255 128 0", true),
        UartShortcut("Recall favorite", "set favorite 0", true),
        UartShortcut("Save favorite", "set new_favorite 0", true),
        UartShortcut("Lamp favorites", "sys fav"),
    ))
    if (sunrise) add(UartShortcut("Sunrise end time", "set sunrise 7 0 0 0", true))
}

private val DiagnosticHelp = Regex("""((?:sys|debug) (?:recovery|rtrace))(?::.*)?""")

internal fun advertisedDiagnostic(line: String): String? =
    DiagnosticHelp.matchEntire(firmwareLine(line))?.groupValues?.get(1)
