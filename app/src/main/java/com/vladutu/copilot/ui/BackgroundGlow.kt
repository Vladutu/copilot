package com.vladutu.copilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Glow shape, pulled out for on-device tuning like SWEEP_ALPHAS: where the light
// sits and how far it reaches. Intensity lives in the theme's glow color itself.
private const val CENTER_X_FRACTION = 0.5f // horizontal center of the glow
private const val CENTER_Y_FRACTION = 0.0f // top edge — light falls from above, iDrive-style
private const val RADIUS_FRACTION = 0.9f // reach, as fraction of screen width

/** Settings-slider bounds for the glow, in percent — 0 hides the glow entirely. */
object GlowDefaults {
    const val INTENSITY_MIN = 0f
    const val INTENSITY_MAX = 100f
    const val INTENSITY_DEFAULT = 100f // 100% = the glow color as authored (BmwGlow)
}

/**
 * A soft radial wash behind everything: the theme's
 * [ThemeAccents.glow][com.vladutu.copilot.ui.theme.ThemeAccents] color at the top
 * center melting into the flat background toward the edges, like a screen lit from
 * above. Draw it below [TricolorSweep] so the stripes ride on top of the light.
 * [alpha] scales the whole wash (0 = invisible, 1 = the color as authored).
 */
@Composable
fun BackgroundGlow(color: Color, alpha: Float = 1f, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = color.alpha * alpha), Color.Transparent),
                center = Offset(size.width * CENTER_X_FRACTION, size.height * CENTER_Y_FRACTION),
                radius = size.width * RADIUS_FRACTION,
            ),
        )
    }
}
