package com.lampda.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateNoticeTest {
    @Test fun comparesVersionsNumerically() {
        assertEquals(AppUpdateResult.Available("v0.4.10"), compareStableRelease("0.4.9", "v0.4.10"))
        assertEquals(AppUpdateResult.Available("v1.0.0"), compareStableRelease("0.9.9", "v1.0.0"))
    }

    @Test fun ignoresSameAndOlderReleases() {
        assertEquals(AppUpdateResult.UpToDate, compareStableRelease("0.4.0", "v0.4.0"))
        assertEquals(AppUpdateResult.UpToDate, compareStableRelease("0.10.0", "v0.9.9"))
    }

    @Test fun ignoresPrereleasesAndMalformedTags() {
        assertEquals(AppUpdateResult.Unavailable, compareStableRelease("0.4.0", "v0.5.0-beta.1"))
        assertEquals(AppUpdateResult.Unavailable, compareStableRelease("0.4.0-beta.12", "v0.4.0"))
        assertEquals(AppUpdateResult.Unavailable, compareStableRelease("0.4.0", "v0.5.0/other"))
        assertEquals(AppUpdateResult.Unavailable, compareStableRelease("0.4.0", "v999999999999.0.0"))
    }
}
