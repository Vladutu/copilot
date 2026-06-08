package com.vladutu.copilot.back

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.autoswitch.AutoSwitchBack
import com.vladutu.copilot.bubble.BubbleController
import com.vladutu.copilot.diagnostics.DiagnosticLog

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

    /** Guards against scheduling multiple settle-timers from repeated YT Music window events. */
    private var switchBackPending = false

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        AutoSwitchBack.onForeground(pkg)

        if (pkg == AutoSwitchBack.YT_MUSIC_PKG &&
            AutoSwitchBack.shouldScheduleOnYtMusicShown() &&
            !switchBackPending
        ) {
            switchBackPending = true
            DiagnosticLog.i(TAG, "YT Music shown while armed — switch-back in ${AutoSwitchBack.SETTLE_MS}ms")
            handler.postDelayed({ fireSwitchBack() }, AutoSwitchBack.SETTLE_MS)
        }
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
            DiagnosticLog.w(TAG, "no launch intent for $pkg — staying in YT Music")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { applicationContext.startActivity(intent) }
            .onSuccess { DiagnosticLog.i(TAG, "switched back to $pkg") }
            .onFailure { DiagnosticLog.w(TAG, "switch-back startActivity failed for $pkg", it) }
    }

    override fun onInterrupt() {
        DiagnosticLog.w(TAG, "onInterrupt")
        consumingThisPress = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device?.name ?: ""
        DiagnosticLog.i(
            TAG,
            "key kc=${event.keyCode}(${KeyEvent.keyCodeToString(event.keyCode)}) " +
                "action=${event.action} repeat=${event.repeatCount} dev='$device' " +
                "src=0x${event.source.toString(16)} bubbleVisible=${BubbleController.isVisible()}",
        )

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
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    private companion object {
        const val TAG = "BackGrabber"
    }
}
