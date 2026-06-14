package com.vladutu.copilot.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingMappingTest {

    @Test fun `title and artist map through`() {
        val np = nowPlayingFrom(title = "Bad Habits", artist = "Ed Sheeran", albumArtist = null)
        assertEquals(NowPlaying("Bad Habits", "Ed Sheeran"), np)
    }

    @Test fun `blank or null title yields null (nothing to like)`() {
        assertNull(nowPlayingFrom(title = null, artist = "x", albumArtist = null))
        assertNull(nowPlayingFrom(title = "   ", artist = "x", albumArtist = null))
    }

    @Test fun `missing artist falls back to album artist then null`() {
        assertEquals(NowPlaying("T", "Album Artist"), nowPlayingFrom("T", null, "Album Artist"))
        assertEquals(NowPlaying("T", null), nowPlayingFrom("T", null, null))
        assertEquals(NowPlaying("T", null), nowPlayingFrom("T", "  ", "  "))
    }

    @Test fun `values are trimmed`() {
        assertEquals(NowPlaying("T", "A"), nowPlayingFrom("  T ", " A ", null))
    }
}
