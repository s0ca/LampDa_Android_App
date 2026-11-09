package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class LampIdentityProbeTest {
    private class Harness {
        val scheduled = mutableListOf<Runnable>()
        var requests = 0
        val probe = LampIdentityProbe(
            { task, delay -> assertEquals(2_000L, delay); scheduled.add(task) },
            { task -> scheduled.remove(task); Unit },
            { requests++ },
        )
        fun tick() = scheduled.removeAt(0).run()
    }

    @Test fun missingReplyRetriesOnlyThreeTimes() {
        val h = Harness()
        h.probe.start()
        h.tick()
        h.tick()
        assertEquals(3, h.requests)
        assertTrue(h.scheduled.isEmpty())
    }

    @Test fun receivedIdentityOrDisconnectCancelsRetries() {
        val h = Harness()
        h.probe.start()
        val stale = h.scheduled.single()
        h.probe.stop()
        stale.run()
        assertEquals(1, h.requests)
        assertTrue(h.scheduled.isEmpty())
    }

    @Test fun manualRetryReplacesOldScheduleAndHasAFreshBudget() {
        val h = Harness()
        h.probe.start()
        h.probe.start()
        assertEquals(1, h.scheduled.size)
        h.tick()
        h.tick()
        assertEquals(4, h.requests)
        assertTrue(h.scheduled.isEmpty())
    }

    @Test fun sessionsHaveIndependentRetryLifetimes() {
        val first = Harness()
        val second = Harness()
        first.probe.start()
        second.probe.start()
        first.probe.stop()
        second.tick()
        assertEquals(1, first.requests)
        assertEquals(2, second.requests)
    }
}
