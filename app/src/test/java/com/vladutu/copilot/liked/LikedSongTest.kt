package com.vladutu.copilot.liked

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikedSongTest {

    private fun song(title: String, artist: String?) = LikedSong(title, artist, savedAt = 0L)

    @Test fun `same title and artist match ignoring case and surrounding space`() {
        assertTrue(song("Bad Habits", "Ed Sheeran").sameSongAs(song("  bad habits ", "ED SHEERAN")))
    }

    @Test fun `different artist does not match`() {
        assertFalse(song("Bad Habits", "Ed Sheeran").sameSongAs(song("Bad Habits", "Someone Else")))
    }

    @Test fun `null artist matches null or blank artist`() {
        assertTrue(song("Track", null).sameSongAs(song("Track", null)))
        assertTrue(song("Track", null).sameSongAs(song("Track", "   ")))
    }

    @Test fun `null artist does not match a real artist`() {
        assertFalse(song("Track", null).sameSongAs(song("Track", "Artist")))
    }
}
