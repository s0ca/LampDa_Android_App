package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class ScanSupportTest {
    @Test fun knownLampsComeFirstWithoutHidingAllDevicesOnFirstUse() {
        assertEquals(listOf(ScanListFilter.Known, ScanListFilter.All), ScanListFilter.entries)
        assertEquals(ScanListFilter.Known, defaultScanFilter(true))
        assertEquals(ScanListFilter.All, defaultScanFilter(false))
    }
    @Test fun ranksStrongestFirstButDoesNotReorderOnEveryAdvertisement() {
        val ranking = ScanRanking()
        assertEquals(listOf("A", "B"), ranking.update(listOf(ScanStrength("A", -50.0), ScanStrength("B", -70.0)), 0))
        val changed = listOf(ScanStrength("A", -80.0), ScanStrength("B", -40.0))
        assertEquals(listOf("A", "B"), ranking.update(changed, 500))
        assertEquals(listOf("A", "B"), ranking.update(changed, 2999))
        assertEquals(listOf("B", "A"), ranking.update(changed, 3000))
    }

    @Test fun appendsNewDiscoveriesUntilTheNextRankingAndDropsAbsentDevices() {
        val ranking = ScanRanking()
        ranking.update(listOf(ScanStrength("A", -70.0)), 0)
        val updated = listOf(ScanStrength("A", -70.0), ScanStrength("B", -40.0))
        assertEquals(listOf("A", "B"), ranking.update(updated, 500))
        assertEquals(listOf("B", "A"), ranking.update(updated, 3000))
        assertEquals(listOf("A"), ranking.update(updated.take(1), 3100))
    }

    @Test fun equalStrengthsKeepThePreviousRankingAndStoppingCanForceAFinalSort() {
        val ranking = ScanRanking()
        ranking.update(listOf(ScanStrength("B", -40.0), ScanStrength("A", -60.0)), 0)
        assertEquals(listOf("B", "A"), ranking.update(listOf(ScanStrength("A", -50.0), ScanStrength("B", -50.0)), 3000))
        assertEquals(listOf("A", "B"), ranking.update(listOf(ScanStrength("A", -40.0), ScanStrength("B", -70.0)), 3100, force = true))
    }

    @Test fun smoothingDependsOnElapsedTimeNotTheAdvertisementRate() {
        val oneUpdate = smoothScanRssi(-80.0, -40, 2000)
        var manyUpdates = -80.0
        repeat(100) { manyUpdates = smoothScanRssi(manyUpdates, -40, 20) }
        assertEquals(oneUpdate, manyUpdates, 0.000001)
        assertTrue(oneUpdate > -80.0 && oneUpdate < -40.0)
        assertEquals(-80.0, smoothScanRssi(-80.0, -40, 0), 0.0)
        assertEquals(-80.0, smoothScanRssi(-80.0, -40, -1), 0.0)
    }

    @Test fun defaultListIncludesEverythingAndKnownFilterOnlyUsesRememberedIdentity() {
        assertTrue(ScanListFilter.All.accepts(false))
        assertTrue(ScanListFilter.All.accepts(true))
        assertFalse(ScanListFilter.Known.accepts(false))
        assertTrue(ScanListFilter.Known.accepts(true))
    }

    @Test fun renamedKnownLampUsesItsNewAdvertisedName() {
        val original = mergeScanIdentity(null, listOf("ELK-BLE-Lampda-1234"), "", emptyList(), knownLamp = true)
        val renamed = mergeScanIdentity(original, listOf("Desk-light", "Old cached name"), "", emptyList(), knownLamp = true)
        assertTrue(renamed.isLamp)
        assertEquals("Desk-light", renamed.name)
        assertEquals(renamed, mergeScanIdentity(renamed, emptyList(), "", emptyList(), knownLamp = true))
    }

    @Test fun acceptsLampNamesAcrossSeparatorsAndCase() {
        listOf("ELK-BLE-Lampda-74A0-6A6F", "LAMP-DA RGB", "lamp_da_simple").forEach { name ->
            assertTrue(mergeScanIdentity(null, listOf(name), "", emptyList()).isLamp)
        }
    }

    @Test fun recognizesRenamedLampsByAdvertisedService() {
        assertTrue(mergeScanIdentity(null, listOf("My light"), "", listOf(ElkServiceUuid)).isLamp)
        assertTrue(mergeScanIdentity(null, emptyList(), "", listOf(UartServiceUuid)).isLamp)
    }

    @Test fun keepsRecognitionAndNameAfterAnAnonymousPacket() {
        val first = mergeScanIdentity(null, listOf("ELK-BLE-Lampda-1234"), "", emptyList())
        val next = mergeScanIdentity(first, emptyList(), "", emptyList())
        assertEquals(first, next)
    }

    @Test fun upgradesAnAnonymousDeviceWhenItsNameArrives() {
        val first = mergeScanIdentity(null, emptyList(), "", emptyList())
        assertFalse(first.isLamp)
        val next = mergeScanIdentity(first, listOf("LampDa"), "", emptyList())
        assertTrue(next.isLamp)
        assertEquals("LampDa", next.name)
    }

    @Test fun doesNotClassifyUnrelatedAdvertisementsAsLamps() {
        assertFalse(mergeScanIdentity(null, listOf("Headphones"), "", emptyList()).isLamp)
        assertTrue(mergeScanIdentity(null, emptyList(), "ELK-BLE-Lampda-1234", emptyList()).isLamp)
    }

    @Test fun remembersPreviouslyValidatedLampWithoutNameOrAdvertisedService() {
        assertTrue(mergeScanIdentity(null, listOf("Desk light"), "", emptyList(), knownLamp = true).isLamp)
        assertFalse(mergeScanIdentity(null, listOf("Desk light"), "", emptyList()).isLamp)
    }
}
