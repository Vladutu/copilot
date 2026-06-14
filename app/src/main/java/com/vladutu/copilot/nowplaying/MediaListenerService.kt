package com.vladutu.copilot.nowplaying

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import com.vladutu.copilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pure: build NowPlaying from raw metadata strings. Title is required; artist falls
 *  back to album-artist, then null. Kept top-level so it is JVM-unit-testable. */
fun nowPlayingFrom(title: String?, artist: String?, albumArtist: String?): NowPlaying? {
    val t = title?.trim().orEmpty()
    if (t.isEmpty()) return null
    val a = (artist?.trim()?.takeIf { it.isNotEmpty() })
        ?: albumArtist?.trim()?.takeIf { it.isNotEmpty() }
    return NowPlaying(t, a)
}

/**
 * Reads the active YouTube Music media session and republishes its title/artist as a
 * process-scoped StateFlow ([nowPlaying]). Requires the one-time Notification access
 * grant. When YT Music is not the active session — or access is not granted — the flow
 * is null and the now-playing UI stays hidden.
 *
 * Mirrors ListenerService's companion-flow pattern so a Composable can collect it
 * regardless of service lifecycle.
 */
class MediaListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private var watched: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(controllers ?: emptyList())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(metadata)
        override fun onSessionDestroyed() {
            unwatch()
            nowPlayingState.value = null
        }
    }

    override fun onListenerConnected() {
        val mgr = getSystemService(MediaSessionManager::class.java)
        sessionManager = mgr
        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            mgr.addOnActiveSessionsChangedListener(sessionsListener, component)
            bind(mgr.getActiveSessions(component))
        }.onFailure { DiagnosticLog.w(TAG, "media session listen failed", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unwatch()
        nowPlayingState.value = null
    }

    private fun bind(controllers: List<MediaController>) {
        val yt = controllers.firstOrNull { it.packageName == YT_MUSIC_PKG }
        if (yt == null) {
            unwatch()
            nowPlayingState.value = null
            return
        }
        if (yt == watched) {
            publish(yt.metadata)
            return
        }
        unwatch()
        watched = yt.also { it.registerCallback(controllerCallback) }
        publish(yt.metadata)
    }

    private fun unwatch() {
        watched?.unregisterCallback(controllerCallback)
        watched = null
    }

    private fun publish(metadata: MediaMetadata?) {
        nowPlayingState.value = metadata?.let {
            nowPlayingFrom(
                title = it.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST),
                albumArtist = it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            )
        }
    }

    companion object {
        private const val TAG = "MediaListener"
        const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"

        private val nowPlayingState = MutableStateFlow<NowPlaying?>(null)
        val nowPlaying: StateFlow<NowPlaying?> = nowPlayingState
    }
}
