package com.vladutu.copilot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vladutu.copilot.CopilotApp
import com.vladutu.copilot.R
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.config.Config
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.history.from
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.net.NtfySubscriber
import com.vladutu.copilot.net.ParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ConnState {
    object Connected : ConnState()
    object Reconnecting : ConnState()
    data class Error(val message: String) : ConnState()
}

/** One row in the status screen's "recent events" list. */
data class RecentEvent(
    val timeSec: Long,
    val text: String,
    val ok: Boolean,
)

data class UiState(
    val conn: ConnState = ConnState.Reconnecting,
    val recent: List<RecentEvent> = emptyList(),
    /** Most recent (now - msg.ts) we observed. Positive = box clock ahead of phone. */
    val skewSec: Long? = null,
)

class ListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var subscribeJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (subscribeJob == null) subscribeJob = scope.launch { runLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runLoop() {
        val subscriber = NtfySubscriber(
            base = Config.NTFY_BASE,
            topic = Config.NTFY_TOPIC,
            maxAgeSec = Config.MAX_MESSAGE_AGE_SEC,
        )
        val launcher = AppLauncher(applicationContext)
        val app = applicationContext as CopilotApp
        val history = app.locator.historyRepository
        val artwork = app.locator.artworkCache

        state.value = state.value.copy(conn = ConnState.Reconnecting)

        subscriber.subscribe().collect { result ->
            // Any result coming through means the stream is alive.
            state.value = state.value.copy(conn = ConnState.Connected)

            when (result) {
                is ParseResult.Accepted -> {
                    val msg = result.message
                    val outcome = withContext(Dispatchers.Main) { launcher.launch(msg) }
                    val ok = outcome is AppLauncher.Result.Ok
                    val label = when (msg.cmd) {
                        "ytmusic" -> "play"
                        "waze" -> "navigate"
                        else -> msg.cmd
                    }
                    val text = when (outcome) {
                        AppLauncher.Result.Ok -> "▶ $label · launched"
                        is AppLauncher.Result.Failed -> {
                            Log.w(TAG, "launch failed: ${outcome.reason}")
                            "✗ $label · ${outcome.reason}"
                        }
                    }
                    if (ok) {
                        val savedAt = System.currentTimeMillis() / 1000L
                        val item = SavedItem.from(msg, savedAt)
                        history.save(item)
                        msg.imageUrl?.let { imgUrl ->
                            scope.launch { artwork.download(imgUrl, item.form, item.id) }
                        }
                    }
                    appendRecent(text, ok = ok, skewSec = result.skewSec)
                }
                is ParseResult.Rejected -> {
                    appendRecent("✗ ${result.reason}", ok = false, skewSec = result.skewSec)
                }
                ParseResult.Skipped -> Unit // never reaches us; subscriber filters
            }
        }
    }

    private fun appendRecent(text: String, ok: Boolean, skewSec: Long?) {
        val event = RecentEvent(
            timeSec = System.currentTimeMillis() / 1000L,
            text = text,
            ok = ok,
        )
        val newRecent = (listOf(event) + state.value.recent).take(RECENT_EVENTS_MAX)
        state.value = state.value.copy(
            recent = newRecent,
            // Only update skew when we have a fresh observation; otherwise keep what we knew.
            skewSec = skewSec ?: state.value.skewSec,
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CopilotApp.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Copilot listening")
            .setContentText("Connected to relay")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "ListenerService"
        const val NOTIF_ID = 1
        const val RECENT_EVENTS_MAX = 5

        // Process-scoped so the Activity can bind to it before the service's onCreate runs.
        // (A service-instance-scoped flow loses the race on cold start: Activity composes,
        // captures null/fallback in remember, then service starts and writes to a flow
        // nobody is observing.)
        val state = MutableStateFlow(UiState())
    }
}
