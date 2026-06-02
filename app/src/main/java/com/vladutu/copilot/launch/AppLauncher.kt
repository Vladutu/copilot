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
        val targetPkg = targetPackageFor(msg.cmd)
            ?: return Result.Failed("unknown command: ${msg.cmd}")
        val missingMsg = when (msg.cmd) {
            "ytmusic" -> "YouTube Music not installed"
            "waze" -> "Waze not installed"
            else -> "target app not installed"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(launchUri(msg))).apply {
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

        fun targetPackageFor(cmd: String): String? = when (cmd) {
            "ytmusic" -> YT_MUSIC_PKG
            "waze" -> WAZE_PKG
            else -> null
        }

        fun launchUri(msg: Message): String = msg.url
    }
}
