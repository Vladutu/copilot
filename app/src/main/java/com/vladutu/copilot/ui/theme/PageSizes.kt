package com.vladutu.copilot.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * User-tweakable tiles-per-page for the knob rails. Menu screens (Home, Music) hold a
 * fixed handful of entries; list screens page whatever the data brings. Provided once
 * at the nav root from the persisted settings and read via [LocalPageSizes] (it is
 * KnobPagedGrid's pageSize default), so the screens stay plumbing-free.
 *
 * Defaults mirror the hard-coded values the rails shipped with (4 menu / 5 list), so
 * an install that has never touched Settings pages exactly as before.
 */
data class PageSizes(
    val menuTiles: Int = PageSizeDefaults.MENU_TILES,
    val listTiles: Int = PageSizeDefaults.LIST_TILES,
)

object PageSizeDefaults {
    const val MENU_TILES = 4
    const val LIST_TILES = 5

    // Slider bounds exposed in Settings.
    const val MIN = 2f
    const val MAX = 8f

    // Columns per grid row in portrait mode; landscape keeps the single
    // pageSize-wide row. Two is the widest that leaves drivable tap targets
    // on an upright panel.
    const val PORTRAIT_COLUMNS = 2
}

val LocalPageSizes = staticCompositionLocalOf { PageSizes() }
