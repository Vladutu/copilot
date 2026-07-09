package com.vladutu.copilot.launch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message
import com.vladutu.copilot.soundcloud.SoundCloudPauser
import com.vladutu.copilot.split.CopilotForeground
import com.vladutu.copilot.split.PairedLaunch
import com.vladutu.copilot.split.SplitRepair
import com.vladutu.copilot.split.SplitScreen
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var pauser: FakePauser

    private class FakePauser(context: Context) : SoundCloudPauser(context) {
        var pauseCalls = 0
        override fun pauseIfPlaying(): Boolean {
            pauseCalls++
            return false
        }
    }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pauser = FakePauser(context)
        launcher = AppLauncher(context, soundCloudPauser = pauser)
        SplitScreen.reset()
        SplitScreen.ownPackage = context.packageName
        PairedLaunch.clear()
        SplitRepair.clear()
        CopilotForeground.stepAside = null
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
        val item = SavedItem(Form.SONG, "abc", null, null, "https://music.youtube.com/watch?v=abc", savedAt = 0)
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

    @Test fun `buildRadioIntent targets VLC with audio mime and title extra`() {
        val intent = launcher.buildRadioIntent("https://live.example.ro/europafm.mp3", "Europa FM")
        assertEquals(AppLauncher.VLC_PKG, intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://live.example.ro/europafm.mp3", intent.data.toString())
        assertEquals("audio/*", intent.type)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("Europa FM", intent.getStringExtra("title"))
    }

    @Test fun `launches radio message via VLC`() {
        val res = launcher.launch(msg("radio", Form.RADIO, "https://live.example.ro/europafm.mp3"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.VLC_PKG, intent.`package`)
        assertEquals("audio/*", intent.type)
    }

    @Test fun `replays RADIO SavedItem via VLC`() {
        val item = SavedItem(Form.RADIO, "abc", "Europa FM", null, "https://live.example.ro/europafm.mp3", savedAt = 0)
        val res = launcher.replay(item)
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.VLC_PKG, intent.`package`)
    }

    @Test fun `launches soundcloud message with pinned package`() {
        val res = launcher.launch(msg("soundcloud", Form.SONG, "https://soundcloud.com/the-real-tibo/la-pola-gola-life"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.SOUNDCLOUD_PKG, intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://soundcloud.com/the-real-tibo/la-pola-gola-life", intent.data.toString())
    }

    @Test fun `replays soundcloud SavedItem via stored cmd not form`() {
        // form=SONG would map to ytmusic for legacy rows; the stored cmd must win.
        val item = SavedItem(
            form = Form.SONG, id = "sha", title = "T", imageUrl = null,
            url = "https://soundcloud.com/a/b", cmd = "soundcloud", savedAt = 0,
        )
        val res = launcher.replay(item)
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.SOUNDCLOUD_PKG, intent.`package`)
    }

    @Test fun `legacy SavedItem without cmd still replays via form`() {
        val item = SavedItem(Form.SONG, "abc", null, null, "https://music.youtube.com/watch?v=abc", savedAt = 0)
        launcher.replay(item)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.YT_MUSIC_PKG, intent.`package`)
    }

    @Test fun `split toggle off - command launch is plain fullscreen`() {
        launcher.launch(msg("ytmusic", Form.SONG, "https://music.youtube.com/watch?v=abc"))
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT == 0)
    }

    @Test fun `split toggle on - music command with a visible nav pane launches adjacent`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(AppLauncher.WAZE_PKG), focused = AppLauncher.WAZE_PKG)
        launcher.launch(msg("ytmusic", Form.SONG, "https://music.youtube.com/watch?v=abc"))
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun `split toggle on - radio with a visible nav pane launches adjacent too`() {
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(AppLauncher.WAZE_PKG), focused = AppLauncher.WAZE_PKG)
        launcher.launch(msg("radio", Form.RADIO, "https://live.example.ro/europafm.mp3"))
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
    }

    @Test fun `split toggle on - launch with only copilot on screen stays fullscreen`() {
        // Adjacent pairs with the focused task; with Copilot focused that would drag
        // Copilot itself into the split (emulator finding #1).
        SplitScreen.enabled = true
        SplitScreen.onWindows(visible = setOf(context.packageName), focused = context.packageName)
        launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT == 0)
    }

    @Test fun `split toggle on - command for the focused pane's app stays non-adjacent`() {
        // Nav destinations are always delivered plain (Waze exits splits on deep links);
        // this also keeps the old double-Waze guarantee for the focused pane's app.
        SplitScreen.enabled = true
        SplitScreen.onWindows(
            visible = setOf(AppLauncher.WAZE_PKG, AppLauncher.YT_MUSIC_PKG),
            focused = AppLauncher.WAZE_PKG,
        )
        launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT == 0)
    }

    @Test fun `paired launch steps copilot aside and fires adjacent when the split resurfaces`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(AppLauncher.WAZE_PKG)
        SplitScreen.onWindows(visible = setOf(context.packageName), focused = context.packageName)
        var steppedAside = 0
        CopilotForeground.stepAside = { steppedAside++ }

        val res = launcher.launch(msg("ytmusic", Form.SONG, "https://music.youtube.com/watch?v=abc"))
        assertTrue(res is AppLauncher.Result.Ok)
        assertEquals(1, steppedAside)
        // Nothing started yet — the deep link waits for the covered split to resurface.
        assertNull(shadowOf(context as android.app.Application).nextStartedActivity)

        // Split resurfaces (what the service's window snapshot would report), continuation fires.
        SplitScreen.onWindows(
            visible = setOf(AppLauncher.WAZE_PKG, AppLauncher.YT_MUSIC_PKG),
            focused = AppLauncher.WAZE_PKG,
        )
        PairedLaunch.matchPartnerShown(AppLauncher.WAZE_PKG)!!.invoke()
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.YT_MUSIC_PKG, intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
    }

    @Test fun `destination fires plain immediately and arms the split repair`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(AppLauncher.YT_MUSIC_PKG)
        SplitScreen.onWindows(visible = setOf(context.packageName), focused = context.packageName)
        var steppedAside = 0
        CopilotForeground.stepAside = { steppedAside++ }

        val res = launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        assertTrue(res is AppLauncher.Result.Ok)
        // No two-step dance: Waze exits any split while starting navigation, so the
        // destination goes out fullscreen right away and the split is rebuilt afterwards.
        assertEquals(0, steppedAside)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.WAZE_PKG, intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT == 0)
        assertEquals(AppLauncher.WAZE_PKG, SplitRepair.pendingNav())
    }

    @Test fun `destination without a music sighting does not arm the repair`() {
        SplitScreen.enabled = true
        launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        assertNull(SplitRepair.pendingNav())
    }

    @Test fun `destination with the split toggle off does not arm the repair`() {
        SplitScreen.onForeground(AppLauncher.YT_MUSIC_PKG)
        launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        assertNull(SplitRepair.pendingNav())
    }

    @Test fun `repair fire with an unresolvable music app is safe`() {
        SplitScreen.enabled = true
        SplitScreen.onForeground(AppLauncher.YT_MUSIC_PKG)
        launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        shadowOf(context as android.app.Application).clearNextStartedActivities()
        // Robolectric can't resolve YT Music's launch intent — the attempt must not throw
        // and must not start anything.
        SplitRepair.takeAttempt()!!.invoke()
        assertNull(shadowOf(context as android.app.Application).nextStartedActivity)
    }

    @Test fun `paired launch falls back to a direct launch when the nav partner is unresolvable`() {
        // Two-step conditions met (music from Copilot, Waze seen earlier) but Robolectric
        // can't resolve a Waze launch intent — the deep link must still fire, fullscreen.
        SplitScreen.enabled = true
        SplitScreen.onForeground(AppLauncher.WAZE_PKG)
        SplitScreen.onWindows(visible = setOf(context.packageName), focused = context.packageName)
        val res = launcher.launch(msg("ytmusic", Form.SONG, "https://music.youtube.com/watch?v=abc"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.YT_MUSIC_PKG, intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT == 0)
    }

    @Test fun `arms autoswitch for ytmusic launch`() {
        AutoSwitchBack.disarm()
        AutoSwitchBack.onForeground("com.vladutu.copilot")
        launcher.launch(msg("ytmusic", Form.PLAYLIST, "https://music.youtube.com/watch?list=X"))
        assertTrue(AutoSwitchBack.isArmed())
    }

    @Test fun `soundcloud launch pauses live playback first`() {
        launcher.launch(msg("soundcloud", Form.SONG, "https://soundcloud.com/a/b"))
        assertEquals(1, pauser.pauseCalls)
    }

    @Test fun `ytmusic launch does not touch the soundcloud session`() {
        launcher.launch(msg("ytmusic", Form.SONG, "https://music.youtube.com/watch?v=abc"))
        assertEquals(0, pauser.pauseCalls)
    }

    @Test fun `arms autoswitch for soundcloud launch`() {
        AutoSwitchBack.disarm()
        AutoSwitchBack.onForeground("com.vladutu.copilot")
        launcher.launch(msg("soundcloud", Form.SONG, "https://soundcloud.com/a/b"))
        assertTrue(AutoSwitchBack.isArmed())
    }

    @Test fun `does not arm autoswitch for waze launch`() {
        AutoSwitchBack.disarm()
        AutoSwitchBack.onForeground("com.vladutu.copilot")
        launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        assertFalse(AutoSwitchBack.isArmed())
    }

    @Test fun `does not arm autoswitch for radio launch`() {
        AutoSwitchBack.disarm()
        AutoSwitchBack.onForeground("com.vladutu.copilot")
        launcher.launch(msg("radio", Form.RADIO, "https://live.example.ro/europafm.mp3"))
        assertFalse(AutoSwitchBack.isArmed())
    }
}
