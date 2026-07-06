package com.vladutu.copilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * The BMW M tricolor: slanted parallelogram stripes drawn straight to canvas — no
 * image assets, tints come from the theme's
 * [ThemeAccents.stripes][com.vladutu.copilot.ui.theme.ThemeAccents]. Renders at
 * whatever size the caller's modifier dictates (default 28x14dp, the top-bar badge).
 */
@Composable
fun MTricolor(stripes: List<Color>, modifier: Modifier = Modifier.size(28.dp, 14.dp)) {
    Canvas(modifier = modifier) {
        val stripeWidth = size.width / (stripes.size + 1) // +1 leaves room for the skew
        val skew = stripeWidth * 0.8f
        stripes.forEachIndexed { i, color ->
            val left = i * stripeWidth
            drawPath(
                path = Path().apply {
                    moveTo(left + skew, 0f)
                    lineTo(left + skew + stripeWidth * 0.7f, 0f)
                    lineTo(left + stripeWidth * 0.7f, size.height)
                    lineTo(left, size.height)
                    close()
                },
                color = color,
            )
        }
    }
}
