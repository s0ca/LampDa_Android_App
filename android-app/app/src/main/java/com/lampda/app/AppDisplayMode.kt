package com.lampda.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal val LocalFullscreen = staticCompositionLocalOf { true }
internal val LocalChangeFullscreen = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
internal fun AppSystemBars(fullscreen: Boolean) {
    val view = LocalView.current
    val window = view.context.activity()?.window ?: return
    DisposableEffect(view, window, fullscreen) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (fullscreen)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
        val apply = Runnable {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) hide(WindowInsetsCompat.Type.systemBars())
                else show(WindowInsetsCompat.Type.systemBars())
            }
        }
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) view.post(apply)
        }
        observer.addOnWindowFocusChangeListener(listener)
        view.post(apply)
        onDispose {
            view.removeCallbacks(apply)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
        }
    }
}
