package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vladutu.copilot.ui.GlowDefaults
import com.vladutu.copilot.ui.theme.DefaultTheme
import com.vladutu.copilot.ui.theme.LayoutMode
import com.vladutu.copilot.ui.theme.PageSizeDefaults
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

    /** Visual theme id (see ui/theme/Themes.kt); unknown ids resolve to Default at read time. */
    val themeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: DefaultTheme.id
    }

    suspend fun setTheme(id: String) {
        dataStore.edit { prefs -> prefs[KEY_THEME] = id }
    }

    /**
     * Whether the theme's background sweep (ThemeAccents.sweep) is drawn. Defaults on.
     * Theme-agnostic on purpose: one toggle for whichever theme carries a sweep.
     */
    val themeSweepFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_SWEEP] ?: true
    }

    suspend fun setThemeSweep(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_THEME_SWEEP] = enabled }
    }

    /** Background glow intensity in percent (0 = hidden); applies to ThemeAccents.glow. */
    val themeGlowFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_GLOW] ?: GlowDefaults.INTENSITY_DEFAULT
    }

    suspend fun setThemeGlow(percent: Float) {
        dataStore.edit { prefs -> prefs[KEY_THEME_GLOW] = percent }
    }

    /** Screen orientation + rail layout; unknown ids resolve to Landscape at read time. */
    val layoutModeFlow: Flow<LayoutMode> = dataStore.data.map { prefs ->
        LayoutMode.fromId(prefs[KEY_LAYOUT_MODE])
    }

    suspend fun setLayoutMode(mode: LayoutMode) {
        dataStore.edit { prefs -> prefs[KEY_LAYOUT_MODE] = mode.id }
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

    /** Whether the knob-focused tile fills with an amber wash. Defaults on. */
    val tileFocusFillFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TILE_FOCUS_FILL] ?: TileAppearanceDefaults.FOCUS_FILL
    }

    suspend fun setTileFocusFill(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_TILE_FOCUS_FILL] = enabled }
    }

    /** Tiles per page on the fixed menu rails (Home, Music). */
    val menuPageSizeFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_MENU_PAGE_SIZE] ?: PageSizeDefaults.MENU_TILES
    }

    suspend fun setMenuPageSize(tiles: Int) {
        dataStore.edit { prefs -> prefs[KEY_MENU_PAGE_SIZE] = tiles }
    }

    /** Tiles per page on the list rails (saved lists, Discover, browse results). */
    val listPageSizeFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_LIST_PAGE_SIZE] ?: PageSizeDefaults.LIST_TILES
    }

    suspend fun setListPageSize(tiles: Int) {
        dataStore.edit { prefs -> prefs[KEY_LIST_PAGE_SIZE] = tiles }
    }

    /** Whether launches may join/create a split screen (nav one pane, music the other).
     *  Defaults off: fullscreen launches unless the driver opts in. */
    val splitScreenFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SPLIT_SCREEN] ?: false
    }

    suspend fun setSplitScreen(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SPLIT_SCREEN] = enabled }
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

    /** Speech-recognition language for the Discover voice tile (see [VoiceLanguages]). */
    val voiceLanguageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_VOICE_LANGUAGE] ?: VoiceLanguages.SYSTEM_ID
    }

    suspend fun setVoiceLanguage(id: String) {
        dataStore.edit { prefs -> prefs[KEY_VOICE_LANGUAGE] = id }
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
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_THEME_SWEEP = booleanPreferencesKey("theme_sweep")
        val KEY_THEME_GLOW = floatPreferencesKey("theme_glow_intensity")
        val KEY_LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val KEY_NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        val KEY_TILE_FONT_SIZE = floatPreferencesKey("tile_font_size_sp")
        val KEY_TILE_BORDER_WIDTH = floatPreferencesKey("tile_border_width_dp")
        val KEY_TILE_FOCUS_FILL = booleanPreferencesKey("tile_focus_fill")
        val KEY_MENU_PAGE_SIZE = intPreferencesKey("menu_page_size")
        val KEY_LIST_PAGE_SIZE = intPreferencesKey("list_page_size")
        val KEY_SPLIT_SCREEN = booleanPreferencesKey("split_screen_launches")
        val KEY_WAZE_GO_ENABLED = booleanPreferencesKey("waze_go_enabled")
        val KEY_WAZE_GO_LABEL = stringPreferencesKey("waze_go_label")
        val KEY_VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    }
}
