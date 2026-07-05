package com.vladutu.copilot.history

import com.vladutu.copilot.net.Message
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedItemTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun `round-trips through JSON`() {
        val item = SavedItem(
            form = Form.PLAYLIST,
            id = "OLAK5uy_xxx",
            title = "Morning Drive",
            imageUrl = "https://lh3.googleusercontent.com/abc",
            url = "https://music.youtube.com/watch?list=OLAK5uy_xxx",
            savedAt = 1_717_336_800L,
        )
        val encoded = json.encodeToString(SavedItem.serializer(), item)
        val decoded = json.decodeFromString(SavedItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test fun `nullable fields encode and decode as null`() {
        val item = SavedItem(
            form = Form.DESTINATION,
            id = "abc123",
            title = null,
            imageUrl = null,
            url = "https://waze.com/ul?ll=1,2",
            savedAt = 1L,
        )
        val encoded = json.encodeToString(SavedItem.serializer(), item)
        val decoded = json.decodeFromString(SavedItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    // --- SoundCloud / cmd provenance ---

    @Test fun `soundcloud message saves cmd and sha1 id`() {
        val msg = Message(
            v = 3, ts = 1L, cmd = "soundcloud", form = Form.SONG,
            url = "https://soundcloud.com/the-real-tibo/la-pola-gola-life", title = "T", imageUrl = null,
        )
        val item = SavedItem.from(msg, savedAt = 2L)
        assertEquals("soundcloud", item.cmd)
        assertEquals("https://soundcloud.com/the-real-tibo/la-pola-gola-life", item.url)
        // No v= param → sha1 fallback: 40 hex chars, filesystem-safe for the artwork cache.
        assertTrue(item.id.matches(Regex("[0-9a-f]{40}")))
    }

    @Test fun `soundcloud playlist id falls back to sha1 too`() {
        val msg = Message(
            v = 3, ts = 1L, cmd = "soundcloud", form = Form.PLAYLIST,
            url = "https://soundcloud.com/a/sets/b", title = null, imageUrl = null,
        )
        val item = SavedItem.from(msg, savedAt = 2L)
        assertTrue(item.id.matches(Regex("[0-9a-f]{40}")))
    }

    @Test fun `ytmusic message still extracts video id and carries cmd`() {
        val msg = Message(
            v = 3, ts = 1L, cmd = "ytmusic", form = Form.SONG,
            url = "https://music.youtube.com/watch?v=dQw4w9WgXcQ", title = null, imageUrl = null,
        )
        val item = SavedItem.from(msg, savedAt = 2L)
        assertEquals("dQw4w9WgXcQ", item.id)
        assertEquals("ytmusic", item.cmd)
    }

    @Test fun `legacy row without cmd decodes to null cmd`() {
        val legacy = """{"form":"SONG","id":"dQw4w9WgXcQ","title":"T","imageUrl":null,"url":"https://music.youtube.com/watch?v=dQw4w9WgXcQ","savedAt":1}"""
        val decoded = json.decodeFromString(SavedItem.serializer(), legacy)
        assertEquals(null, decoded.cmd)
    }
}
