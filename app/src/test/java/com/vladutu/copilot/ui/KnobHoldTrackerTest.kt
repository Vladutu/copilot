package com.vladutu.copilot.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sequences mirror the on-car diagnostic capture of 2026-08-03: a knob press is a
 * DPAD_CENTER pulse (down+up ~2ms apart) followed ~1ms later by a BUTTON_1 pair
 * whose UP tracks the real release.
 */
class KnobHoldTrackerTest {

    private val tracker = KnobHoldTracker(timeoutMs = 500)

    private fun pulse(at: Long = 0) {
        assertEquals(
            KnobHoldResult(cancelTimers = true),
            tracker.onCenterDown(downTime = at, eventTime = at, repeatCount = 0, flaggedLongPress = false),
        )
        assertEquals(
            KnobHoldResult(startFallback = true),
            tracker.onCenterUp(downTime = at, eventTime = at + 2),
        )
    }

    @Test fun `car short press clicks on button1 release`() {
        pulse()
        assertEquals(
            KnobHoldResult(cancelTimers = true, startLongPressInMs = 497),
            tracker.onButton1Down(eventTime = 3),
        )
        assertEquals(
            KnobHoldResult(output = KnobHoldOutput.CLICK, cancelTimers = true),
            tracker.onButton1Up(),
        )
    }

    @Test fun `car hold long-presses when the timer expires mid-hold`() {
        pulse()
        tracker.onButton1Down(eventTime = 3)
        assertEquals(KnobHoldOutput.LONG_PRESS, tracker.onLongPressElapsed())
        // The eventual release must not also click.
        assertEquals(KnobHoldResult(), tracker.onButton1Up())
    }

    @Test fun `press works again after a fired long-press`() {
        pulse()
        tracker.onButton1Down(eventTime = 3)
        tracker.onLongPressElapsed()
        tracker.onButton1Up()
        pulse(at = 2000)
        tracker.onButton1Down(eventTime = 2003)
        assertEquals(
            KnobHoldResult(output = KnobHoldOutput.CLICK, cancelTimers = true),
            tracker.onButton1Up(),
        )
    }

    @Test fun `stale long-press timer after release does nothing`() {
        pulse()
        tracker.onButton1Down(eventTime = 3)
        tracker.onButton1Up() // CLICK; caller cancels the timer, but simulate the race:
        assertEquals(KnobHoldOutput.NONE, tracker.onLongPressElapsed())
    }

    @Test fun `pulse without button1 clicks via the fallback`() {
        pulse()
        assertEquals(KnobHoldOutput.CLICK, tracker.onFallbackElapsed())
    }

    @Test fun `fallback after button1 arrived does nothing`() {
        pulse()
        tracker.onButton1Down(eventTime = 3)
        assertEquals(KnobHoldOutput.NONE, tracker.onFallbackElapsed())
    }

    @Test fun `rapid second pulse flushes the pending click instead of dropping it`() {
        pulse()
        assertEquals(
            KnobHoldResult(output = KnobHoldOutput.CLICK, cancelTimers = true),
            tracker.onCenterDown(downTime = 60, eventTime = 60, repeatCount = 0, flaggedLongPress = false),
        )
    }

    @Test fun `adb longpress flag fires through the center channel`() {
        tracker.onCenterDown(0, 0, 0, false)
        assertEquals(
            KnobHoldResult(output = KnobHoldOutput.LONG_PRESS, cancelTimers = true),
            tracker.onCenterDown(0, 10, 1, flaggedLongPress = true),
        )
        // Release after the fire: no click, and the tracker returns to idle.
        assertEquals(KnobHoldResult(), tracker.onCenterUp(0, 12))
        pulse(at = 1000)
        assertEquals(KnobHoldOutput.CLICK, tracker.onFallbackElapsed())
    }

    @Test fun `center key genuinely held long-presses on release`() {
        tracker.onCenterDown(0, 0, 0, false)
        assertEquals(
            KnobHoldResult(output = KnobHoldOutput.LONG_PRESS, cancelTimers = true),
            tracker.onCenterUp(downTime = 0, eventTime = 700),
        )
    }

    @Test fun `center repeats below the timeout stay quiet`() {
        tracker.onCenterDown(0, 0, 0, false)
        assertEquals(KnobHoldResult(), tracker.onCenterDown(0, 400, 1, false))
    }

    @Test fun `button1 down before center up still enters holding`() {
        // Ordering safety: the bridge usually sends pulse-up before button1-down,
        // but the tracker must not depend on it.
        tracker.onCenterDown(0, 0, 0, false)
        assertEquals(
            KnobHoldResult(cancelTimers = true, startLongPressInMs = 499),
            tracker.onButton1Down(eventTime = 1),
        )
        assertEquals(KnobHoldResult(), tracker.onCenterUp(0, 2))
        assertEquals(KnobHoldOutput.LONG_PRESS, tracker.onLongPressElapsed())
    }
}
