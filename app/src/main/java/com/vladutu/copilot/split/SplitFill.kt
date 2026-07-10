package com.vladutu.copilot.split

/**
 * The adjacent-scaffold experiment for SystemUIs that refuse GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
 * (carbox-verified 2026-07-10: `toggle split dispatched=false`, yet a LAUNCH_ADJACENT launch
 * built a HALF-EMPTY split — the target in one pane, the other pane black, the old foreground
 * fullscreen behind). The empty stage is a slot waiting for a task: this coordinator watches
 * for that half-empty split and launches the partner adjacent to fill the black pane.
 *
 * Armed by [com.vladutu.copilot.back.BackGrabberService] when the toggle is refused, right
 * before the adjacent launch fires. The service then classifies what the launch produced from
 * the target's window bounds ([classify]):
 *  - PANE (half-ish window): the scaffold exists — fill the black half with the partner.
 *  - FULLSCREEN: the flag was ignored (emulator-style); no split to fill. If [recoverPartner]
 *    was requested (split-repair path: the music launch must never end up covering the
 *    navigation the driver asked for), the partner is brought back to the front plain.
 *  - AMBIGUOUS: mid-animation — wait for the next window event and measure again.
 *
 * Pure Kotlin, main-thread-only, one-shot with generation tokens, like [SplitRepair].
 */
object SplitFill {

    /** Delay after the adjacent-launched target first shows before measuring its window:
     *  the split-container animation needs a beat to reach its final bounds. */
    const val SCAFFOLD_SETTLE_MS = 700L

    /** Give up (and clear) when no verdict was reached this long after arming. */
    const val FILL_TTL_MS = 6_000L

    /** A window at most this fraction of the screen is a split pane. */
    const val PANE_MAX_FRACTION = 0.7f

    /** A window at least this fraction of the screen is fullscreen (flag ignored). */
    const val FULLSCREEN_MIN_FRACTION = 0.85f

    enum class Verdict { PANE, FULLSCREEN, AMBIGUOUS }

    /** Classify the adjacent launch's outcome from the target window's share of the screen. */
    fun classify(windowFraction: Float): Verdict = when {
        windowFraction <= PANE_MAX_FRACTION -> Verdict.PANE
        windowFraction >= FULLSCREEN_MIN_FRACTION -> Verdict.FULLSCREEN
        else -> Verdict.AMBIGUOUS
    }

    private var awaitPkg: String? = null
    private var fillPkg: String? = null
    private var recoverPartner = false
    private var generation = 0

    /** Arm for the upcoming adjacent launch of [await]; [fill] is the partner for the black
     *  pane. [recover] brings [fill] back to the front plain when the scaffold never appears
     *  (split-repair path only). Returns a token for [expire]. */
    fun arm(await: String, fill: String, recover: Boolean): Int {
        awaitPkg = await
        fillPkg = fill
        recoverPartner = recover
        return ++generation
    }

    /** The package whose window outcome is being awaited, or null when idle. */
    fun pendingAwait(): String? = awaitPkg

    /** The partner that should fill the black pane (PANE verdict). Consuming clears. */
    fun takeFill(): String? {
        val fill = fillPkg ?: return null
        clear()
        return fill
    }

    /** The partner to bring back to the front (FULLSCREEN verdict), or null when the paired
     *  path armed us (a fullscreen song launch is that path's normal fallback). Clears
     *  either way: the verdict is final. */
    fun takeRecovery(): String? {
        val fill = if (recoverPartner) fillPkg else null
        clear()
        return fill
    }

    /** End the watch identified by [token]; a newer arm keeps its watch. */
    fun expire(token: Int) {
        if (token == generation) clear()
    }

    fun clear() {
        awaitPkg = null
        fillPkg = null
        recoverPartner = false
    }
}
