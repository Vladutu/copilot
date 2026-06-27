package com.vladutu.copilot.waze

/**
 * Pure matcher for a node's label against the configured button text. Case-insensitive and
 * trimmed on both sides. Android-free so it is unit-testable; the service walks the real
 * AccessibilityNodeInfo tree and feeds each node's text/contentDescription here.
 */
object GoNodeMatcher {
    fun matches(label: String, text: String?, contentDescription: String?): Boolean {
        val target = label.trim()
        if (target.isEmpty()) return false
        return text?.trim().equals(target, ignoreCase = true) ||
            contentDescription?.trim().equals(target, ignoreCase = true)
    }
}
