package com.vladutu.copilot.net

import com.vladutu.copilot.history.Form
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {
    private val now = 1_717_336_800L
    private val maxAge = 60L

    private fun envelope(body: String): String =
        """{"event":"message","message":${escape(body)}}"""

    private fun escape(s: String): String =
        '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"'

    @Test fun `accepts v3 with all fields`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"playlist","url":"https://music.youtube.com/watch?list=L","title":"Title","imageUrl":"https://img/x.jpg"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertEquals(3, msg.v)
        assertEquals("ytmusic", msg.cmd)
        assertEquals(Form.PLAYLIST, msg.form)
        assertEquals("Title", msg.title)
        assertEquals("https://img/x.jpg", msg.imageUrl)
    }

    @Test fun `accepts v3 with null title and imageUrl`() {
        val body = """{"v":3,"ts":$now,"cmd":"waze","form":"destination","url":"https://ul.waze.com/ul?ll=1,2"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertNull(msg.title)
        assertNull(msg.imageUrl)
        assertEquals(Form.DESTINATION, msg.form)
    }

    @Test fun `rejects v2`() {
        val body = """{"v":2,"ts":$now,"cmd":"ytmusic","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertTrue((res as ParseResult.Rejected).reason.contains("v=2"))
    }

    @Test fun `rejects missing form`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("unknown form", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects cmd-form mismatch`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"destination","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects waze cmd with playlist form`() {
        val body = """{"v":3,"ts":$now,"cmd":"waze","form":"playlist","url":"https://ul.waze.com/ul?ll=1,2"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects untrusted host (existing behavior)`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"playlist","url":"https://evil.example/"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("untrusted host", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects stale (existing behavior)`() {
        val body = """{"v":3,"ts":${now - 1000},"cmd":"ytmusic","form":"song","url":"https://music.youtube.com/watch?v=abc"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertTrue((res as ParseResult.Rejected).reason.contains("stale"))
    }

    @Test fun `accepts v3 with cmd=maps and Google Maps URL`() {
        val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://www.google.com/maps/place/Brandenburg+Gate/@52.5,13.4,17z/"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertEquals("maps", msg.cmd)
        assertEquals(Form.DESTINATION, msg.form)
    }

    @Test fun `accepts maps cmd with maps_app_goo_gl short URL`() {
        val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://maps.app.goo.gl/abc123"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
    }

    @Test fun `rejects maps cmd with untrusted host`() {
        val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://evil.example/place/x"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("untrusted host", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects maps cmd with non-destination form`() {
        val body = """{"v":3,"ts":$now,"cmd":"maps","form":"playlist","url":"https://www.google.com/maps/place/X"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `accepts maps cmd with bare google_com host (no www)`() {
        val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://google.com/maps/place/Brandenburg+Gate"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
    }

    @Test fun `accepts radio cmd with https stream url`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"https://live.example.ro/europafm.mp3","title":"Europa FM","imageUrl":"https://example.ro/fav.png"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertEquals("radio", msg.cmd)
        assertEquals(Form.RADIO, msg.form)
        assertEquals("Europa FM", msg.title)
        assertEquals("https://example.ro/fav.png", msg.imageUrl)
    }

    @Test fun `accepts radio cmd with http stream url`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"http://1.2.3.4:8000/stream.aac"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
    }

    @Test fun `accepts radio cmd from arbitrary host (no allow-list)`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"https://some-random-icecast.example/stream"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
    }

    @Test fun `rejects radio cmd with non-http scheme`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"ftp://example.ro/stream"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("non-http(s) radio url", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects radio cmd with non-radio form`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"playlist","url":"https://live.example.ro/x.mp3"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects radio form with non-radio cmd`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"radio","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }
}
