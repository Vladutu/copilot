package com.vladutu.copilot.autoswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoSwitchBackTest {

    private val ytMusic = AutoSwitchBack.YT_MUSIC_PKG

    private var now = 1_000L

    @Before fun reset() {
        // Deterministic, controllable clock and a clean slate between tests.
        now = 1_000L
        AutoSwitchBack.clock = { now }
        AutoSwitchBack.ownPackage = "com.vladutu.copilot"
        AutoSwitchBack.disarm()
        AutoSwitchBack.onForeground("com.android.launcher") // a benign default
    }

    @Test fun `not armed by default`() {
        AutoSwitchBack.disarm()
        assertFalse(AutoSwitchBack.isArmed())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `arm snapshots current foreground as target`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        assertTrue(AutoSwitchBack.isArmed())
        assertTrue(AutoSwitchBack.shouldScheduleOnYtMusicShown())
        assertEquals("com.waze", AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `arm with yt music in foreground yields no target`() {
        AutoSwitchBack.onForeground(ytMusic)
        AutoSwitchBack.arm()
        assertTrue(AutoSwitchBack.isArmed())
        assertFalse(AutoSwitchBack.shouldScheduleOnYtMusicShown())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `in-app case returns to copilot`() {
        AutoSwitchBack.onForeground("com.vladutu.copilot")
        AutoSwitchBack.arm()
        AutoSwitchBack.onForeground(ytMusic) // YT Music came up
        assertEquals("com.vladutu.copilot", AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `copilot overlay reading does not count as user moving away`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        AutoSwitchBack.onForeground(ytMusic)
        AutoSwitchBack.onForeground("com.vladutu.copilot") // bubble overlay window event
        assertEquals("com.waze", AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `user moving to a third app aborts`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        AutoSwitchBack.onForeground(ytMusic)
        AutoSwitchBack.onForeground("com.android.chrome") // user opened something else
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `arm expires after ttl`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        now += 8_001L
        assertFalse(AutoSwitchBack.isArmed())
        assertFalse(AutoSwitchBack.shouldScheduleOnYtMusicShown())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `disarm clears state`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        AutoSwitchBack.disarm()
        assertFalse(AutoSwitchBack.isArmed())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }
}
