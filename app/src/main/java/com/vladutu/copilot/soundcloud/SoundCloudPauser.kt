package com.vladutu.copilot.soundcloud

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.nowplaying.MediaListenerService

/**
 * Pauses SoundCloud's live playback right before a deep-link launch.
 *
 * SoundCloud ignores a deep link's track while something is actively playing — it shows the
 * new track's page but keeps playing the old one. Observed on-car: after stopping playback,
 * the same deep link plays. So when SoundCloud's media session reports STATE_PLAYING, send
 * pause() first and let the deep link take over; the track identity stays exact (it's the URL).
 *
 * Session access rides on notification access (already granted on the carbox for Liked Songs;
 * [MediaListenerService] is the listener component the permission is keyed on). Every failure
 * path is non-fatal — log and let the deep link fire as before.
 */
open class SoundCloudPauser(private val context: Context) {

    /** True when a pause was actually sent (playback was live). */
    open fun pauseIfPlaying(): Boolean {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, MediaListenerService::class.java)
            val controller = mgr.getActiveSessions(listener)
                .firstOrNull { it.packageName == AutoSwitchBack.SOUNDCLOUD_PKG }
            if (controller == null) {
                DiagnosticLog.i(TAG, "no active SoundCloud session — nothing to pause")
                return false
            }
            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
                DiagnosticLog.i(TAG, "paused live SoundCloud playback before deep link")
                true
            } else {
                DiagnosticLog.i(TAG, "SoundCloud session state=$state — no pause needed")
                false
            }
        } catch (e: SecurityException) {
            DiagnosticLog.w(TAG, "no notification access — cannot pause SoundCloud", e)
            false
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "pauseIfPlaying failed", e)
            false
        }
    }

    private companion object { const val TAG = "SoundCloud" }
}
