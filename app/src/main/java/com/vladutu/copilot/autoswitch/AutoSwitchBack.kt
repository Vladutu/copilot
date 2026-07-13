package com.vladutu.copilot.autoswitch

/**
 * Process-wide coordinator for "after the music app (YT Music or SoundCloud) loads, switch
 * back to where we were".
 *
 * Pure Kotlin (no Android imports) so the decision logic is unit-testable. The
 * accessibility service ([com.vladutu.copilot.back.BackGrabberService]) feeds it foreground
 * packages and performs the actual app switch; [com.vladutu.copilot.launch.AppLauncher] arms
 * it right before launching a music app for a song/playlist.
 *
 * The return target is snapshotted at [arm] time — when the real foreground is still the
 * previous app — so it is immune to later window events from Copilot's own bubble overlay.
 *
 * Threading: every entry point is invoked on the main thread — [arm] from `AppLauncher`
 * (ntfy path runs it inside `withContext(Dispatchers.Main)`; the in-app path runs it from a
 * Compose callback), and [onForeground]/[resolveTargetAtFire]/[disarm] from the accessibility
 * service callback and its main-`Looper` handler. State is therefore single-threaded; the
 * `@Volatile` markers are belt-and-suspenders, not a substitute for locking.
 */
object AutoSwitchBack {

    const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
    const val SOUNDCLOUD_PKG = "com.soundcloud.android"
    const val YOUTUBE_PKG = "com.google.android.youtube"

    /** The media apps we launch deep links into. YouTube is here for the shared consumers
     *  (now-playing strip, moved-away checks) even though a youtube launch never arms this
     *  coordinator — a video is watched in the foreground, so there is nothing to return from. */
    val MUSIC_PKGS = setOf(YT_MUSIC_PKG, SOUNDCLOUD_PKG, YOUTUBE_PKG)

    /** Delay after the music app's window appears before we pull focus back, giving it
     *  time to process the deep link and start playback (mirrors Waze deep-link timing). */
    const val SETTLE_MS = 1_200L

    /** How long an arm stays valid if the music app never appears (not installed / launch failed). */
    private const val ARM_TTL_MS = 8_000L

    /** Copilot's own application id, set once by the service (handles the .debug suffix). */
    @Volatile var ownPackage: String? = null

    /** Injectable for deterministic tests; defaults to wall-clock. */
    @Volatile var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile private var currentForeground: String? = null
    @Volatile private var armedAtMillis: Long? = null
    @Volatile private var targetPackage: String? = null

    /** Record the latest foreground package (called from the accessibility service). */
    fun onForeground(pkg: String) {
        currentForeground = pkg
    }

    /** Read-only view of the last seen foreground package, for diagnostic logging. */
    fun foregroundForDiagnostics(): String? = currentForeground

    /** Snapshot the return target and start the arm window. Call just before launching the music app. */
    fun arm() {
        val fg = currentForeground
        targetPackage = if (fg == null || fg in MUSIC_PKGS) null else fg
        armedAtMillis = clock()
    }

    fun isArmed(): Boolean {
        val armedAt = armedAtMillis ?: return false
        return clock() - armedAt < ARM_TTL_MS
    }

    /** True when the music app appearing should schedule a switch-back. */
    fun shouldScheduleOnMusicAppShown(): Boolean = isArmed() && targetPackage != null

    /**
     * The package to restore, or null to abort. Aborts when the driver has moved to a
     * third app — foreground is non-null and is neither a music app nor Copilot itself (the
     * latter covers the bubble-overlay window reading, which must not look like "moved").
     */
    fun resolveTargetAtFire(): String? {
        val target = if (isArmed()) targetPackage else null
        target ?: return null
        // ownPackage is set by the service in onServiceConnected; since this method only runs
        // from the service's own settle-timer, ownPackage is non-null by the time we get here.
        val fg = currentForeground
        val movedAway = fg != null && fg !in MUSIC_PKGS && fg != ownPackage && fg != target
        return if (movedAway) null else target
    }

    fun disarm() {
        armedAtMillis = null
        targetPackage = null
    }
}
