package com.vladutu.copilot.timemachine

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataStore-backed [YearVideoCache]: a single JSON blob mapping year → resolved video IDs.
 * Same flavour as LikedSongStore. The data is immutable, so there is no eviction; the only
 * write path is an upsert when a year fully resolves.
 */
class TimeMachineCache(private val dataStore: DataStore<Preferences>) : YearVideoCache {

    override suspend fun get(year: Int): List<String>? =
        decode(dataStore.data.first()[KEY])[year]

    override suspend fun put(year: Int, videoIds: List<String>) {
        dataStore.edit { prefs ->
            val map = decode(prefs[KEY]).toMutableMap()
            map[year] = videoIds
            prefs[KEY] = json.encodeToString(map.toMap())
        }
    }

    private fun decode(blob: String?): Map<Int, List<String>> {
        if (blob.isNullOrEmpty()) return emptyMap()
        return try {
            json.decodeFromString(blob)
        } catch (e: Exception) {
            Log.w(TAG, "time-machine cache unreadable; resetting", e)
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "TimeMachineCache"
        val KEY = stringPreferencesKey("time_machine_years")
        val json = Json { ignoreUnknownKeys = true }
    }
}
