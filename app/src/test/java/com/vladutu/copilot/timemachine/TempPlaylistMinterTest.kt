package com.vladutu.copilot.timemachine

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

    @Test fun `returns the list id parsed from the redirect Location`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .setHeader("Location", "https://www.youtube.com/watch?v=aaa&list=TLGGtest123"),
        )
        assertEquals("TLGGtest123", minter.mint(listOf("aaa", "bbb", "ccc")))
        assertEquals("/watch_videos?video_ids=aaa,bbb,ccc", server.takeRequest().path)
    }

    @Test fun `non-redirect response throws TimeMachineException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("nope"))
        try {
            minter.mint(listOf("aaa"))
            fail("expected TimeMachineException")
        } catch (expected: TimeMachineException) {
        }
    }

    @Test fun `network failure surfaces as TimeMachineException`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            minter.mint(listOf("aaa"))
            fail("expected TimeMachineException")
        } catch (expected: TimeMachineException) {
        }
    }

    @Test fun `redirect without a list id throws TimeMachineException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .setHeader("Location", "https://www.youtube.com/watch?v=aaa"),
        )
        try {
            minter.mint(listOf("aaa"))
            fail("expected TimeMachineException")
        } catch (expected: TimeMachineException) {
            assertTrue(expected.message!!.contains("list"))
        }
    }
}
