package com.lampda.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialFirmwareDownloadTest {
    @Test fun selectsOnlyTheStandardApplicationVariants() {
        assertEquals("simple_1_10.uf2", officialFirmwareName("v1.10", LampType.Simple))
        assertEquals("indexable_1_10.uf2", officialFirmwareName("v1.10", LampType.Rgb))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedTags() {
        officialFirmwareName("v1.10/other", LampType.Simple)
    }

    @Test fun checksumMustMatchTheExactFilenameAndBeUnique() {
        val hash = "a".repeat(64)
        val name = "indexable_1_10.uf2"
        val line = "${hash.uppercase()}  $name"
        assertEquals(hash, officialFirmwareChecksum("$line\n$hash  indexable_hd_1_10.uf2", name))
        assertNull(officialFirmwareChecksum("$hash  indexable_hd_1_10.uf2", name))
        assertNull(officialFirmwareChecksum("$line\n$line", name))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDownloads() {
        ByteArray(17).inputStream().readFirmwareBytes(16)
    }
}
