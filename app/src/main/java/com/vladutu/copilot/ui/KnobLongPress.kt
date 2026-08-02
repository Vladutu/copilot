package com.vladutu.copilot.ui

/** What the tile should do with one knob confirm-key event. */
enum class KnobPressAction {
    /** Not ours — let Compose's clickable handle it (normal knob click). */
    PASS,

    /** Part of a press that already long-fired — swallow so no click follows. */
    CONSUME,

    /** Long press detected — run the action and consume the event. */
    FIRE,
}

/**
 * Long-press detection for the knob's confirm key (DPAD_CENTER / ENTER), which
 * Compose's combinedClickable only long-presses from touch. Pure state machine so
 * it is JVM-testable; MediaRowTile feeds it raw key timings.
 *
 * Primary path: the carbox delivers key repeats while the knob is held (see
 * BackGrabberService, which swallows "repeat of a claimed press"), so the first
 * repeat at or past [timeoutMs] fires while still holding — same feel as a touch
 * long-press. Fallback: if no repeat ever arrives, the DOWN→UP duration fires on
 * release instead.
 *
 * Once fired, the remainder of that press (repeats + UP) is consumed so the tile's
 * normal knob click doesn't also land under the just-opened confirm dialog. Presses
 * are identified by their downTime, which Android keeps constant across a hold.
 */
class KnobLongPress(private val timeoutMs: Long) {

    private var firedDownTime = Long.MIN_VALUE

    /**
     * [flaggedLongPress] is KeyEvent.isLongPress — the system stamps FLAG_LONG_PRESS on
     * the first repeat past its own long-press timeout, and `adb shell input keyevent
     * --longpress` injects it with near-zero timestamps, so honoring the flag both
     * trusts the platform's timing and makes the feature testable from adb.
     */
    fun onDown(downTime: Long, eventTime: Long, repeatCount: Int, flaggedLongPress: Boolean = false): KnobPressAction {
        if (downTime == firedDownTime) return KnobPressAction.CONSUME
        if (repeatCount > 0 && (flaggedLongPress || eventTime - downTime >= timeoutMs)) {
            firedDownTime = downTime
            return KnobPressAction.FIRE
        }
        // Initial down (and early repeats) must pass: clickable only clicks an UP
        // whose DOWN it saw, so consuming here would kill short presses too.
        return KnobPressAction.PASS
    }

    fun onUp(downTime: Long, eventTime: Long): KnobPressAction {
        if (downTime == firedDownTime) {
            firedDownTime = Long.MIN_VALUE
            return KnobPressAction.CONSUME
        }
        return if (eventTime - downTime >= timeoutMs) KnobPressAction.FIRE else KnobPressAction.PASS
    }
}
