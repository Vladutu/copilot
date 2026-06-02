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

    private fun ytPayload(ts: Long, list: String): String =
        """{"v":2,"ts":$ts,"cmd":"ytmusic","url":"https://music.youtube.com/watch?list=$list&shuffle=1"}"""

    @Test
    fun `emits accepted result for a valid message`() = runTest {
        server.enqueue(MockResponse().setBody(envelope(ytPayload(now, "PLone"))))

        val result = withTimeout(2000) { makeSubscriber().subscribe().first() }
        assertTrue(result is ParseResult.Accepted)
        assertEquals(
            "https://music.youtube.com/watch?list=PLone&shuffle=1",
            (result as ParseResult.Accepted).message.url,
        )
    }

    @Test
    fun `emits rejected result for a stale message`() = runTest {
        val staleTs = now - maxAge - 100
        server.enqueue(MockResponse().setBody(envelope(ytPayload(staleTs, "PLstale"))))

        val result = withTimeout(2000) { makeSubscriber().subscribe().first() }
        assertTrue(result is ParseResult.Rejected)
        assertTrue((result as ParseResult.Rejected).reason.contains("stale"))
    }

    @Test
    fun `filters out keepalive lines, only emits real results`() = runTest {
        val keepalive = """{"id":"x","time":$now,"event":"keepalive","topic":"t"}"""
        server.enqueue(MockResponse().setBody(keepalive + "\n" + envelope(ytPayload(now, "PLgood"))))

        val result = withTimeout(2000) { makeSubscriber().subscribe().first() }
        assertTrue(result is ParseResult.Accepted)
        assertEquals(
            "https://music.youtube.com/watch?list=PLgood&shuffle=1",
            (result as ParseResult.Accepted).message.url,
        )
    }

    @Test
    fun `reconnects after the server closes the stream`() = runTest {
        server.enqueue(MockResponse().setBody(envelope(ytPayload(now, "PLfirst"))))
        server.enqueue(MockResponse().setBody(envelope(ytPayload(now, "PLsecond"))))

        val urls = withTimeout(5000) {
            makeSubscriber().subscribe()
                .take(2)
                .toList()
                .map { (it as ParseResult.Accepted).message.url }
        }
        assertEquals(
            listOf(
                "https://music.youtube.com/watch?list=PLfirst&shuffle=1",
                "https://music.youtube.com/watch?list=PLsecond&shuffle=1",
            ),
            urls,
        )
    }

    private fun envelope(payload: String): String =
        """{"id":"x","time":$now,"event":"message","topic":"t","message":${q(payload)}}""" + "\n"

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
