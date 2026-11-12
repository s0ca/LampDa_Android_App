package com.lampda.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbDetectionTest {
    private val lamp = UsbIdentity(0x1209, 0x7A05, setOf(2, 10))
    private val bootloader = UsbIdentity(0x1209, 0x8A05, setOf(2, 10, 8))
    private val other = UsbIdentity(0x239A, 0x8029)

    @Test fun emptyBusIsNotAPermissionError() {
        assertEquals(UsbDetection.Empty, detectUsb(emptyList()))
    }

    @Test fun recognizesRuntimeBesideUnrelatedDevice() {
        assertEquals(UsbDetection.Runtime, detectUsb(listOf(other, lamp)))
    }

    @Test fun bootloaderCannotAnswerRuntimeCommands() {
        assertEquals(UsbDetection.Bootloader, detectUsb(listOf(bootloader)))
    }

    @Test fun screenshotBootloaderReusesRuntimePidWithMassStorageInterface() {
        val actualBootloader = UsbIdentity(0x1209, 0x7A05, setOf(2, 10, 8))
        assertEquals(UsbDetection.Bootloader, detectUsb(listOf(actualBootloader)))
        org.junit.Assert.assertFalse(actualBootloader.isLamp)
    }

    @Test fun vendorAndMassStorageAloneDoNotIdentifyALamp() {
        assertEquals(UsbDetection.Other, detectUsb(listOf(UsbIdentity(0x1209, 0x9999, setOf(8)))))
        assertEquals(UsbDetection.Other, detectUsb(listOf(UsbIdentity(0x239A, 0x7A05, setOf(2, 10, 8)))))
        assertEquals(UsbDetection.Other, detectUsb(listOf(UsbIdentity(0x1209, 0x7A05))))
    }

    @Test fun refusesUnknownIdentifiersEvenWithMatchingProductId() {
        assertEquals(UsbDetection.Other, detectUsb(listOf(other, UsbIdentity(0x239A, 0x7A05))))
    }

    @Test fun refusesMultipleLampTargets() {
        assertEquals(UsbDetection.MultipleLamps, detectUsb(listOf(lamp, lamp)))
        assertEquals(UsbDetection.MultipleLamps, detectUsb(listOf(lamp, bootloader)))
    }

    @Test fun assistedInstallRequiresTheSameSingleBootloader() {
        assertEquals(true, isExpectedUsbBootloader(mapOf(12 to bootloader, 8 to other), 12))
        assertEquals(false, isExpectedUsbBootloader(mapOf(13 to bootloader), 12))
        assertEquals(false, isExpectedUsbBootloader(mapOf(12 to bootloader, 13 to lamp), 12))
        assertEquals(false, isExpectedUsbBootloader(mapOf(12 to lamp, 13 to other), 12))
    }

    @Test fun installAcceptsTheVerifiedTypeRetainedThroughDfu() {
        assertNull(usbInstallBlocker(false, LampType.Simple, true, true, LampType.Simple, false))
        assertNull(usbInstallBlocker(false, LampType.Rgb, true, true, LampType.Rgb, false))
    }

    @Test fun aMountedVolumeAloneDoesNotAuthorizeAnUpdate() {
        assertEquals("Confirm the lamp type for this bootloader session.",
            usbInstallBlocker(false, LampType.Simple, true, true, null, false))
    }

    @Test fun wrongVariantAndUnconfirmedFilesStayBlocked() {
        assertEquals("The firmware filename does not match the lamp type.",
            usbInstallBlocker(false, LampType.Rgb, true, true, LampType.Simple, false))
        assertEquals("Confirm the firmware source and variant.",
            usbInstallBlocker(false, LampType.Simple, false, true, LampType.Simple, false))
    }

    @Test fun missingVolumeAndPreviousCopyStayBlocked() {
        assertEquals("Select and validate the bootloader USB volume.",
            usbInstallBlocker(false, LampType.Simple, true, false, LampType.Simple, false))
        assertEquals("Verify the lamp before attempting another update.",
            usbInstallBlocker(false, LampType.Simple, true, true, LampType.Simple, true))
    }
}
