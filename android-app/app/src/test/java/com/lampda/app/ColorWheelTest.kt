package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class ColorWheelTest {
    @Test fun cardinalPointsMatchTheRenderedHueWheel() {
        assertEquals(0f, wheelSelection(100f, 0f, 100f).hue, 0.001f)
        assertEquals(90f, wheelSelection(0f, 100f, 100f).hue, 0.001f)
        assertEquals(180f, wheelSelection(-100f, 0f, 100f).hue, 0.001f)
        assertEquals(270f, wheelSelection(0f, -100f, 100f).hue, 0.001f)
    }

    @Test fun centerIsUnsaturatedAndDraggingOutsideClampsToTheEdge() {
        assertEquals(0f, wheelSelection(0f, 0f, 100f).saturation, 0f)
        assertEquals(0.5f, wheelSelection(50f, 0f, 100f).saturation, 0.001f)
        assertEquals(1f, wheelSelection(1000f, 1000f, 100f).saturation, 0f)
    }
}
