package com.vladutu.copilot.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message

class AppLauncher(private val context: Context) {

    sealed class Result {
        object Ok : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Entry point for Pilot-driven launches via ListenerService. */
    fun launch(msg: Message): Result =
        if (msg.cmd == "radio") launchRadio(msg.url, msg.title)
        else launchUrl(msg.cmd, msg.form, msg.url)

    /** Entry point for UI-driven re-plays from a saved tile. */
    fun replay(item: SavedItem): Result =
        if (item.form == Form.RADIO) launchRadio(item.url, item.title)
        else launchUrl(cmdForForm(item.form), item.form, item.url)

    /** Open Waze app (no nav target). */
    fun openWazeApp(): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(WAZE_PKG)
            ?: return Result.Failed("Waze not installed")
        return startNewTask(launch)
    }

    /** Open Google Maps app (no nav target). */
    fun openMapsApp(): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(MAPS_PKG)
            ?: return Result.Failed("Google Maps not installed")
        return startNewTask(launch)
    }

    private fun cmdForForm(form: Form) = when (form) {
        Form.PLAYLIST, Form.SONG -> "ytmusic"
        Form.DESTINATION -> "waze"
        Form.RADIO -> "radio"
    }

    private fun launchUrl(cmd: String, form: Form, url: String): Result {
        // Maps share URLs (maps.app.goo.gl/...) are Firebase App Links — Maps' package
        // doesn't claim them in intent-filters, only via verified App Links at the OS
        // resolver level. So for cmd=maps we leave the package unset and let Android
        // route the URL; for ytmusic/waze we pin the package to prevent a browser fallback.
        val targetPkg: String? = when (cmd) {
            "ytmusic" -> YT_MUSIC_PKG
            "waze" -> WAZE_PKG
            "maps" -> null
            else -> return Result.Failed("unknown command: $cmd")
        }
        val missingMsg = when (cmd) {
            "ytmusic" -> "YouTube Music not installed"
            "waze" -> "Waze not installed"
            "maps" -> "Google Maps not installed"
            else -> "target app not installed"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (targetPkg != null) setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity for cmd=$cmd pkg=${targetPkg ?: "<unset>"} url=$url", e)
            Result.Failed(missingMsg)
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
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

    private fun launchRadio(url: String, title: String?): Result {
        return try {
            context.startActivity(buildRadioIntent(url, title))
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity for radio url=$url", e)
            Result.Failed("VLC not installed")
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    private fun startNewTask(intent: Intent): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        const val WAZE_PKG = "com.waze"
        const val MAPS_PKG = "com.google.android.apps.maps"
        const val VLC_PKG = "org.videolan.vlc"
    }
}
