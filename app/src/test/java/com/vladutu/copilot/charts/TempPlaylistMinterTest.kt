package com.vladutu.copilot.charts

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TempPlaylistMinterTest {

    private lateinit var server: MockWebServer
    private lateinit var minter: TempPlaylistMinter

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        minter = TempPlaylistMinter(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `mints a music-youtube launch url from the redirect Location`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .setHeader("Location", "https://www.youtube.com/watch?v=aaa&list=TLGGtest123"),
        )
        val url = minter.mint(listOf("aaa", "bbb", "ccc"))
        assertEquals("https://music.youtube.com/watch?v=aaa&list=TLGGtest123", url)
        val recorded = server.takeRequest()
        assertEquals("/watch_videos?video_ids=aaa,bbb,ccc", recorded.path)
    }

    @Test fun `non-redirect response throws ChartsException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("nope"))
        try {
            minter.mint(listOf("aaa"))
            fail("expected ChartsException")
        } catch (expected: ChartsException) {
        }
    }

    @Test fun `network failure surfaces as ChartsException`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            minter.mint(listOf("aaa"))
            fail("expected ChartsException")
        } catch (expected: ChartsException) {
        }
    }

    @Test fun `redirect without a list id throws ChartsException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .setHeader("Location", "https://www.youtube.com/watch?v=aaa"),
        )
        try {
            minter.mint(listOf("aaa"))
            fail("expected ChartsException")
        } catch (expected: ChartsException) {
            assertTrue(expected.message!!.contains("list"))
        }
    }
}
