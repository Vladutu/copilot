package com.vladutu.copilot.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * User-tweakable appearance knobs for the grid tiles (see [MediaRowTile][com.vladutu.copilot.ui.MediaRowTile]).
 * Provided once at the nav root from the persisted settings and read deep in the tree
 * via [LocalTileAppearance], so the five tile-using screens need no plumbing.
 *
 * Defaults mirror the hard-coded values the tiles shipped with, so an install that has
 * never touched Settings looks exactly as before.
 */
data class TileAppearance(
    val fontSize: TextUnit = TileAppearanceDefaults.FONT_SIZE_SP.sp,
    val focusBorderWidth: Dp = TileAppearanceDefaults.BORDER_WIDTH_DP.dp,
) {
    /** Label line height, holding the original 32sp → 38sp proportion as the font scales. */
    val lineHeight: TextUnit get() = (fontSize.value * LINE_HEIGHT_RATIO).sp

    private companion object {
        const val LINE_HEIGHT_RATIO = 38f / 32f
    }
}

object TileAppearanceDefaults {
    const val FONT_SIZE_SP = 32f
    const val BORDER_WIDTH_DP = 4f

    // Slider bounds exposed in Settings.
    const val FONT_SIZE_MIN = 18f
    const val FONT_SIZE_MAX = 60f
    const val BORDER_WIDTH_MIN = 0f
    const val BORDER_WIDTH_MAX = 10f
}

val LocalTileAppearance = staticCompositionLocalOf { TileAppearance() }
