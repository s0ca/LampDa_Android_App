package com.lampda.app

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.*
import org.junit.Test

class SunTimerTest {
    @Test fun sunriseUsesItsOwnNativeCommand() {
        val target = GregorianCalendar(2026, Calendar.OCTOBER, 25, 7, 30, 0)
        assertEquals("set sunrise 7 30 0 0", clockCommand(SunTimerKind.Sunrise.command, target))
        assertEquals("set sunset 7 30 0 0", clockCommand(SunTimerKind.Sunset.command, target))
    }

    @Test fun clockScheduleChoosesTheNextOccurrenceAndDoesNotMutateNow() {
        val now = GregorianCalendar(2026, Calendar.OCTOBER, 24, 23, 59, 30)
        val target = nextSunTimerTime(now, 7, 30)
        assertEquals(25, target.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, target.get(Calendar.MINUTE))
        assertEquals(0, target.get(Calendar.SECOND))
        assertEquals(24, now.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun futureClockTimeStaysOnTheSameDay() {
        val now = GregorianCalendar(2026, Calendar.OCTOBER, 23, 12, 0, 0)
        val target = nextSunTimerTime(now, 14, 0)
        assertEquals(23, target.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun countdownRequiresAnActualTimerConfirmation() {
        assertNull(sunTimerReply("set time 0 7h 30m 0s (validity: 1)", SunTimerKind.Sunrise))
        assertNull(sunTimerReply("timer will finish on 0 7h 30m 0s", SunTimerKind.Sunrise))
        assertEquals(SunTimerReply.Accepted,
            sunTimerReply("1203> sun timer set to 30 minutes", SunTimerKind.Sunrise))
        assertEquals(SunTimerReply.Accepted,
            sunTimerReply("sun timer updated to 1 minutes", SunTimerKind.Sunset))
    }

    @Test fun recognizesOfficial19SunsetConfirmationAndShutdown() {
        assertEquals(SunTimerReply.Accepted,
            sunTimerReply("1203> sunset timer set to 30 minutes", SunTimerKind.Sunset))
        assertEquals(SunTimerReply.Accepted,
            sunTimerReply("sunset timer updated to 1 minutes", SunTimerKind.Sunset))
        assertNull(sunTimerReply("sunset timer set to 30 minutes", SunTimerKind.Sunrise))
        assertEquals(SunTimerReply.Refused,
            sunTimerReply("shutdown time is less than current time: 0", SunTimerKind.Sunset))
    }

    @Test fun recognizesRefusedAndInvalidTimers() {
        assertEquals(SunTimerReply.Refused,
            sunTimerReply("Sun timer cannot be used currently", SunTimerKind.Sunrise))
        assertEquals(SunTimerReply.Refused,
            sunTimerReply("Refusing sunrise command 0 7h 30m 0s: Target time is incoherent", SunTimerKind.Sunrise))
        assertNull(sunTimerReply("Refusing sunset command", SunTimerKind.Sunrise))
    }
}
