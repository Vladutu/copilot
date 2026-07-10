package com.vladutu.copilot.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplitRepairTest {

    @Before fun setUp() {
        SplitRepair.clear()
    }

    @Test fun `idle - nothing pending, no attempt handed out`() {
        assertNull(SplitRepair.pendingNav())
        assertNull(SplitRepair.pendingMusic())
        assertNull(SplitRepair.takeAttempt())
    }

    @Test fun `armed - exposes both packages and keeps watching after an attempt`() {
        var fired = 0
        SplitRepair.arm(WAZE, YTM) { fired++ }
        assertEquals(WAZE, SplitRepair.pendingNav())
        assertEquals(YTM, SplitRepair.pendingMusic())
        SplitRepair.takeAttempt()!!.invoke()
        assertEquals(1, fired)
        // Attempts remain: a repair that landed on the confirm screen gets collapsed by
        // the nav-start task recreation, and the next attempt must still be available.
        assertEquals(WAZE, SplitRepair.pendingNav())
    }

    @Test fun `clears after the last attempt`() {
        SplitRepair.arm(WAZE, YTM) {}
        repeat(SplitRepair.MAX_ATTEMPTS) { assertTrue(SplitRepair.takeAttempt() != null) }
        assertNull(SplitRepair.pendingNav())
        assertNull(SplitRepair.pendingMusic())
        assertNull(SplitRepair.takeAttempt())
    }

    @Test fun `expire ends the watch`() {
        val token = SplitRepair.arm(WAZE, YTM) {}
        SplitRepair.expire(token)
        assertNull(SplitRepair.pendingNav())
        assertNull(SplitRepair.takeAttempt())
    }

    @Test fun `stale expiry token does not kill a newer watch`() {
        val old = SplitRepair.arm(WAZE, YTM) {}
        SplitRepair.arm(MAPS, YTM) {}
        SplitRepair.expire(old)
        assertEquals(MAPS, SplitRepair.pendingNav())
    }

    @Test fun `re-arm resets the attempt budget`() {
        SplitRepair.arm(WAZE, YTM) {}
        SplitRepair.takeAttempt()
        SplitRepair.arm(WAZE, YTM) {}
        repeat(SplitRepair.MAX_ATTEMPTS) { assertTrue(SplitRepair.takeAttempt() != null) }
    }

    private companion object {
        const val WAZE = SplitScreen.WAZE_PKG
        const val MAPS = SplitScreen.MAPS_PKG
        const val YTM = SplitScreen.YT_MUSIC_PKG
    }
}
