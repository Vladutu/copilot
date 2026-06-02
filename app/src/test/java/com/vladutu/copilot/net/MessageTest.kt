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
}
