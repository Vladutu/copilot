package com.vladutu.copilot.boot

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vladutu.copilot.CopilotApp
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.R
import com.vladutu.copilot.diagnostics.DiagnosticLog

/**
 * One-shot foreground service started by [BootReceiver] solely to bypass the Background
 * Activity Launch restriction. A BroadcastReceiver's process counts as background, so
 * `startActivity` from it gets silently dropped on Android 10+. A foreground service is
 * in foreground state when it calls `startActivity`, which is an explicit BAL exemption
 * on Android 10–13. (Android 14+ tightens this further; if the carbox is on 14 we may
 * still get dropped, in which case BootReceiver's diagnostic log will show the failure.)
 */
class BootLaunchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DiagnosticLog.i("BootLaunch", "onStartCommand")
        startForeground(NOTIF_ID, buildNotification())
        try {
            val launch = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            DiagnosticLog.i("BootLaunch", "startActivity called from FGS")
        } catch (t: Throwable) {
            DiagnosticLog.e("BootLaunch", "FGS startActivity failed (${t.javaClass.simpleName})", t)
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CopilotApp.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Copilot")
            .setContentText("Starting…")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val NOTIF_ID = 2
    }
}
