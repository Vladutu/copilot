package com.vladutu.copilot.config

object Config {
    const val NTFY_BASE: String = "https://ntfy.sh"

    // Drop messages whose ts is more than this many seconds away from "now".
    // Defends against ntfy's ~12h cache replaying stale commands on reconnect.
    const val MAX_MESSAGE_AGE_SEC: Long = 30L
}
