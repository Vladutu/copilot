package com.vladutu.copilot.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * User-chosen screen orientation (Settings > Display). Head units have no meaningful
 * orientation sensor and often report odd dimensions, so instead of sniffing the screen
 * the user says what their panel is: [LANDSCAPE] keeps the wide single-row rails the app
 * shipped with; [PORTRAIT] rotates the activity and folds each rail page into a
 * [PageSizeDefaults.PORTRAIT_COLUMNS]-wide grid (see KnobPagedGrid).
 *
 * Persisted by [id] so renaming a constant never breaks a saved preference; unknown ids
 * resolve to [LANDSCAPE] at read time, mirroring themeById.
 */
enum class LayoutMode(val id: String, val label: String) {
    LANDSCAPE("landscape", "Landscape"),
    PORTRAIT("portrait", "Portrait"),
    ;

    companion object {
        fun fromId(id: String?): LayoutMode = entries.firstOrNull { it.id == id } ?: LANDSCAPE
    }
}

val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.LANDSCAPE }
