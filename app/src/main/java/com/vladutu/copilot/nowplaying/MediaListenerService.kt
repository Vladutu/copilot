package com.vladutu.copilot.nowplaying

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import com.vladutu.copilot.autoswitch.AutoSwitchBack
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

/** Android-free view of one music-app media session, listed in the OS's priority order. */
data class SessionSnapshot(
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
)

/**
 * Pure: pick the session the strip should show. An actively playing session always wins;
 * with none playing, the highest-priority session (the OS lists most-recently-active
 * first) is shown. A playing session with no title yet yields null rather than falling
 * back to a paused one — its metadata callback fills the title moments later, and showing
 * the *other* app's song in the meantime would be a lie. Top-level so it is
 * JVM-unit-testable.
 */
fun pickNowPlaying(sessions: List<SessionSnapshot>): NowPlaying? {
    val best = sessions.firstOrNull { it.isPlaying } ?: sessions.firstOrNull()
    return best?.let { nowPlayingFrom(it.title, it.artist, it.albumArtist) }
}

/**
 * Watches the media sessions of every supported music app (YT Music, SoundCloud — see
 * [AutoSwitchBack.MUSIC_PKGS]) and republishes the winning session's title/artist as a
 * process-scoped StateFlow ([nowPlaying]). "Winning" is decided by [pickNowPlaying]:
 * the app actually playing beats one sitting paused.
 *
 * All music sessions are watched *simultaneously* — rather than binding to just the
 * current winner — because Android does not reliably announce priority reorders via
 * OnActiveSessionsChangedListener. With every controller subscribed, the moment either
 * app starts playing, its own callback fires and [reevaluate] flips the strip; no
 * sessions-changed event needed.
 *
 * Requires the one-time Notification access grant. With no music session active — or
 * access not granted — the flow is null and the now-playing UI shows its idle state.
 *
 * Mirrors ListenerService's companion-flow pattern so a Composable can collect it
 * regardless of service lifecycle. Threading: every callback lands on the main looper
 * (registration happens there), so state needs no locking.
 */
class MediaListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null

    /** Music-app controllers in OS priority order, refreshed on every sessions change. */
    private var sessions: List<MediaController> = emptyList()

    /** One registered callback per watched session, keyed by token (MediaController has
     *  no useful equality; tokens compare by underlying binder). */
    private val callbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(controllers ?: emptyList())
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
        bind(emptyList())
    }

    /** Sync the watch set to the current music-app sessions, then re-derive the strip. */
    private fun bind(controllers: List<MediaController>) {
        sessions = controllers.filter { it.packageName in AutoSwitchBack.MUSIC_PKGS }
        val current = sessions.associateBy { it.sessionToken }
        (callbacks.keys - current.keys).forEach { token ->
            callbacks.remove(token)?.let { (controller, cb) -> controller.unregisterCallback(cb) }
        }
        current.forEach { (token, controller) ->
            if (token !in callbacks) {
                val cb = callbackFor(controller)
                controller.registerCallback(cb)
                callbacks[token] = controller to cb
            }
        }
        reevaluate()
    }

    /** Every event on any watched session funnels into one re-evaluation of the winner. */
    private fun callbackFor(controller: MediaController) = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = reevaluate()
        override fun onPlaybackStateChanged(state: PlaybackState?) = reevaluate()
        override fun onSessionDestroyed() {
            callbacks.remove(controller.sessionToken)?.let { (c, cb) -> c.unregisterCallback(cb) }
            sessions = sessions.filterNot { it.sessionToken == controller.sessionToken }
            reevaluate()
        }
    }

    private fun reevaluate() {
        nowPlayingState.value = pickNowPlaying(sessions.map { it.snapshot() })
    }

    private fun MediaController.snapshot() = SessionSnapshot(
        isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        albumArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
    )

    companion object {
        private const val TAG = "MediaListener"

        private val nowPlayingState = MutableStateFlow<NowPlaying?>(null)
        val nowPlaying: StateFlow<NowPlaying?> = nowPlayingState
    }
}
