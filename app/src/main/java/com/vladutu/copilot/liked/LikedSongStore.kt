package com.vladutu.copilot.liked

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LikedSongStore(private val dataStore: DataStore<Preferences>) {

    fun items(): Flow<List<LikedSong>> =
        dataStore.data.map { prefs -> decode(prefs[KEY]) }

    suspend fun mutate(transform: (List<LikedSong>) -> List<LikedSong>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY])
            prefs[KEY] = json.encodeToString(transform(current))
        }
    }

    private fun decode(blob: String?): List<LikedSong> {
        if (blob.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(blob)
        } catch (e: Exception) {
            Log.w(TAG, "liked JSON unreadable; resetting", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "LikedSongStore"
        val KEY = stringPreferencesKey("liked_songs")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
