package com.vladutu.copilot.settings

/**
 * The QR / pairing payload. Encoding a URI (not the bare topic) lets Pilot's scanner
 * confirm a QR is genuinely a pairing code and reject unrelated QRs.
 *
 * No percent-encoding needed: a valid topic is `^copilot-[0-9a-f]{32}$`, all
 * URI-safe characters.
 */
object PairingUri {
    const val SCHEME = "pilot"
    const val HOST = "pair"

    fun forTopic(topic: String): String = "$SCHEME://$HOST?topic=$topic"
}
