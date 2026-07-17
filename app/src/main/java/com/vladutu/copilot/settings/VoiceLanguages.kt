package com.vladutu.copilot.settings

/**
 * Languages offered for the Discover voice tile's speech recognition. A setting of
 * its own because the recognizer defaults to the device locale — the carbox runs in
 * English while the driver may speak Romanian. Ids are BCP-47 tags (plus [SYSTEM_ID]),
 * stored as-is in SettingsStore; extend [options] to offer more.
 */
object VoiceLanguages {
    const val SYSTEM_ID = "system"

    /** id to display label, in dropdown order. */
    val options: List<Pair<String, String>> = listOf(
        SYSTEM_ID to "Device language",
        "en-US" to "English",
        "ro-RO" to "Română",
    )

    fun labelFor(id: String): String =
        options.firstOrNull { it.first == id }?.second ?: options.first().second

    /** Language tag for the recognizer intent; null → recognizer's own default. */
    fun tagFor(id: String): String? = if (id == SYSTEM_ID) null else id
}
