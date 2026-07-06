package com.vladutu.copilot.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun CopilotDriveTheme(theme: ThemeSpec = DefaultTheme, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // statusBarColor/navigationBarColor are deprecated in favour of edge-to-edge, which
            // has no like-for-like replacement and would change layout insets. targetSdk is pinned
            // at 34 on purpose, so we keep the solid bars that match the app background.
            @Suppress("DEPRECATION")
            window.statusBarColor = theme.colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = theme.colorScheme.background.toArgb()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    CompositionLocalProvider(LocalThemeSpec provides theme) {
        MaterialTheme(
            colorScheme = theme.colorScheme,
            typography = theme.typography,
            shapes = PilotShapes,
            content = content,
        )
    }
}
