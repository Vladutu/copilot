package com.vladutu.copilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

// Sweep geometry + intensity, pulled out for on-device tuning. 0.35 was picked by
// eye on the emulator (5-8% was invisible there); if the carbox panel renders it
// brighter at night, SWEEP_ALPHAS is the knob to turn down.
private const val SLOT_WIDTH_FRACTION = 0.13f // per-stripe slot, as fraction of screen width
private const val STRIPE_FILL = 0.7f // drawn width within a slot; the rest is gap (matches MTricolor)
private const val SKEW_FRACTION = 0.45f // horizontal lean over the full height (~24° from vertical)
private const val RIGHT_ANCHOR_FRACTION = 0.92f // top-right edge of the last stripe
private val SWEEP_ALPHAS = listOf(0.35f, 0.35f, 0.35f) // light blue / dark blue / red

/**
 * The M tricolor as a full-screen background wash: the same slanted bands as
 * [MTricolor] blown up to screen height, anchored to the right third and drawn at
 * a few percent opacity so they tint the background instead of glowing over it.
 * Canvas-only, no assets; colors come from
 * [ThemeAccents.sweep][com.vladutu.copilot.ui.theme.ThemeAccents]. Draw it as the
 * bottom layer of the root Box, above the background fill and below all content.
 */
@Composable
fun TricolorSweep(stripes: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val slotWidth = size.width * SLOT_WIDTH_FRACTION
        val skew = size.height * SKEW_FRACTION
        val startX = size.width * RIGHT_ANCHOR_FRACTION - stripes.size * slotWidth
        stripes.forEachIndexed { i, color ->
            val topLeft = startX + i * slotWidth
            drawPath(
                path = Path().apply {
                    moveTo(topLeft, 0f)
                    lineTo(topLeft + slotWidth * STRIPE_FILL, 0f)
                    lineTo(topLeft + slotWidth * STRIPE_FILL - skew, size.height)
                    lineTo(topLeft - skew, size.height)
                    close()
                },
                color = color.copy(alpha = SWEEP_ALPHAS.getOrElse(i) { SWEEP_ALPHAS.first() }),
            )
        }
    }
}
