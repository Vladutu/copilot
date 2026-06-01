package com.vladutu.copilot.launch

import com.vladutu.copilot.net.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLauncherTest {

    private fun msg(form: String, id: String) =
        Message(v = 1, ts = 1L, cmd = "ytmusic", form = form, id = id)

    @Test
    fun `buildYtMusicUri for playlist form returns watch_list URL`() {
        val uri = AppLauncher.buildYtMusicUri(msg(form = "playlist", id = "PLabc"))
        assertEquals("https://music.youtube.com/watch?list=PLabc", uri)
    }

    @Test
    fun `buildYtMusicUri for song form returns watch_v URL`() {
        val uri = AppLauncher.buildYtMusicUri(msg(form = "song", id = "dQw4w9WgXcQ"))
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", uri)
    }

    @Test
    fun `buildYtMusicUri for unknown form returns null`() {
        assertNull(AppLauncher.buildYtMusicUri(msg(form = "album", id = "x")))
    }
}
