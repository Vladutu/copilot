package com.vladutu.copilot.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistIdParserTest {

    @Test fun extracts_from_music_playlist_url() {
        assertEquals(
            "PLabcDEF_123",
            PlaylistIdParser.parse("https://music.youtube.com/playlist?list=PLabcDEF_123")
        )
    }

    @Test fun extracts_from_url_with_extra_params() {
        assertEquals(
            "OLAK5uy_xyz",
            PlaylistIdParser.parse("https://music.youtube.com/playlist?list=OLAK5uy_xyz&si=foo")
        )
    }

    @Test fun extracts_from_youtube_com_url() {
        assertEquals(
            "PLqrst",
            PlaylistIdParser.parse("https://www.youtube.com/playlist?list=PLqrst")
        )
    }

    @Test fun bare_id_returned_as_is() {
        assertEquals("PLabcDEF_123", PlaylistIdParser.parse("PLabcDEF_123"))
    }

    @Test fun trims_whitespace() {
        assertEquals("PLabcDEF123", PlaylistIdParser.parse("  PLabcDEF123  "))
    }

    @Test fun rejects_id_under_minimum_length() {
        assertNull(PlaylistIdParser.parse("PL12"))
    }

    @Test fun rejects_id_with_invalid_chars() {
        assertNull(PlaylistIdParser.parse("PLabc!@#"))
    }

    @Test fun rejects_empty() {
        assertNull(PlaylistIdParser.parse(""))
        assertNull(PlaylistIdParser.parse("   "))
    }

    @Test fun rejects_url_without_list_param() {
        assertNull(PlaylistIdParser.parse("https://music.youtube.com/"))
    }
}
