package com.vladutu.copilot.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun checker(current: String) = UpdateChecker(
        client = OkHttpClient(),
        currentVersion = current,
        latestReleaseUrl = server.url("/repos/Vladutu/copilot/releases/latest").toString(),
    )

    private fun release(tag: String, vararg assets: Pair<String, String>): String {
        val list = assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url","size":123}"""
        }
        return """{"tag_name":"$tag","name":"Release $tag","assets":[$list],"body":"notes"}"""
    }

    @Test fun `isNewer compares semver numerically`() {
        assertTrue(UpdateChecker.isNewer(latest = "0.30.1", current = "0.9.3"))
        assertTrue(UpdateChecker.isNewer(latest = "1.0.0", current = "0.99.99"))
        assertFalse(UpdateChecker.isNewer(latest = "0.30.1", current = "0.30.1"))
        assertFalse(UpdateChecker.isNewer(latest = "0.30.0", current = "0.30.1"))
        // Debug builds carry a suffix; compare the numeric part only.
        assertTrue(UpdateChecker.isNewer(latest = "0.30.2", current = "0.30.1-debug"))
        assertFalse(UpdateChecker.isNewer(latest = "0.30.1", current = "0.30.1-debug"))
    }

    @Test fun `returns the apk asset when a newer release exists`() = runTest {
        val apk = "https://github.com/Vladutu/copilot/releases/download/v0.31.0/copilot-0.31.0.apk"
        server.enqueue(MockResponse().setBody(release("v0.31.0", "copilot-0.31.0.apk" to apk)))

        val update = checker(current = "0.30.1").check()

        assertEquals(UpdateChecker.Update(version = "0.31.0", apkUrl = apk), update)
    }

    @Test fun `returns null when already on the latest version`() = runTest {
        server.enqueue(MockResponse().setBody(release("v0.30.1", "copilot-0.30.1.apk" to "https://x/a.apk")))
        assertNull(checker(current = "0.30.1").check())
    }

    @Test fun `returns null when the release has no apk asset`() = runTest {
        server.enqueue(MockResponse().setBody(release("v0.31.0", "notes.txt" to "https://x/notes.txt")))
        assertNull(checker(current = "0.30.1").check())
    }

    @Test fun `returns null on http error instead of throwing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        assertNull(checker(current = "0.30.1").check())
    }

    @Test fun `returns null on malformed body instead of throwing`() = runTest {
        server.enqueue(MockResponse().setBody("<html>rate limited</html>"))
        assertNull(checker(current = "0.30.1").check())
    }

    @Test fun `asks github for json with a copilot user agent`() = runTest {
        server.enqueue(MockResponse().setBody(release("v0.30.1", "copilot-0.30.1.apk" to "https://x/a.apk")))
        checker(current = "0.30.1").check()
        val request = server.takeRequest()
        assertEquals("/repos/Vladutu/copilot/releases/latest", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Copilot/"))
    }
}
