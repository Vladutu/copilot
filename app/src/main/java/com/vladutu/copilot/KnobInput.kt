package com.vladutu.copilot

import android.view.KeyEvent

/**
 * The BMW iDrive knob arrives via the carbox's CarPlay bridge as one set of events
 * from device "gaei" (src=0x301). The system then re-injects a second, synthetic
 * copy from a nameless device (src=0x101) for DPAD_CENTER / BACK — without
 * filtering, every knob press and every back press would fire twice.
 *
 * KEYCODE_BUTTON_1 is deliberately NOT filtered (it was until v0.29.0): the
 * nameless BUTTON_1 pair is not a duplicate but the knob's hold channel — the
 * named DPAD_CENTER is an instant down+up pulse at physical contact, while
 * BUTTON_1's UP arrives only when the finger releases (diagnostic capture
 * 2026-08-03). MediaRowTile's long-press tracking depends on receiving it, and
 * nothing else reacts to it (Compose ignores gamepad buttons for clicks).
 *
 * MainActivity drops the copy for the activity window; dialogs are their own window
 * and bypass Activity.dispatchKeyEvent entirely, so any dialog with knob-clickable
 * buttons must re-apply this check via onPreviewKeyEvent on its surface (see
 * ui/voice/VoiceDialog).
 */
fun isSyntheticKnobDuplicate(event: KeyEvent): Boolean {
    val hasNamedDevice = !event.device?.name.isNullOrEmpty()
    if (hasNamedDevice) return false
    return when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_BACK -> true
        else -> false
    }
}
