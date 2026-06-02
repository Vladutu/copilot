package com.vladutu.copilot.launch

import com.vladutu.copilot.net.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLauncherTest {

    private fun msg(cmd: String, url: String) =
        Message(v = 2, ts = 1L, cmd = cmd, url = url)

    @Test
    fun `targetPackageFor ytmusic returns YouTube Music package`() {
        assertEquals(AppLauncher.YT_MUSIC_PKG, AppLauncher.targetPackageFor("ytmusic"))
    }

    @Test
    fun `targetPackageFor waze returns Waze package`() {
        assertEquals(AppLauncher.WAZE_PKG, AppLauncher.targetPackageFor("waze"))
    }

    @Test
    fun `targetPackageFor unknown cmd returns null`() {
        assertNull(AppLauncher.targetPackageFor("bogus"))
    }

    @Test
    fun `launch uri for ytmusic is the message url verbatim`() {
        val url = "https://music.youtube.com/watch?list=PLabc&shuffle=1"
        assertEquals(url, AppLauncher.launchUri(msg(cmd = "ytmusic", url = url)))
    }

    @Test
    fun `launch uri for waze is the message url verbatim`() {
        val url = "https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes"
        assertEquals(url, AppLauncher.launchUri(msg(cmd = "waze", url = url)))
    }
}
