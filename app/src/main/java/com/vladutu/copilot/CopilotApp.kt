package com.vladutu.copilot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class CopilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Copilot listener",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps Copilot connected to the relay"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "copilot-listener"
    }
}
