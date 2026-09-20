package com.vladutu.copilot.bubble

import android.content.Context
import android.content.Intent
import android.os.Build

object BubbleController {

    enum class State { HIDDEN, VISIBLE, SUPPRESSED_WHILE_FOREGROUND }

    @Volatile private var state: State = State.HIDDEN
    @Volatile private var pendingShow: Boolean = false

    /** True only when the bubble overlay is actually on screen (i.e. Copilot is not the
     *  foreground activity). Read by the accessibility BACK-grabber to decide whether to
     *  intercept the hardware back button. */
    fun isVisible(): Boolean = state == State.VISIBLE

    fun requestShow(context: Context) {
        pendingShow = true
        if (state == State.SUPPRESSED_WHILE_FOREGROUND) return
        // Copilot's activity is already in the background (remote launch while another app
        // is on screen), so no onPause() will follow to flip the state: mark it visible
        // here, or the BACK grabber sees HIDDEN and passes the press through to Waze.
        state = State.VISIBLE
        startService(context)
    }

    fun onActivityResumed(context: Context) {
        state = State.SUPPRESSED_WHILE_FOREGROUND
        stopService(context)
    }

    fun onActivityPaused(context: Context) {
        if (pendingShow) {
            state = State.VISIBLE
            startService(context)
        } else {
            state = State.HIDDEN
        }
    }

    fun clear(context: Context) {
        onDismissed()
        stopService(context)
    }

    /** The driver dismissed the bubble from its notification; the service is already
     *  stopping itself, so only the state is reset here. Forgetting the pending request
     *  means a later pause of MainActivity does not resurrect the bubble. */
    fun onDismissed() {
        pendingShow = false
        state = State.HIDDEN
    }

    private fun startService(context: Context) {
        val intent = Intent(context, BubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, BubbleService::class.java))
    }
}
