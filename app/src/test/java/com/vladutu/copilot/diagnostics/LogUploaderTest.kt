package com.vladutu.copilot.diagnostics

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LogUploaderTest {

    private lateinit var server: MockWebServer   // dpaste (primary)
    private lateinit var fallback: MockWebServer // paste.c-net.org
    private lateinit var uploader: LogUploader

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        fallback = MockWebServer().also { it.start() }
        uploader = LogUploader(
            client = OkHttpClient(),
            dpasteEndpoint = server.url("/api/v2/").toString(),
            cnetEndpoint = fallback.url("/").toString(),
        )
    }

    @After fun tearDown() {
        server.shutdown()
        fallback.shutdown()
    }

    @Test fun `falls back to c-net when dpaste fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        fallback.enqueue(MockResponse().setBody("https://paste.c-net.org/SkirtMedical\n"))

        assertEquals("https://paste.c-net.org/SkirtMedical", uploader.upload("some log"))

        val request = fallback.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("some log", request.body.readUtf8())
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Copilot/"))
    }

    @Test fun `fallback scrubs the ntfy topic too`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        fallback.enqueue(MockResponse().setBody("https://paste.c-net.org/X"))
        val topic = "copilot-" + "0123456789abcdef".repeat(2)
        uploader.upload("topic $topic")
        val body = fallback.takeRequest().body.readUtf8()
        assertFalse(body.contains(topic))
        assertTrue(body.contains("copilot-<redacted>"))
    }

    @Test fun `does not touch the fallback when dpaste succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("https://dpaste.com/ABC123"))
        uploader.upload("some log")
        assertEquals(0, fallback.requestCount)
    }

    @Test fun `reports both failures when every host fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("dpaste down"))
        fallback.enqueue(MockResponse().setResponseCode(500).setBody("cnet down"))
        try {
            uploader.upload("some log")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message, e.message!!.contains("503"))
            assertTrue(e.message, e.message!!.contains("500"))
        }
    }

    @Test fun `returns raw-text url on success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("https://dpaste.com/ABC123\n"))
        assertEquals("https://dpaste.com/ABC123.txt", uploader.upload("some log"))
    }

    @Test fun `sends content and expiry as form fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("https://dpaste.com/ABC123"))
        uploader.upload("some log")
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"content\""))
        assertTrue(body.contains("some log"))
        assertTrue(body.contains("name=\"expiry_days\""))
        assertTrue(body.contains(LogUploader.RETENTION_DAYS.toString()))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Copilot/"))
    }

    @Test fun `scrubs ntfy topic from uploaded content`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("https://dpaste.com/ABC123"))
        val topic = "copilot-" + "0123456789abcdef".repeat(2)
        uploader.upload("connect failed: https://ntfy.sh/$topic/json timeout")
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains(topic))
        assertTrue(body.contains("copilot-<redacted>"))
    }

    @Test fun `collapses nul runs before upload`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("https://dpaste.com/ABC123"))
        uploader.upload("before\u0000\u0000\u0000after")
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains('\u0000'))
        assertTrue(body.contains("before�after"))
    }

    @Test fun `error status surfaces code and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(413).setBody("Paste too large."))
        fallback.enqueue(MockResponse().setResponseCode(500).setBody("x"))
        try {
            uploader.upload("some log")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("413"))
            assertTrue(e.message!!.contains("Paste too large."))
        }
    }

    @Test fun `success status with non-url body is an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<!doctype html>challenge page"))
        fallback.enqueue(MockResponse().setResponseCode(500).setBody("x"))
        try {
            uploader.upload("some log")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("200"))
        }
    }
}
