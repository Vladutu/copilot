package be.doccle.copilot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import be.doccle.copilot.CopilotApp
import be.doccle.copilot.R
import be.doccle.copilot.StatusActivity
import be.doccle.copilot.config.Config
import be.doccle.copilot.launch.AppLauncher
import be.doccle.copilot.net.Message
import be.doccle.copilot.net.NtfySubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ConnState {
    object Connected : ConnState()
    object Reconnecting : ConnState()
    data class Error(val message: String) : ConnState()
}

data class UiState(
    val conn: ConnState = ConnState.Reconnecting,
    val lastMessage: Message? = null,
    val lastLaunchOk: Boolean? = null,
    val lastLaunchError: String? = null,
)

class ListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UiState())

    private var subscribeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (subscribeJob == null) subscribeJob = scope.launch { runLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        instance = null
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

        _state.value = _state.value.copy(conn = ConnState.Reconnecting)

        subscriber.subscribe().collect { msg ->
            _state.value = _state.value.copy(conn = ConnState.Connected, lastMessage = msg)
            val result = withContext(Dispatchers.Main) { launcher.launch(msg) }
            _state.value = when (result) {
                is AppLauncher.Result.Ok ->
                    _state.value.copy(lastLaunchOk = true, lastLaunchError = null)
                is AppLauncher.Result.Failed -> {
                    Log.w(TAG, "launch failed: ${result.reason}")
                    _state.value.copy(lastLaunchOk = false, lastLaunchError = result.reason)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, StatusActivity::class.java),
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

        // The service is effectively a singleton at runtime; the Activity reads state
        // through this rather than a full bind/unbind (overkill for MVP).
        @Volatile private var instance: ListenerService? = null

        fun state(): StateFlow<UiState>? = instance?._state?.asStateFlow()
    }
}
