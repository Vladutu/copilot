# Configurable Tiles-Per-Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two Settings sliders (Menu/List tiles per page, defaults 4/5, range 2–8) driving every knob rail, with Home migrated onto the shared `KnobPagedGrid`.

**Architecture:** `LocalPageSizes` CompositionLocal fed from two DataStore Ints; `KnobPagedGrid` gains a trailing-stop (Home's heart), a `bottom` slot (now-playing strip inside the key-handler scope), and a `horizontalPadding` param; `KnobGridNav` gains `hasTrailingStop` nav math.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, JUnit4.

## Global Constraints

- NO gradle runs on this Linux box (no Android SDK). Test/verify steps are executed by Georgian on his Mac (`./gradlew test`).
- NO commits — Georgian reviews on his Mac and says "commit" first.
- Defaults (menu=4, list=5) must render an untouched install pixel-identically, except Home's rail→dots gap goes 12dp→8dp (grid-internal spacing; accepted in design).
- Callers passing no new params must get bit-identical grid behavior.

---

### Task 1: PageSizes model + CompositionLocal

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/ui/theme/PageSizes.kt`

**Interfaces:**
- Produces: `PageSizes(menuTiles: Int, listTiles: Int)`, `PageSizeDefaults.MENU_TILES=4/LIST_TILES=5/MIN=2f/MAX=8f`, `LocalPageSizes`.

- [ ] **Step 1: Write the file** (content in Task 1 code block of this plan — mirrors `TileAppearance.kt`)

```kotlin
package com.vladutu.copilot.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * User-tweakable tiles-per-page for the knob rails. Menu screens (Home, Music) hold a
 * fixed handful of entries; list screens page whatever the data brings. Provided once
 * at the nav root from the persisted settings and read via [LocalPageSizes] (it is
 * KnobPagedGrid's pageSize default), so the screens stay plumbing-free.
 *
 * Defaults mirror the hard-coded values the rails shipped with (4 menu / 5 list), so
 * an install that has never touched Settings pages exactly as before.
 */
data class PageSizes(
    val menuTiles: Int = PageSizeDefaults.MENU_TILES,
    val listTiles: Int = PageSizeDefaults.LIST_TILES,
)

object PageSizeDefaults {
    const val MENU_TILES = 4
    const val LIST_TILES = 5

    // Slider bounds exposed in Settings.
    const val MIN = 3f
    const val MAX = 6f
}

val LocalPageSizes = staticCompositionLocalOf { PageSizes() }
```

### Task 2: SettingsStore Int settings + tests

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt`
- Test: `app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt`

**Interfaces:**
- Produces: `menuPageSizeFlow: Flow<Int>` / `setMenuPageSize(tiles: Int)`, `listPageSizeFlow: Flow<Int>` / `setListPageSize(tiles: Int)`; keys `menu_page_size`, `list_page_size`.

- [ ] **Step 1: Add tests** (defaults 4/5 + round-trips, same style as tile settings)
- [ ] **Step 2: Add flows/setters after `tileFocusFillFlow` block, `intPreferencesKey` import, keys in companion; defaults from `PageSizeDefaults`**
- [ ] **Step 3 (Mac): `./gradlew test` — PASS**

### Task 3: KnobGridNav trailing stop + tests

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/KnobGridNav.kt`
- Test: `app/src/test/java/com/vladutu/copilot/ui/KnobGridNavTest.kt`

**Interfaces:**
- Produces: `KnobGridNav(itemCount, pageSize, stopsPerItem, hasTrailingStop = false)`, `isTrailingStop(pos: KnobPos): Boolean`. Trailing stop = one extra stop appended to the LAST page only (`stopsOnPage` grows by 1 there); `next`/`prev`/`clamp` handle it for free since they derive from `stopsOnPage`.

- [ ] **Step 1: Add tests** — trailing stop reachable after last item (multi-page 4/3, single-page 4/4, full last page 6/3), clamps at heart, `prev` returns to last tile, `clamp` repairs a trailing pos when `hasTrailingStop=false`, `isTrailingStop` true only on that stop.
- [ ] **Step 2: Implement** — constructor param, `stopsOnPage` + `isTrailingStop`.
- [ ] **Step 3 (Mac): `./gradlew test` — PASS (existing tests untouched)**

### Task 4: KnobPagedGrid — settings default, TrailingStop, bottom slot, padding

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/KnobPagedGrid.kt`

**Interfaces:**
- Produces: `class TrailingStop(focusRequester: FocusRequester, heartFilled: Boolean)`; new params `pageSize: Int = LocalPageSizes.current.listTiles`, `trailingStop: TrailingStop? = null`, `horizontalPadding: Dp = 32.dp`, `bottom: (@Composable () -> Unit)? = null`.

- [ ] **Step 1:** signature + `TrailingStop` class; nav `remember` keys gain `trailingStop != null`; KDoc update.
- [ ] **Step 2:** move `keyHandler` from the pager modifier to the root `Column` (`modifier.then(keyHandler)`) so the `bottom` slot (heart) stays inside the interception scope.
- [ ] **Step 3:** clamp effect keyed on `nav` (covers deletion AND pageSize/trailing changes); focus effect targets `trailingStop?.focusRequester` when `nav.isTrailingStop(pos)`, else `tileFocus.getOrNull(pos.stop)` (stale-trailing pos can briefly exceed the list before clamp runs).
- [ ] **Step 4:** dots: `dotCount = items.size + (trailing? 1 : 0)`; `current = items.size` on trailing stop; `heartAtLast`/`heartFilled` passthrough. Pager row uses `horizontalPadding`; `bottom?.invoke()` after the dots.

### Task 5: MusicScreen uses menu size

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/music/MusicScreen.kt`

- [ ] Delete `MUSIC_PAGE_SIZE`; pass `pageSize = LocalPageSizes.current.menuTiles`; update the paging comment.

### Task 6: Home migrates onto KnobPagedGrid

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
- Delete: `app/src/main/java/com/vladutu/copilot/ui/home/HomeKnob.kt`, `app/src/test/java/com/vladutu/copilot/ui/home/HomeKnobTest.kt`

**Interfaces:**
- Consumes: Task 4's `TrailingStop`, `bottom`, `horizontalPadding`; `LocalPageSizes.current.menuTiles`.

- [ ] **Step 1:** `HomeTile(labelRes, onClick, packageName?, fallbackIcon?, fallbackRes?)` list of the 4 fixed tiles in knob order.
- [ ] **Step 2:** replace rail Row + `focusedIndex` + `onPreviewKeyEvent` + focus `LaunchedEffect`s + `DotStrip` + trailing `NowPlayingStrip` with one `KnobPagedGrid(items=tiles, resetKey="home", pageSize=LocalPageSizes.current.menuTiles, perItemDots=true, trailingStop = songPlaying? TrailingStop(heartFocus, isLiked) : null, horizontalPadding=0.dp, bottom={ strip with likeControl, top padding 12.dp })`. `MediaRowTile(maxLines=1)` in the tile slot, `TopBar` + `BackHandler` unchanged.
- [ ] **Step 3:** delete `HomeKnob.kt` + `HomeKnobTest.kt`; prune unused imports.

### Task 7: Settings UI + MainActivity wiring + strings

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt`, `app/src/main/java/com/vladutu/copilot/MainActivity.kt`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1 `PageSizes`/`PageSizeDefaults`/`LocalPageSizes`, Task 2 flows/setters.
- Produces: `SettingsScreen` params `menuPageSize/onMenuPageSizeChange/listPageSize/onListPageSizeChange` (Int).

- [ ] **Step 1:** strings `settings_menu_page_size` = "Menu tiles per page", `settings_list_page_size` = "List tiles per page".
- [ ] **Step 2:** SettingsScreen: 4 new params; two `SliderRow`s in the Tiles card between the border-width slider and the focus-fill switch, `PageSizeDefaults.MIN..MAX`, Int↔Float via `roundToInt()`.
- [ ] **Step 3:** MainActivity: collect both flows in `CopilotNav`, add `LocalPageSizes provides PageSizes(menuPageSize, listPageSize)` to the `CompositionLocalProvider`, pass values+setters to `SettingsScreen`.

### Verification (Mac, by Georgian)

- [ ] `./gradlew test` green.
- [ ] Phone/car: defaults look unchanged (Home, Music 4/page, lists 5/page, dots as before).
- [ ] Menu=3: Home pages 3+1 with slide + swipe; heart reachable after Music tile; heart dot; song stop while heart focused → focus lands on Music tile. Music pages 3/3.
- [ ] List=6: saved lists page 6-up; single-page lists show per-item dots; multi-page show per-page pills.
- [ ] Fast knob twists across page edges: no bounce-back.
