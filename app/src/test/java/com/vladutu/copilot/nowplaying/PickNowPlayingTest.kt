package com.vladutu.copilot.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickNowPlayingTest {

    private fun session(
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
    ) = SessionSnapshot(isPlaying = isPlaying, title = title, artist = artist, albumArtist = null)

    @Test fun `no sessions yields null`() {
        assertNull(pickNowPlaying(emptyList()))
    }

    @Test fun `playing session beats higher-priority paused one`() {
        // YT Music paused but most recent (index 0), SoundCloud actively playing below it.
        val picked = pickNowPlaying(
            listOf(
                session(isPlaying = false, title = "Old YT Song", artist = "A"),
                session(isPlaying = true, title = "SC Song", artist = "B"),
            ),
        )
        assertEquals(NowPlaying("SC Song", "B"), picked)
    }

    @Test fun `none playing falls back to OS priority order`() {
        val picked = pickNowPlaying(
            listOf(
                session(isPlaying = false, title = "Most Recent", artist = "A"),
                session(isPlaying = false, title = "Older", artist = "B"),
            ),
        )
        assertEquals(NowPlaying("Most Recent", "A"), picked)
    }

    @Test fun `both playing keeps OS priority order`() {
        val picked = pickNowPlaying(
            listOf(
                session(isPlaying = true, title = "First"),
                session(isPlaying = true, title = "Second"),
            ),
        )
        assertEquals(NowPlaying("First", null), picked)
    }

    @Test fun `playing session without title yet yields null, not the paused app's song`() {
        val picked = pickNowPlaying(
            listOf(
                session(isPlaying = false, title = "Paused Song"),
                session(isPlaying = true, title = null),
            ),
        )
        assertNull(picked)
    }

    @Test fun `single paused session still shows on the strip`() {
        val picked = pickNowPlaying(listOf(session(isPlaying = false, title = "T", artist = "A")))
        assertEquals(NowPlaying("T", "A"), picked)
    }
}
