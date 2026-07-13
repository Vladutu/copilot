package com.vladutu.copilot.split

/**
 * Process-wide split-screen launch policy: decides whether a launch should carry
 * FLAG_ACTIVITY_LAUNCH_ADJACENT (land in a split pane) or stay a plain fullscreen launch.
 *
 * Pure Kotlin (no Android imports) so the decision logic is unit-testable, mirroring
 * [com.vladutu.copilot.autoswitch.AutoSwitchBack]. The accessibility service
 * ([com.vladutu.copilot.back.BackGrabberService]) feeds it the visible app windows and
 * foreground changes; [com.vladutu.copilot.CopilotApp] mirrors the Settings toggle into
 * [enabled]; [com.vladutu.copilot.launch.AppLauncher] and the service's switch-back consult it.
 *
 * Carbox-established rules (2026-07-10 logs) this policy encodes:
 *  - A LAUNCH-INTENT launch with LAUNCH_ADJACENT onto a focused, settled partner builds a
 *    real split (the system pairs both apps itself — log: "split complete").
 *  - A DEEP-LINK launch with LAUNCH_ADJACENT only half-creates: the target lands in a pane
 *    with the other pane black. Deep links may therefore only go adjacent into an
 *    already-live split (pane delivery, phone-proven) — never to create one.
 *  - A nav app must never be launched adjacent at all: relaunching Waze into a pane
 *    re-delivers its root destination intent and duplicates it (Waze|Waze, one navigating,
 *    one back on the confirm screen).
 *
 * So every command is delivered plain fullscreen first, and [SplitRepair] then converges
 * the screen to the nav|music split: bring the nav app to front, wait for it to settle,
 * launch the music app's task adjacent ([repairNavFor] / [repairMusicFor]).
 *
 * The intended driving setup is nav (Waze/Maps) in one pane and a music app in the other.
 * Android offers no public API to pin an app to a specific side — placement follows launch
 * order — so this policy only controls *whether/with whom* a launch splits, never *where*.
 */
object SplitScreen {

    const val WAZE_PKG = "com.waze"
    const val MAPS_PKG = "com.google.android.apps.maps"
    const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
    const val SOUNDCLOUD_PKG = "com.soundcloud.android"
    const val YOUTUBE_PKG = "com.google.android.youtube"
    const val VLC_PKG = "org.videolan.vlc"

    /** Nav apps: the pane a music launch pairs against. */
    val NAV_PKGS = setOf(WAZE_PKG, MAPS_PKG)

    /** Apps a from-Copilot launch may two-step pair with a nav app (media side of the split). */
    val PAIRS_WITH_NAV = setOf(YT_MUSIC_PKG, SOUNDCLOUD_PKG, YOUTUBE_PKG, VLC_PKG)

    /** Mirrors the Settings "split screen" toggle; kept current by CopilotApp. Defaults off:
     *  every launch stays fullscreen until the driver opts in. */
    @Volatile var enabled: Boolean = false

    /** Copilot's own application id, set once by CopilotApp (handles the .debug suffix).
     *  Copilot is never a valid split partner and never launches itself adjacent. */
    @Volatile var ownPackage: String? = null

    @Volatile private var visiblePackages: Set<String> = emptySet()
    @Volatile private var focusedPackage: String? = null

    /** Last nav / music app seen in the foreground — the partner the opposite-side launch
     *  pairs with. Never expire: "the nav (or music) app from earlier this drive" is exactly
     *  the pairing the driver means. */
    @Volatile private var lastNavApp: String? = null
    @Volatile private var lastMusicApp: String? = null

    /** Publish the currently visible app windows and which of them holds focus
     *  (called from the accessibility service on every window change). */
    fun onWindows(visible: Set<String>, focused: String?) {
        visiblePackages = visible
        focusedPackage = focused
    }

    /** Record a foreground change (called from the accessibility service, restorable-filtered). */
    fun onForeground(pkg: String) {
        if (pkg in NAV_PKGS) lastNavApp = pkg
        if (pkg in PAIRS_WITH_NAV) lastMusicApp = pkg
    }

    /** Whether [pkg] currently has a window on screen (fullscreen or split pane). */
    fun isVisible(pkg: String): Boolean = pkg in visiblePackages

    /** A real app to pair with is on screen: visible, not Copilot, not the target itself. */
    fun hasVisiblePartner(targetPkg: String): Boolean =
        visiblePackages.any { it != ownPackage && it != targetPkg }

    /** Two real apps on screen at once = a split is already live (don't toggle it away). */
    fun isSplitActive(): Boolean = visiblePackages.count { it != ownPackage } >= 2

    /**
     * True when launching [targetPkg] should request a split pane (LAUNCH_ADJACENT).
     *
     * Requires a visible partner: with only Copilot (or nothing) on screen the system would
     * pair the split with Copilot itself. And never for the app that already owns the
     * *focused* pane — the system resolves "adjacent" relative to the focused window, so
     * relaunching that same app adjacent duplicates it into the other half (the double-Waze
     * the carbox showed). An app visible in the *unfocused* pane is safe: adjacent-of-focused
     * is the pane it already occupies, so the launch reuses its task in place.
     *
     * Destination deep links never reach this decision: nav apps recreate their task while
     * starting navigation and fall out of any split, so AppLauncher delivers them plain and
     * arms [SplitRepair] instead of flagging them adjacent.
     */
    fun launchAdjacent(targetPkg: String): Boolean {
        if (!enabled) return false
        if (targetPkg == ownPackage) return false
        if (!hasVisiblePartner(targetPkg)) return false
        if (targetPkg == focusedPackage && targetPkg in visiblePackages) return false
        return true
    }

    /**
     * The nav app [SplitRepair] should rebuild the split around after a plain delivery of
     * the music target [targetPkg], or null when there's no rebuild to do: toggle off, not
     * a music target, a split already live (the deep link was delivered adjacent into its
     * pane instead), or no nav app known this drive. Prefers the visible nav app over the
     * remembered one so the rebuild matches what's actually on screen.
     */
    fun repairNavFor(targetPkg: String): String? {
        if (!enabled) return null
        if (targetPkg !in PAIRS_WITH_NAV) return null
        if (isSplitActive()) return null
        return visiblePackages.firstOrNull { it in NAV_PKGS && it != targetPkg } ?: lastNavApp
    }

    /**
     * The music app [SplitRepair] should re-attach after a destination launch of [targetPkg],
     * or null when there's nothing to rebuild (toggle off, not a nav target, no music app
     * seen this drive). Deliberately ignores what's currently visible: even a live music
     * pane won't survive the nav app's task recreation, so the repair is armed regardless.
     */
    fun repairMusicFor(targetPkg: String): String? {
        if (!enabled) return null
        if (targetPkg !in NAV_PKGS) return null
        return lastMusicApp
    }

    /** Whether [pkg] is focused with no other real app on screen (Copilot doesn't count) —
     *  the screen state a repair rebuilds the split from. */
    fun isSoleForeground(pkg: String): Boolean =
        focusedPackage == pkg && visiblePackages.all { it == pkg || it == ownPackage }

    /** One-line policy-state dump for the diagnostic log, so a wrong launch shape is
     *  attributable on the box: toggle off vs no nav seen vs stale window snapshot. */
    fun stateForDiagnostics(): String =
        "enabled=$enabled visible=$visiblePackages focused=$focusedPackage " +
            "lastNav=$lastNavApp lastMusic=$lastMusicApp"

    /** Back to boot state (tests only — production state is fed by the collaborators above). */
    fun reset() {
        enabled = false
        visiblePackages = emptySet()
        focusedPackage = null
        lastNavApp = null
        lastMusicApp = null
    }
}
