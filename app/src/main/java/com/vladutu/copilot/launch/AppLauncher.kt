package com.vladutu.copilot.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.vladutu.copilot.net.Message

class AppLauncher(private val context: Context) {

    sealed class Result {
        object Ok : Result()
        data class Failed(val reason: String) : Result()
    }

    fun launch(msg: Message): Result {
        val (uri, targetPkg, missingMsg) = when (msg.cmd) {
            "ytmusic" -> {
                val u = buildLaunchUri(msg) ?: return Result.Failed("unknown form: ${msg.form}")
                Triple(u, YT_MUSIC_PKG, "YouTube Music not installed")
            }
            "waze" -> {
                val u = buildLaunchUri(msg) ?: return Result.Failed("missing waze url")
                Triple(u, WAZE_PKG, "Waze not installed")
            }
            else -> return Result.Failed("unknown command: ${msg.cmd}")
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "$targetPkg not installed", e)
            Result.Failed(missingMsg)
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    internal companion object {
        const val TAG = "AppLauncher"
        const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
        const val WAZE_PKG = "com.waze"

        /**
         * Returns the deep-link URI for [msg]. Returns null when the message is unusable
         * (unknown form for ytmusic, blank url for waze).
         */
        fun buildLaunchUri(msg: Message): String? = when (msg.cmd) {
            "ytmusic" -> when (msg.form) {
                "playlist" -> "https://music.youtube.com/watch?list=${msg.id}"
                "song" -> "https://music.youtube.com/watch?v=${msg.id}"
                else -> null
            }
            "waze" -> msg.url.takeIf { it.isNotBlank() }
            else -> null
        }
    }
}
