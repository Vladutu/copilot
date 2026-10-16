package com.vladutu.copilot.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ShuffleRequestTest {

    private val pkg = NewPipeRoute.PKG

    @Before fun setUp() {
        ShuffleRequest.clear()
    }

    @Test fun `idle - nothing pending`() {
        assertNull(ShuffleRequest.pending(pkg))
        assertNull(ShuffleRequest.take(pkg, nowMs = 0))
    }

    @Test fun `armed shuffle is handed out once`() {
        ShuffleRequest.arm(pkg, shuffle = true, nowMs = 1_000)
        assertEquals(true, ShuffleRequest.pending(pkg))
        assertEquals(true, ShuffleRequest.take(pkg, nowMs = 2_000))
        assertNull(ShuffleRequest.take(pkg, nowMs = 2_000))
    }

    @Test fun `armed ordered launch hands out false`() {
        ShuffleRequest.arm(pkg, shuffle = false, nowMs = 1_000)
        assertEquals(false, ShuffleRequest.take(pkg, nowMs = 1_500))
    }

    @Test fun `other package gets nothing and does not consume`() {
        ShuffleRequest.arm(pkg, shuffle = true, nowMs = 1_000)
        assertNull(ShuffleRequest.take("com.other", nowMs = 1_500))
        assertEquals(true, ShuffleRequest.take(pkg, nowMs = 1_500))
    }

    @Test fun `request expires after the window`() {
        ShuffleRequest.arm(pkg, shuffle = true, nowMs = 1_000)
        assertNull(ShuffleRequest.take(pkg, nowMs = 1_000 + ShuffleRequest.WINDOW_MS + 1))
        assertNull(ShuffleRequest.pending(pkg))
    }

    @Test fun `re-arming replaces the previous request`() {
        ShuffleRequest.arm(pkg, shuffle = true, nowMs = 1_000)
        ShuffleRequest.arm(pkg, shuffle = false, nowMs = 2_000)
        assertEquals(false, ShuffleRequest.take(pkg, nowMs = 2_500))
    }
}
