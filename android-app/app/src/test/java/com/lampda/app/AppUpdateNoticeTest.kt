package com.lampda.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateNoticeTest {
    @Test fun comparesVersionsNumerically() {
        assertEquals("v0.4.10", newerStableReleaseTag("0.4.9", "v0.4.10"))
        assertEquals("v1.0.0", newerStableReleaseTag("0.9.9", "v1.0.0"))
    }

    @Test fun ignoresSameAndOlderReleases() {
        assertNull(newerStableReleaseTag("0.4.0", "v0.4.0"))
        assertNull(newerStableReleaseTag("0.10.0", "v0.9.9"))
    }

    @Test fun ignoresPrereleasesAndMalformedTags() {
        assertNull(newerStableReleaseTag("0.4.0", "v0.5.0-beta.1"))
        assertNull(newerStableReleaseTag("0.4.0-beta.12", "v0.4.0"))
        assertNull(newerStableReleaseTag("0.4.0", "v0.5.0/other"))
        assertNull(newerStableReleaseTag("0.4.0", "v999999999999.0.0"))
    }
}
