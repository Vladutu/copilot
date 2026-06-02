package com.vladutu.copilot.bubble

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BubblePositionStore(private val dataStore: DataStore<Preferences>) {
    val positionFlow: Flow<Pair<Int, Int>?> = dataStore.data.map { prefs ->
        val x = prefs[KEY_X]
        val y = prefs[KEY_Y]
        if (x != null && y != null) x to y else null
    }

    suspend fun save(x: Int, y: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_X] = x
            prefs[KEY_Y] = y
        }
    }

    private companion object {
        val KEY_X = intPreferencesKey("bubble_x")
        val KEY_Y = intPreferencesKey("bubble_y")
    }
}
