package com.vladutu.copilot.back

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.vladutu.copilot.CopilotApp
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.bubble.BubbleController
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.split.PairedLaunch
import com.vladutu.copilot.split.SplitFill
import com.vladutu.copilot.split.SplitRepair
import com.vladutu.copilot.split.SplitScreen
import com.vladutu.copilot.waze.GoNodeMatcher
import com.vladutu.copilot.waze.GoTapDecider
import com.vladutu.copilot.waze.WazeGoDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Accessibility service whose sole job is to bring Copilot back to the foreground when the
 * hardware BACK key is pressed while the bubble is showing. When Copilot is already
 * foreground (or has never been opened) BACK is passed through to whoever owns it.
 *
 * Also drops the carbox's synthetic duplicate of BACK globally — same dedup MainActivity does
 * for its own focused-window events, but here it benefits every app on the box.
 *
 * Heavy DiagnosticLog usage on purpose: this service can be silently misconfigured by the
 * carbox's Android variant and the only way to know is by reading the log on the box itself.
 */
class BackGrabberService : AccessibilityService() {

    /** Whether the in-flight BACK press is one we've committed to consuming end-to-end. We
     *  must mirror ACTION_DOWN/ACTION_UP consumption to avoid leaving the foreground app in
     *  a half-pressed state. */
    private var consumingThisPress = false

    private val handler = Handler(Looper.getMainLooper())

    /** Guards against scheduling multiple settle-timers from repeated music-app window events. */
    private var switchBackPending = false

    /** Guards against stacking repair settle-timers from the window-event burst while
     *  navigation starts. */
    private var repairCheckPending = false

    /** Guards against stacking scaffold-measure timers from the split-container animation's
     *  window-event burst. */
    private var fillCheckPending = false

    /** Live mirror of the Waze Go-now settings, kept current by collectors started in
     *  onServiceConnected. Read synchronously from onKeyEvent (which is not a coroutine). */
    @Volatile private var wazeGoEnabled = true
    @Volatile private var wazeGoLabel = WazeGoDefaults.LABEL

    /** Key code of an in-flight knob press we're consuming end-to-end (DOWN+UP), or null when
     *  none. Tracks the code rather than a bool because the carbox interleaves a synthetic
     *  BUTTON_1 with the duplicate DPAD_CENTER, so UP-matching must be per-keycode to pair the
     *  right halves (mirrors consumingThisPress for BACK, but BACK can't interleave). */
    private var consumingKnobKeyCode: Int? = null

    /** eventTime of the last Go-now tap we dispatched. The carbox re-injects every knob press
     *  as a 2nd DPAD_CENTER from a nameless device ~tens of ms later (same synthetic-duplicate
     *  behavior the BACK handling drops); we debounce on this so one press taps exactly once. */
    @Volatile private var lastWazeTapAt = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Belt-and-suspenders: also set the filter-key-events flag programmatically. Some
        // Android variants on aftermarket head units honor the runtime value but ignore the
        // XML one, or vice-versa.
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }
        AutoSwitchBack.ownPackage = applicationContext.packageName
        DiagnosticLog.i(TAG, "onServiceConnected flags=0x${serviceInfo?.flags?.toString(16)} caps=0x${serviceInfo?.capabilities?.toString(16)} ownPkg=${applicationContext.packageName}")

        val store = (applicationContext as CopilotApp).locator.settingsStore
        serviceScope.launch { store.wazeGoEnabledFlow.collect { wazeGoEnabled = it } }
        serviceScope.launch { store.wazeGoLabelFlow.collect { wazeGoLabel = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        // Both event types can change what's on screen / who has focus; keep the split
        // policy's window snapshot current before any launch decision reads it.
        updateWindowSnapshot()
        maybeFirePairedLaunch()
        maybeRepairSplit()
        maybeFillSplit()
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Only track packages we could actually return to. Overlay / floating-widget apps
        // (e.g. com.applepie.floatingball) and system UI (the volume HUD, etc.) fire
        // window-state-changed events too, but they aren't real foreground apps and have no
        // launcher activity. Tracking them would poison the restore target (we'd snapshot an
        // unlaunchable overlay, as seen on the carbox) or trigger a spurious "user moved away"
        // abort. Filtering to restorable packages keeps `currentForeground` on the real app.
        if (!isRestorable(pkg)) return
        AutoSwitchBack.onForeground(pkg)
        SplitScreen.onForeground(pkg)

        if (pkg in AutoSwitchBack.MUSIC_PKGS &&
            AutoSwitchBack.shouldScheduleOnMusicAppShown() &&
            !switchBackPending
        ) {
            switchBackPending = true
            DiagnosticLog.i(TAG, "$pkg shown while armed — switch-back in ${AutoSwitchBack.SETTLE_MS}ms")
            handler.postDelayed({ fireSwitchBack() }, AutoSwitchBack.SETTLE_MS)
        }
    }

    /** Second half of a paired launch: the awaited nav partner is now visible — either the
     *  split Copilot stepped away from resurfaced, or the partner AppLauncher started came
     *  up. Matched on window-snapshot visibility, not the event's package: a resurfacing
     *  split may only emit state-changed events for its focused half. */
    private fun maybeFirePairedLaunch() {
        val partner = PairedLaunch.pendingPartner() ?: return
        if (!SplitScreen.isVisible(partner)) return
        val target = PairedLaunch.pendingTarget()
        PairedLaunch.matchPartnerShown(partner)?.let { fire ->
            DiagnosticLog.i(TAG, "pair partner $partner visible — entering split in ${PairedLaunch.PARTNER_SETTLE_MS}ms")
            handler.postDelayed(
                { enterSplitThenFire(target, partner, fire, recoverPartner = false) },
                PairedLaunch.PARTNER_SETTLE_MS,
            )
        }
    }

    /**
     * Post-destination split rebuild ([SplitRepair], armed by AppLauncher when a destination
     * was delivered): once the nav app has held the screen alone for NAV_STABLE_MS, toggle
     * split mode around it and pull the music app into the other pane. The stability wait
     * plus the Waze confirm-screen guard aim the rebuild *after* the nav-start task
     * recreation that collapses any earlier split; if an attempt still lands too early and
     * gets collapsed, the recreation's own window events re-enter this path and the next
     * attempt converges. Skipped rounds don't reschedule themselves — the next window event
     * (there is always one when navigation starts and the task is recreated) re-evaluates.
     */
    private fun maybeRepairSplit() {
        val nav = SplitRepair.pendingNav() ?: return
        if (!SplitScreen.isSoleForeground(nav)) return
        if (repairCheckPending) return
        repairCheckPending = true
        DiagnosticLog.i(TAG, "repair: $nav alone — re-split check in ${SplitRepair.NAV_STABLE_MS}ms")
        handler.postDelayed({ fireRepairCheck(nav) }, SplitRepair.NAV_STABLE_MS)
    }

    private fun fireRepairCheck(nav: String) {
        repairCheckPending = false
        if (SplitRepair.pendingNav() != nav) return
        if (!SplitScreen.isSoleForeground(nav)) {
            DiagnosticLog.i(TAG, "repair check: $nav no longer alone — waiting")
            return
        }
        // Splitting while Waze still shows its "Go now" confirm is wasted: the task
        // recreation at navigation start would collapse the fresh split (and steal focus
        // from Waze, breaking the knob confirm tap). Wait for the confirm to clear; the
        // retry budget covers a label mismatch making this guard miss.
        if (nav == SplitScreen.WAZE_PKG && wazeConfirmOnScreen()) {
            DiagnosticLog.i(TAG, "repair check: Waze still on the confirm screen — waiting for navigation start")
            return
        }
        val music = SplitRepair.pendingMusic()
        SplitRepair.takeAttempt()?.let { fire ->
            DiagnosticLog.i(TAG, "repair: rebuilding split around $nav (${SplitRepair.attemptsForDiagnostics()})")
            enterSplitThenFire(music, nav, fire, recoverPartner = true)
        }
    }

    /** Searches every Waze application window rather than rootInActiveWindow: at repair time
     *  the active window can be an overlay (the bubble, the carbox's floating ball), which
     *  made this guard miss on the carbox while the confirm was plainly on screen. */
    private fun wazeConfirmOnScreen(): Boolean {
        val label = wazeGoLabel.ifBlank { WazeGoDefaults.LABEL }
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() != SplitScreen.WAZE_PKG) continue
            if (findGoNode(root, label) != null) return true
        }
        return false
    }

    /**
     * Third stage of the two-step split build. LAUNCH_ADJACENT can only place an app into an
     * *existing* split — a background caller can't create one with the flag alone (emulator:
     * the deep link just opened fullscreen over the partner). Preferred route: enter split
     * mode via the global action (the programmatic long-press-Recents — puts the focused nav
     * partner into a pane), give the system a beat to engage, then fire the adjacent deep
     * link into the other pane. If the split is somehow already live, toggling would tear it
     * down — skip straight to the deep link.
     *
     * When the toggle is REFUSED (dispatched=false — emulator AND carbox), fall back to the
     * adjacent-scaffold route: fire the adjacent launch anyway and arm [SplitFill]. On the
     * carbox that launch builds a half-empty split ([targetPkg] in a pane, the other pane
     * black, [partnerPkg] fullscreen behind); the fill watcher then launches the partner
     * adjacent into the black half. Where the flag is ignored instead (emulator), the fill
     * watcher sees a fullscreen window: the paired path accepts it (the song must play,
     * [recoverPartner]=false), the repair path relaunches the partner plain so music never
     * ends up covering navigation ([recoverPartner]=true).
     */
    private fun enterSplitThenFire(
        targetPkg: String?,
        partnerPkg: String?,
        fire: () -> Unit,
        recoverPartner: Boolean,
    ) {
        if (SplitScreen.isSplitActive()) {
            DiagnosticLog.i(TAG, "split already active — firing deep link directly")
            fire()
            return
        }
        val dispatched = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        if (dispatched) {
            DiagnosticLog.i(TAG, "toggle split dispatched — firing in ${PairedLaunch.SPLIT_ENGAGE_MS}ms")
            handler.postDelayed(fire, PairedLaunch.SPLIT_ENGAGE_MS)
            return
        }
        if (targetPkg == null || partnerPkg == null) {
            // No package context to run the scaffold with — behave like the pre-scaffold code.
            if (recoverPartner) {
                DiagnosticLog.w(TAG, "toggle refused, no scaffold context — skipping repair launch")
            } else {
                fire()
            }
            return
        }
        DiagnosticLog.i(TAG, "toggle refused — adjacent scaffold: firing $targetPkg, fill=$partnerPkg recover=$recoverPartner")
        val token = SplitFill.arm(targetPkg, partnerPkg, recoverPartner)
        handler.postDelayed({
            if (SplitFill.pendingAwait() != null) DiagnosticLog.w(TAG, "scaffold fill TTL — no verdict reached")
            SplitFill.expire(token)
        }, SplitFill.FILL_TTL_MS)
        fire()
    }

    /**
     * Adjacent-scaffold verdict watcher ([SplitFill]): once the adjacent-launched app has a
     * window, measure its share of the screen. Pane-sized → the half-empty split exists;
     * launch the partner adjacent into the black half. Fullscreen → the flag was ignored;
     * recover the partner if the repair path asked for it. In-between → the split container
     * is still animating; measure again on the next event or after another settle delay.
     */
    private fun maybeFillSplit() {
        val await = SplitFill.pendingAwait() ?: return
        if (!SplitScreen.isVisible(await)) return
        if (fillCheckPending) return
        fillCheckPending = true
        handler.postDelayed({ fireFillCheck() }, SplitFill.SCAFFOLD_SETTLE_MS)
    }

    private fun fireFillCheck() {
        fillCheckPending = false
        val await = SplitFill.pendingAwait() ?: return
        val fraction = windowScreenFraction(await)
        if (fraction == null) {
            DiagnosticLog.i(TAG, "scaffold check: no window for $await — waiting")
            return
        }
        val pct = (fraction * 100).toInt()
        when (SplitFill.classify(fraction)) {
            SplitFill.Verdict.PANE -> {
                val fill = SplitFill.takeFill() ?: return
                if (SplitScreen.isVisible(fill)) {
                    DiagnosticLog.i(TAG, "scaffold check: $await in a pane ($pct%) and $fill already visible — split complete")
                } else {
                    DiagnosticLog.i(TAG, "scaffold check: $await in a pane ($pct%) — filling the black half with $fill")
                    startPackage(fill, adjacent = true)
                }
            }
            SplitFill.Verdict.FULLSCREEN -> {
                val recover = SplitFill.takeRecovery()
                if (recover != null) {
                    DiagnosticLog.w(TAG, "scaffold check: $await fullscreen ($pct%) — bringing $recover back to front")
                    startPackage(recover, adjacent = false)
                } else {
                    DiagnosticLog.i(TAG, "scaffold check: $await fullscreen ($pct%) — no scaffold on this SystemUI")
                }
            }
            SplitFill.Verdict.AMBIGUOUS -> {
                DiagnosticLog.i(TAG, "scaffold check: $await at $pct% — still animating, re-measuring")
                fillCheckPending = true
                handler.postDelayed({ fireFillCheck() }, SplitFill.SCAFFOLD_SETTLE_MS)
            }
        }
    }

    /** The largest application-window share of the screen held by [pkg], or null when it
     *  has no window right now. */
    private fun windowScreenFraction(pkg: String): Float? {
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toFloat() * dm.heightPixels.toFloat()
        if (screenArea <= 0f) return null
        var best = -1f
        val bounds = Rect()
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (window.root?.packageName?.toString() != pkg) continue
            window.getBoundsInScreen(bounds)
            val fraction = (bounds.width().toFloat() * bounds.height().toFloat()) / screenArea
            if (fraction > best) best = fraction
        }
        return if (best < 0f) null else best
    }

    /** Launch [pkg]'s main task from the service (fill or recovery half of the scaffold). */
    private fun startPackage(pkg: String, adjacent: Boolean) {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            DiagnosticLog.w(TAG, "no launch intent for $pkg")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { applicationContext.startActivity(intent) }
            .onSuccess { DiagnosticLog.i(TAG, "started $pkg adjacent=$adjacent flags=0x${intent.flags.toString(16)}") }
            .onFailure { DiagnosticLog.w(TAG, "start failed for $pkg", it) }
    }

    /** Publish which real apps have a window on screen (and which is focused) to the split
     *  policy. Same restorable filter as [onAccessibilityEvent]: overlays and system windows
     *  (the bubble, floating widgets, volume HUD) must not read as split panes. */
    private fun updateWindowSnapshot() {
        val visible = LinkedHashSet<String>()
        var focused: String? = null
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val pkg = window.root?.packageName?.toString() ?: continue
            if (!isRestorable(pkg)) continue
            visible.add(pkg)
            if (window.isFocused) focused = pkg
        }
        SplitScreen.onWindows(visible, focused)
    }

    /** Cache of package -> "has a launcher activity". Launchability doesn't change at runtime,
     *  so we look each package up once instead of hitting PackageManager on every window event. */
    private val restorableCache = HashMap<String, Boolean>()

    /** A package is restorable if we can bring it back to the foreground: Copilot itself,
     *  the music apps, or any app with a launcher activity. Everything else (overlays, system
     *  UI, IMEs) is ignored so it can't become the restore target or look like the user moved. */
    private fun isRestorable(pkg: String): Boolean {
        if (pkg == applicationContext.packageName || pkg in AutoSwitchBack.MUSIC_PKGS) return true
        restorableCache[pkg]?.let { return it }
        val restorable = applicationContext.packageManager.getLaunchIntentForPackage(pkg) != null
        restorableCache[pkg] = restorable
        if (!restorable) DiagnosticLog.i(TAG, "ignoring non-app foreground pkg=$pkg (no launcher)")
        return restorable
    }

    private fun fireSwitchBack() {
        switchBackPending = false
        val target = AutoSwitchBack.resolveTargetAtFire()
        AutoSwitchBack.disarm()
        if (target == null) {
            // Logs the foreground that caused the abort so a false-abort from a transient
            // system window (e.g. the volume HUD when playback starts) is diagnosable on the box.
            DiagnosticLog.i(TAG, "switch-back aborted — foreground=${AutoSwitchBack.foregroundForDiagnostics()}")
            return
        }
        restoreApp(target)
    }

    private fun restoreApp(pkg: String) {
        // In a live split the target was never covered — it is still on screen next to the
        // music app — so there is nothing to restore, and relaunching would only reshuffle
        // panes. Gated on the toggle so the feature-off state behaves exactly as it did
        // before split support existed.
        if (SplitScreen.enabled && SplitScreen.isVisible(pkg)) {
            DiagnosticLog.i(TAG, "switch-back skipped — $pkg still visible (split pane)")
            return
        }
        val intent = if (pkg == applicationContext.packageName) {
            // REORDER_TO_FRONT preserves Copilot's nav back stack (driver returns to the
            // screen they were last on rather than resetting to Home).
            Intent(applicationContext, MainActivity::class.java)
        } else {
            applicationContext.packageManager.getLaunchIntentForPackage(pkg)
        }
        if (intent == null) {
            DiagnosticLog.w(TAG, "no launch intent for $pkg — staying in the music app")
            return
        }
        // The split policy decides pane vs fullscreen: never a pane for Copilot itself,
        // adjacent for a covered target when the split toggle is on (inert outside split mode).
        intent.addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or
                (if (SplitScreen.launchAdjacent(pkg)) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else 0),
        )
        runCatching { applicationContext.startActivity(intent) }
            .onSuccess { DiagnosticLog.i(TAG, "switched back to $pkg") }
            .onFailure { DiagnosticLog.w(TAG, "switch-back startActivity failed for $pkg", it) }
    }

    override fun onInterrupt() {
        DiagnosticLog.w(TAG, "onInterrupt")
        consumingThisPress = false
        consumingKnobKeyCode = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device?.name ?: ""
        DiagnosticLog.i(
            TAG,
            "key kc=${event.keyCode}(${KeyEvent.keyCodeToString(event.keyCode)}) " +
                "action=${event.action} repeat=${event.repeatCount} dev='$device' " +
                "src=0x${event.source.toString(16)} bubbleVisible=${BubbleController.isVisible()}",
        )

        // Waze "Go now" knob tap. Independent of BACK handling below. We claim both halves
        // of a press only when we actually dispatch the tap, so non-acting presses (feature
        // off, not Waze, no node yet) fall straight through to their normal behavior.
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.keyCode == consumingKnobKeyCode) return true // swallow repeat of a claimed press
                if (event.repeatCount == 0 && tryTapWazeGo(event.keyCode, event.eventTime)) return true
            }
            KeyEvent.ACTION_UP ->
                if (event.keyCode == consumingKnobKeyCode) {
                    consumingKnobKeyCode = null
                    return true
                }
        }

        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false

        // The carbox CarPlay bridge re-injects BACK from a nameless device (the same
        // synthetic duplicate the probe captured). Drop it system-wide so no app sees both.
        if (device.isEmpty()) {
            DiagnosticLog.i(TAG, "dropping synthetic duplicate BACK")
            return true
        }

        if (!BubbleController.isVisible()) {
            DiagnosticLog.i(TAG, "bubble not visible — passing BACK through to foreground")
            return false
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    consumingThisPress = true
                    DiagnosticLog.i(TAG, "BACK down — bringing Copilot to front")
                    bringCopilotToFront()
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                val wasConsuming = consumingThisPress
                consumingThisPress = false
                if (!wasConsuming) {
                    DiagnosticLog.w(TAG, "BACK up without matching down — letting it pass")
                }
                wasConsuming
            }
            else -> false
        }
    }

    private fun bringCopilotToFront() {
        // REORDER_TO_FRONT preserves the activity's nav back stack, so Copilot
        // returns to the screen the driver was last on rather than resetting Home.
        // Deliberately no LAUNCH_ADJACENT: Copilot always comes back fullscreen,
        // never as a split pane.
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    /**
     * If the press is an enabled knob press while Waze is foreground and a node matching the
     * configured label is on screen, dispatch a tap at its center and claim the press.
     * Returns true only when a tap was dispatched (so the caller consumes the event).
     */
    private fun tryTapWazeGo(keyCode: Int, eventTime: Long): Boolean {
        val foreground = AutoSwitchBack.foregroundForDiagnostics()
        if (!GoTapDecider.shouldAttempt(wazeGoEnabled, foreground, keyCode, WazeGoDefaults.KNOB_KEYCODES)) {
            return false
        }
        // The carbox re-injects the same press a few tens of ms later as a synthetic DPAD_CENTER.
        // If we just tapped, swallow the duplicate (consume it) instead of tapping a second time
        // on whatever replaced the "Go now" screen once navigation started.
        if (eventTime - lastWazeTapAt < TAP_DEBOUNCE_MS) {
            consumingKnobKeyCode = keyCode
            return true
        }
        val label = wazeGoLabel.ifBlank { WazeGoDefaults.LABEL }
        val root = rootInActiveWindow
        if (root == null) {
            DiagnosticLog.w(TAG, "waze-go: knob pressed but no active-window root")
            return false
        }
        val node = findGoNode(root, label)
        if (node == null) {
            DiagnosticLog.i(TAG, "waze-go: knob pressed, no '$label' node — ${describeNodes(root)}")
            return false
        }
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())
        DiagnosticLog.i(TAG, "waze-go: tapped '$label' at (${bounds.centerX()},${bounds.centerY()})")
        lastWazeTapAt = eventTime
        consumingKnobKeyCode = keyCode
        return true
    }

    /** Breadth-first search for the first node whose text/contentDescription matches [label]. */
    private fun findGoNode(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (GoNodeMatcher.matches(label, node.text?.toString(), node.contentDescription?.toString())) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) DiagnosticLog.w(TAG, "dispatchGesture returned false")
    }

    /** Compact dump of labeled nodes, so the first car test reveals how Waze exposes "Go now"
     *  (text vs contentDescription, bounds, clickable) even when the configured label misses. */
    private fun describeNodes(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder("nodes=[")
        var shown = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && shown < MAX_DUMP_NODES) {
            val node = queue.removeFirst()
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            if (!text.isNullOrBlank() || !desc.isNullOrBlank()) {
                val b = Rect().also { node.getBoundsInScreen(it) }
                sb.append("{t='$text',d='$desc',b=${b.toShortString()},clk=${node.isClickable}} ")
                shown++
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.append(']').toString()
    }

    private companion object {
        const val TAG = "BackGrabber"
        const val TAP_DURATION_MS = 50L
        const val MAX_DUMP_NODES = 40

        /** Window in which a 2nd knob press is treated as the carbox's synthetic duplicate of the
         *  first and swallowed. Comfortably covers the ~45–200ms re-injection seen in logs while
         *  staying below a realistic deliberate double-press. */
        const val TAP_DEBOUNCE_MS = 800L
    }
}
