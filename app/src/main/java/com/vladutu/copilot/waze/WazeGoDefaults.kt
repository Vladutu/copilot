package com.vladutu.copilot.waze

import android.view.KeyEvent

/** Defaults for the knob-press "Go now" tap. Mirrors the TileAppearanceDefaults pattern. */
object WazeGoDefaults {
    /** Text Copilot looks for on Waze's route-preview confirm button. User-overridable. */
    const val LABEL: String = "Go now"

    /** Key codes a carbox knob press is expected to emit. The first build accepts both; the
     *  real code is confirmed from DiagnosticLog (every key event is already logged). */
    val KNOB_KEYCODES: Set<Int> = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
}
