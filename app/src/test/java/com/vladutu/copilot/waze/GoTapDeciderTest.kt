package com.vladutu.copilot.waze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoTapDeciderTest {

    private val knob = setOf(23, 66) // DPAD_CENTER, ENTER

    @Test fun `acts when enabled, waze foreground, knob key`() {
        assertTrue(GoTapDecider.shouldAttempt(true, "com.waze", 23, knob))
        assertTrue(GoTapDecider.shouldAttempt(true, "com.waze", 66, knob))
    }

    @Test fun `does not act when disabled`() {
        assertFalse(GoTapDecider.shouldAttempt(false, "com.waze", 23, knob))
    }

    @Test fun `does not act when foreground is not waze`() {
        assertFalse(GoTapDecider.shouldAttempt(true, "com.google.android.apps.maps", 23, knob))
        assertFalse(GoTapDecider.shouldAttempt(true, null, 23, knob))
    }

    @Test fun `does not act for a non-knob key`() {
        assertFalse(GoTapDecider.shouldAttempt(true, "com.waze", 4, knob)) // BACK
    }
}
