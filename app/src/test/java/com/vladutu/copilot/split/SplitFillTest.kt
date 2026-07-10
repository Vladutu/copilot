package com.vladutu.copilot.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SplitFillTest {

    @Before fun setUp() {
        SplitFill.clear()
    }

    // classify

    @Test fun `half-screen window is a pane`() {
        assertEquals(SplitFill.Verdict.PANE, SplitFill.classify(0.5f))
    }

    @Test fun `full-screen window means the flag was ignored`() {
        assertEquals(SplitFill.Verdict.FULLSCREEN, SplitFill.classify(1.0f))
        assertEquals(SplitFill.Verdict.FULLSCREEN, SplitFill.classify(0.9f))
    }

    @Test fun `between the thresholds is still animating`() {
        assertEquals(SplitFill.Verdict.AMBIGUOUS, SplitFill.classify(0.78f))
    }

    // arm / consume

    @Test fun `idle - nothing awaited, nothing handed out`() {
        assertNull(SplitFill.pendingAwait())
        assertNull(SplitFill.takeFill())
        assertNull(SplitFill.takeRecovery())
    }

    @Test fun `pane verdict hands out the fill partner exactly once`() {
        SplitFill.arm(YTM, WAZE, recover = false)
        assertEquals(YTM, SplitFill.pendingAwait())
        assertEquals(WAZE, SplitFill.takeFill())
        assertNull(SplitFill.pendingAwait())
        assertNull(SplitFill.takeFill())
    }

    @Test fun `fullscreen verdict recovers the partner only when asked`() {
        SplitFill.arm(YTM, WAZE, recover = true)
        assertEquals(WAZE, SplitFill.takeRecovery())
        assertNull(SplitFill.pendingAwait())
    }

    @Test fun `fullscreen verdict without recovery still ends the watch`() {
        SplitFill.arm(YTM, WAZE, recover = false)
        assertNull(SplitFill.takeRecovery())
        assertNull(SplitFill.pendingAwait())
    }

    @Test fun `expire ends the watch`() {
        val token = SplitFill.arm(YTM, WAZE, recover = false)
        SplitFill.expire(token)
        assertNull(SplitFill.pendingAwait())
    }

    @Test fun `stale expiry token does not kill a newer watch`() {
        val old = SplitFill.arm(YTM, WAZE, recover = false)
        SplitFill.arm(SplitScreen.SOUNDCLOUD_PKG, WAZE, recover = false)
        SplitFill.expire(old)
        assertEquals(SplitScreen.SOUNDCLOUD_PKG, SplitFill.pendingAwait())
    }

    private companion object {
        const val WAZE = SplitScreen.WAZE_PKG
        const val YTM = SplitScreen.YT_MUSIC_PKG
    }
}
