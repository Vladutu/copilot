# Top Weekly Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** A "Top Weekly" home tile that, with one tap, plays the current US+GB weekly YouTube chart hits in YouTube Music as a single ~50-song queue.

**Architecture:** New `charts/` package mirroring the Discover containment pattern: a `ChartFetcher` boundary interface (NewPipe implementation in `charts/newpipe/`), a pure `ChartMerger` (US-priority rank interleave, dedupe, cap 50), a `TempPlaylistMinter` that turns video IDs into an anonymous `TLGG…` queue via YouTube's `watch_videos` redirect, and a `ChartsRepository` that orchestrates and **never throws** — every failure degrades to launching the US chart playlist directly. UI is one new tile wired through `MainActivity`'s existing `launchOrReport` path.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), NewPipe Extractor v0.26.3, OkHttp 5, JUnit 4 + MockWebServer + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-12-top-weekly-design.md`

## ⚠ Environment rules (override the default skill workflow)

- **NEVER run gradle or `make check` on this machine** — there is no Android SDK here; the build is verified by Georgian on his Mac at the end. Skip every "run the test" step; the TDD discipline here is *write the test first, then the implementation*, with execution deferred.
- **NEVER `git commit`** — Georgian reviews and commits himself. Skip every commit step. `git add` is also unnecessary.
- Kotlin style: 4-space indent, trailing commas, KDoc on classes explaining *why* (match the files you touch).

---

### Task 1: ChartMerger (pure merge logic)

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/charts/ChartMerger.kt`
- Test: `app/src/test/java/com/vladutu/copilot/charts/ChartMergerTest.kt`

- [x] **Step 1: Write the failing test**

```kotlin
package com.vladutu.copilot.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMergerTest {

    @Test fun `interleaves by rank with primary first`() {
        val merged = ChartMerger.merge(listOf("us1", "us2"), listOf("gb1", "gb2"))
        assertEquals(listOf("us1", "gb1", "us2", "gb2"), merged)
    }

    @Test fun `dedupes across charts keeping the first occurrence`() {
        // "hit" is US #2 and GB #1 — it appears once, at its earliest interleave slot.
        val merged = ChartMerger.merge(listOf("us1", "hit"), listOf("hit", "gb2"))
        assertEquals(listOf("us1", "hit", "gb2"), merged)
    }

    @Test fun `dedupes repeats within a single chart`() {
        val merged = ChartMerger.merge(listOf("a", "a", "b"), emptyList())
        assertEquals(listOf("a", "b"), merged)
    }

    @Test fun `one empty chart degrades to the other in order`() {
        assertEquals(listOf("gb1", "gb2"), ChartMerger.merge(emptyList(), listOf("gb1", "gb2")))
        assertEquals(listOf("us1", "us2"), ChartMerger.merge(listOf("us1", "us2"), emptyList()))
    }

    @Test fun `caps at 50 (watch_videos hard limit)`() {
        val us = (1..100).map { "us$it" }
        val gb = (1..100).map { "gb$it" }
        val merged = ChartMerger.merge(us, gb)
        assertEquals(50, merged.size)
        // First 50 of the interleave: us1, gb1, us2, gb2, … us25, gb25.
        assertEquals(listOf("us1", "gb1", "us2", "gb2"), merged.take(4))
        assertEquals(listOf("us25", "gb25"), merged.takeLast(2))
    }

    @Test fun `shorter inputs than the cap are returned whole`() {
        val merged = ChartMerger.merge(listOf("us1", "us2", "us3"), listOf("gb1"))
        assertEquals(listOf("us1", "gb1", "us2", "us3"), merged)
    }
}
```

- [x] **Step 2: Write the implementation**

```kotlin
package com.vladutu.copilot.charts

/**
 * Rank-interleaves two charts with the primary chart winning ties (P1, S1, P2, S2, …),
 * dedupes by ID keeping the earliest occurrence, and caps the result — YouTube's
 * watch_videos endpoint silently truncates temp playlists at 50 entries (verified
 * 2026-06-12, see spec), so sending more is pointless.
 */
object ChartMerger {
    const val MAX_QUEUE = 50

    fun merge(primary: List<String>, secondary: List<String>, cap: Int = MAX_QUEUE): List<String> {
        val out = LinkedHashSet<String>()
        for (i in 0 until maxOf(primary.size, secondary.size)) {
            primary.getOrNull(i)?.let { out.add(it) }
            secondary.getOrNull(i)?.let { out.add(it) }
            if (out.size >= cap) break
        }
        return out.take(cap)
    }
}
```

---

### Task 2: ChartFetcher boundary + ChartsException + PlaylistMinter interface

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/charts/ChartFetcher.kt`

No test — declarations only.

- [x] **Step 1: Write the boundary file**

```kotlin
package com.vladutu.copilot.charts

/** Wraps any backend failure so callers never see library- or HTTP-specific exceptions. */
class ChartsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Containment boundary for the chart backend (spec 2026-06-12-top-weekly), mirroring
 * [com.vladutu.copilot.discover.MusicSearcher]: callers see only video IDs in chart
 * order, never NewPipe types, so the backend swaps at one construction site in
 * ServiceLocator.
 */
interface ChartFetcher {
    /** Ordered video IDs of a chart playlist, rank #1 first. Throws [ChartsException]. */
    suspend fun fetchVideoIds(playlistUrl: String): List<String>
}

/** Mints a launch URL for an ad-hoc queue of videos. Throws [ChartsException]. */
fun interface PlaylistMinter {
    suspend fun mint(videoIds: List<String>): String
}
```

---

### Task 3: TempPlaylistMinter (watch_videos redirect → TLGG queue URL)

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/charts/TempPlaylistMinter.kt`
- Test: `app/src/test/java/com/vladutu/copilot/charts/TempPlaylistMinterTest.kt`

- [x] **Step 1: Write the failing test** (MockWebServer import style matches `NtfySubscriberTest`: `okhttp3.mockwebserver.*`)

```kotlin
package com.vladutu.copilot.charts

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TempPlaylistMinterTest {

    private lateinit var server: MockWebServer
    private lateinit var minter: TempPlaylistMinter

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        minter = TempPlaylistMinter(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `mints a music-youtube launch url from the redirect Location`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .setHeader("Location", "https://www.youtube.com/watch?v=aaa&list=TLGGtest123"),
        )
        val url = minter.mint(listOf("aaa", "bbb", "ccc"))
        assertEquals("https://music.youtube.com/watch?v=aaa&list=TLGGtest123", url)
        val recorded = server.takeRequest()
        assertEquals("/watch_videos?video_ids=aaa,bbb,ccc", recorded.path)
    }

    @Test fun `non-redirect response throws ChartsException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("nope"))
        try {
            minter.mint(listOf("aaa"))
            fail("expected ChartsException")
        } catch (expected: ChartsException) {
        }
    }

    @Test fun `redirect without a list id throws ChartsException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(303)
                .setHeader("Location", "https://www.youtube.com/watch?v=aaa"),
        )
        try {
            minter.mint(listOf("aaa"))
            fail("expected ChartsException")
        } catch (expected: ChartsException) {
            assertTrue(expected.message!!.contains("list"))
        }
    }
}
```

- [x] **Step 2: Write the implementation**

```kotlin
package com.vladutu.copilot.charts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Mints an anonymous YouTube temp playlist from a list of video IDs via the
 * undocumented `watch_videos` endpoint: YouTube creates a server-side "TLGG…" list
 * and answers 303 with its ID in the Location header. Redirect-following is disabled
 * because the header IS the payload — we never need the watch page itself.
 *
 * The minted list is short-lived (the ID encodes its creation date), which is fine:
 * a fresh one is minted per tap and used seconds later. Verified working 2026-06-12.
 */
class TempPlaylistMinter(
    okHttp: OkHttpClient,
    private val baseUrl: String = "https://www.youtube.com",
) : PlaylistMinter {

    private val client = okHttp.newBuilder().followRedirects(false).build()

    /** Returns a music.youtube.com URL that plays [videoIds] in order. */
    override suspend fun mint(videoIds: List<String>): String = withContext(Dispatchers.IO) {
        require(videoIds.isNotEmpty()) { "no video ids to mint" }
        val request = Request.Builder()
            .url("$baseUrl/watch_videos?video_ids=${videoIds.joinToString(",")}")
            .header("User-Agent", USER_AGENT)
            .build()
        val listId = try {
            client.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (!response.isRedirect || location == null) {
                    throw ChartsException("watch_videos: expected redirect, got ${response.code}")
                }
                queryParam(location, "list")
                    ?: throw ChartsException("watch_videos: no list id in redirect '$location'")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChartsException) {
            throw e
        } catch (e: Exception) {
            throw ChartsException("watch_videos call failed: ${e.message}", e)
        }
        "https://music.youtube.com/watch?v=${videoIds.first()}&list=$listId"
    }

    /** Same JVM-pure query-param extractor as NewPipeMusicSearcher (no android.net.Uri). */
    private fun queryParam(url: String, param: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == param }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        // Desktop UA: keeps YouTube on the plain redirect path (mobile UAs sometimes
        // get interstitials).
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    }
}
```

---

### Task 4: NewPipeChartFetcher

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/charts/newpipe/NewPipeChartFetcher.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/discover/newpipe/NewPipeMusicSearcher.kt:76` — change `private companion object` to `internal companion object` so the lazy idempotent `NewPipe.init` is shared instead of duplicated.

No unit test — network-bound extractor, same policy as `NewPipeMusicSearcher` (which has none); covered by `ChartsRepository` tests via the `ChartFetcher` fake and by on-device verification.

- [x] **Step 1: Make the NewPipe init shareable**

In `NewPipeMusicSearcher.kt`, change:

```kotlin
    private companion object {
```

to:

```kotlin
    internal companion object {
```

- [x] **Step 2: Write the fetcher**

```kotlin
package com.vladutu.copilot.charts.newpipe

import com.vladutu.copilot.charts.ChartFetcher
import com.vladutu.copilot.charts.ChartsException
import com.vladutu.copilot.discover.newpipe.NewPipeMusicSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * NewPipe Extractor implementation of [ChartFetcher]. Everything NewPipe stays in
 * this package (spec: containment boundary) — all failures surface as [ChartsException].
 * The first response page (~100 items) covers a full chart; no pagination needed.
 */
class NewPipeChartFetcher(private val okHttp: OkHttpClient) : ChartFetcher {

    override suspend fun fetchVideoIds(playlistUrl: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                NewPipeMusicSearcher.ensureInit(okHttp)
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl)
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { queryParam(it.url, "v") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ChartsException("chart fetch '$playlistUrl' failed: ${e.message}", e)
            }
        }

    /** Tiny query-param extractor; avoids android.net.Uri so this class stays JVM-pure. */
    private fun queryParam(url: String?, param: String): String? {
        val query = url?.substringAfter('?', "") ?: return null
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == param }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotEmpty() }
    }
}
```

---

### Task 5: ChartsRepository (orchestration + fallback)

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/charts/ChartsRepository.kt`
- Test: `app/src/test/java/com/vladutu/copilot/charts/ChartsRepositoryTest.kt`

- [x] **Step 1: Write the failing test**

```kotlin
package com.vladutu.copilot.charts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeFetcher : ChartFetcher {
    var byUrl: Map<String, List<String>> = emptyMap()
    var failUrls: Set<String> = emptySet()

    override suspend fun fetchVideoIds(playlistUrl: String): List<String> {
        if (playlistUrl in failUrls) throw ChartsException("boom")
        return byUrl[playlistUrl] ?: emptyList()
    }
}

private class FakeMinter : PlaylistMinter {
    var mintedIds: List<String>? = null
    var throwOnMint = false

    override suspend fun mint(videoIds: List<String>): String {
        if (throwOnMint) throw ChartsException("mint boom")
        mintedIds = videoIds
        return "https://music.youtube.com/watch?v=${videoIds.first()}&list=TLGGfake"
    }
}

class ChartsRepositoryTest {

    private val fetcher = FakeFetcher()
    private val minter = FakeMinter()
    private val repo = ChartsRepository(fetcher, minter)

    @Test fun `mints the US-priority interleaved dedupe of both charts`() = runTest {
        fetcher.byUrl = mapOf(
            ChartsRepository.US_CHART_URL to listOf("us1", "shared", "us3"),
            ChartsRepository.GB_CHART_URL to listOf("shared", "gb2", "gb3"),
        )
        val url = repo.topWeeklyLaunchUrl()
        assertEquals("https://music.youtube.com/watch?v=us1&list=TLGGfake", url)
        assertEquals(listOf("us1", "shared", "gb2", "us3", "gb3"), minter.mintedIds)
    }

    @Test fun `one failed chart degrades to the other alone`() = runTest {
        fetcher.byUrl = mapOf(ChartsRepository.US_CHART_URL to listOf("us1", "us2"))
        fetcher.failUrls = setOf(ChartsRepository.GB_CHART_URL)
        repo.topWeeklyLaunchUrl()
        assertEquals(listOf("us1", "us2"), minter.mintedIds)
    }

    @Test fun `both charts failing falls back to the US chart playlist`() = runTest {
        fetcher.failUrls = setOf(ChartsRepository.US_CHART_URL, ChartsRepository.GB_CHART_URL)
        assertEquals(ChartsRepository.FALLBACK_URL, repo.topWeeklyLaunchUrl())
        assertNull(minter.mintedIds)
    }

    @Test fun `mint failure falls back to the US chart playlist`() = runTest {
        fetcher.byUrl = mapOf(ChartsRepository.US_CHART_URL to listOf("us1"))
        minter.throwOnMint = true
        assertEquals(ChartsRepository.FALLBACK_URL, repo.topWeeklyLaunchUrl())
    }
}
```

- [x] **Step 2: Write the implementation**

```kotlin
package com.vladutu.copilot.charts

import com.vladutu.copilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One-tap "Top Weekly" orchestration: fetch the US and GB weekly chart playlists in
 * parallel, rank-interleave with US priority (spec 2026-06-12-top-weekly), mint an
 * anonymous queue, and return the launch URL.
 *
 * Never throws: any failure — one chart, both charts, or the minting call — degrades
 * stepwise so the tile always plays something, in the worst case the US chart playlist
 * launched directly (in chart order, hence no &shuffle=1 on the fallback URL).
 */
class ChartsRepository(
    private val fetcher: ChartFetcher,
    private val minter: PlaylistMinter,
) {
    suspend fun topWeeklyLaunchUrl(): String = coroutineScope {
        val us = async { fetchSafe(US_CHART_URL, "US") }
        val gb = async { fetchSafe(GB_CHART_URL, "GB") }
        val ids = ChartMerger.merge(us.await(), gb.await())
        if (ids.isEmpty()) {
            DiagnosticLog.e(TAG, "both chart fetches failed — falling back to US chart playlist")
            return@coroutineScope FALLBACK_URL
        }
        try {
            minter.mint(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "minting failed — falling back to US chart playlist", e)
            FALLBACK_URL
        }
    }

    private suspend fun fetchSafe(url: String, label: String): List<String> =
        try {
            fetcher.fetchVideoIds(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "$label chart fetch failed", e)
            emptyList()
        }

    companion object {
        private const val TAG = "Charts"

        // YouTube's own auto-updating weekly chart playlists: stable IDs, contents
        // refreshed weekly (verified 2026-06-12, see spec).
        const val US_PLAYLIST_ID = "PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI"
        const val GB_PLAYLIST_ID = "PL4fGSI1pDJn6_f5P3MnzXg9l3GDfnSlXa"
        const val US_CHART_URL = "https://www.youtube.com/playlist?list=$US_PLAYLIST_ID"
        const val GB_CHART_URL = "https://www.youtube.com/playlist?list=$GB_PLAYLIST_ID"
        const val FALLBACK_URL = "https://music.youtube.com/watch?list=$US_PLAYLIST_ID"
    }
}
```

Note: `DiagnosticLog` is a documented no-op when `init` was never called, so it is safe in unit tests.

---

### Task 6: ServiceLocator wiring

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt`

- [x] **Step 1: Add the repository**

Add imports:

```kotlin
import com.vladutu.copilot.charts.ChartsRepository
import com.vladutu.copilot.charts.TempPlaylistMinter
import com.vladutu.copilot.charts.newpipe.NewPipeChartFetcher
```

Add below `discoverRepository` (keep the boundary comment style):

```kotlin
    val chartsRepository: ChartsRepository by lazy {
        // Same containment rule as discoverRepository: the only line that names the
        // chart backend.
        ChartsRepository(NewPipeChartFetcher(okHttp), TempPlaylistMinter(okHttp))
    }
```

---

### Task 7: HomeTile busy state + string resource

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] **Step 1: Add the string**

In `strings.xml`, after `home_discover` (line 13):

```xml
    <string name="home_top_weekly">Top Weekly</string>
```

- [x] **Step 2: Add a `busy` parameter to HomeTile**

Add the parameter (after `iconTint`):

```kotlin
fun HomeTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    @DrawableRes fallbackRes: Int? = null,
    fallbackIcon: ImageVector? = null,
    iconTint: Color? = null,
    busy: Boolean = false,
) {
```

Change the icon `when` block so a busy tile shows a spinner in the icon slot (busy wins over all icon variants):

```kotlin
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(96.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                appIcon != null -> Image(
```

(rest of the `when` unchanged). Add the import:

```kotlin
import androidx.compose.material3.CircularProgressIndicator
```

---

### Task 8: HomeScreen — 8th tile, knob walks it

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`

- [x] **Step 1: Extend the tile model and grid**

1. Update the count and its comment (lines 39-40):

```kotlin
// Waze + Maps + Playlists + Songs + Discover + Places + Radio + Top Weekly.
// Knob walks all eight.
private const val TILE_COUNT = 8
```

2. Give `MediaTile` a busy flag (line 43):

```kotlin
private data class MediaTile(
    val labelRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val busy: Boolean = false,
)
```

3. Add the two parameters to `HomeScreen` (after `onOpenRadio`):

```kotlin
    onOpenTopWeekly: () -> Unit,
    topWeeklyBusy: Boolean,
```

4. Update the knob-order comment (lines 60-61) to `… → Places → Radio → Top Weekly` and the media-tile comment (line 68) to `indices 2..7`, then append the tile to `mediaTiles`:

```kotlin
        MediaTile(R.string.home_radio, Icons.Filled.Radio, onOpenRadio),
        MediaTile(
            R.string.home_top_weekly,
            Icons.AutoMirrored.Filled.TrendingUp,
            onOpenTopWeekly,
            busy = topWeeklyBusy,
        ),
    )
```

5. Pass `busy` through in the grid body (line 144):

```kotlin
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[globalIndex]),
                            label = stringResource(tile.labelRes),
                            onClick = tile.onClick,
                            fallbackIcon = tile.icon,
                            busy = tile.busy,
                        )
```

6. Add the import:

```kotlin
import androidx.compose.material.icons.automirrored.filled.TrendingUp
```

The grid math needs no change: 6 media tiles chunk into two full rows of `MEDIA_COLUMNS = 3` (Playlists/Songs/Discover, Places/Radio/Top Weekly).

---

### Task 9: MainActivity wiring (tap → fetch → launch)

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/MainActivity.kt`

- [x] **Step 1: Wire the callback in the `home` composable**

Replace the `composable("home")` block (lines 115-129) with:

```kotlin
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            // Tap → chart fetch + queue mint (1-3 s) → YT Music. Busy guards re-taps
            // and drives the tile's spinner; the repository never throws (it falls
            // back to the US chart playlist), so only the launch itself can fail.
            var topWeeklyBusy by remember { mutableStateOf(false) }
            HomeScreen(
                state = uiState,
                onOpenWaze = { launchOrReport(launcher.openWazeApp()) { onLeftToOtherApp() } },
                onOpenMaps = { launchOrReport(launcher.openMapsApp()) { onLeftToOtherApp() } },
                onOpenPlaylists = { nav.navigate("list/playlist") },
                onOpenSongs = { nav.navigate("list/song") },
                onOpenDiscover = { nav.navigate("discover") },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenRadio = { nav.navigate("list/radio") },
                onOpenTopWeekly = {
                    if (!topWeeklyBusy) {
                        topWeeklyBusy = true
                        scope.launch {
                            try {
                                val url = app.locator.chartsRepository.topWeeklyLaunchUrl()
                                launchOrReport(launcher.launchYtMusic(url)) { onLeftToOtherApp() }
                            } finally {
                                topWeeklyBusy = false
                            }
                        }
                    }
                },
                topWeeklyBusy = topWeeklyBusy,
                onOpenStatus = { nav.navigate("status") },
                onBackFromHome = onLeftToOtherApp,
            )
        }
```

Add the imports:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
```

(`getValue` and `kotlinx.coroutines.launch` are already imported.)

---

### Task 10: Final verification (on Georgian's Mac — NOT here)

- [ ] **Step 1: Georgian runs `make check`** — `assembleDebug testDebugUnitTest lintDebug` must pass.
- [ ] **Step 2: On-device:** tap Top Weekly → spinner shows → YT Music opens an "Untitled List" queue, US #1 playing, ~50 songs, US/GB interleaved; knob walks all 8 tiles in reading order; auto-switch-back returns to Copilot.
- [ ] **Step 3:** Georgian reviews the diff and commits when satisfied.
