package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val autoStartFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START] ?: false
    }

    suspend fun setAutoStart(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_START] = enabled }
    }

    private companion object {
        val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")
    }
}
