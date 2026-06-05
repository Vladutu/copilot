package com.vladutu.copilot.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vladutu.copilot.diagnostics.DiagnosticLog

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        DiagnosticLog.i("Boot", "received action=$action")
        if (action !in HANDLED_ACTIONS) return
        // Directly calling startActivity from a receiver hits Background Activity
        // Launch and gets silently dropped on Android 10+. Bounce through a
        // foreground service which can legitimately start the activity.
        val svc = Intent(context, BootLaunchService::class.java)
        try {
            context.startForegroundService(svc)
            DiagnosticLog.i("Boot", "startForegroundService(BootLaunchService) ok")
        } catch (t: Throwable) {
            DiagnosticLog.e("Boot", "startForegroundService failed (${t.javaClass.simpleName})", t)
        }
    }

    private companion object {
        // QUICKBOOT_POWERON is fired by many headunits / Chinese ROMs when resuming
        // from sleep instead of a full cold boot — include both so the carbox has a
        // chance regardless of which lifecycle it uses on ignition.
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
