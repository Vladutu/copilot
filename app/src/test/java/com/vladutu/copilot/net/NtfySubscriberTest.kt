package com.vladutu.copilot.net

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfySubscriberTest {

    private lateinit var server: MockWebServer

    private val now = 1717250000L
    private val maxAge = 30L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun makeSubscriber(): NtfySubscriber = NtfySubscriber(
        client = OkHttpClient(),
        base = server.url("").toString().trimEnd('/'),
        topic = "test-topic",
        clock = { now },
        maxAgeSec = maxAge,
    )

    @Test
    fun `emits accepted result for a valid message`() = runTest {
        val payload = """{"v":1,"ts":$now,"cmd":"ytmusic","form":"playlist","id":"PLone"}"""
        server.enqueue(MockResponse().setBody(envelope(payload)))

        val result = withTimeout(2000) { makeSubscriber().subscribe().first() }
        assertTrue(result is ParseResult.Accepted)
        assertEquals("PLone", (result as ParseResult.Accepted).message.id)
    }

    @Test
    fun `emits rejected result for a stale message`() = runTest {
        val staleTs = now - maxAge - 100
        val payload = """{"v":1,"ts":$staleTs,"cmd":"ytmusic","form":"playlist","id":"PLstale"}"""
        server.enqueue(MockResponse().setBody(envelope(payload)))

        val result = withTimeout(2000) { makeSubscriber().subscribe().first() }
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("stale"))
    }

    @Test
    fun `filters out keepalive lines, only emits real results`() = runTest {
        val keepalive = """{"id":"x","time":$now,"event":"keepalive","topic":"t"}"""
        val payload = """{"v":1,"ts":$now,"cmd":"ytmusic","form":"playlist","id":"PLgood"}"""
        server.enqueue(MockResponse().setBody(keepalive + "\n" + envelope(payload)))

        val result = withTimeout(2000) { makeSubscriber().subscribe().first() }
        assertTrue(result is ParseResult.Accepted)
        assertEquals("PLgood", (result as ParseResult.Accepted).message.id)
    }

    @Test
    fun `reconnects after the server closes the stream`() = runTest {
        val first = """{"v":1,"ts":$now,"cmd":"ytmusic","form":"playlist","id":"PLfirst"}"""
        val second = """{"v":1,"ts":$now,"cmd":"ytmusic","form":"playlist","id":"PLsecond"}"""
        server.enqueue(MockResponse().setBody(envelope(first)))
        server.enqueue(MockResponse().setBody(envelope(second)))

        val ids = withTimeout(5000) {
            makeSubscriber().subscribe()
                .take(2)
                .toList()
                .map { (it as ParseResult.Accepted).message.id }
        }
        assertEquals(listOf("PLfirst", "PLsecond"), ids)
    }

    private fun envelope(payload: String): String =
        """{"id":"x","time":$now,"event":"message","topic":"t","message":${q(payload)}}""" + "\n"

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
