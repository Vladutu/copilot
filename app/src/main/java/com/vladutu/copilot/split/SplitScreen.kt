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
 * The core rule (learned in the emulator): the system pairs an adjacent launch with the
 * *focused* task. So adjacent is only allowed when a real partner app is on screen to pair
 * with — launching adjacent while Copilot is focused would drag Copilot itself into the
 * split. Building a split *from* Copilot therefore takes two steps (see [pairingPartnerFor]
 * and [PairedLaunch]): bring the nav app to front first, then launch the music app adjacent.
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
    const val VLC_PKG = "org.videolan.vlc"

    /** Nav apps: the pane a music launch pairs against. */
    val NAV_PKGS = setOf(WAZE_PKG, MAPS_PKG)

    /** Apps a from-Copilot launch may two-step pair with a nav app (music side of the split). */
    val PAIRS_WITH_NAV = setOf(YT_MUSIC_PKG, SOUNDCLOUD_PKG, VLC_PKG)

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
     */
    fun launchAdjacent(targetPkg: String): Boolean {
        if (!enabled) return false
        if (targetPkg == ownPackage) return false
        if (!hasVisiblePartner(targetPkg)) return false
        if (targetPkg == focusedPackage && targetPkg in visiblePackages) return false
        // Nav apps exit the split on their own while processing a destination deep link
        // (Waze recreates its task as navigation starts — phone-verified: pane for ~2s,
        // then the split collapses into the MUSIC app). Since the split is lost either
        // way, deliver plain so the driver deterministically ends on fullscreen nav
        // instead of fullscreen music.
        if (targetPkg in NAV_PKGS && targetPkg in visiblePackages) return false
        return true
    }

    /**
     * The opposite-side app to get on screen before launching [targetPkg] (the paired
     * launch from inside Copilot): music targets pair with the last nav app, nav targets
     * (a saved destination while a song plays) with the last music app. Null when a
     * plain/single launch is right: toggle off, a partner pane is already visible
     * (single adjacent launch suffices), or no opposite-side app has been seen yet.
     */
    fun pairingPartnerFor(targetPkg: String): String? {
        if (!enabled) return null
        if (hasVisiblePartner(targetPkg)) return null
        return when (targetPkg) {
            in PAIRS_WITH_NAV -> lastNavApp
            in NAV_PKGS -> lastMusicApp
            else -> null
        }
    }

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
