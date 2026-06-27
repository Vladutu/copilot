package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vladutu.copilot.ui.theme.TileAppearanceDefaults
import com.vladutu.copilot.waze.WazeGoDefaults
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

    /** Tile label text size in sp; defaults to the value tiles shipped with. */
    val tileFontSizeFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TILE_FONT_SIZE] ?: TileAppearanceDefaults.FONT_SIZE_SP
    }

    suspend fun setTileFontSize(sp: Float) {
        dataStore.edit { prefs -> prefs[KEY_TILE_FONT_SIZE] = sp }
    }

    /** Highlighted (focused) tile border thickness in dp; defaults to the shipped value. */
    val tileBorderWidthFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_TILE_BORDER_WIDTH] ?: TileAppearanceDefaults.BORDER_WIDTH_DP
    }

    suspend fun setTileBorderWidth(dp: Float) {
        dataStore.edit { prefs -> prefs[KEY_TILE_BORDER_WIDTH] = dp }
    }

    /** Whether a knob press taps Waze's "Go now". Defaults on. */
    val wazeGoEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WAZE_GO_ENABLED] ?: true
    }

    suspend fun setWazeGoEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_WAZE_GO_ENABLED] = enabled }
    }

    /** Text Copilot taps on Waze's confirm screen; defaults to "Go now". */
    val wazeGoLabelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WAZE_GO_LABEL] ?: WazeGoDefaults.LABEL
    }

    suspend fun setWazeGoLabel(label: String) {
        dataStore.edit { prefs -> prefs[KEY_WAZE_GO_LABEL] = label }
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
        val KEY_TILE_FONT_SIZE = floatPreferencesKey("tile_font_size_sp")
        val KEY_TILE_BORDER_WIDTH = floatPreferencesKey("tile_border_width_dp")
        val KEY_WAZE_GO_ENABLED = booleanPreferencesKey("waze_go_enabled")
        val KEY_WAZE_GO_LABEL = stringPreferencesKey("waze_go_label")
    }
}
