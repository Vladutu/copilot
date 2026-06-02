package com.vladutu.copilot.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    private val now = 1717250000L
    private val maxAge = 30L

    private fun ntfyEnvelope(body: String): String =
        """{"id":"x","time":$now,"event":"message","topic":"t","message":${q(body)}}"""

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun `parses a valid ytmusic message`() {
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":2,"ts":$now,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Accepted)
        val accepted = result as ParseResult.Accepted
        assertEquals("ytmusic", accepted.message.cmd)
        assertEquals(url, accepted.message.url)
        assertEquals(now, accepted.message.ts)
        assertEquals(0L, accepted.skewSec)
    }

    @Test
    fun `accepted message reports positive skew when box is ahead`() {
        val ts = now - 5
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":2,"ts":$ts,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Accepted)
        assertEquals(5L, (result as ParseResult.Accepted).skewSec)
    }

    @Test
    fun `accepted message reports negative skew when box is behind`() {
        val ts = now + 5
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":2,"ts":$ts,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Accepted)
        assertEquals(-5L, (result as ParseResult.Accepted).skewSec)
    }

    @Test
    fun `rejects stale messages older than maxAge with skew`() {
        val staleTs = now - maxAge - 1
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":2,"ts":$staleTs,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        val rejected = result as ParseResult.Rejected
        assertTrue(
            "expected 'stale' in reason but was '${rejected.reason}'",
            rejected.reason.contains("stale"),
        )
        assertEquals(maxAge + 1, rejected.skewSec)
    }

    @Test
    fun `accepts messages exactly at maxAge`() {
        val edgeTs = now - maxAge
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":2,"ts":$edgeTs,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Accepted)
    }

    @Test
    fun `rejects messages from the future beyond maxAge`() {
        val futureTs = now + maxAge + 1
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":2,"ts":$futureTs,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
    }

    @Test
    fun `rejects unknown schema version`() {
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        val env = ntfyEnvelope("""{"v":1,"ts":$now,"cmd":"ytmusic","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("schema"))
    }

    @Test
    fun `rejects unknown cmd`() {
        val url = "https://music.youtube.com/watch?list=PLabc"
        val env = ntfyEnvelope("""{"v":2,"ts":$now,"cmd":"bogus","url":"$url"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("cmd"))
    }

    @Test
    fun `rejects ytmusic with untrusted host`() {
        val env = ntfyEnvelope(
            """{"v":2,"ts":$now,"cmd":"ytmusic","url":"https://evil.example.com/playlist?list=PLabc"}"""
        )
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        val reason = (result as ParseResult.Rejected).reason
        assertTrue("expected untrusted-host, got: $reason", reason.contains("untrusted host"))
    }

    @Test
    fun `rejects ytmusic with blank url`() {
        val env = ntfyEnvelope("""{"v":2,"ts":$now,"cmd":"ytmusic","url":""}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("url"))
    }

    @Test
    fun `rejects ytmusic without url`() {
        val env = ntfyEnvelope("""{"v":2,"ts":$now,"cmd":"ytmusic"}""")
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("url"))
    }

    @Test
    fun `skips ntfy keepalive events`() {
        val keepalive = """{"id":"x","time":$now,"event":"keepalive","topic":"t"}"""
        assertEquals(ParseResult.Skipped, Message.parseEnvelope(keepalive, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `skips malformed envelope json`() {
        assertEquals(ParseResult.Skipped, Message.parseEnvelope("not json", nowSec = now, maxAgeSec = maxAge))
        assertEquals(ParseResult.Skipped, Message.parseEnvelope("", nowSec = now, maxAgeSec = maxAge))
        assertEquals(ParseResult.Skipped, Message.parseEnvelope("{}", nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `rejects malformed inner message`() {
        val env = """{"id":"x","time":$now,"event":"message","topic":"t","message":"not json"}"""
        val result = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        assertNotNull((result as ParseResult.Rejected).reason)
    }

    @Test
    fun `accepts waze message with ul waze host`() {
        val body = """{"v":2,"ts":$now,"cmd":"waze","url":"https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes"}"""
        val result = Message.parseEnvelope(ntfyEnvelope(body), nowSec = now, maxAgeSec = maxAge)

        assertTrue(result is ParseResult.Accepted)
        val accepted = result as ParseResult.Accepted
        assertEquals("waze", accepted.message.cmd)
        assertEquals("https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes", accepted.message.url)
    }

    @Test
    fun `accepts waze message with waze com host`() {
        val body = """{"v":2,"ts":$now,"cmd":"waze","url":"https://waze.com/ul?ll=1,2&navigate=yes"}"""
        val result = Message.parseEnvelope(ntfyEnvelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Accepted)
    }

    @Test
    fun `rejects waze without url`() {
        val body = """{"v":2,"ts":$now,"cmd":"waze"}"""
        val result = Message.parseEnvelope(ntfyEnvelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("url"))
    }

    @Test
    fun `rejects waze with blank url`() {
        val body = """{"v":2,"ts":$now,"cmd":"waze","url":""}"""
        val result = Message.parseEnvelope(ntfyEnvelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
    }

    @Test
    fun `rejects waze with untrusted host`() {
        val body = """{"v":2,"ts":$now,"cmd":"waze","url":"https://evil.example.com/foo"}"""
        val result = Message.parseEnvelope(ntfyEnvelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
        val reason = (result as ParseResult.Rejected).reason
        assertTrue("expected untrusted-host message, got: $reason", reason.contains("untrusted host"))
    }

    @Test
    fun `rejects waze with http scheme`() {
        val body = """{"v":2,"ts":$now,"cmd":"waze","url":"http://ul.waze.com/ul?ll=1,2"}"""
        val result = Message.parseEnvelope(ntfyEnvelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(result is ParseResult.Rejected)
    }
}
