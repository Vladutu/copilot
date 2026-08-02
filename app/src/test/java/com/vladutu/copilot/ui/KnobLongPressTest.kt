package com.vladutu.copilot.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KnobLongPressTest {

    private val detector = KnobLongPress(timeoutMs = 500)

    @Test fun `short press passes both halves through`() {
        assertEquals(KnobPressAction.PASS, detector.onDown(downTime = 0, eventTime = 0, repeatCount = 0))
        assertEquals(KnobPressAction.PASS, detector.onUp(downTime = 0, eventTime = 300))
    }

    @Test fun `repeats before the timeout pass through`() {
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 0, 0))
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 400, 1))
    }

    @Test fun `repeat at the timeout fires once then consumes the rest of the press`() {
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 0, 0))
        assertEquals(KnobPressAction.FIRE, detector.onDown(0, 500, 1))
        assertEquals(KnobPressAction.CONSUME, detector.onDown(0, 550, 2))
        assertEquals(KnobPressAction.CONSUME, detector.onDown(0, 600, 3))
        assertEquals(KnobPressAction.CONSUME, detector.onUp(0, 650))
    }

    @Test fun `no repeats falls back to firing on a long up`() {
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 0, 0))
        assertEquals(KnobPressAction.FIRE, detector.onUp(0, 700))
    }

    @Test fun `press after a fired one starts clean`() {
        detector.onDown(0, 0, 0)
        detector.onDown(0, 500, 1)
        detector.onUp(0, 650)
        assertEquals(KnobPressAction.PASS, detector.onDown(1000, 1000, 0))
        assertEquals(KnobPressAction.PASS, detector.onUp(1000, 1200))
    }

    @Test fun `up exactly at the timeout fires`() {
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 0, 0))
        assertEquals(KnobPressAction.FIRE, detector.onUp(0, 500))
    }

    @Test fun `system long-press flag fires regardless of timestamps`() {
        // adb input keyevent --longpress injects down + flagged repeat + up with
        // near-zero deltas; the flag alone must fire.
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 0, 0))
        assertEquals(KnobPressAction.FIRE, detector.onDown(0, 10, 1, flaggedLongPress = true))
        assertEquals(KnobPressAction.CONSUME, detector.onUp(0, 20))
    }

    @Test fun `flag on the initial down is ignored`() {
        assertEquals(KnobPressAction.PASS, detector.onDown(0, 0, 0, flaggedLongPress = true))
    }
}
