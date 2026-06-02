package com.vladutu.copilot.history

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArtworkCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private lateinit var cache: ArtworkCache

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        cacheDir = Files.createTempDirectory("artwork").toFile()
        cache = ArtworkCache(client = OkHttpClient(), cacheDir = cacheDir)
    }

    @After fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun bytes(): Buffer = Buffer().writeUtf8("FAKE_JPEG_BYTES")

    @Test fun `downloads to expected path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bytes()))
        val file = cache.download(server.url("/img.jpg").toString(), Form.PLAYLIST, "abc")
        assertTrue(file != null && file.exists())
        assertEquals(File(cacheDir, "artwork/playlist-abc.jpg"), file)
    }

    @Test fun `write-once skips network on second call`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bytes()))
        cache.download(server.url("/img.jpg").toString(), Form.SONG, "vid")
        val before = server.requestCount
        cache.download(server.url("/img.jpg").toString(), Form.SONG, "vid")
        assertEquals(before, server.requestCount)
    }

    @Test fun `failure leaves no file and throws nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val file = cache.download(server.url("/missing.jpg").toString(), Form.PLAYLIST, "x")
        assertEquals(null, file)
        assertFalse(File(cacheDir, "artwork/playlist-x.jpg").exists())
    }

    @Test fun `cached file lookup`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bytes()))
        cache.download(server.url("/img.jpg").toString(), Form.DESTINATION, "d1")
        val found = cache.fileFor(Form.DESTINATION, "d1")
        assertTrue(found.exists())
    }
}
