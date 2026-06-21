package com.vladutu.copilot.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.di.settingsDataStore
import com.vladutu.copilot.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        DiagnosticLog.i("Boot", "received action=$action")
        if (action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        // onReceive runs on the main thread, and this reads the flag from disk at
        // boot. Bound the blocking read so a slow read under boot-time I/O contention
        // can never approach the BroadcastReceiver ANR budget; on timeout or any
        // error, fail safe to disabled.
        val enabled = runCatching {
            runBlocking {
                withTimeoutOrNull(BOOT_READ_TIMEOUT_MS) {
                    SettingsStore(appContext.settingsDataStore).autoStartFlow.first()
                }
            }
        }.getOrNull() ?: false
        if (!enabled) {
            DiagnosticLog.i("Boot", "auto-start disabled, skipping")
            return
        }

        // "Display over other apps" is our Background-Activity-Launch exemption.
        // If it is somehow missing, the launch below will be dropped silently —
        // log it so the failure is diagnosable rather than invisible.
        if (!Settings.canDrawOverlays(appContext)) {
            DiagnosticLog.e("Boot", "overlay permission missing — launch may be dropped by BAL")
        }

        try {
            val launch = Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launch)
            DiagnosticLog.i("Boot", "startActivity(MainActivity) called")
        } catch (t: Throwable) {
            DiagnosticLog.e("Boot", "boot startActivity failed (${t.javaClass.simpleName})", t)
        }
    }

    private companion object {
        const val BOOT_READ_TIMEOUT_MS = 2_000L
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
