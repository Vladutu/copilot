package com.vladutu.copilot.split

/**
 * Hook MainActivity registers while resumed so AppLauncher can ask Copilot to step out of
 * the way (moveTaskToBack) before a paired launch. A split covered by fullscreen Copilot
 * resurfaces intact when Copilot steps aside; relaunching one of its members with
 * startActivity instead rips that member out and dissolves the pair (emulator-verified).
 * Null whenever Copilot isn't the foreground activity.
 */
object CopilotForeground {
    @Volatile var stepAside: (() -> Unit)? = null
}
