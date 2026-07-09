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
import com.vladutu.copilot.CopilotApp
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.bubble.BubbleController
import com.vladutu.copilot.diagnostics.DiagnosticLog
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
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        }
        AutoSwitchBack.ownPackage = applicationContext.packageName
        DiagnosticLog.i(TAG, "onServiceConnected flags=0x${serviceInfo?.flags?.toString(16)} caps=0x${serviceInfo?.capabilities?.toString(16)} ownPkg=${applicationContext.packageName}")

        val store = (applicationContext as CopilotApp).locator.settingsStore
        serviceScope.launch { store.wazeGoEnabledFlow.collect { wazeGoEnabled = it } }
        serviceScope.launch { store.wazeGoLabelFlow.collect { wazeGoLabel = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Only track packages we could actually return to. Overlay / floating-widget apps
        // (e.g. com.applepie.floatingball) and system UI (the volume HUD, etc.) fire
        // window-state-changed events too, but they aren't real foreground apps and have no
        // launcher activity. Tracking them would poison the restore target (we'd snapshot an
        // unlaunchable overlay, as seen on the carbox) or trigger a spurious "user moved away"
        // abort. Filtering to restorable packages keeps `currentForeground` on the real app.
        if (!isRestorable(pkg)) return
        AutoSwitchBack.onForeground(pkg)

        if (pkg in AutoSwitchBack.MUSIC_PKGS &&
            AutoSwitchBack.shouldScheduleOnMusicAppShown() &&
            !switchBackPending
        ) {
            switchBackPending = true
            DiagnosticLog.i(TAG, "$pkg shown while armed — switch-back in ${AutoSwitchBack.SETTLE_MS}ms")
            handler.postDelayed({ fireSwitchBack() }, AutoSwitchBack.SETTLE_MS)
        }
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
        // LAUNCH_ADJACENT: when the screen is split, bring the target forward inside its
        // pane instead of dissolving the split into a fullscreen launch; ignored otherwise.
        intent.addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
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
        // LAUNCH_ADJACENT: in split-screen, open Copilot into the focused pane and keep
        // the other app visible instead of going fullscreen; ignored when not split.
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
            )
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
