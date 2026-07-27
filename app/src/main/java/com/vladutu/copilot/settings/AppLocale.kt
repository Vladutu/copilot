package com.vladutu.copilot.settings

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * Applies the chosen app language (see [AppLanguages]) to a Context. Every component
 * that resolves user-visible strings — MainActivity, ListenerService, BubbleService —
 * wraps its base context in attachBaseContext, so getString/stringResource pick the
 * chosen language instead of the device locale. A language change recreates the
 * activity to re-run the wrap; long-running services keep the old language until
 * their next restart (in practice: the next drive).
 *
 * The DataStore read is blocking on purpose: attachBaseContext is synchronous, runs
 * once per component creation, and the preferences file is tiny and cached after the
 * first read.
 */
object AppLocale {
    fun wrap(base: Context, settings: SettingsStore): Context {
        val id = runBlocking { settings.appLanguageFlow.first() }
        val locale = AppLanguages.localeFor(id)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
