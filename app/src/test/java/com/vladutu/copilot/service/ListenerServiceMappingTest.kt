package com.vladutu.copilot.service

import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.history.from
import com.vladutu.copilot.net.Message
import com.vladutu.copilot.net.savesToHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerServiceMappingTest {

    private fun msg(form: Form, url: String, title: String? = null) =
        Message(v = 3, ts = 1_700_000_000L, cmd = if (form == Form.DESTINATION) "waze" else "ytmusic",
                form = form, url = url, title = title, imageUrl = null)

    @Test fun `playlist mapping uses parsed list id`() {
        val m = msg(Form.PLAYLIST, "https://music.youtube.com/watch?list=OLAK5uy_xxx", "Mix")
        val item = SavedItem.from(m, savedAt = 42L)
        assertEquals(Form.PLAYLIST, item.form)
        assertEquals("OLAK5uy_xxx", item.id)
        assertEquals("Mix", item.title)
        assertEquals(42L, item.savedAt)
    }

    @Test fun `song mapping uses parsed video id`() {
        val m = msg(Form.SONG, "https://music.youtube.com/watch?v=abc123")
        val item = SavedItem.from(m, savedAt = 1L)
        assertEquals("abc123", item.id)
    }

    @Test fun `destination mapping uses sha1 of url`() {
        val m = msg(Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2")
        val item = SavedItem.from(m, savedAt = 1L)
        assertEquals(40, item.id.length) // SHA-1 hex
    }

    @Test fun `savesToHistory returns true for waze and ytmusic`() {
        val waze = msg(Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2")
        val ytmusic = msg(Form.SONG, "https://music.youtube.com/watch?v=abc")
        assertEquals(true, waze.savesToHistory())
        assertEquals(true, ytmusic.savesToHistory())
    }

    @Test fun `savesToHistory returns false for maps`() {
        val maps = Message(
            v = 3, ts = 1_700_000_000L, cmd = "maps",
            form = Form.DESTINATION,
            url = "https://www.google.com/maps/place/X",
            title = null, imageUrl = null,
        )
        assertEquals(false, maps.savesToHistory())
    }
}
