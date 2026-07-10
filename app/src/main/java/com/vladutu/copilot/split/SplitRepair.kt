package com.vladutu.copilot.split

/**
 * The split reconciler: every command is delivered plain fullscreen, then this object is
 * armed and [com.vladutu.copilot.back.BackGrabberService] converges the screen to the
 * nav|music split in stages, each gated on the window snapshot being stable for
 * [NAV_STABLE_MS]:
 *  - music app alone in the foreground (a song was just delivered plain) → bring the nav
 *    app to front with a plain launch intent (budgeted by [MAX_NAV_BRINGS]);
 *  - nav app alone in the foreground (a destination settled, or the bring above landed,
 *    and Waze is past its "Go now" confirm) → take an attempt and launch the music app's
 *    task ADJACENT — the carbox-proven step that makes the system pair both into a split.
 *
 * Delivering plain first is not just caution: deep links must never carry LAUNCH_ADJACENT
 * outside a live split (half-empty black-pane split), nav apps must never be launched
 * adjacent at all (Waze duplicates), and Waze recreates its task while starting navigation,
 * killing any split built before the delivery. See [SplitScreen]'s header for the log
 * evidence behind each rule.
 *
 * Attempts rather than a one-shot on purpose: a repair that lands too early gets collapsed
 * by Waze's nav-start task recreation — the next attempt converges on the recreated task.
 * The whole watch expires after [WATCH_WINDOW_MS] so a repair can never fight the driver
 * minutes after the command.
 *
 * Pure Kotlin and main-thread-only: no locking needed.
 */
object SplitRepair {

    /** How long the nav app must be alone-and-focused before a repair attempt. Long enough
     *  to skip the transient sole-visible moments while windows shuffle, short enough that
     *  free-tier music (paused once its pane vanished) resumes quickly. */
    const val NAV_STABLE_MS = 3_000L

    /** Watch window per destination command; afterwards the screen belongs to the driver. */
    const val WATCH_WINDOW_MS = 60_000L

    /** Upper bound on rebuilds per command: attempt 1 may land on the confirm screen
     *  and get collapsed by the nav-start task recreation, attempt 2 converges, 3 is margin. */
    const val MAX_ATTEMPTS = 3

    /** Upper bound on bring-nav-to-front launches per command (the music-delivered-first
     *  stage). Bounded separately from attempts so a nav app that refuses to come up can't
     *  burn the rebuild budget. */
    const val MAX_NAV_BRINGS = 2

    /** Delay between the split toggle dispatch and the adjacent launch, giving the system
     *  time to engage split mode (only relevant where the toggle is supported). */
    const val SPLIT_ENGAGE_MS = 600L

    private var navPkg: String? = null
    private var musicPkg: String? = null
    private var fire: (() -> Unit)? = null
    private var attemptsLeft = 0
    private var navBringsLeft = 0
    private var generation = 0

    /** Arm (or re-arm, replacing any previous watch) for [nav]. [onRepair] launches the
     *  [music] partner adjacent; the service invokes it via [takeAttempt] once the split
     *  toggle is engaged (or the scaffold path replaces it). Returns a token for [expire]
     *  so a stale expiry timer left over from an earlier destination can never kill a
     *  newer watch. */
    fun arm(nav: String, music: String, onRepair: () -> Unit): Int {
        navPkg = nav
        musicPkg = music
        fire = onRepair
        attemptsLeft = MAX_ATTEMPTS
        navBringsLeft = MAX_NAV_BRINGS
        return ++generation
    }

    /** The nav package being watched, or null when idle. */
    fun pendingNav(): String? = navPkg

    /** The music app the repair re-attaches — the scaffold-fill's awaited window. */
    fun pendingMusic(): String? = musicPkg

    /** Consume one repair attempt and return the music re-attach continuation, or null when
     *  idle. Clears the watch after the last attempt. */
    fun takeAttempt(): (() -> Unit)? {
        if (navPkg == null || attemptsLeft <= 0) return null
        attemptsLeft--
        val f = fire
        if (attemptsLeft == 0) clear()
        return f
    }

    /** Consume one bring-nav-to-front launch (the music-settled-first stage); false when
     *  idle or the bring budget is exhausted. */
    fun takeNavBring(): Boolean {
        if (navPkg == null || navBringsLeft <= 0) return false
        navBringsLeft--
        return true
    }

    /** End the watch identified by [token]; a newer arm's token keeps its watch alive. */
    fun expire(token: Int) {
        if (token == generation) clear()
    }

    /** For the diagnostic log: which attempt is firing. */
    fun attemptsForDiagnostics(): String = "attemptsLeft=$attemptsLeft/$MAX_ATTEMPTS"

    fun clear() {
        navPkg = null
        musicPkg = null
        fire = null
        attemptsLeft = 0
        navBringsLeft = 0
    }
}
