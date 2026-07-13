package com.vladutu.copilot.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message
import com.vladutu.copilot.soundcloud.SoundCloudPauser
import com.vladutu.copilot.split.SplitFill
import com.vladutu.copilot.split.SplitRepair
import com.vladutu.copilot.split.SplitScreen

class AppLauncher(
    private val context: Context,
    private val soundCloudPauser: SoundCloudPauser = SoundCloudPauser(context),
) {

    private val handler = Handler(Looper.getMainLooper())

    sealed class Result {
        object Ok : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Entry point for Pilot-driven launches via ListenerService. */
    fun launch(msg: Message): Result =
        if (msg.cmd == "radio") launchRadio(msg.url, msg.title)
        else launchUrl(msg.cmd, msg.url)

    /** Entry point for UI-driven re-plays from a saved tile. Legacy rows have no cmd; derive it from form. */
    fun replay(item: SavedItem): Result =
        if (item.form == Form.RADIO) launchRadio(item.url, item.title)
        else launchUrl(item.cmd ?: cmdForForm(item.form), item.url)

    /** Entry point for Discover-driven launches (found playlist or radio mix). */
    fun launchYtMusic(url: String): Result = launchUrl("ytmusic", url)

    /** Open Waze app (no nav target). */
    fun openWazeApp(): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(WAZE_PKG)
            ?: return Result.Failed("Waze not installed")
        return startNewTask(launch, WAZE_PKG)
    }

    /** Open Google Maps app (no nav target). */
    fun openMapsApp(): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(MAPS_PKG)
            ?: return Result.Failed("Google Maps not installed")
        return startNewTask(launch, MAPS_PKG)
    }

    private fun cmdForForm(form: Form) = when (form) {
        Form.PLAYLIST, Form.SONG -> "ytmusic"
        Form.DESTINATION -> "waze"
        Form.RADIO -> "radio"
    }

    private fun launchUrl(cmd: String, url: String): Result {
        // Maps share URLs (maps.app.goo.gl/...) are Firebase App Links — Maps' package
        // doesn't claim them in intent-filters, only via verified App Links at the OS
        // resolver level. So for cmd=maps we leave the package unset and let Android
        // route the URL; for ytmusic/waze we pin the package to prevent a browser fallback.
        val targetPkg: String? = when (cmd) {
            "ytmusic" -> YT_MUSIC_PKG
            "youtube" -> YOUTUBE_PKG
            "soundcloud" -> SOUNDCLOUD_PKG
            "waze" -> WAZE_PKG
            "maps" -> null
            else -> return Result.Failed("unknown command: $cmd")
        }
        val missingMsg = when (cmd) {
            "ytmusic" -> "YouTube Music not installed"
            "youtube" -> "YouTube not installed"
            "soundcloud" -> "SoundCloud not installed"
            "waze" -> "Waze not installed"
            "maps" -> "Google Maps not installed"
            else -> "target app not installed"
        }

        // SoundCloud won't switch to a deep-linked track while another one is actively playing
        // (it shows the page, keeps the old audio). Pausing first makes the deep link take over
        // — the same thing that works manually. No-op when nothing is playing.
        if (cmd == "soundcloud") soundCloudPauser.pauseIfPlaying()

        // maps launches leave intent.package unset (App Links, see above), but the split
        // policy still needs to know which app the command is for.
        val policyPkg = targetPkg ?: MAPS_PKG
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (targetPkg != null) setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Auto-return only for song/playlist launches that take over the foreground (YT Music
        // or SoundCloud; cmds imply form SONG/PLAYLIST via Message validation). Radio (VLC,
        // background), maps/waze (nav stays foreground) and youtube (a video is watched, not
        // background-listened) are deliberately not armed.
        return dispatch(intent, policyPkg, missingMsg, armAutoSwitch = cmd == "ytmusic" || cmd == "soundcloud")
    }

    /**
     * Start [intent] under the split policy. Every delivery is plain fullscreen except one:
     * a music deep link aimed at an already-live split goes adjacent straight into its pane
     * (phone-proven delivery; the flag is inert on the pane the app already occupies).
     * Everything else converges to the split AFTER the delivery via [SplitRepair] — deep
     * links must never create a split (adjacent deep link = half-empty black-pane split on
     * the carbox) and nav apps must never launch adjacent at all (duplicate Waze):
     *  - nav destination: plain, then repair re-attaches the last music app once
     *    navigation has settled;
     *  - music command with a nav app known this drive: plain (the song starts right
     *    away), then repair brings the nav app up and pulls the music task adjacent.
     * Auto-return is not armed when a repair is: the command's end state is the split, so
     * pulling the arm-time foreground back on top would fight the reconciler.
     */
    private fun dispatch(intent: Intent, targetPkg: String, missingMsg: String, armAutoSwitch: Boolean): Result {
        // A new command supersedes any pending rebuild or scaffold fill: a stale stage
        // firing mid-delivery could wreck the fresh launch's windowing.
        SplitRepair.clear()
        SplitFill.clear()
        if (targetPkg in SplitScreen.NAV_PKGS) {
            val music = SplitScreen.repairMusicFor(targetPkg)
            val result = startDirect(intent, targetPkg, missingMsg, armAutoSwitch, forcePlain = true)
            if (result is Result.Ok && music != null) armRepair(targetPkg, music)
            return result
        }
        if (SplitScreen.isSplitActive()) {
            return startDirect(intent, targetPkg, missingMsg, armAutoSwitch)
        }
        val nav = SplitScreen.repairNavFor(targetPkg)
        if (nav != null) {
            val result = startDirect(intent, targetPkg, missingMsg, armAutoSwitch = false, forcePlain = true)
            if (result is Result.Ok) armRepair(nav, targetPkg)
            return result
        }
        // No split intent (toggle off, or no nav app seen): plain — a deep link may never
        // carry the adjacent flag outside a live split.
        return startDirect(intent, targetPkg, missingMsg, armAutoSwitch, forcePlain = true)
    }

    /** Arm the post-delivery split rebuild: once the service sees [navPkg] settled alone in
     *  the foreground, it invokes this continuation to pull [musicPkg]'s (already playing)
     *  task into the other pane. A plain launch intent, no deep link — the launch-intent
     *  adjacent launch is the one shape the carbox pairs into a real split. */
    private fun armRepair(navPkg: String, musicPkg: String) {
        val fire = fire@{
            val launch = context.packageManager.getLaunchIntentForPackage(musicPkg)
            if (launch == null) {
                DiagnosticLog.w(TAG, "repair fire: no launch intent for $musicPkg")
                return@fire
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val adjacent = SplitScreen.launchAdjacent(musicPkg)
            if (adjacent) launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            DiagnosticLog.i(TAG, "repair fire $musicPkg adjacent=$adjacent — ${SplitScreen.stateForDiagnostics()}")
            startSilently(launch)
        }
        val token = SplitRepair.arm(navPkg, musicPkg, fire)
        handler.postDelayed({ SplitRepair.expire(token) }, SplitRepair.WATCH_WINDOW_MS)
        DiagnosticLog.i(TAG, "repair armed: rebuild $navPkg|$musicPkg once navigation settles — ${SplitScreen.stateForDiagnostics()}")
    }

    private fun startDirect(
        intent: Intent,
        targetPkg: String,
        missingMsg: String,
        armAutoSwitch: Boolean,
        forcePlain: Boolean = false,
    ): Result {
        val adjacent = !forcePlain && SplitScreen.launchAdjacent(targetPkg)
        if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        DiagnosticLog.i(TAG, "direct launch $targetPkg adjacent=$adjacent — ${SplitScreen.stateForDiagnostics()}")
        if (armAutoSwitch) AutoSwitchBack.arm()
        return try {
            context.startActivity(intent)
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity for pkg=$targetPkg url=${intent.data}", e)
            Result.Failed(missingMsg)
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    /** Deferred half of a split repair: by the time it fires the command was already
     *  acked, so failures can only be logged, not surfaced. */
    private fun startSilently(intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onSuccess { DiagnosticLog.i(TAG, "deferred stage started: ${intent.`package` ?: intent.data} flags=0x${intent.flags.toString(16)}") }
            .onFailure { DiagnosticLog.w(TAG, "deferred stage failed for ${intent.`package` ?: intent.data}", it) }
    }

    // Build the VLC launch intent for a radio stream. Internal + pure so it can be
    // asserted in tests without touching the Activity stack.
    //
    // MIME is "audio/*" (opens VLC's audio player). If a station opens VLC but does
    // not auto-play on the carbox, change this to "video/*" (known-reliable) — see plan.
    internal fun buildRadioIntent(url: String, title: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage(VLC_PKG)
            setDataAndTypeAndNormalize(Uri.parse(url), "audio/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            title?.let { putExtra("title", it) }
        }

    private fun launchRadio(url: String, title: String?): Result =
        dispatch(buildRadioIntent(url, title), VLC_PKG, "VLC not installed", armAutoSwitch = false)

    private fun startNewTask(intent: Intent, targetPkg: String): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (SplitScreen.launchAdjacent(targetPkg)) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        return try {
            context.startActivity(intent); Result.Ok
        } catch (e: ActivityNotFoundException) {
            Result.Failed("not installed")
        } catch (e: SecurityException) {
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    companion object {
        const val TAG = "AppLauncher"
        const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
        const val YOUTUBE_PKG = "com.google.android.youtube"
        const val SOUNDCLOUD_PKG = "com.soundcloud.android"
        const val WAZE_PKG = "com.waze"
        const val MAPS_PKG = "com.google.android.apps.maps"
        const val VLC_PKG = "org.videolan.vlc"
    }
}
