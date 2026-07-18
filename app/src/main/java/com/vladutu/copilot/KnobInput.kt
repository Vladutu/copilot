package com.vladutu.copilot

import android.view.KeyEvent

/**
 * The BMW iDrive knob arrives via the carbox's CarPlay bridge as one set of events
 * from device "gaei" (src=0x301). The system then re-injects a second, synthetic
 * copy from a nameless device (src=0x101) for DPAD_CENTER / BACK / BUTTON_1 —
 * without filtering, every knob press and every back press would fire twice.
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
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_BUTTON_1 -> true
        else -> false
    }
}
