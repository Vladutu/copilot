package com.vladutu.copilot.split

/**
 * Reconciler for the nav half of the split: after a destination deep link, converge the
 * screen back to the nav|music split instead of trying to protect one through the delivery.
 *
 * Waze recreates its task while starting navigation (phone-verified), which throws it out
 * of any split it's in — no launch flag prevents it. So destinations are delivered plain
 * fullscreen and this object is armed with the music re-attach continuation.
 * [com.vladutu.copilot.back.BackGrabberService] then watches the window snapshot: whenever
 * the nav app has been alone and focused for [NAV_STABLE_MS] (and Waze is past its
 * "Go now" confirm), it takes an attempt and rebuilds the split around the running
 * navigation via the same toggle-then-adjacent machinery the music direction uses.
 *
 * Attempts rather than a one-shot on purpose: a repair that lands while Waze is still on
 * the confirm screen gets collapsed again by the task recreation moments later — the next
 * attempt then converges on the recreated task. The whole watch expires after
 * [WATCH_WINDOW_MS] so a repair can never fight the driver minutes after the command.
 *
 * Pure Kotlin and main-thread-only like [PairedLaunch]: no locking needed.
 */
object SplitRepair {

    /** How long the nav app must be alone-and-focused before a repair attempt. Long enough
     *  to skip the transient sole-visible moments while windows shuffle, short enough that
     *  free-tier music (paused once its pane vanished) resumes quickly. */
    const val NAV_STABLE_MS = 3_000L

    /** Watch window per destination command; afterwards the screen belongs to the driver. */
    const val WATCH_WINDOW_MS = 60_000L

    /** Upper bound on rebuilds per destination: attempt 1 may land on the confirm screen
     *  and get collapsed by the nav-start task recreation, attempt 2 converges, 3 is margin. */
    const val MAX_ATTEMPTS = 3

    private var navPkg: String? = null
    private var fire: (() -> Unit)? = null
    private var attemptsLeft = 0
    private var generation = 0

    /** Arm (or re-arm, replacing any previous watch) for [nav]. [onRepair] launches the
     *  music partner adjacent; the service invokes it via [takeAttempt] once the split
     *  toggle is engaged. Returns a token for [expire] so a stale expiry timer left over
     *  from an earlier destination can never kill a newer watch. */
    fun arm(nav: String, onRepair: () -> Unit): Int {
        navPkg = nav
        fire = onRepair
        attemptsLeft = MAX_ATTEMPTS
        return ++generation
    }

    /** The nav package being watched, or null when idle. */
    fun pendingNav(): String? = navPkg

    /** Consume one repair attempt and return the music re-attach continuation, or null when
     *  idle. Clears the watch after the last attempt. */
    fun takeAttempt(): (() -> Unit)? {
        if (navPkg == null || attemptsLeft <= 0) return null
        attemptsLeft--
        val f = fire
        if (attemptsLeft == 0) clear()
        return f
    }

    /** End the watch identified by [token]; a newer arm's token keeps its watch alive. */
    fun expire(token: Int) {
        if (token == generation) clear()
    }

    /** For the diagnostic log: which attempt is firing. */
    fun attemptsForDiagnostics(): String = "attemptsLeft=$attemptsLeft/$MAX_ATTEMPTS"

    fun clear() {
        navPkg = null
        fire = null
        attemptsLeft = 0
    }
}
