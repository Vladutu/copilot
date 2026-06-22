package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private val topicMutex = Mutex()

    val autoStartFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START] ?: false
    }

    suspend fun setAutoStart(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_START] = enabled }
    }

    /** Null until [ensureTopic] (or [regenerateTopic]) has minted one. */
    val topicFlow: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_NTFY_TOPIC] }

    /**
     * Returns the persisted topic, minting + saving one on first call. Idempotent.
     * The mutex serializes the read-then-write so two concurrent first-run callers
     * (e.g. the service and the UI) can't mint two different topics.
     */
    suspend fun ensureTopic(): String = topicMutex.withLock {
        val existing = topicFlow.first()
        if (existing != null) return existing
        val minted = TopicGenerator.generate()
        dataStore.edit { prefs -> prefs[KEY_NTFY_TOPIC] = minted }
        minted
    }

    /** Mints and saves a new topic unconditionally (destructive re-pair). */
    suspend fun regenerateTopic(): String = topicMutex.withLock {
        val minted = TopicGenerator.generate()
        dataStore.edit { prefs -> prefs[KEY_NTFY_TOPIC] = minted }
        minted
    }

    private companion object {
        val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val KEY_NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
    }
}
