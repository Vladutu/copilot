package com.vladutu.copilot.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DDDarkColors = darkColorScheme(
    primary = DDAccent,
    onPrimary = DDAccentOn,
    background = DDBlack,
    onBackground = DDOnSurface,
    surface = DDSurface,
    onSurface = DDOnSurface,
    surfaceVariant = DDSurfaceVariant,
    onSurfaceVariant = DDOnSurface,
    error = DDError,
    onError = DDOnSurface,
)

@Composable
fun CopilotDriveTheme(content: @Composable () -> Unit) {
    val colors = DDDarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.value.toInt()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = DriveDeckTypography,
        content = content
    )
}
