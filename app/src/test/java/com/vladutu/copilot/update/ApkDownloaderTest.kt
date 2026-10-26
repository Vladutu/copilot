package com.vladutu.copilot.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ApkDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: ApkDownloader

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        downloader = ApkDownloader(OkHttpClient(), tmp.root)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `writes the apk bytes to a file named after the version`() = runTest {
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))

        val file = downloader.download(UpdateChecker.Update("0.31.0", server.url("/copilot-0.31.0.apk").toString()))

        assertEquals("copilot-0.31.0.apk", file.name)
        assertEquals(tmp.root, file.parentFile)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun `http error throws and leaves no partial file behind`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("gone"))
        try {
            downloader.download(UpdateChecker.Update("0.31.0", server.url("/copilot-0.31.0.apk").toString()))
            fail("expected IOException")
        } catch (e: IOException) {
            assertFalse(tmp.root.resolve("copilot-0.31.0.apk").exists())
        }
    }

    @Test fun `a stale download of the same version is replaced`() = runTest {
        tmp.root.resolve("copilot-0.31.0.apk").writeText("old partial junk")
        server.enqueue(MockResponse().setBody("fresh"))

        val file = downloader.download(UpdateChecker.Update("0.31.0", server.url("/copilot-0.31.0.apk").toString()))

        assertEquals("fresh", file.readText())
    }
}
