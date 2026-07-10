package com.vladutu.copilot.split

/**
 * One-shot coordinator for the two-step split build: [com.vladutu.copilot.launch.AppLauncher]
 * starts the nav partner, arms this with the deep-link continuation, and the accessibility
 * service fires it once the partner's window is confirmed foreground — so LAUNCH_ADJACENT
 * pairs with the nav app, never with Copilot. A TTL timer armed by AppLauncher fires
 * [takeTimeout] as a plain fullscreen fallback when the partner never shows up (cold start
 * too slow, partner uninstalled mid-flight, accessibility service off).
 *
 * Pure Kotlin and single-threaded like AutoSwitchBack: every entry point runs on the main
 * thread, so the one-shot handoff (whichever of match/timeout comes first clears the other)
 * needs no locking.
 */
object PairedLaunch {

    /** Delay after the partner's window appears before entering split, giving the
     *  partner time to take focus (the toggle splits whichever app is focused). */
    const val PARTNER_SETTLE_MS = 400L

    /** Delay after GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN before the adjacent deep link, giving
     *  the system time to actually enter split mode — LAUNCH_ADJACENT is ignored until the
     *  screen is genuinely split (emulator-verified: a background caller cannot create a
     *  split with the flag alone). */
    const val SPLIT_ENGAGE_MS = 600L

    /** How long AppLauncher waits for the partner before falling back to a plain launch.
     *  Generous on purpose: a cold Waze start on the carbox takes a few seconds. */
    const val PAIR_TTL_MS = 4_000L

    /** How long after Copilot steps aside we wait for a covered split to resurface before
     *  concluding there was none and launching the partner ourselves. */
    const val REVEAL_TTL_MS = 1_500L

    private var partnerPkg: String? = null
    private var targetPkg: String? = null
    private var onPaired: (() -> Unit)? = null
    private var onTimeout: (() -> Unit)? = null
    private var generation = 0

    /** Arm the pair. [target] is the app the deep link is for — the service needs it to arm
     *  the scaffold fill when the split toggle is refused. Returns a token for [takeTimeout]
     *  so a stale TTL timer left over from an earlier arm can never fire a newer one's
     *  fallback early. */
    fun arm(partner: String, target: String, paired: () -> Unit, timeout: () -> Unit): Int {
        partnerPkg = partner
        targetPkg = target
        onPaired = paired
        onTimeout = timeout
        return ++generation
    }

    /** The partner package an arm is waiting on, or null when idle. The service checks the
     *  awaited partner against its window snapshot (visibility, not event package: a split
     *  resurfacing may only emit events for its focused half). */
    fun pendingPartner(): String? = partnerPkg

    /** The deep link's target app for the pending arm (read before [matchPartnerShown]
     *  consumes the arm), or null when idle. */
    fun pendingTarget(): String? = targetPkg

    /** The adjacent-launch continuation when [pkg] is the awaited partner, else null.
     *  Consuming it disarms the pair (the TTL fallback becomes a no-op). */
    fun matchPartnerShown(pkg: String): (() -> Unit)? {
        if (pkg != partnerPkg) return null
        val fire = onPaired
        clear()
        return fire
    }

    /** The fullscreen fallback if the arm identified by [token] is still pending, else null. */
    fun takeTimeout(token: Int): (() -> Unit)? {
        if (token != generation || partnerPkg == null) return null
        val fire = onTimeout
        clear()
        return fire
    }

    fun clear() {
        partnerPkg = null
        targetPkg = null
        onPaired = null
        onTimeout = null
    }
}
