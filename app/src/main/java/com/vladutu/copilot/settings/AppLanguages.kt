package com.vladutu.copilot.settings

import androidx.annotation.StringRes
import com.vladutu.copilot.R
import java.util.Locale

/**
 * Languages the app's own UI can render in (Settings > General). Deliberately not tied
 * to the device locale — the carbox runs in English while the driver may want Romanian —
 * and separate from [VoiceLanguages], which only steers the speech recognizer. Ids are
 * stored as-is in SettingsStore; adding a language = a values-<tag>/strings.xml plus an
 * [options] entry. See [AppLocale] for how the choice is applied.
 */
object AppLanguages {
    const val DEFAULT_ID = "en"

    /** id to display-label resource, in dropdown order. Labels are endonyms, so each
     *  option reads the same no matter which language is currently active. */
    val options: List<Pair<String, Int>> = listOf(
        DEFAULT_ID to R.string.app_lang_english,
        "ro" to R.string.app_lang_romanian,
    )

    @StringRes
    fun labelResFor(id: String): Int =
        options.firstOrNull { it.first == id }?.second ?: options.first().second

    /** Unknown ids resolve to English, mirroring themeById's downgrade-safety. */
    fun localeFor(id: String): Locale = when (id) {
        "ro" -> Locale.forLanguageTag("ro-RO")
        else -> Locale.ENGLISH
    }
}
