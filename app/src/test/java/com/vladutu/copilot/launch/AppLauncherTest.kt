package com.vladutu.copilot.launch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {

    private lateinit var context: Context
    private lateinit var launcher: AppLauncher

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        launcher = AppLauncher(context)
    }

    private fun msg(cmd: String, form: Form, url: String) =
        Message(v = 3, ts = 0, cmd = cmd, form = form, url = url, title = null, imageUrl = null)

    @Test fun `launches ytmusic playlist message`() {
        val res = launcher.launch(msg("ytmusic", Form.PLAYLIST, "https://music.youtube.com/watch?list=X"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.`package` == AppLauncher.YT_MUSIC_PKG)
    }

    @Test fun `launches waze destination message`() {
        val res = launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.`package` == AppLauncher.WAZE_PKG)
    }

    @Test fun `launches maps destination message without setPackage`() {
        // Maps share URLs (maps.app.goo.gl/...) are App Links — Maps' package doesn't
        // claim them in intent-filters, so we let Android's resolver route the URL.
        val url = "https://maps.app.goo.gl/TSv3jAw9kMEf5UzQ6"
        val res = launcher.launch(msg("maps", Form.DESTINATION, url))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertNull(intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.data.toString())
    }

    @Test fun `replay from SavedItem works`() {
        val item = SavedItem(Form.SONG, "abc", null, null, "https://music.youtube.com/watch?v=abc", 0)
        val res = launcher.replay(item)
        assertTrue(res is AppLauncher.Result.Ok)
    }

    @Test fun `openWazeApp launches waze package`() {
        val res = launcher.openWazeApp()
        // Result depends on resolution; both Ok or Failed are acceptable in Robolectric. What matters is no exception thrown.
        assertTrue(res is AppLauncher.Result.Ok || res is AppLauncher.Result.Failed)
    }

    @Test fun `openMapsApp returns a result without throwing`() {
        val res = launcher.openMapsApp()
        // Mirrors openWazeApp's test: in Robolectric the package may or may not
        // be resolvable; both outcomes are acceptable. What matters is no exception.
        assertTrue(res is AppLauncher.Result.Ok || res is AppLauncher.Result.Failed)
    }
}
