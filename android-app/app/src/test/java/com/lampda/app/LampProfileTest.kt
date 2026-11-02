package com.lampda.app

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.*
import org.junit.Test

class LampProfileTest {
    @Test fun rgbProfileHasACompactLabel() {
        assertEquals("RGB", LampType.Rgb.label)
    }
    @Test fun rgbPagesFollowNativeGroupsBeforeBluetoothOnlyEffects() {
        val modes = LampType.Rgb.availableModes()
        assertEquals(listOf("Palettes", "Calm", "Party", "Sound", "Images", "Color effects"),
            modes.map { it.category }.distinct())
        assertEquals(LampMode.PaletteWarm, modes.first())
        assertTrue(modes.indexOf(LampMode.VuMeter) < modes.indexOf(LampMode.RgbCustom))
    }
    @Test fun official19SimpleOnlyExposesStrobeSpeed() {
        assertEquals(listOf(LampMode.Strobe), LampType.Simple.availableModes().filter { it.supportsSpeed })
        assertEquals("Speed", LampMode.Strobe.rampLabel)
        assertEquals("set ramp 128", Firmware19.ramp(128))
    }

    @Test fun profilesExposeSeparateCatalogs() {
        assertEquals(6, LampType.Simple.availableModes().size)
        assertEquals(54, LampType.Rgb.availableModes().size)
        assertTrue(LampType.Simple.availableModes().none { it.lampType == LampType.Rgb })
        assertEquals(6, LampType.Rgb.availableModes().map { it.category }.distinct().size)
        assertEquals(54, LampType.Rgb.availableModes().map { it.groupId to it.modeId }.distinct().size)
        assertFalse(LampMode.RgbCustom.supportsSpeed)
        assertTrue(LampMode.RgbFadeSeven.supportsSpeed)
    }

    @Test fun decodesTheFirmwareTypeWithoutMatchingArbitraryLogs() {
        assertEquals(LampType.Rgb, reportedLampType("928> indexable"))
        assertEquals(LampType.Simple, reportedLampType("simple"))
        assertNull(reportedLampType("Unsupported simple mode"))
        assertNull(reportedLampType("cct"))
    }

    @Test fun distinguishesPaletteControlsFromSpeed() {
        assertEquals("Color temperature", LampMode.PaletteWarm.rampLabel)
        assertEquals("Palette", LampMode.PaletteRainbow.rampLabel)
        assertEquals("Speed", LampMode.RgbFadeSeven.rampLabel)
    }

    @Test fun validatesHexInsteadOfSilentlyClampingBadInput() {
        assertEquals(RgbColor(255, 128, 0), RgbColor.fromHex("#ff8000"))
        assertEquals("#FF8000", RgbColor(255, 128, 0).hex)
        assertNull(RgbColor.fromHex("#GG0000"))
        assertNull(RgbColor.fromHex("#FFF"))
    }

    @Test fun schedulesSunsetAcrossMidnightUsingZeroBasedWeekdays() {
        val now = GregorianCalendar(2026, Calendar.OCTOBER, 24, 23, 59, 0)
        val target = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 2) }
        assertEquals("set time 23 59 0 6", clockCommand("time", now))
        assertEquals("set sunset 0 1 0 0", clockCommand("sunset", target))
    }
}
