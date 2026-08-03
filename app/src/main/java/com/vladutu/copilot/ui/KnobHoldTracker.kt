package com.vladutu.copilot.ui

/** What a tracked knob press resolved to. */
enum class KnobHoldOutput { NONE, CLICK, LONG_PRESS }

/**
 * Directives for one key event: deliver [output] now, [cancelTimers] first, then
 * arm the no-BUTTON_1 fallback and/or the long-press timer. The caller owns the
 * actual timers (coroutines) and feeds expiry back via onFallbackElapsed /
 * onLongPressElapsed, which re-check state so a stale timer can never act.
 */
data class KnobHoldResult(
    val output: KnobHoldOutput = KnobHoldOutput.NONE,
    val cancelTimers: Boolean = false,
    val startFallback: Boolean = false,
    val startLongPressInMs: Long? = null,
)

/**
 * Knob press tracker built for how the carbox actually delivers a press
 * (diagnostic capture 2026-08-03): DPAD_CENTER arrives as an instant pulse —
 * down+up 1–2ms apart at the moment of physical contact, regardless of hold —
 * and the companion KEYCODE_BUTTON_1 from the nameless injected device carries
 * the real duration: its DOWN lands right after the pulse and its UP lands when
 * the finger releases. So the pulse says "pressed", BUTTON_1 says "for how long".
 *
 * MediaRowTile consumes every one of these events on tiles with a long-press
 * action and lets this tracker own the semantics:
 *  - BUTTON_1 released before the long-press timeout → CLICK (fires on release,
 *    like any physical button);
 *  - still held at the timeout → LONG_PRESS mid-hold, same feel as touch;
 *  - no BUTTON_1 within [FALLBACK_MS] of the pulse (emulator, plain keyboards)
 *    → CLICK, so non-car input keeps working;
 *  - a DPAD_CENTER/ENTER that genuinely holds (repeats with FLAG_LONG_PRESS —
 *    also what `adb shell input keyevent --longpress` injects — or a real
 *    down→up duration) → LONG_PRESS through the center channel itself.
 *
 * Pure state machine, JVM-tested; no clocks or timers inside.
 */
class KnobHoldTracker(private val timeoutMs: Long) {

    private enum class State { IDLE, PULSED, HOLDING, FIRED }

    private var state = State.IDLE
    private var pulseDownTime = 0L
    private var button1Held = false

    fun onCenterDown(
        downTime: Long,
        eventTime: Long,
        repeatCount: Int,
        flaggedLongPress: Boolean,
    ): KnobHoldResult {
        if (repeatCount == 0) {
            // A new pulse while the previous press still awaits its fallback click
            // must flush that click first — a fast double-press loses no taps.
            val flush = if (state == State.PULSED) KnobHoldOutput.CLICK else KnobHoldOutput.NONE
            state = State.PULSED
            pulseDownTime = downTime
            return KnobHoldResult(output = flush, cancelTimers = true)
        }
        if (state == State.FIRED || state == State.IDLE) return KnobHoldResult()
        if (flaggedLongPress || eventTime - downTime >= timeoutMs) {
            state = State.FIRED
            return KnobHoldResult(output = KnobHoldOutput.LONG_PRESS, cancelTimers = true)
        }
        return KnobHoldResult()
    }

    fun onCenterUp(downTime: Long, eventTime: Long): KnobHoldResult = when (state) {
        State.PULSED ->
            if (eventTime - downTime >= timeoutMs) {
                // A center key that truly held (no repeats arrived) long-presses
                // on release.
                state = State.IDLE
                KnobHoldResult(output = KnobHoldOutput.LONG_PRESS, cancelTimers = true)
            } else {
                KnobHoldResult(startFallback = true)
            }
        State.FIRED -> {
            if (!button1Held) state = State.IDLE
            KnobHoldResult()
        }
        // HOLDING: BUTTON_1 owns the press now. IDLE: stray up, swallow.
        else -> KnobHoldResult()
    }

    fun onButton1Down(eventTime: Long): KnobHoldResult {
        button1Held = true
        if (state != State.PULSED) return KnobHoldResult()
        state = State.HOLDING
        // The hold started at the pulse, not at this event — count the timeout
        // from there so the long-press lands at the same feel as touch.
        val remaining = (timeoutMs - (eventTime - pulseDownTime)).coerceIn(0L, timeoutMs)
        return KnobHoldResult(cancelTimers = true, startLongPressInMs = remaining)
    }

    fun onButton1Up(): KnobHoldResult {
        button1Held = false
        return when (state) {
            State.HOLDING -> {
                state = State.IDLE
                KnobHoldResult(output = KnobHoldOutput.CLICK, cancelTimers = true)
            }
            State.FIRED -> {
                state = State.IDLE
                KnobHoldResult()
            }
            else -> KnobHoldResult()
        }
    }

    /** The fallback window closed with no BUTTON_1: the pulse was the whole press. */
    fun onFallbackElapsed(): KnobHoldOutput =
        if (state == State.PULSED) {
            state = State.IDLE
            KnobHoldOutput.CLICK
        } else {
            KnobHoldOutput.NONE
        }

    /** The long-press timer expired while BUTTON_1 is (still) held. */
    fun onLongPressElapsed(): KnobHoldOutput =
        if (state == State.HOLDING) {
            state = State.FIRED
            KnobHoldOutput.LONG_PRESS
        } else {
            KnobHoldOutput.NONE
        }

    companion object {
        /** How long after the pulse to wait for BUTTON_1 before clicking anyway. */
        const val FALLBACK_MS = 150L
    }
}
