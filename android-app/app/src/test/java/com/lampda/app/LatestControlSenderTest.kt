package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class LatestControlSenderTest {
    private class Harness {
        val scheduled = mutableListOf<Runnable>()
        val sent = mutableListOf<Int>()
        val sender = LatestControlSender<Int>(
            { task, delay -> assertEquals(150L, delay); scheduled.add(task) },
            { task -> scheduled.remove(task); Unit },
            { sent.add(it) })
        fun tick() { scheduled.removeAt(0).run() }
    }

    @Test fun continuousMovementSendsTheNewestValueWithoutWaitingForAQuietPeriod() {
        val h = Harness()
        (1..100).forEach { h.sender.update(it) }
        assertEquals(1, h.scheduled.size)
        h.tick()
        assertEquals(listOf(100), h.sent)
        h.sender.update(101)
        h.tick()
        assertEquals(listOf(100, 101), h.sent)
    }

    @Test fun releaseCommitsTheLatestValueAndCancelsTheDelayedCopy() {
        val h = Harness()
        h.sender.update(10)
        h.sender.update(250)
        h.sender.commit()
        assertEquals(listOf(250), h.sent)
        assertTrue(h.scheduled.isEmpty())
    }

    @Test fun presetOverridesAnyPendingSliderValueImmediately() {
        val h = Harness()
        h.sender.update(100)
        h.sender.update(200, commit = true)
        assertEquals(listOf(200), h.sent)
        assertTrue(h.scheduled.isEmpty())
    }

    @Test fun cancellationDropsStaleValuesAcrossModeChangesOrDisconnects() {
        val h = Harness()
        h.sender.update(10)
        h.sender.cancel()
        h.sender.commit()
        assertTrue(h.sent.isEmpty())
        assertTrue(h.scheduled.isEmpty())
        h.sender.update(40)
        h.tick()
        assertEquals(listOf(40), h.sent)
    }

    @Test fun disconnectingOneSenderDoesNotCancelAnotherLampsPendingValue() {
        val first = Harness()
        val second = Harness()
        first.sender.update(200)
        second.sender.update(800)
        first.sender.cancel()
        second.tick()
        assertTrue(first.sent.isEmpty())
        assertEquals(listOf(800), second.sent)
    }

    @Test fun targetChangesCanCancelAllScheduledValuesWithoutReplayingOnCommit() {
        val lamps = listOf(Harness(), Harness())
        lamps.forEach { it.sender.update(512) }
        lamps.forEach { it.sender.cancel() }
        lamps.forEach { it.sender.commit() }
        assertTrue(lamps.all { it.sent.isEmpty() && it.scheduled.isEmpty() })
    }
}
