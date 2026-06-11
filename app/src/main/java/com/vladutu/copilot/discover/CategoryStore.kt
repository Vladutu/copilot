package com.vladutu.copilot.discover

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Keyword categories for the Discover screen. Authored in Pilot and synced over
 * ntfy (cmd=category); the headunit can only delete (long-press). Insertion order
 * is preserved so the grid keeps a stable reading order for the knob.
 */
class CategoryStore(private val dataStore: DataStore<Preferences>) {

    private val mutex = Mutex()

    fun categories(): Flow<List<String>> = dataStore.data.map { prefs -> decode(prefs[KEY]) }

    /** Idempotent: re-sending an existing keyword (any casing) is a no-op. */
    suspend fun add(keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isEmpty()) return
        mutate { current ->
            if (current.any { it.equals(cleaned, ignoreCase = true) }) current else current + cleaned
        }
    }

    suspend fun delete(keyword: String) = mutate { current ->
        current.filterNot { it.equals(keyword, ignoreCase = true) }
    }

    private suspend fun mutate(transform: (List<String>) -> List<String>) = mutex.withLock {
        dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(transform(decode(prefs[KEY])))
        }
    }

    private fun decode(blob: String?): List<String> {
        if (blob.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(blob)
        } catch (e: Exception) {
            Log.w(TAG, "categories JSON unreadable; resetting", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CategoryStore"
        val KEY = stringPreferencesKey("discover_categories")
        val json = Json { ignoreUnknownKeys = true }
    }
}
