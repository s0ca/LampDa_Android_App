package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class ControlSummariesTest {
    @Test fun durationSummaryShowsConfigurationNotAnActiveCountdown() {
        assertEquals("Sunset | 30 min", sunTimerSummary(null, 0, SunTimerKind.Sunset, 30))
        assertEquals("Sunrise | 5 min", sunTimerSummary(null, 0, SunTimerKind.Sunrise, 5))
    }

    @Test fun clockSummaryShowsTheChosenDeadline() {
        assertEquals("Sunrise | Ends at 07:05",
            sunTimerSummary(null, 0, SunTimerKind.Sunrise, 30, 7 to 5))
    }

    @Test fun runningSummaryUsesActiveTimerNotTheNextConfiguration() {
        assertEquals("Sunset 00:12:34",
            sunTimerSummary(SunTimerKind.Sunset, 754, SunTimerKind.Sunrise, 5, 7 to 30))
    }

    @Test fun noTimerKindCannotTurnAStaleNumberIntoARunningTimer() {
        assertEquals("Sunrise | 5 min", sunTimerSummary(null, 754, SunTimerKind.Sunrise, 5))
        assertEquals("Sunset | 30 min", sunTimerSummary(SunTimerKind.Sunset, 0, SunTimerKind.Sunset, 30))
    }

    @Test fun countdownHandlesHoursAndNeverDisplaysNegativeValues() {
        assertEquals("00:00:00", timerRemainingText(-1))
        assertEquals("00:00:59", timerRemainingText(59))
        assertEquals("01:01:01", timerRemainingText(3661))
        assertEquals("24:00:00", timerRemainingText(86400))
    }
}
