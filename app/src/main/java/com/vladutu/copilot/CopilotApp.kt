package com.vladutu.copilot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.vladutu.copilot.di.ServiceLocator

class CopilotApp : Application() {

    val locator: ServiceLocator by lazy { ServiceLocator(applicationContext) }

    val applicationScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

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
