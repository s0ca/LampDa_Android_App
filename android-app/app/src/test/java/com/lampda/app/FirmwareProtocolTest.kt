package com.lampda.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirmwareProtocolTest {
    @Test fun stateRefreshWaitsForStatusAndBothDistinctTimers() {
        val refresh = LampStateRefresh()
        assertEquals(false, refresh.receiveTimer(SunTimerKind.Sunrise))
        assertEquals(true, refresh.receiveStatus())
        assertEquals(false, refresh.receiveStatus())
        assertEquals(false, refresh.receiveTimer(SunTimerKind.Sunset))
        assertEquals(false, refresh.receiveTimer(SunTimerKind.Sunset))
        assertEquals(true, refresh.receiveTimer(SunTimerKind.Sunrise))
    }
    @Test fun recognizesSunriseInManuallyRequestedFirmwareHelp() {
        assertEquals(true, Firmware19.advertisesSunrise("58420> set sunrise [hour] [minute]: Set sunrise"))
        assertEquals(false, Firmware19.advertisesSunrise("set sunrise_min [minutes]"))
        assertEquals(false, Firmware19.advertisesSunrise("Refusing sunrise command"))
    }

    @Test fun connectionOnlyRequestsTheCompactLampTypeNotHelpOrDiagnostics() {
        assertEquals(listOf("t", "sys ble_name"), Firmware19.connectionQueries)
    }

    @Test fun mapsAllSixSimpleModesToOfficialGroups() {
        assertEquals(listOf("set mode 0 0", "set mode 1 0", "set mode 1 1",
            "set mode 2 0", "set mode 2 1", "set mode 2 2"), LampType.Simple.availableModes().map(Firmware19::mode))
    }

    @Test fun routesDiagnosticsWithoutChangingManualCommands() {
        assertEquals("sys bat", Firmware19.command("bat"))
        assertEquals("debug pd", Firmware19.command("PD"))
        assertEquals("sys pd", Firmware19.command("sys pd"))
        assertEquals("v", Firmware19.command("v"))
        assertEquals("set mode 2 1", Firmware19.command("set mode 2 1"))
    }

    @Test fun usesFullResolutionUartValuesAndClampsBounds() {
        assertEquals("set brgt 513", Firmware19.brightness(513))
        assertEquals("set brgt 0", Firmware19.brightness(-1))
        assertEquals("set brgt 1024", Firmware19.brightness(2000))
        assertEquals("set ramp 129", Firmware19.ramp(129))
        assertEquals("set ramp 255", Firmware19.ramp(300))
        assertEquals("set on 1", Firmware19.power(true))
        assertEquals("set on 0", Firmware19.power(false))
    }

    @Test fun addressesNativeGroupsAndBluetoothEffectsWithoutElkFrames() {
        assertEquals("set mode 0 0", Firmware19.mode(LampMode.PaletteWarm))
        assertEquals("set mode 1 11", Firmware19.mode(LampMode.Sierpinski))
        assertEquals("set mode 3 1", Firmware19.mode(LampMode.Spectrum))
        assertEquals("set mode 4 3", Firmware19.mode(LampMode.NudzBeer))
        assertEquals("set mode 5 1", Firmware19.mode(LampMode.RgbRed))
        assertEquals("set mode 5 29", Firmware19.mode(LampMode.RgbStrobeWhite))
        assertEquals("set color 255 128 0", Firmware19.color(RgbColor(255, 128, 0)))
    }

    @Test fun favoritesUseZeroBasedEightSlotIndices() {
        assertEquals("set favorite 0", Firmware19.favorite(0, false))
        assertEquals("set new_favorite 7", Firmware19.favorite(7, true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedFavoriteSlot() { Firmware19.favorite(8, true) }

    @Test fun supportsRenamingWithoutRequiringAnElkPrefix() {
        assertEquals("set ble_name My-Lamp-2", Firmware19.rename("My-Lamp-2"))
    }

    @Test fun paleRgbColorsRetainAllThreeFullResolutionChannels() {
        val line = Firmware19.color(RgbColor(229, 255, 217))
        assertEquals("set color 229 255 217", line)
        assertEquals(22, (line + "\n").toByteArray(Charsets.UTF_8).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCommandInjectionInName() { Firmware19.rename("Lamp\nact dfu") }

    @Test fun removesOnlyTheFirmwareTimestamp() {
        assertEquals("hardware:1.0", firmwareLine("1204> hardware:1.0"))
        assertEquals("battery level:98.20%", firmwareLine("battery level:98.20%"))
        assertEquals("BEHAVIOR_S_MACH > switched to state OUTPUT_LIGHT",
            firmwareLine("1205> BEHAVIOR_S_MACH > switched to state OUTPUT_LIGHT"))
    }

    @Test fun readsOutputSnapshotsWithoutInventingColorOrModeWhenOff() {
        assertEquals(FirmwareFeedback.OutputOff, firmwareFeedback("123> \$stat:OFF"))
        assertEquals(FirmwareFeedback.OutputStatus(5, 29, 255, 1024),
            firmwareFeedback("\$stat:g5:m29:r255:F brgt:1024"))
        assertEquals(FirmwareFeedback.OutputStatus(0, 0, 0, 0),
            firmwareFeedback("\$stat:g0:m0:r0:X brgt:0"))
        assertNull(firmwareFeedback("\$stat:g5:m29:r256:X brgt:1024"))
        assertNull(firmwareFeedback("\$stat:g5:m29:r255:X brgt:1025"))
        assertNull(firmwareFeedback("\$stat:g5:m29:r255:X"))
    }

    @Test fun readsTimerStatusAndRecognizesUpdatedRefusal() {
        assertEquals(SunTimerStatus(SunTimerKind.Sunset, 125), sunTimerStatus("120> sunset: enabled, ends in 2m 5s"))
        assertEquals(SunTimerStatus(SunTimerKind.Sunrise, null), sunTimerStatus("sunrise: disabled"))
        assertNull(sunTimerStatus("sunset: enabled, ends in 2m 60s"))
        assertEquals(SunTimerReply.Refused, sunTimerReply("timer end time is less than current time: 5", SunTimerKind.Sunset))
    }

    @Test fun onlyExplicitStopRepliesConfirmCancellation() {
        assertEquals("set sun_cancel", Firmware19.SunTimerStop)
        assertEquals(true, sunTimerStopConfirmed("123> Sunset timer canceled"))
        assertEquals(true, sunTimerStopConfirmed("Sunrise timer canceled"))
        assertEquals(true, sunTimerStopConfirmed("No sun timer is running"))
        assertEquals(false, sunTimerStopConfirmed("sunset: disabled"))
        assertEquals(false, sunTimerStopConfirmed("unknown command: 'sun_cancel'"))
    }
}
