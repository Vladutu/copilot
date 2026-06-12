# Copilot Screen Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the 8-tile home screen into a 2×2 home (Waze, Maps / Places, Music) plus a new Music page (Playlists, Songs, Top Weekly / Discover, Radio) with knob navigation, per the spec at `docs/superpowers/specs/2026-06-12-screen-reorg-design.md`.

**Architecture:** Pure Compose UI rewiring. A new `MusicScreen` mirrors the existing `HomeScreen` knob pattern (N FocusRequesters + `focusedIndex` + always-consume DPAD left/right). `HomeScreen` shrinks to 4 tiles. `MainActivity`'s NavHost gains a `"music"` route; the five music callbacks (including the Top Weekly busy fetch) move from the home composable to the music composable.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, material-icons-extended.

**WORKFLOW CONSTRAINTS (override any default in your instructions):**
- Do NOT run gradle or any build/test command — there is no Android SDK on this machine. Verification is by careful reading and the grep checks given in each task.
- Do NOT commit. Georgian reviews and tests on his Mac, then asks for the commit himself.
- There are no unit tests for Compose screens in this codebase and this plan adds none (no extractable logic; on-device verification is listed at the end).

---

### Task 1: Add the Music label string

**Files:**
- Modify: `/home/geo/projects/Copilot/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add `home_music` after `home_top_weekly`**

Current lines 7–14 of the file:

```xml
    <string name="home_waze">Waze</string>
    <string name="home_maps">Maps</string>
    <string name="home_playlists">Playlists</string>
    <string name="home_songs">Songs</string>
    <string name="home_destinations">Places</string>
    <string name="home_radio">Radio</string>
    <string name="home_discover">Discover</string>
    <string name="home_top_weekly">Top Weekly</string>
```

Add directly after the `home_top_weekly` line:

```xml
    <string name="home_music">Music</string>
```

- [ ] **Step 2: Verify**

Run: `grep -n "home_music" /home/geo/projects/Copilot/app/src/main/res/values/strings.xml`
Expected: exactly one line, `<string name="home_music">Music</string>`.

---

### Task 2: Create MusicScreen

**Files:**
- Create: `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/music/MusicScreen.kt`

Notes:
- `HomeTile` is a public composable in `com.vladutu.copilot.ui.home` — import it, don't copy it.
- The knob pattern (always consume `DirectionLeft/Right`, clamp at the ends) is copied deliberately from `HomeScreen`; do not "fix" it to return false at the ends — that desyncs Compose's focus search from `focusedIndex`.
- Layout: row 1 = Playlists, Songs, Top Weekly; row 2 = Discover, Radio, empty `Box` placeholder so tile sizes match. Knob walk = 5 stops; the empty slot is not a stop.

- [ ] **Step 1: Create the file with exactly this content**

```kotlin
package com.vladutu.copilot.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.ui.home.HomeTile

// Playlists + Songs + Top Weekly + Discover + Radio. Knob walks all five;
// the empty sixth grid slot is a placeholder, not a stop.
private const val TILE_COUNT = 5
private const val COLUMNS = 3

private data class MusicTile(
    val labelRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val busy: Boolean = false,
)

@Composable
fun MusicScreen(
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenTopWeekly: () -> Unit,
    topWeeklyBusy: Boolean,
    onOpenDiscover: () -> Unit,
    onOpenRadio: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // Knob twist (DPAD_LEFT/RIGHT) walks the five tiles linearly in reading order:
    // Playlists → Songs → Top Weekly → Discover → Radio.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    // Order must match the knob reading order above.
    val tiles = listOf(
        MusicTile(R.string.home_playlists, Icons.Filled.PlaylistPlay, onOpenPlaylists),
        MusicTile(R.string.home_songs, Icons.Filled.MusicNote, onOpenSongs),
        MusicTile(
            R.string.home_top_weekly,
            Icons.AutoMirrored.Filled.TrendingUp,
            onOpenTopWeekly,
            busy = topWeeklyBusy,
        ),
        MusicTile(R.string.home_discover, Icons.Filled.Explore, onOpenDiscover),
        MusicTile(R.string.home_radio, Icons.Filled.Radio, onOpenRadio),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Always consume Left/Right so focusedIndex stays the single source
                // of truth. Returning false at the ends would hand the event to
                // Compose's default directional focus search, which moves the
                // on-screen focus independently of focusedIndex — the two desync and
                // the knob appears to bounce back into earlier tiles. Clamp instead.
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusedIndex < TILE_COUNT - 1) focusedIndex++
                        true
                    }
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) focusedIndex--
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 3-column grid: Playlists/Songs/Top Weekly, then Discover/Radio/(empty).
        // Each row keeps weight 1f so tile size stays consistent; the trailing
        // slot in the partial row is an empty placeholder so tiles stay
        // grid-aligned.
        tiles.chunked(COLUMNS).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (col in 0 until COLUMNS) {
                    val tile = rowTiles.getOrNull(col)
                    if (tile != null) {
                        val globalIndex = rowIndex * COLUMNS + col
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[globalIndex]),
                            label = stringResource(tile.labelRes),
                            onClick = tile.onClick,
                            fallbackIcon = tile.icon,
                            busy = tile.busy,
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify**

Run: `grep -c "FocusRequester()" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/music/MusicScreen.kt`
Expected: 1 (the `List(TILE_COUNT) { FocusRequester() }` line).

Run: `grep -n "TILE_COUNT = 5" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/music/MusicScreen.kt`
Expected: one match.

---

### Task 3: Shrink HomeScreen to the 2×2 grid

**Files:**
- Modify: `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`

This is a near-rewrite: the `MediaTile` data class, the 6-tile media grid, and the music-related parameters all go away. The StatusPill header row, the Waze/Maps row, the BackHandler, and the knob clamp logic stay exactly as they are.

- [ ] **Step 1: Replace the entire file with this content**

```kotlin
package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.UiState

// Waze + Maps + Places + Music. Knob walks all four.
private const val TILE_COUNT = 4

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    // Knob twist (DPAD_LEFT/RIGHT) walks the four tiles linearly in reading order:
    // Waze → Maps → Places → Music. StatusPill is touch-only.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Always consume Left/Right so focusedIndex stays the single source
                // of truth. Returning false at the ends would hand the event to
                // Compose's default directional focus search, which moves the
                // on-screen focus independently of focusedIndex — the two desync and
                // the knob appears to bounce back into earlier tiles. Clamp instead.
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusedIndex < TILE_COUNT - 1) focusedIndex++
                        true
                    }
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) focusedIndex--
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header strip — pill flush right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        // Bottom row — Places + the Music hub page (indices 2..3).
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
```

- [ ] **Step 2: Verify the music plumbing is gone from HomeScreen**

Run: `grep -nE "onOpenPlaylists|onOpenSongs|onOpenDiscover|onOpenRadio|onOpenTopWeekly|topWeeklyBusy|MediaTile|TrendingUp|MusicNote|PlaylistPlay" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
Expected: no output.

Run: `grep -n "TILE_COUNT = 4" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
Expected: one match.

---

### Task 4: Rewire MainActivity navigation

**Files:**
- Modify: `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`

Three edits inside `CopilotNav`: simplify the `"home"` composable, add a `"music"` composable directly after it, and add the `MusicScreen` import. Everything else in the file (key-event dedup, other routes) stays untouched.

- [ ] **Step 1: Add the import**

After the line `import com.vladutu.copilot.ui.lists.SavedListScreen` add:

```kotlin
import com.vladutu.copilot.ui.music.MusicScreen
```

(Keep imports alphabetical: `ui.music` sorts after `ui.lists` and before `ui.permissions`.)

- [ ] **Step 2: Replace the `composable("home") { ... }` block**

The current block (lines 118–154, from `composable("home") {` through its closing `}`) becomes:

```kotlin
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = uiState,
                onOpenWaze = { launchOrReport(launcher.openWazeApp()) { onLeftToOtherApp() } },
                onOpenMaps = { launchOrReport(launcher.openMapsApp()) { onLeftToOtherApp() } },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenMusic = { nav.navigate("music") },
                onOpenStatus = { nav.navigate("status") },
                onBackFromHome = onLeftToOtherApp,
            )
        }
```

Note: `rememberCoroutineScope`, `topWeeklyBusy`, and the Top Weekly comment block move to the music composable in the next step — do not delete the `rememberCoroutineScope` or `mutableStateOf` imports at the top of the file; they are still used.

- [ ] **Step 3: Add the `"music"` composable directly after the `"home"` one**

```kotlin
        composable("music") {
            val scope = rememberCoroutineScope()
            // Tap → chart fetch + queue mint (1-3 s) → YT Music. Busy guards re-taps
            // and drives the tile's spinner; the repository never throws (it falls
            // back to the US chart playlist), so only the launch itself can fail.
            // Deliberate: leaving Copilot during the busy window doesn't cancel the
            // launch — music still starts when ready, same as a Pilot-driven launch
            // landing while navigating.
            var topWeeklyBusy by remember { mutableStateOf(false) }
            MusicScreen(
                onOpenPlaylists = { nav.navigate("list/playlist") },
                onOpenSongs = { nav.navigate("list/song") },
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
                onOpenDiscover = { nav.navigate("discover") },
                onOpenRadio = { nav.navigate("list/radio") },
                onBack = { nav.popBackStack() },
            )
        }
```

- [ ] **Step 4: Verify**

Run: `grep -n "composable(\"music\")" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`
Expected: one match.

Run: `grep -nE "onOpenPlaylists|onOpenSongs|onOpenTopWeekly|onOpenDiscover|onOpenRadio" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`
Expected: matches ONLY inside the `composable("music")` block (5 parameter lines), none inside `composable("home")`.

Run: `grep -nE "rememberCoroutineScope|mutableStateOf" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`
Expected: the two imports at the top plus one use of each inside `composable("music")`.

---

### Task 5: Whole-change consistency check (no build available)

**Files:** read-only pass over the three modified files + the new one.

- [ ] **Step 1: Cross-file signature check**

Confirm the `HomeScreen(...)` call in MainActivity passes exactly the parameters `HomeScreen.kt` declares (state, onOpenWaze, onOpenMaps, onOpenDestinations, onOpenMusic, onOpenStatus, onBackFromHome) and the `MusicScreen(...)` call matches `MusicScreen.kt`'s declaration (onOpenPlaylists, onOpenSongs, onOpenTopWeekly, topWeeklyBusy, onOpenDiscover, onOpenRadio, onBack).

- [ ] **Step 2: Stale-reference sweep**

Run: `grep -rn "onOpenTopWeekly\|MediaTile" /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/`
Expected: no output.

Run: `grep -rn "MusicScreen" /home/geo/projects/Copilot/app/src/main/java/`
Expected: the definition in `ui/music/MusicScreen.kt` and the import + call in `MainActivity.kt` only.

- [ ] **Step 3: Icon import sanity**

Run: `grep -rn "LibraryMusic" /home/geo/projects/Copilot/app/src/main/java/`
Expected: import + usage in `HomeScreen.kt` only. (`material-icons-extended` is already a dependency — `PlaylistPlay`, `Explore`, `TrendingUp` come from it.)

---

## On-device verification (Georgian, Mac + car)

1. Home knob walk hits all 4 tiles and clamps at both ends (no bounce/desync).
2. Knob press on Music opens the music page with focus on Playlists.
3. Music page knob walk hits all 5 tiles; the empty sixth slot is not a stop.
4. Top Weekly spinner shows on the music-page tile while fetching; re-taps guarded.
5. BACK (knob back) from the music page returns to home; BACK from home still backgrounds the app.
6. Waze, Maps, Places launch as before; Playlists/Songs/Radio lists, Discover, and Top Weekly all behave as before from the music page.
