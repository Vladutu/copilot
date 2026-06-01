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
        if (msg.cmd != "ytmusic" || msg.form != "playlist") {
            return Result.Failed("unknown command: ${msg.cmd}/${msg.form}")
        }
        val uri = Uri.parse("https://music.youtube.com/watch?list=${msg.id}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(YT_MUSIC_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "YT Music not installed", e)
            Result.Failed("YouTube Music not installed")
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    private companion object {
        const val TAG = "AppLauncher"
        const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
    }
}
