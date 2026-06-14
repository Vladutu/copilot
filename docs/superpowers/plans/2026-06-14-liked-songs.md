# Liked Songs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the driver mark the currently-playing YouTube Music track and keep its title + artist in a local, separate "Liked" list inside Copilot that they can review, delete from, or clear.

**Architecture:** A `NotificationListenerService` reads the active YT Music media session and publishes `NowPlaying?` as a process-scoped `StateFlow`. The Home header shows a now-playing chunk with a heart (the last knob stop) that upserts into an independent `LikedSongStore` (own DataStore). A new Liked screen (reached as the last tile of the Music submenu) lists entries with per-item delete and clear-all.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, kotlinx.serialization, Android `MediaSessionManager`, JUnit + Robolectric (matching existing tests).

---

## Workflow constraints (read first)

- **Do NOT run gradle and do NOT commit.** There is no Android SDK on the Linux dev box. Georgian builds, runs tests, and commits on his Mac, then says "commit". Each task ends by leaving the code written; the "verify" notes are commands for Georgian to run on his Mac.
- Verify command (Georgian, Mac): `./gradlew :app:testDebugUnitTest`
- Follow existing patterns exactly (see `HistoryStore`, `HistoryRepository`, `HistoryRepositoryTest`, `ListenerService`, `SavedListScreen`, `MusicScreen`, `HomeScreen`).
- Package root: `com.vladutu.copilot`. Source root: `Copilot/app/src/main/java/com/vladutu/copilot`. Test root: `Copilot/app/src/test/java/com/vladutu/copilot`.

---

## Task 1: Domain models — `NowPlaying` and `LikedSong`

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/nowplaying/NowPlaying.kt`
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/liked/LikedSong.kt`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/liked/LikedSongTest.kt`

- [ ] **Step 1: Write the failing test for `LikedSong.sameSongAs`**

```kotlin
package com.vladutu.copilot.liked

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikedSongTest {

    private fun song(title: String, artist: String?) = LikedSong(title, artist, savedAt = 0L)

    @Test fun `same title and artist match ignoring case and surrounding space`() {
        assertTrue(song("Bad Habits", "Ed Sheeran").sameSongAs(song("  bad habits ", "ED SHEERAN")))
    }

    @Test fun `different artist does not match`() {
        assertFalse(song("Bad Habits", "Ed Sheeran").sameSongAs(song("Bad Habits", "Someone Else")))
    }

    @Test fun `null artist matches null or blank artist`() {
        assertTrue(song("Track", null).sameSongAs(song("Track", null)))
        assertTrue(song("Track", null).sameSongAs(song("Track", "   ")))
    }

    @Test fun `null artist does not match a real artist`() {
        assertFalse(song("Track", null).sameSongAs(song("Track", "Artist")))
    }
}
```

- [ ] **Step 2: Verify it fails (Mac).** `./gradlew :app:testDebugUnitTest --tests '*LikedSongTest'` → FAIL (unresolved `LikedSong`).

- [ ] **Step 3: Create `NowPlaying.kt`**

```kotlin
package com.vladutu.copilot.nowplaying

/** Title + optional artist of the track currently playing in YouTube Music. */
data class NowPlaying(
    val title: String,
    val artist: String?,
)
```

- [ ] **Step 4: Create `LikedSong.kt`**

```kotlin
package com.vladutu.copilot.liked

import kotlinx.serialization.Serializable

/**
 * One entry in the local Liked list. Text-only by design: there is no reliable way
 * to get the YouTube videoId of the externally-playing track, so we store what the
 * media session exposes — title + (optional) artist — plus when it was saved.
 */
@Serializable
data class LikedSong(
    val title: String,
    val artist: String? = null,
    val savedAt: Long,
) {
    /** Identity for dedup: title + artist compared trimmed and case-insensitively;
     *  null and blank artist are treated as the same "no artist". */
    fun sameSongAs(other: LikedSong): Boolean =
        title.trim().equals(other.title.trim(), ignoreCase = true) &&
            normArtist(artist) == normArtist(other.artist)

    private fun normArtist(a: String?): String = a?.trim()?.lowercase() ?: ""
}
```

- [ ] **Step 5: Verify it passes (Mac).** `./gradlew :app:testDebugUnitTest --tests '*LikedSongTest'` → PASS.

---

## Task 2: `LikedSongStore` (own DataStore)

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/liked/LikedSongStore.kt`

No standalone test — exercised through `LikedSongsRepository` in Task 3 (mirrors how `HistoryStore` is covered via `HistoryRepositoryTest`).

- [ ] **Step 1: Create `LikedSongStore.kt`** (mirrors `HistoryStore`, single key, no `Form`)

```kotlin
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
```

- [ ] **Step 2:** No run step (covered by Task 3).

---

## Task 3: `LikedSongsRepository` (upsert/dedup, delete, clearAll)

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/liked/LikedSongsRepository.kt`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/liked/LikedSongsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** (Robolectric + real DataStore, mirrors `HistoryRepositoryTest`)

```kotlin
package com.vladutu.copilot.liked

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.vladutu.copilot.nowplaying.NowPlaying
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LikedSongsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: LikedSongsRepository
    private val storeName = "test_liked_${System.nanoTime()}"
    private var fakeNow: Long = 0L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val ds = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) }
        )
        repo = LikedSongsRepository(LikedSongStore(ds), clock = { fakeNow })
    }

    @After fun tearDown() {
        context.preferencesDataStoreFile(storeName).delete()
    }

    @Test fun `like then read returns the song`() = runTest {
        fakeNow = 100L
        repo.like(NowPlaying("Bad Habits", "Ed Sheeran"))
        val list = repo.items().first()
        assertEquals(1, list.size)
        assertEquals("Bad Habits", list[0].title)
        assertEquals("Ed Sheeran", list[0].artist)
        assertEquals(100L, list[0].savedAt)
    }

    @Test fun `liking the same song again dedups and refreshes savedAt`() = runTest {
        fakeNow = 100L
        repo.like(NowPlaying("Bad Habits", "Ed Sheeran"))
        fakeNow = 150L
        repo.like(NowPlaying("Shivers", "Ed Sheeran"))
        fakeNow = 200L
        repo.like(NowPlaying(" bad habits ", "ED SHEERAN"))

        val list = repo.items().first()
        assertEquals(2, list.size)
        assertEquals("Bad Habits", list[0].title) // refreshed → top (newest first)
        assertEquals(200L, list[0].savedAt)
    }

    @Test fun `items are sorted newest first`() = runTest {
        fakeNow = 100L; repo.like(NowPlaying("A", "x"))
        fakeNow = 200L; repo.like(NowPlaying("B", "x"))
        fakeNow = 150L; repo.like(NowPlaying("C", "x"))
        assertEquals(listOf("B", "C", "A"), repo.items().first().map { it.title })
    }

    @Test fun `delete removes the entry`() = runTest {
        fakeNow = 100L; repo.like(NowPlaying("A", "x"))
        fakeNow = 200L; repo.like(NowPlaying("B", "x"))
        repo.delete(LikedSong("A", "x", savedAt = 100L))
        assertEquals(listOf("B"), repo.items().first().map { it.title })
    }

    @Test fun `clearAll empties the list`() = runTest {
        fakeNow = 100L; repo.like(NowPlaying("A", "x"))
        fakeNow = 200L; repo.like(NowPlaying("B", "x"))
        repo.clearAll()
        assertTrue(repo.items().first().isEmpty())
    }
}
```

- [ ] **Step 2: Verify it fails (Mac).** `./gradlew :app:testDebugUnitTest --tests '*LikedSongsRepositoryTest'` → FAIL (unresolved `LikedSongsRepository`).

- [ ] **Step 3: Create `LikedSongsRepository.kt`**

```kotlin
package com.vladutu.copilot.liked

import com.vladutu.copilot.nowplaying.NowPlaying
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LikedSongsRepository(
    private val store: LikedSongStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    fun items(): Flow<List<LikedSong>> =
        store.items().map { list -> list.sortedByDescending { it.savedAt } }

    /** Upsert: liking a song already present refreshes its savedAt (promotes it) rather
     *  than adding a duplicate row — same behavior as HistoryRepository.save. */
    suspend fun like(now: NowPlaying) {
        val song = LikedSong(now.title, now.artist, clock())
        store.mutate { current -> current.filterNot { it.sameSongAs(song) } + song }
    }

    suspend fun delete(song: LikedSong) =
        store.mutate { current -> current.filterNot { it.sameSongAs(song) } }

    suspend fun clearAll() = store.mutate { emptyList() }
}
```

- [ ] **Step 4: Verify it passes (Mac).** `./gradlew :app:testDebugUnitTest --tests '*LikedSongsRepositoryTest'` → PASS.

---

## Task 4: Media session reader — `MediaListenerService`

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/nowplaying/MediaListenerService.kt`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/nowplaying/NowPlayingMappingTest.kt`

The metadata-extraction logic is a pure function so it is JVM-testable (the existing `ListenerServiceMappingTest` follows the same "thin service, pure mapping" split).

- [ ] **Step 1: Write the failing mapping test**

```kotlin
package com.vladutu.copilot.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingMappingTest {

    @Test fun `title and artist map through`() {
        val np = nowPlayingFrom(title = "Bad Habits", artist = "Ed Sheeran", albumArtist = null)
        assertEquals(NowPlaying("Bad Habits", "Ed Sheeran"), np)
    }

    @Test fun `blank or null title yields null (nothing to like)`() {
        assertNull(nowPlayingFrom(title = null, artist = "x", albumArtist = null))
        assertNull(nowPlayingFrom(title = "   ", artist = "x", albumArtist = null))
    }

    @Test fun `missing artist falls back to album artist then null`() {
        assertEquals(NowPlaying("T", "Album Artist"), nowPlayingFrom("T", null, "Album Artist"))
        assertEquals(NowPlaying("T", null), nowPlayingFrom("T", null, null))
        assertEquals(NowPlaying("T", null), nowPlayingFrom("T", "  ", "  "))
    }

    @Test fun `values are trimmed`() {
        assertEquals(NowPlaying("T", "A"), nowPlayingFrom("  T ", " A ", null))
    }
}
```

- [ ] **Step 2: Verify it fails (Mac).** `./gradlew :app:testDebugUnitTest --tests '*NowPlayingMappingTest'` → FAIL (unresolved `nowPlayingFrom`).

- [ ] **Step 3: Create `MediaListenerService.kt`** (pure `nowPlayingFrom` at top level + the service)

```kotlin
package com.vladutu.copilot.nowplaying

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import com.vladutu.copilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pure: build NowPlaying from raw metadata strings. Title is required; artist falls
 *  back to album-artist, then null. Kept top-level so it is JVM-unit-testable. */
fun nowPlayingFrom(title: String?, artist: String?, albumArtist: String?): NowPlaying? {
    val t = title?.trim().orEmpty()
    if (t.isEmpty()) return null
    val a = (artist?.trim()?.takeIf { it.isNotEmpty() })
        ?: albumArtist?.trim()?.takeIf { it.isNotEmpty() }
    return NowPlaying(t, a)
}

/**
 * Reads the active YouTube Music media session and republishes its title/artist as a
 * process-scoped StateFlow ([nowPlaying]). Requires the one-time Notification access
 * grant. When YT Music is not the active session — or access is not granted — the flow
 * is null and the now-playing UI stays hidden.
 *
 * Mirrors ListenerService's companion-flow pattern so a Composable can collect it
 * regardless of service lifecycle.
 */
class MediaListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private var watched: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(controllers ?: emptyList())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(metadata)
        override fun onSessionDestroyed() {
            unwatch()
            nowPlayingState.value = null
        }
    }

    override fun onListenerConnected() {
        val mgr = getSystemService(MediaSessionManager::class.java)
        sessionManager = mgr
        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            mgr.addOnActiveSessionsChangedListener(sessionsListener, component)
            bind(mgr.getActiveSessions(component))
        }.onFailure { DiagnosticLog.w(TAG, "media session listen failed", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unwatch()
        nowPlayingState.value = null
    }

    private fun bind(controllers: List<MediaController>) {
        val yt = controllers.firstOrNull { it.packageName == YT_MUSIC_PKG }
        if (yt == null) {
            unwatch()
            nowPlayingState.value = null
            return
        }
        if (yt == watched) {
            publish(yt.metadata)
            return
        }
        unwatch()
        watched = yt.also { it.registerCallback(controllerCallback) }
        publish(yt.metadata)
    }

    private fun unwatch() {
        watched?.unregisterCallback(controllerCallback)
        watched = null
    }

    private fun publish(metadata: MediaMetadata?) {
        nowPlayingState.value = metadata?.let {
            nowPlayingFrom(
                title = it.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST),
                albumArtist = it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            )
        }
    }

    companion object {
        private const val TAG = "MediaListener"
        const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"

        private val nowPlayingState = MutableStateFlow<NowPlaying?>(null)
        val nowPlaying: StateFlow<NowPlaying?> = nowPlayingState
    }
}
```

- [ ] **Step 4: Verify it passes (Mac).** `./gradlew :app:testDebugUnitTest --tests '*NowPlayingMappingTest'` → PASS.

---

## Task 5: Wire stores into `ServiceLocator`

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt`

- [ ] **Step 1: Add the DataStore delegate** next to the existing ones (after line 21)

```kotlin
private val Context.likedDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_liked")
```

- [ ] **Step 2: Add imports** at the top of the file

```kotlin
import com.vladutu.copilot.liked.LikedSongStore
import com.vladutu.copilot.liked.LikedSongsRepository
```

- [ ] **Step 3: Add the repository** inside `ServiceLocator` (after `categoryStore`)

```kotlin
    val likedSongsRepository: LikedSongsRepository by lazy {
        LikedSongsRepository(LikedSongStore(appContext.likedDataStore))
    }
```

- [ ] **Step 4: Verify (Mac).** `./gradlew :app:assembleDebug` compiles.

---

## Task 6: Home knob arithmetic — `HomeKnob`

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeKnob.kt`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/ui/home/HomeKnobTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vladutu.copilot.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeKnobTest {

    @Test fun `four tiles when nothing playing`() {
        assertEquals(4, HomeKnob.tileCount(songPlaying = false))
    }

    @Test fun `five tiles when a song is playing (heart is the last stop)`() {
        assertEquals(5, HomeKnob.tileCount(songPlaying = true))
    }

    @Test fun `focus is clamped into range`() {
        assertEquals(0, HomeKnob.clampFocus(focused = 0, tileCount = 4))
        assertEquals(3, HomeKnob.clampFocus(focused = 3, tileCount = 4))
    }

    @Test fun `when the song stops while focused on the heart, focus drops to the last tile`() {
        // heart was index 4 (count 5); song stops → count 4 → focus clamps to 3
        assertEquals(3, HomeKnob.clampFocus(focused = 4, tileCount = 4))
    }
}
```

- [ ] **Step 2: Verify it fails (Mac).** `./gradlew :app:testDebugUnitTest --tests '*HomeKnobTest'` → FAIL.

- [ ] **Step 3: Create `HomeKnob.kt`**

```kotlin
package com.vladutu.copilot.ui.home

/** Pure knob-stop arithmetic for the Home screen. Four fixed tiles
 *  (Waze, Maps, Places, Music); a fifth stop — the Like heart — appears only while a
 *  song is playing, and only as the LAST stop, so default focus (index 0) never lands
 *  on it and a left-twist from Waze never reaches it. */
object HomeKnob {
    const val BASE_TILES = 4

    fun tileCount(songPlaying: Boolean): Int = if (songPlaying) BASE_TILES + 1 else BASE_TILES

    fun clampFocus(focused: Int, tileCount: Int): Int = focused.coerceIn(0, tileCount - 1)
}
```

- [ ] **Step 4: Verify it passes (Mac).** `./gradlew :app:testDebugUnitTest --tests '*HomeKnobTest'` → PASS.

---

## Task 7: Home header now-playing chunk + heart as last knob stop

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`

Composable — verified on device (no unit test). Replace the whole file with the version below. Changes vs. current: dynamic `TILE_COUNT` via `HomeKnob`, new `nowPlaying`/`onLike` params, the header now-playing chunk, and an extra `FocusRequester` for the heart as the last stop.

- [ ] **Step 1: Rewrite `HomeScreen.kt`**

```kotlin
package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.nowplaying.NowPlaying
import com.vladutu.copilot.service.UiState

@Composable
fun HomeScreen(
    state: UiState,
    nowPlaying: NowPlaying?,
    onLike: () -> Unit,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    val songPlaying = nowPlaying != null
    val tileCount = HomeKnob.tileCount(songPlaying)
    // Four fixed tiles + (optional) heart as the last stop.
    val tileFocus = remember { List(HomeKnob.BASE_TILES) { FocusRequester() } }
    val heartFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }

    // If the song stops while the heart was focused, clamp back onto the last tile.
    LaunchedEffect(tileCount) {
        focusedIndex = HomeKnob.clampFocus(focusedIndex, tileCount)
    }
    LaunchedEffect(focusedIndex, tileCount) {
        val target = if (focusedIndex < HomeKnob.BASE_TILES) tileFocus[focusedIndex] else heartFocus
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { if (focusedIndex < tileCount - 1) focusedIndex++; true }
                    Key.DirectionLeft -> { if (focusedIndex > 0) focusedIndex--; true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header strip: now-playing + heart on the left (only when a song is playing),
        // status pill flush right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nowPlaying != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = nowPlayingLabel(nowPlaying),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.like_song),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(heartFocus)
                            .clickable(onClick = onLike)
                            .padding(8.dp),
                    )
                }
            } else {
                // keep the pill flush-right when nothing is playing
                Box(modifier = Modifier.weight(1f))
            }
            StatusPill(state = state, onClick = onOpenStatus)
        }

        // Top row — outbound nav apps (indices 0..1).
        Row(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[0]),
                label = stringResource(R.string.home_waze),
                onClick = onOpenWaze,
                packageName = AppLauncher.WAZE_PKG,
                fallbackRes = R.drawable.ic_map_pin,
            )
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[1]),
                label = stringResource(R.string.home_maps),
                onClick = onOpenMaps,
                packageName = AppLauncher.MAPS_PKG,
                fallbackRes = R.drawable.ic_map_pin,
            )
        }
        // Bottom row — Places + Music (indices 2..3).
        Row(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[2]),
                label = stringResource(R.string.home_destinations),
                onClick = onOpenDestinations,
                fallbackIcon = Icons.Filled.Place,
            )
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[3]),
                label = stringResource(R.string.home_music),
                onClick = onOpenMusic,
                fallbackIcon = Icons.Filled.LibraryMusic,
            )
        }
    }
}

private fun nowPlayingLabel(np: NowPlaying): String =
    if (np.artist.isNullOrBlank()) "♪ ${np.title}" else "♪ ${np.title} — ${np.artist}"
```

> Note for implementer: add the missing imports flagged by the IDE — `androidx.compose.foundation.layout.Box` and `androidx.compose.foundation.layout.size`. They are used above (`Box(Modifier.weight)`, `.size(...)`).

- [ ] **Step 2: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; on device the heart shows only when a song plays, knob reaches it only by twisting right past Music.

---

## Task 8: Add the Liked tile as the LAST tile of the Music submenu

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/music/MusicScreen.kt`

- [ ] **Step 1: Bump the tile count** (line 38)

```kotlin
private const val TILE_COUNT = 6
```

- [ ] **Step 2: Add the `onOpenLiked` parameter** to `MusicScreen` (after `onOpenRadio`)

```kotlin
    onOpenLiked: () -> Unit,
```

- [ ] **Step 3: Add the Liked tile LAST in the `tiles` list** (after the Radio entry)

```kotlin
        MusicTile(R.string.home_liked, Icons.Filled.Favorite, onOpenLiked),
```

- [ ] **Step 4: Add the icon import** with the other icon imports

```kotlin
import androidx.compose.material.icons.filled.Favorite
```

- [ ] **Step 5: Update the reading-order comment** (lines 36-37 and 58-59) to append "→ Liked". The empty-slot comment no longer applies (6 tiles fill the 3×2 grid exactly); change the line-37 comment to: `// Playlists + Songs + Top Weekly + Discover + Radio + Liked; knob walks all six.`

- [ ] **Step 6: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; the grid shows Discover / Radio / Liked on row 2.

---

## Task 9: Liked list screen

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/liked/LikedSongsScreen.kt`

Composable — verified on device. Mirrors `SavedListScreen` (KnobPagedGrid + long-press delete dialog) and adds a clear-all button with its own confirm dialog. No tap-to-play.

- [ ] **Step 1: Create `LikedSongsScreen.kt`**

```kotlin
package com.vladutu.copilot.ui.liked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.liked.LikedSong
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.ScreenHeader

@Composable
fun LikedSongsScreen(
    items: List<LikedSong>,
    onDelete: (LikedSong) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LikedSong?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.home_liked), onBack = onBack)

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.empty_liked), style = MaterialTheme.typography.titleLarge)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.clear_all))
                }
            }
            KnobPagedGrid(
                items = items,
                resetKey = items.firstOrNull()?.let { it.title + "|" + it.artist },
                modifier = Modifier.weight(1f),
            ) { item, requesters ->
                LikedTile(
                    song = item,
                    onLongPress = { pendingDelete = item },
                    modifier = Modifier.fillMaxSize().let { base ->
                        if (requesters != null) base.focusRequester(requesters[0]) else base
                    },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, target.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(target); pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_no))
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_message)) },
            confirmButton = {
                TextButton(onClick = { onClearAll(); confirmClear = false }) {
                    Text(stringResource(R.string.confirm_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.confirm_delete_no))
                }
            },
        )
    }
}
```

- [ ] **Step 2: Create the `LikedTile` composable** in the same file (a simple text tile; mirror `SavedTile`'s card styling by reading `SavedTile.kt` first and matching its container/typography). Minimal version:

```kotlin
@Composable
private fun LikedTile(
    song: LikedSong,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .androidx_combinedClickablePlaceholder(onLongPress),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            song.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

> Implementer: the `androidx_combinedClickablePlaceholder` above is pseudo — replace by reading `SavedTile.kt` and reusing its exact long-press mechanism (`Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)` with `@OptIn(ExperimentalFoundationApi::class)`). Match `SavedTile`'s card container, elevation, and focus-highlight styling so the Liked tile looks consistent with the other lists.

- [ ] **Step 3: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; list renders, long-press deletes, clear-all empties.

---

## Task 10: Wire navigation — `liked` route + Home/Music wiring + Saved toast

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`

- [ ] **Step 1: Add imports** (with the other UI imports near the top)

```kotlin
import com.vladutu.copilot.nowplaying.MediaListenerService
import com.vladutu.copilot.ui.liked.LikedSongsScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

(`collectAsStateWithLifecycle` is already imported — do not duplicate.)

- [ ] **Step 2: Update the `home` composable** to collect now-playing and pass `onLike`

```kotlin
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            val nowPlaying by MediaListenerService.nowPlaying.collectAsStateWithLifecycle()
            HomeScreen(
                state = uiState,
                nowPlaying = nowPlaying,
                onLike = {
                    val np = nowPlaying ?: return@HomeScreen
                    app.applicationScope.launch { app.locator.likedSongsRepository.like(np) }
                    Toast.makeText(context, context.getString(R.string.liked_saved), Toast.LENGTH_SHORT).show()
                },
                onOpenWaze = { launchOrReport(launcher.openWazeApp()) { onLeftToOtherApp() } },
                onOpenMaps = { launchOrReport(launcher.openMapsApp()) { onLeftToOtherApp() } },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenMusic = { nav.navigate("music") },
                onOpenStatus = { nav.navigate("status") },
                onBackFromHome = onLeftToOtherApp,
            )
        }
```

> Note: `onLike`'s `return@HomeScreen` is illegal (it's a lambda arg, not the HomeScreen call). Implementer: capture into a local first:
> ```kotlin
> onLike = {
>     nowPlaying?.let { np ->
>         app.applicationScope.launch { app.locator.likedSongsRepository.like(np) }
>         Toast.makeText(context, context.getString(R.string.liked_saved), Toast.LENGTH_SHORT).show()
>     }
> },
> ```

- [ ] **Step 3: Pass `onOpenLiked` into the `music` composable's `MusicScreen(...)`**

```kotlin
                onOpenLiked = { nav.navigate("liked") },
```

- [ ] **Step 4: Add the `liked` route** (after the `music` composable block)

```kotlin
        composable("liked") {
            val liked by app.locator.likedSongsRepository.items()
                .collectAsStateWithLifecycle(emptyList())
            LikedSongsScreen(
                items = liked,
                onDelete = { song ->
                    app.applicationScope.launch { app.locator.likedSongsRepository.delete(song) }
                },
                onClearAll = {
                    app.applicationScope.launch { app.locator.likedSongsRepository.clearAll() }
                },
                onBack = { nav.popBackStack() },
            )
        }
```

- [ ] **Step 5: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; navigation Home→Music→Liked works; liking from Home toasts "Saved".

---

## Task 11: Register `MediaListenerService` in the manifest

**Files:**
- Modify: `Copilot/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the service** inside `<application>`, alongside the other `<service>` blocks (after the accessibility service)

```xml
        <service
            android:name=".nowplaying.MediaListenerService"
            android:exported="true"
            android:label="@string/liked_listener_label"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 2: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; after install, "Copilot" appears under Settings → Notification access.

---

## Task 12: Notification-access grant entry in Status screen

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/permissions/PermissionHelpers.kt`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt`

- [ ] **Step 1: Add helpers to `PermissionHelpers.kt`**

```kotlin
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

// inside object PermissionHelpers:
    fun isNotificationAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
```

- [ ] **Step 2: Add a grant row to `StatusScreen`** — after the `OutlinedButton(onClick = onOpenLogs)` block (around line 109). It shows only when access is missing.

```kotlin
        val ctx = LocalContext.current
        if (!PermissionHelpers.isNotificationAccessGranted(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openNotificationAccessSettings(ctx) }) {
                Text(stringResource(R.string.grant_now_playing_access))
            }
        }
```

- [ ] **Step 3: Add imports to `StatusScreen.kt`**

```kotlin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vladutu.copilot.ui.permissions.PermissionHelpers
import com.vladutu.copilot.R
```

- [ ] **Step 4: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; the Status screen shows the grant button until access is granted, then hides it on next resume.

---

## Task 13: Strings

**Files:**
- Modify: `Copilot/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the new strings** (read the file first; match its style; reuse `confirm_delete_*` which already exist)

```xml
    <string name="home_liked">Liked</string>
    <string name="empty_liked">No liked songs yet</string>
    <string name="like_song">Like this song</string>
    <string name="liked_saved">Saved</string>
    <string name="clear_all">Clear all</string>
    <string name="clear_all_title">Clear liked songs?</string>
    <string name="clear_all_message">This removes every song from your Liked list.</string>
    <string name="grant_now_playing_access">Enable now-playing access</string>
    <string name="liked_listener_label">Copilot now-playing</string>
```

- [ ] **Step 2: Verify (Mac).** `./gradlew :app:assembleDebug` compiles; no missing-resource errors.

---

## Final verification (Georgian, on Mac)

- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests pass (LikedSong, LikedSongsRepository, NowPlayingMapping, HomeKnob).
- [ ] `./gradlew :app:assembleDebug` — app builds.
- [ ] On the carbox: grant Notification access (Status screen button) → play a YT Music playlist → switch back to Copilot → header shows `♪ Title — Artist` → twist knob right past Music to the heart → select → "Saved" toast → Music → Liked shows the entry → long-press deletes → Clear all empties.
- [ ] Then: commit.

## Self-review notes (author)

- Spec coverage: capture (T4), storage separate from history (T2/T3/T5), header element + heart-last knob stop (T6/T7), Music Liked tile last (T8), liked screen delete + clear-all (T9), nav wiring (T10), manifest (T11), optional permission via Status (T12), strings (T13), tests (T1/T3/T4/T6). All spec sections covered.
- Type consistency: `LikedSong(title, artist, savedAt)`, `NowPlaying(title, artist)`, `LikedSongsRepository.{items,like,delete,clearAll}`, `HomeKnob.{tileCount,clampFocus,BASE_TILES}`, `MediaListenerService.nowPlaying`, `nowPlayingFrom(title,artist,albumArtist)` — names used consistently across tasks.
- Known implementer fix-ups flagged inline: HomeScreen missing `Box`/`size` imports; the `return@HomeScreen` correction in T10; `SavedTile` long-press/styling reuse in T9.
```
