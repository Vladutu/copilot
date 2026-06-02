package com.vladutu.copilot.history

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HistoryStore(private val dataStore: DataStore<Preferences>) {

    fun itemsFor(form: Form): Flow<List<SavedItem>> =
        dataStore.data.map { prefs -> decode(prefs[keyFor(form)]) }

    suspend fun mutate(form: Form, transform: (List<SavedItem>) -> List<SavedItem>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[keyFor(form)])
            val updated = transform(current)
            prefs[keyFor(form)] = json.encodeToString(updated)
        }
    }

    private fun keyFor(form: Form) = when (form) {
        Form.PLAYLIST -> KEY_PLAYLISTS
        Form.SONG -> KEY_SONGS
        Form.DESTINATION -> KEY_DESTINATIONS
    }

    private fun decode(blob: String?): List<SavedItem> {
        if (blob.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(blob)
        } catch (e: Exception) {
            Log.w(TAG, "history JSON unreadable; resetting", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "HistoryStore"
        val KEY_PLAYLISTS = stringPreferencesKey("saved_playlists")
        val KEY_SONGS = stringPreferencesKey("saved_songs")
        val KEY_DESTINATIONS = stringPreferencesKey("saved_destinations")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
