package com.vladutu.copilot.nowplaying

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.launch.NewPipeRoute
import com.vladutu.copilot.launch.ShuffleRequest
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
    private val handler = Handler(Looper.getMainLooper())

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
        sessions = controllers.filter { AutoSwitchBack.isMusicApp(it.packageName) }
        val current = sessions.associateBy { it.sessionToken }
        (callbacks.keys - current.keys).forEach { token ->
            callbacks.remove(token)?.let { (controller, cb) -> controller.unregisterCallback(cb) }
        }
        current.forEach { (token, controller) ->
            if (token !in callbacks) {
                val cb = callbackFor(controller)
                controller.registerCallback(cb)
                callbacks[token] = controller to cb
                // A session that appears right after a launch is the freshly started app
                // (no-op unless it already publishes a queue; onQueueChanged covers the rest).
                applyPendingShuffle(controller)
            }
        }
        reevaluate()
    }

    /**
     * Apply a [ShuffleRequest] armed by AppLauncher to NewPipe's session. Only ever acts
     * for NewPipe and only while the Settings toggle is on — nothing here runs otherwise.
     *
     * Trigger: the session has a *published queue* (a fresh session that appears with one,
     * or [MediaController.Callback.onQueueChanged] delivering one). A paused or playing
     * state is not enough: when a new playlist is sent while an old one is still playing,
     * the state flips before the queue is swapped, and the command would shuffle the old
     * queue. The queue appearing is the new playlist.
     *
     * Shuffle on = pause, shuffle, skip to next, then resume once the track changes.
     * NewPipe's shuffle keeps the current item first and randomises the rest, so the skip
     * is what makes the opener random. NewPipe resolves a stream only when its item becomes
     * current, so the random track needs a network round trip before it can play — track
     * one already has its URL and would be heard for those seconds. Pausing makes that gap
     * silent; [resumeWhenSwitched] plays again when the metadata shows a different title
     * (or after [RESUME_FALLBACK_MS], so playback can never stay stuck paused).
     */
    private fun applyPendingShuffle(controller: MediaController) {
        val pkg = controller.packageName
        if (!NewPipeRoute.enabled || pkg != NewPipeRoute.PKG) return
        if (ShuffleRequest.pending(pkg) == null) return
        if (controller.queue.isNullOrEmpty()) return
        val shuffle = ShuffleRequest.take(pkg) ?: return
        runCatching {
            val controls = controller.transportControls
            if (shuffle) {
                val titleAtSkip = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                controls.pause()
                controls.setShuffleModeCompat(SHUFFLE_MODE_ALL)
                controls.skipToNext()
                resumeWhenSwitched(controller, titleAtSkip)
            } else {
                controls.setShuffleModeCompat(SHUFFLE_MODE_NONE)
            }
            DiagnosticLog.i(TAG, "applied shuffle=$shuffle to $pkg (queue=${controller.queue?.size} state=${controller.playbackState?.state})")
        }.onFailure { DiagnosticLog.w(TAG, "shuffle command to $pkg failed", it) }
    }

    /** Pending "play once the track switched" after a shuffle skip: the session it is for
     *  and the title that was current at the skip. Null when nothing is pending. */
    private var resumeToken: MediaSession.Token? = null
    private var resumeTitleAtSkip: String? = null
    private val resumeFallback = Runnable { resume("fallback timer") }

    private fun resumeWhenSwitched(controller: MediaController, titleAtSkip: String?) {
        handler.removeCallbacks(resumeFallback)
        resumeToken = controller.sessionToken
        resumeTitleAtSkip = titleAtSkip
        handler.postDelayed(resumeFallback, RESUME_FALLBACK_MS)
    }

    /** Called on every metadata change: resume once the title differs from the skipped one. */
    private fun maybeResume(controller: MediaController, metadata: MediaMetadata?) {
        if (controller.sessionToken != resumeToken) return
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank() || title == resumeTitleAtSkip) return
        resume("track switched to '$title'")
    }

    private fun resume(reason: String) {
        val token = resumeToken ?: return
        handler.removeCallbacks(resumeFallback)
        resumeToken = null
        resumeTitleAtSkip = null
        val controller = callbacks[token]?.first ?: return
        runCatching { controller.transportControls.play() }
            .onSuccess { DiagnosticLog.i(TAG, "resumed after shuffle skip ($reason)") }
            .onFailure { DiagnosticLog.w(TAG, "resume after shuffle skip failed", it) }
    }

    private fun cancelResume(token: MediaSession.Token) {
        if (resumeToken != token) return
        handler.removeCallbacks(resumeFallback)
        resumeToken = null
        resumeTitleAtSkip = null
    }

    /** Every event on any watched session funnels into one re-evaluation of the winner. */
    private fun callbackFor(controller: MediaController) = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            maybeResume(controller, metadata)
            reevaluate()
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) = reevaluate()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            applyPendingShuffle(controller)
        }
        override fun onSessionDestroyed() {
            cancelResume(controller.sessionToken)
            callbacks.remove(controller.sessionToken)?.let { (c, cb) -> c.unregisterCallback(cb) }
            sessions = sessions.filterNot { it.sessionToken == controller.sessionToken }
            reevaluate()
        }
    }

    private fun reevaluate() {
        nowPlayingState.value = pickNowPlaying(sessions.map { it.snapshot() })
    }

    /**
     * The framework controller has no shuffle API; the support-library one implements it as
     * a custom action that a MediaSessionCompat (what NewPipe's session connector wraps)
     * unpacks into its onSetShuffleMode callback. Same wire format, no androidx.media dep.
     */
    private fun MediaController.TransportControls.setShuffleModeCompat(mode: Int) =
        sendCustomAction(ACTION_SET_SHUFFLE_MODE, Bundle().apply { putInt(ARGUMENT_SHUFFLE_MODE, mode) })

    private fun MediaController.snapshot() = SessionSnapshot(
        isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        albumArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
    )

    companion object {
        private const val TAG = "MediaListener"

        /** Upper bound on the paused gap after a shuffle skip. */
        private const val RESUME_FALLBACK_MS = 4_000L

        // MediaSessionCompat.ACTION_SET_SHUFFLE_MODE / ACTION_ARGUMENT_SHUFFLE_MODE and
        // PlaybackStateCompat.SHUFFLE_MODE_NONE / SHUFFLE_MODE_ALL.
        private const val ACTION_SET_SHUFFLE_MODE = "android.support.v4.media.session.action.SET_SHUFFLE_MODE"
        private const val ARGUMENT_SHUFFLE_MODE = "android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE"
        private const val SHUFFLE_MODE_NONE = 0
        private const val SHUFFLE_MODE_ALL = 1

        private val nowPlayingState = MutableStateFlow<NowPlaying?>(null)
        val nowPlaying: StateFlow<NowPlaying?> = nowPlayingState
    }
}
