package com.vladutu.copilot.history

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
