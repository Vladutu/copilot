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
import com.vladutu.copilot.split.CopilotForeground
import com.vladutu.copilot.split.PairedLaunch
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
            "soundcloud" -> SOUNDCLOUD_PKG
            "waze" -> WAZE_PKG
            "maps" -> null
            else -> return Result.Failed("unknown command: $cmd")
        }
        val missingMsg = when (cmd) {
            "ytmusic" -> "YouTube Music not installed"
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
        // background) and maps/waze (nav stays foreground) are deliberately not armed.
        return dispatch(intent, policyPkg, missingMsg, armAutoSwitch = cmd == "ytmusic" || cmd == "soundcloud")
    }

    /**
     * Start [intent] under the split policy. Shapes:
     *  - paired launch (from-Copilot music launch, [SplitScreen.pairingPartnerFor]): get the
     *    nav partner on screen, then fire the deep link once the accessibility service
     *    confirms it's visible. When Copilot is the foreground activity it steps aside first
     *    (moveTaskToBack): a split covered by Copilot resurfaces intact, whereas relaunching
     *    one of its members with startActivity rips it out and dissolves the pair
     *    (emulator-verified). Only if nothing resurfaces within the reveal TTL do we launch
     *    the partner ourselves. The adjacent-vs-fullscreen flag is decided at fire time from
     *    the fresh window snapshot, and a final TTL guarantees the song plays regardless.
     *  - single adjacent launch when a visible partner pane already exists;
     *  - plain fullscreen otherwise (or whenever the split toggle is off).
     * Auto-return is deliberately not armed on the paired path: the driver asked for the
     * split, so pulling Copilot (the arm-time foreground) back on top would cover it.
     */
    private fun dispatch(intent: Intent, targetPkg: String, missingMsg: String, armAutoSwitch: Boolean): Result {
        val partner = SplitScreen.pairingPartnerFor(targetPkg)
        if (partner != null) {
            val fire = {
                val adjacent = SplitScreen.launchAdjacent(targetPkg)
                if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                DiagnosticLog.i(TAG, "paired fire $targetPkg adjacent=$adjacent — ${SplitScreen.stateForDiagnostics()}")
                startSilently(intent)
            }
            val stepAside = CopilotForeground.stepAside
            if (stepAside != null) {
                DiagnosticLog.i(TAG, "paired launch: stepping aside, waiting for $partner — ${SplitScreen.stateForDiagnostics()}")
                val token = PairedLaunch.arm(partner, paired = fire, timeout = { launchPartner(partner, fire) })
                handler.postDelayed({ PairedLaunch.takeTimeout(token)?.invoke() }, PairedLaunch.REVEAL_TTL_MS)
                stepAside()
            } else {
                DiagnosticLog.i(TAG, "paired launch: $partner first — ${SplitScreen.stateForDiagnostics()}")
                launchPartner(partner, fire)
            }
            return Result.Ok
        }
        return startDirect(intent, targetPkg, missingMsg, armAutoSwitch)
    }

    /** Second stage when no covered split resurfaced (or Copilot wasn't the foreground
     *  activity): bring the partner up ourselves, fire on the service's confirmation, with
     *  a TTL so the song still plays if the partner never shows. */
    private fun launchPartner(partner: String, fire: () -> Unit) {
        val partnerIntent = context.packageManager.getLaunchIntentForPackage(partner)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val started = partnerIntent != null && runCatching { context.startActivity(partnerIntent) }.isSuccess
        if (!started) {
            DiagnosticLog.w(TAG, "pair partner $partner could not start — firing directly")
            fire()
            return
        }
        DiagnosticLog.i(TAG, "pair partner $partner launched, firing on confirm")
        val token = PairedLaunch.arm(partner, paired = fire, timeout = {
            DiagnosticLog.w(TAG, "pair TTL expired ($partner never confirmed) — firing anyway")
            fire()
        })
        handler.postDelayed({ PairedLaunch.takeTimeout(token)?.invoke() }, PairedLaunch.PAIR_TTL_MS)
    }

    private fun startDirect(intent: Intent, targetPkg: String, missingMsg: String, armAutoSwitch: Boolean): Result {
        val adjacent = SplitScreen.launchAdjacent(targetPkg)
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

    /** Deferred half of a paired launch: by the time it fires the command was already
     *  acked, so failures can only be logged, not surfaced. */
    private fun startSilently(intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onSuccess { DiagnosticLog.i(TAG, "paired second stage started: ${intent.`package` ?: intent.data} flags=0x${intent.flags.toString(16)}") }
            .onFailure { DiagnosticLog.w(TAG, "paired launch failed for ${intent.`package` ?: intent.data}", it) }
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
        const val SOUNDCLOUD_PKG = "com.soundcloud.android"
        const val WAZE_PKG = "com.waze"
        const val MAPS_PKG = "com.google.android.apps.maps"
        const val VLC_PKG = "org.videolan.vlc"
    }
}
