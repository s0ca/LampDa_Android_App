package com.lampda.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirmwareProtocolTest {
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
        assertEquals("sys pd", Firmware19.command("PD"))
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
}
