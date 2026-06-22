package com.vladutu.copilot.settings

import java.security.SecureRandom

/**
 * Mints and validates the per-install ntfy topic.
 *
 * Format (shared across Copilot/Pilot/Wingman): `copilot-` + 32 lowercase hex chars,
 * derived from 16 secure-random bytes. The `copilot-` prefix keeps topics recognizable
 * and drop-in compatible with the old hardcoded value's shape.
 */
object TopicGenerator {

    const val PREFIX = "copilot-"

    /** Single validation rule used at every entry point. */
    val REGEX = Regex("^copilot-[0-9a-f]{32}\$")

    /** 16 random bytes → lowercase hex → prefixed. `random` is injectable for tests. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val hex = buildString(32) {
            for (b in bytes) append("%02x".format(b))
        }
        return PREFIX + hex
    }

    fun isValid(topic: String?): Boolean = topic != null && REGEX.matches(topic)
}
