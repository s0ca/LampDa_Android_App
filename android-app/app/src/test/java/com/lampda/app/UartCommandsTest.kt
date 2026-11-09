package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class UartCommandsTest {
    @Test fun categoriesPartitionCommandsWithoutLosingAnyEntry() {
        val commands = uartShortcuts(LampType.Rgb, true, setOf("debug recovery", "debug rtrace"))
        assertEquals(commands.toSet(), UartCategory.entries.flatMap { group ->
            commands.filter { it.category == group }
        }.toSet())
        assertEquals(UartCategory.entries.toSet(), commands.map { it.category }.toSet())
        assertTrue(commands.filter { it.category == UartCategory.Sys }.all { it.command.startsWith("sys ") })
        assertTrue(commands.filter { it.category == UartCategory.Set }.all { it.command.startsWith("set ") })
        assertEquals(listOf("act h"), commands.filter { it.category == UartCategory.Act }.map { it.command })
    }

    @Test fun queriesUseOfficialNamesAndNeverRunActions() {
        val queries = uartShortcuts(LampType.Simple, false, emptySet()).filterNot { it.template }.map { it.command }
        assertTrue(queries.containsAll(listOf("h", "set h", "sys h", "act h", "debug h", "sum", "t", "id",
            "sys bat", "debug adc", "sys status", "sys sun")))
        assertTrue(queries.none { it.startsWith("set ") && it != "set h" })
        assertEquals(listOf("act h"), queries.filter { it.startsWith("act ") })
    }

    @Test fun editsAreTemplatesAndRgbOptionsStayOnRgb() {
        val simple = uartShortcuts(LampType.Simple, false, emptySet())
        assertTrue(simple.first { it.command == "set brgt 512" }.template)
        assertTrue(simple.first { it.command == "set sun_cancel" }.template)
        assertTrue(simple.first { it.command == "set bind_reset" }.template)
        assertTrue(simple.none { it.command.startsWith("set favorite ") || it.command.startsWith("set color ") })
        val rgb = uartShortcuts(LampType.Rgb, false, emptySet())
        assertTrue(rgb.first { it.command == "set new_favorite 0" }.template)
        assertTrue(rgb.first { it.command == "set color 255 128 0" }.template)
    }

    @Test fun optionalCommandsOnlyAppearWhenAdvertised() {
        assertTrue(uartShortcuts(LampType.Simple, false, emptySet()).none {
            it.command.contains("sunrise") || it.command.contains("rtrace")
        })
        val supported = uartShortcuts(LampType.Simple, true, setOf("debug rtrace", "sys recovery", "debug format"))
        assertTrue(supported.any { it.command == "debug rtrace" && !it.template })
        assertTrue(supported.any { it.command == "sys recovery" && !it.template })
        assertTrue(supported.any { it.command.startsWith("set sunrise ") && it.template })
        assertTrue(supported.none { it.command == "debug format" })
    }

    @Test fun readsDiagnosticAvailabilityFromHelpNotFromLogs() {
        assertEquals("debug rtrace", advertisedDiagnostic("235> debug rtrace"))
        assertEquals("sys recovery", advertisedDiagnostic("sys recovery: recovery diagnostics"))
        assertNull(advertisedDiagnostic("trace boot_ms=23 stage=charger_probe result=2"))
    }
}
