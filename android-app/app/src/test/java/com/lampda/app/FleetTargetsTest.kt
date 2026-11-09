package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class FleetTargetsTest {
    private val a = LampTargetState("A", LampType.Simple, connected = true, verified = true)
    private val b = LampTargetState("B", LampType.Simple, connected = true, verified = true)
    private val rgb = LampTargetState("RGB", LampType.Rgb, connected = true, verified = true)

    @Test fun singleSelectionTargetsOnlyTheCheckedLamp() {
        assertEquals(listOf("B"), controlTargetAddresses(setOf("B"), listOf(a, b, rgb)))
    }

    @Test fun checkingTwoLampsFormsTheGroupWithoutAModeSwitch() {
        val first = selectionAfterToggle(emptySet(), "A", true, listOf(a, b))
        val group = selectionAfterToggle(first, "B", true, listOf(a, b))
        assertEquals(setOf("A", "B"), group)
        assertEquals(listOf("A", "B"), controlTargetAddresses(group, listOf(a, b)))
    }

    @Test fun uncheckingThePrimaryDoesNotKeepItAsAnImplicitTarget() {
        val next = selectionAfterToggle(setOf("A", "B"), "A", false, listOf(a, b))
        assertEquals(setOf("B"), next)
        assertEquals("B", primarySelection("A", next))
        assertEquals(listOf("B"), controlTargetAddresses(next, listOf(a, b)))
    }

    @Test fun uncheckingTheLastLampLeavesNoTargets() {
        val next = selectionAfterToggle(setOf("A"), "A", false, listOf(a, b))
        assertTrue(next.isEmpty())
        assertNull(primarySelection("A", next))
        assertTrue(controlTargetAddresses(next, listOf(a, b)).isEmpty())
    }

    @Test fun uncheckingAPeerPreservesTheOtherSelection() {
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A", "B"), "B", false, listOf(a, b)))
    }

    @Test fun incompatibleTypesCannotBeAddedToTheSelection() {
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A"), "RGB", true, listOf(a, rgb)))
        assertTrue(controlTargetAddresses(setOf("A", "RGB"), listOf(a, rgb)).isEmpty())
    }

    @Test fun anEmptySelectionCanStartWithAnotherLampType() {
        assertEquals(setOf("RGB"), selectionAfterToggle(emptySet(), "RGB", true, listOf(a, rgb)))
    }

    @Test fun disconnectedPeersCannotJoin() {
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A"), "B", true, listOf(a, b.copy(connected = false))))
        assertTrue(selectionAfterToggle(emptySet(), "B", true, listOf(b.copy(connected = false))).isEmpty())
    }

    @Test fun missingTypeReplyDoesNotDisableCheckboxButStillBlocksUnverifiedGroupCommands() {
        val pending = b.copy(verified = false)
        val selection = selectionAfterToggle(setOf("A"), "B", true, listOf(a, pending))
        assertEquals(setOf("A", "B"), selection)
        assertEquals(listOf("A"), controlTargetAddresses(selection, listOf(a, pending)))
        assertEquals(listOf("A", "B"), controlTargetAddresses(selection, listOf(a, b)))
        assertEquals(setOf("A", "B"), selectionAfterToggle(setOf("A"), "B", true,
            listOf(a.copy(verified = false), b)))
    }

    @Test fun twoPendingIdentitiesNeverReceiveGroupCommands() {
        assertTrue(controlTargetAddresses(setOf("A", "B"),
            listOf(a.copy(verified = false), b.copy(verified = false))).isEmpty())
    }

    @Test fun discoveredTypeMismatchStopsGroupAndSelectionCanBeCorrected() {
        val selection = selectionAfterToggle(setOf("A"), "RGB", true,
            listOf(a, rgb.copy(type = LampType.Simple, verified = false)))
        assertEquals(setOf("A", "RGB"), selection)
        assertTrue(controlTargetAddresses(selection, listOf(a, rgb)).isEmpty())
        val solo = selectionAfterToggle(selection, "A", false, listOf(a, rgb))
        assertEquals(listOf("RGB"), controlTargetAddresses(solo, listOf(a, rgb)))
    }

    @Test fun aLostPrimaryDoesNotBlockTheRemainingCheckedLamp() {
        assertEquals(listOf("B"), controlTargetAddresses(setOf("A", "B"),
            listOf(a.copy(connected = false, verified = false), b)))
    }

    @Test fun unavailableSelectedLampsCanStillBeUnchecked() {
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A", "B"), "B", false,
            listOf(a, b.copy(connected = false, verified = false))))
    }

    @Test fun singleSelectionWorksBeforeItsTypeReply() {
        val unknown = a.copy(verified = false)
        assertEquals(setOf("A"), selectionAfterToggle(emptySet(), "A", true, listOf(unknown)))
        assertEquals(listOf("A"), controlTargetAddresses(setOf("A"), listOf(unknown)))
    }

    @Test fun missingSelectedAddressesFailClosedAndCanBeRemoved() {
        assertTrue(controlTargetAddresses(setOf("missing"), listOf(a, b)).isEmpty())
        assertTrue(controlTargetAddresses(setOf("A", "missing"), listOf(a, b)).isEmpty())
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A", "missing"), "missing", false, listOf(a, b)))
    }

    @Test fun repeatedCheckboxEventsDoNotDuplicateOrClearTargets() {
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A"), "A", true, listOf(a, b)))
        assertEquals(setOf("A"), selectionAfterToggle(setOf("A"), "B", false, listOf(a, b)))
        assertEquals(listOf("A", "B"), controlTargetAddresses(setOf("A", "B"), listOf(a, b, b)))
    }

    @Test fun primaryRemainsStableWhileItIsChecked() {
        assertEquals("B", primarySelection("B", setOf("A", "B")))
        assertEquals("A", primarySelection(null, setOf("A", "B")))
    }

    @Test fun oldCommandsAreNotReplayedWhenAPeerReturns() {
        val sent = controlTargetAddresses(setOf("A", "B"), listOf(a, b.copy(connected = false)))
        assertEquals(listOf("A"), sent)
        assertEquals(listOf("A", "B"), controlTargetAddresses(setOf("A", "B"), listOf(a, b)))
        assertEquals(listOf("A"), sent)
    }

    @Test fun disconnectedPrimaryPromotesAnotherCheckedReadyLampWithoutChangingTargets() {
        assertEquals("B", readyPrimary("A", setOf("A", "B"), setOf("B")))
        assertEquals("B", readyPrimary("B", setOf("A", "B"), setOf("A", "B")))
        assertNull(readyPrimary("A", emptySet(), setOf("A", "B")))
        assertEquals("A", readyPrimary("A", setOf("A"), emptySet()))
    }
}
