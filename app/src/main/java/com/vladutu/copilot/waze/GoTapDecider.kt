package com.vladutu.copilot.waze

/**
 * Pure predicate: should a key press trigger a "Go now" tap attempt? Android-free so it is
 * unit-testable. The accessibility service supplies the live foreground package and key code.
 */
object GoTapDecider {
    const val WAZE_PKG: String = "com.waze"

    fun shouldAttempt(
        enabled: Boolean,
        foregroundPkg: String?,
        keyCode: Int,
        knobKeyCodes: Set<Int>,
    ): Boolean = enabled && foregroundPkg == WAZE_PKG && keyCode in knobKeyCodes
}
