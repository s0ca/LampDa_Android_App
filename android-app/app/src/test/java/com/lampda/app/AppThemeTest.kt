package com.lampda.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AppThemeTest {
    @Test fun restoresSavedThemesAndDefaultsToS0ca() {
        assertEquals(AppTheme.S0ca, AppTheme.fromStored(null))
        assertEquals(AppTheme.S0ca, AppTheme.fromStored("unknown"))
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromStored(it.name)) }
        assertEquals("s0ca", AppTheme.S0ca.label)
        assertEquals("Half-Life", AppTheme.HalfLife.label)
        assertEquals("Lambda", AppTheme.Lambda.label)
    }

    @Test fun lambdaUsesPinkAndCyanWithReadableSecondaryControls() {
        val colors = AppTheme.Lambda.colors()
        assertEquals(Color(0xFFFF86C7), colors.primary)
        assertEquals(Color(0xFF64E4EB), colors.secondary)
        assertTrue(contrast(colors.secondary, colors.onSecondary) >= 4.5f)
        assertTrue(contrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5f)
        assertTrue(contrast(colors.secondaryContainer, colors.onSecondaryContainer) >= 4.5f)
    }

    @Test fun themesHaveReadableTextOnPrimaryAndMainSurfaces() {
        AppTheme.entries.forEach { theme ->
            val colors = theme.colors()
            assertTrue("${theme.label} primary", contrast(colors.primary, colors.onPrimary) >= 4.5f)
            assertTrue("${theme.label} surface", contrast(colors.surface, colors.onSurface) >= 4.5f)
            assertTrue("${theme.label} variant", contrast(colors.surfaceVariant, colors.onSurfaceVariant) >= 4.5f)
        }
        assertNotEquals(AppTheme.S0ca.colors().primary, AppTheme.HalfLife.colors().primary)
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
