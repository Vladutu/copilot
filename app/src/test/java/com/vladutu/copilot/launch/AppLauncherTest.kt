package com.vladutu.copilot.launch

import com.vladutu.copilot.net.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLauncherTest {

    private fun msg(form: String, id: String) =
        Message(v = 1, ts = 1L, cmd = "ytmusic", form = form, id = id)

    @Test
    fun `buildLaunchUri for playlist form returns shuffled watch_list URL`() {
        val uri = AppLauncher.buildLaunchUri(msg(form = "playlist", id = "PLabc"))
        assertEquals("https://music.youtube.com/watch?list=PLabc&shuffle=1", uri)
    }

    @Test
    fun `buildLaunchUri for song form returns watch_v URL`() {
        val uri = AppLauncher.buildLaunchUri(msg(form = "song", id = "dQw4w9WgXcQ"))
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", uri)
    }

    @Test
    fun `buildLaunchUri for unknown form returns null`() {
        assertNull(AppLauncher.buildLaunchUri(msg(form = "album", id = "x")))
    }

    @Test
    fun `buildLaunchUri returns url verbatim for waze`() {
        val msg = Message(
            v = 1,
            ts = 0L,
            cmd = "waze",
            url = "https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes",
        )
        assertEquals("https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes", AppLauncher.buildLaunchUri(msg))
    }

    @Test
    fun `buildLaunchUri returns null for waze with blank url`() {
        val msg = Message(v = 1, ts = 0L, cmd = "waze", url = "")
        assertNull(AppLauncher.buildLaunchUri(msg))
    }
}
