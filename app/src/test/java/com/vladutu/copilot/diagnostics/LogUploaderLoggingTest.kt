package com.vladutu.copilot.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric so [DiagnosticLog] is initialised and its file can be read back. */
@RunWith(RobolectricTestRunner::class)
class LogUploaderLoggingTest {

    private lateinit var dpaste: MockWebServer
    private lateinit var cnet: MockWebServer
    private lateinit var uploader: LogUploader

    @Before fun setUp() {
        DiagnosticLog.init(ApplicationProvider.getApplicationContext<Context>())
        dpaste = MockWebServer().also { it.start() }
        cnet = MockWebServer().also { it.start() }
        uploader = LogUploader(
            client = OkHttpClient(),
            dpasteEndpoint = dpaste.url("/api/v2/").toString(),
            cnetEndpoint = cnet.url("/").toString(),
        )
    }

    @After fun tearDown() {
        dpaste.shutdown()
        cnet.shutdown()
    }

    @Test fun `logs the host that served a successful upload`() = runTest {
        dpaste.enqueue(MockResponse().setResponseCode(201).setBody("https://dpaste.com/ABC123"))
        uploader.upload("some log")
        assertTrue(DiagnosticLog.read(), DiagnosticLog.read().contains("uploaded to dpaste: https://dpaste.com/ABC123.txt"))
    }

    @Test fun `logs the fallback host when dpaste failed`() = runTest {
        dpaste.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        cnet.enqueue(MockResponse().setBody("https://paste.c-net.org/X"))
        uploader.upload("some log")
        assertTrue(DiagnosticLog.read(), DiagnosticLog.read().contains("uploaded to c-net: https://paste.c-net.org/X"))
    }
}
