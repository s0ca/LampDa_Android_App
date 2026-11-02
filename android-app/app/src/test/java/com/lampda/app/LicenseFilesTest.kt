package com.lampda.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseFilesTest {
    private val root = listOf(File("../.."), File("..")).first {
        File(it, "licenses/THIRD_PARTY_NOTICES.txt").isFile
    }

    @Test fun applicationLicenseIsAttributedAndBundledWithoutChanges() {
        val license = File(root, "LICENSE").readText()
        assertTrue(license.startsWith("MIT License"))
        assertTrue(license.contains("Copyright (c) 2026 s0ca"))
        assertEquals(license, File(root, "licenses/App-MIT.txt").readText())
    }

    @Test fun thirdPartyLicenseTextsArePresent() {
        val apache = File(root, "licenses/Apache-2.0.txt").readText()
        assertTrue(apache.contains("Version 2.0, January 2004"))
        assertTrue(apache.contains("END OF TERMS AND CONDITIONS"))
        val usb = File(root, "licenses/usb-serial-for-android-MIT.txt").readText()
        assertTrue(usb.contains("Copyright (c) 2013 Mike Wakerly"))
        assertTrue(File(root, "tools/legal/Gradle-LICENSE.txt").isFile)
        assertTrue(File(root, "tools/legal/Gradle-NOTICE.txt").isFile)
    }
}
