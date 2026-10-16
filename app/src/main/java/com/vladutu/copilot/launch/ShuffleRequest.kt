package com.vladutu.copilot.launch

/**
 * One pending "set the queue's shuffle state" request for a music app Copilot just launched.
 *
 * NewPipe has no URL or intent switch for shuffle, but its media session honours the
 * standard shuffle and skip commands. So [AppLauncher] arms this after every NewPipe
 * launch — shuffle on for a `shuffle=1` playlist, off for everything else, because
 * NewPipe's player is long-lived and would otherwise carry the previous queue's shuffle
 * into an ordered Time Machine tour — and
 * [com.vladutu.copilot.nowplaying.MediaListenerService] applies it over the session the
 * first time the app reports a loading/playing state after the launch.
 *
 * The request expires after [WINDOW_MS] so a launch that never produced a session can't
 * reshuffle whatever the driver plays minutes later. Pure Kotlin, main-thread-only.
 */
object ShuffleRequest {

    /** How long after the launch the request stays valid. */
    const val WINDOW_MS = 30_000L

    private var pkg: String? = null
    private var shuffle = false
    private var armedAt = 0L

    /** Arm (replacing any previous request) for [targetPkg]. */
    fun arm(targetPkg: String, shuffle: Boolean, nowMs: Long = System.currentTimeMillis()) {
        pkg = targetPkg
        this.shuffle = shuffle
        armedAt = nowMs
    }

    /** The desired shuffle state for [targetPkg], or null when nothing is pending for it. */
    fun pending(targetPkg: String): Boolean? = if (pkg == targetPkg) shuffle else null

    /** Consume the request for [targetPkg]: the desired shuffle state, or null when none is
     *  pending for that package or the window has passed (an expired request is dropped). */
    fun take(targetPkg: String, nowMs: Long = System.currentTimeMillis()): Boolean? {
        if (pkg == null) return null
        if (nowMs - armedAt > WINDOW_MS) { clear(); return null }
        if (pkg != targetPkg) return null
        val wanted = shuffle
        clear()
        return wanted
    }

    fun clear() {
        pkg = null
        shuffle = false
        armedAt = 0L
    }
}
