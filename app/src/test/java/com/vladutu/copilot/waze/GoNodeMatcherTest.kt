package com.vladutu.copilot.waze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoNodeMatcherTest {

    @Test fun `matches exact text`() {
        assertTrue(GoNodeMatcher.matches("Go now", "Go now", null))
    }

    @Test fun `matches case-insensitively and trims`() {
        assertTrue(GoNodeMatcher.matches("Go now", "  GO NOW ", null))
        assertTrue(GoNodeMatcher.matches(" Go now ", "go now", null))
    }

    @Test fun `matches on contentDescription when text is null`() {
        assertTrue(GoNodeMatcher.matches("Go now", null, "Go now"))
    }

    @Test fun `ignores non-matching labels`() {
        assertFalse(GoNodeMatcher.matches("Go now", "Cancel", "Close"))
        assertFalse(GoNodeMatcher.matches("Go now", null, null))
    }

    @Test fun `blank target never matches`() {
        assertFalse(GoNodeMatcher.matches("   ", "Go now", null))
    }
}
