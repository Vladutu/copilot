package com.vladutu.copilot.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PairedLaunchTest {

    private var pairedFired = 0
    private var timeoutFired = 0

    @Before fun setUp() {
        PairedLaunch.clear()
        pairedFired = 0
        timeoutFired = 0
    }

    private fun arm(partner: String = WAZE): Int =
        PairedLaunch.arm(partner, target = YTM, paired = { pairedFired++ }, timeout = { timeoutFired++ })

    @Test fun `pendingTarget exposes the deep link's app and clears on consume`() {
        assertNull(PairedLaunch.pendingTarget())
        arm()
        assertEquals(YTM, PairedLaunch.pendingTarget())
        PairedLaunch.matchPartnerShown(WAZE)
        assertNull(PairedLaunch.pendingTarget())
    }

    @Test fun `partner shown hands out the paired continuation exactly once`() {
        arm()
        PairedLaunch.matchPartnerShown(WAZE)?.invoke()
        assertEquals(1, pairedFired)
        assertNull(PairedLaunch.matchPartnerShown(WAZE))
    }

    @Test fun `a different package does not match the partner`() {
        arm()
        assertNull(PairedLaunch.matchPartnerShown(YTM))
        assertEquals(0, pairedFired)
    }

    @Test fun `timeout hands out the fallback when the partner never showed`() {
        val token = arm()
        PairedLaunch.takeTimeout(token)?.invoke()
        assertEquals(1, timeoutFired)
        assertEquals(0, pairedFired)
    }

    @Test fun `timeout is a no-op after the partner fired`() {
        val token = arm()
        assertNotNull(PairedLaunch.matchPartnerShown(WAZE))
        assertNull(PairedLaunch.takeTimeout(token))
    }

    @Test fun `partner match is a no-op after the timeout fired`() {
        val token = arm()
        assertNotNull(PairedLaunch.takeTimeout(token))
        assertNull(PairedLaunch.matchPartnerShown(WAZE))
    }

    @Test fun `pendingPartner reflects the current arm and clears on consume`() {
        assertNull(PairedLaunch.pendingPartner())
        arm()
        assertEquals(WAZE, PairedLaunch.pendingPartner())
        PairedLaunch.matchPartnerShown(WAZE)
        assertNull(PairedLaunch.pendingPartner())
    }

    @Test fun `a stale timeout token cannot fire a newer arm's fallback`() {
        val staleToken = arm()
        arm() // re-armed by a second launch before the first TTL fires
        assertNull(PairedLaunch.takeTimeout(staleToken))
        assertEquals(0, timeoutFired)
    }

    private companion object {
        const val WAZE = "com.waze"
        const val YTM = "com.google.android.apps.youtube.music"
    }
}
