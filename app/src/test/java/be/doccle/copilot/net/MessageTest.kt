package be.doccle.copilot.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MessageTest {

    private val now = 1717250000L
    private val maxAge = 30L

    private fun ntfyEnvelope(body: String): String =
        """{"id":"x","time":$now,"event":"message","topic":"t","message":${q(body)}}"""

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun `parses a valid ytmusic playlist message`() {
        val env = ntfyEnvelope(
            """{"v":1,"ts":$now,"cmd":"ytmusic","form":"playlist","id":"PLabc"}"""
        )
        val msg = Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge)
        assertNotNull(msg)
        assertEquals("ytmusic", msg!!.cmd)
        assertEquals("playlist", msg.form)
        assertEquals("PLabc", msg.id)
        assertEquals(now, msg.ts)
    }

    @Test
    fun `rejects stale messages older than maxAge`() {
        val staleTs = now - maxAge - 1
        val env = ntfyEnvelope(
            """{"v":1,"ts":$staleTs,"cmd":"ytmusic","form":"playlist","id":"PLabc"}"""
        )
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `accepts messages exactly at maxAge`() {
        val edgeTs = now - maxAge
        val env = ntfyEnvelope(
            """{"v":1,"ts":$edgeTs,"cmd":"ytmusic","form":"playlist","id":"PLabc"}"""
        )
        assertNotNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `rejects messages from the future beyond maxAge`() {
        val futureTs = now + maxAge + 1
        val env = ntfyEnvelope(
            """{"v":1,"ts":$futureTs,"cmd":"ytmusic","form":"playlist","id":"PLabc"}"""
        )
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `rejects unknown schema version`() {
        val env = ntfyEnvelope(
            """{"v":2,"ts":$now,"cmd":"ytmusic","form":"playlist","id":"PLabc"}"""
        )
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `rejects unknown cmd`() {
        val env = ntfyEnvelope(
            """{"v":1,"ts":$now,"cmd":"waze","form":"playlist","id":"PLabc"}"""
        )
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `rejects unknown form`() {
        val env = ntfyEnvelope(
            """{"v":1,"ts":$now,"cmd":"ytmusic","form":"song","id":"VIDabc"}"""
        )
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `rejects blank id`() {
        val env = ntfyEnvelope(
            """{"v":1,"ts":$now,"cmd":"ytmusic","form":"playlist","id":""}"""
        )
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `tolerates ntfy keepalive events`() {
        val keepalive = """{"id":"x","time":$now,"event":"keepalive","topic":"t"}"""
        assertNull(Message.parseEnvelope(keepalive, nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `tolerates malformed json`() {
        assertNull(Message.parseEnvelope("not json", nowSec = now, maxAgeSec = maxAge))
        assertNull(Message.parseEnvelope("", nowSec = now, maxAgeSec = maxAge))
        assertNull(Message.parseEnvelope("{}", nowSec = now, maxAgeSec = maxAge))
    }

    @Test
    fun `tolerates malformed inner message`() {
        val env = """{"id":"x","time":$now,"event":"message","topic":"t","message":"not json"}"""
        assertNull(Message.parseEnvelope(env, nowSec = now, maxAgeSec = maxAge))
    }
}
