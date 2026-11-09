package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class ControlReadingTest {
    @Test fun defaultsAreLocalAndNotPhysicalState() {
        assertEquals(ControlOrigin.Local, ControlReading(512).origin)
        assertEquals(ControlOrigin.Local, ControlReading(LampMode.Static).origin)
    }

    @Test fun requestingEvenThePreviousValueNeedsNewFeedback() {
        val received = ControlReading(512).receive(512)
        assertEquals(ControlOrigin.Reported, received.origin)
        assertEquals(ControlOrigin.Requested, received.request(512).origin)
        assertEquals(ControlOrigin.Reported, received.request(700).receive(700).origin)
    }

    @Test fun aDifferentPhysicalValueIsAReadingNotAnAcknowledgementOfTheRequest() {
        val state = ControlReading(512).request(700).receive(300)
        assertEquals(300, state.value)
        assertEquals(ControlOrigin.Reported, state.origin)
    }

    @Test fun reconnectKeepsPositionButInvalidatesItsOrigin() {
        val state = ControlReading(128).receive(220).invalidate()
        assertEquals(220, state.value)
        assertEquals(ControlOrigin.Local, state.origin)
    }

    @Test fun onePeersFeedbackCannotConfirmTheEntireGroup() {
        val local = ControlReading(512)
        val received = local.receive(512)
        assertEquals(ControlOrigin.Local, combinedControlOrigin(listOf(local, received), true))
        assertEquals(ControlOrigin.Requested,
            combinedControlOrigin(listOf(received, local.request(512)), true))
        assertEquals(ControlOrigin.Reported, combinedControlOrigin(listOf(received, received), true))
    }

    @Test fun differentGroupReadingsAreExplicit() {
        assertEquals(ControlOrigin.Mixed, combinedControlOrigin(
            listOf(ControlReading(512).receive(300), ControlReading(512).receive(700)), true))
        assertEquals(ControlOrigin.Mixed, combinedControlOrigin(
            listOf(ControlReading(LampMode.Static).receive(LampMode.Static),
                ControlReading(LampMode.Static).receive(LampMode.Strobe)), true))
    }

    @Test fun missingPeersAndEmptySelectionsAreNotSynchronized() {
        assertEquals(ControlOrigin.Unavailable,
            combinedControlOrigin(listOf(ControlReading(512).receive(512)), false))
        assertEquals(ControlOrigin.Unavailable, combinedControlOrigin(emptyList<ControlReading<Int>>(), true))
    }

    @Test fun customColorCannotBeConfirmedByAModeFeedback() {
        val color = ControlReading(RgbColor()).request(RgbColor(229, 255, 217))
        val mode = ControlReading(LampMode.Static).request(LampMode.RgbCustom).receive(LampMode.RgbCustom)
        assertEquals(ControlOrigin.Requested, color.origin)
        assertEquals(ControlOrigin.Reported, mode.origin)
    }
}
