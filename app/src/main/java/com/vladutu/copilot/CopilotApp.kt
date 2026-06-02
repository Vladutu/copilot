package com.vladutu.copilot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import com.vladutu.copilot.di.ServiceLocator
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CopilotApp : Application() {

    val locator: ServiceLocator by lazy { ServiceLocator(applicationContext) }

    val applicationScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
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

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashToDownloads(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashToDownloads(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("=== Copilot crash at $timestamp ===")
            appendLine("Thread: ${thread.name}")
            appendLine(
                "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / " +
                    "Android ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})"
            )
            appendLine()
            appendLine(sw.toString())
        }
        val filename = "copilot-crash-${System.currentTimeMillis()}.txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray()) }
    }

    companion object {
        const val CHANNEL_ID = "copilot-listener"
    }
}
