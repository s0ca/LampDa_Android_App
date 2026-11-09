package com.lampda.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal enum class AppTheme(val label: String) {
    S0ca("s0ca"), HalfLife("Half-Life"), Lambda("Lambda");

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: S0ca
    }
}

internal val LocalAppTheme = staticCompositionLocalOf { AppTheme.S0ca }
internal val LocalChangeTheme = staticCompositionLocalOf<(AppTheme) -> Unit> { {} }

internal fun AppTheme.colors() = when (this) {
    AppTheme.S0ca -> darkColorScheme(
        primary = Color(0xFF8DE7D1), secondary = Color(0xFFFFC971), tertiary = Color(0xFFFFA6C1),
        background = Color(0xFF101416), surface = Color(0xFF182023), surfaceVariant = Color(0xFF243034),
    )
    AppTheme.Lambda -> darkColorScheme(
        primary = Color(0xFFFF86C7), onPrimary = Color(0xFF350B24),
        primaryContainer = Color(0xFF512039), onPrimaryContainer = Color(0xFFFFD8EB),
        secondary = Color(0xFF64E4EB), onSecondary = Color(0xFF003338),
        secondaryContainer = Color(0xFF123E42), onSecondaryContainer = Color(0xFFB4FAFF),
        tertiary = Color(0xFF64E4EB), onTertiary = Color(0xFF003338),
        tertiaryContainer = Color(0xFF123E42), onTertiaryContainer = Color(0xFFB4FAFF),
        background = Color(0xFF0C1012), onBackground = Color(0xFFF3EEF2),
        surface = Color(0xFF151B1E), onSurface = Color(0xFFF3EEF2),
        surfaceVariant = Color(0xFF293236), onSurfaceVariant = Color(0xFFCCD6D9),
        surfaceDim = Color(0xFF101517), surfaceBright = Color(0xFF353C40),
        surfaceContainerLowest = Color(0xFF080C0E), surfaceContainerLow = Color(0xFF12181A),
        surfaceContainer = Color(0xFF1B2225), surfaceContainerHigh = Color(0xFF252D30),
        surfaceContainerHighest = Color(0xFF30393D),
        outline = Color(0xFF99AAAF), outlineVariant = Color(0xFF435156),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE3ECEE), inverseOnSurface = Color(0xFF263034),
        inversePrimary = Color(0xFF92265E), surfaceTint = Color(0xFFFF86C7), scrim = Color.Black,
    )
    AppTheme.HalfLife -> darkColorScheme(
        primary = Color(0xFFFF9D23), onPrimary = Color(0xFF211300),
        primaryContainer = Color(0xFF563000), onPrimaryContainer = Color(0xFFFFDEBA),
        secondary = Color(0xFFEBC35B), onSecondary = Color(0xFF241C00),
        secondaryContainer = Color(0xFF413819), onSecondaryContainer = Color(0xFFFFE7A1),
        tertiary = Color(0xFF9CC7AE), onTertiary = Color(0xFF122C20),
        tertiaryContainer = Color(0xFF294738), onTertiaryContainer = Color(0xFFB8E3C9),
        background = Color(0xFF0B0C0B), onBackground = Color(0xFFF1EDE5),
        surface = Color(0xFF171917), onSurface = Color(0xFFF1EDE5),
        surfaceVariant = Color(0xFF282B26), onSurfaceVariant = Color(0xFFCECCBF),
        surfaceDim = Color(0xFF111310), surfaceBright = Color(0xFF343730),
        surfaceContainerLowest = Color(0xFF080A08), surfaceContainerLow = Color(0xFF131611),
        surfaceContainer = Color(0xFF1C1F19), surfaceContainerHigh = Color(0xFF252820),
        surfaceContainerHighest = Color(0xFF303329),
        outline = Color(0xFF96988A), outlineVariant = Color(0xFF474B40),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE4E4D8), inverseOnSurface = Color(0xFF2E312A),
        inversePrimary = Color(0xFF8A4E00), surfaceTint = Color(0xFFFF9D23), scrim = Color.Black,
    )
}

@Composable
internal fun AppThemeHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(AppTheme.fromStored(preferences.getString("theme", null))) }
    var language by remember { mutableStateOf(AppLanguage.fromStored(preferences.getString("language", null))) }
    var fullscreen by remember { mutableStateOf(preferences.getBoolean("fullscreen", true)) }
    AppSystemBars(fullscreen)
    val resources = remember(context, language) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        context.createConfigurationContext(config).resources
    }
    CompositionLocalProvider(
        LocalUiResources provides resources,
        LocalFullscreen provides fullscreen,
        LocalChangeFullscreen provides { enabled ->
            fullscreen = enabled
            preferences.edit().putBoolean("fullscreen", enabled).apply()
        },
        LocalAppLanguage provides language,
        LocalChangeLanguage provides { selected ->
            language = selected
            preferences.edit().putString("language", selected.name).apply()
        },
        LocalAppTheme provides theme,
        LocalChangeTheme provides { selected ->
            theme = selected
            preferences.edit().putString("theme", selected.name).apply()
        },
    ) {
        MaterialTheme(colorScheme = theme.colors()) {
            Surface(Modifier.fillMaxSize()) {
                Box(if (fullscreen) Modifier else Modifier.safeDrawingPadding()) {
                    content()
                }
            }
        }
    }
}
