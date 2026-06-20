# Readable Inline Tiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put every tile grid (Places, Playlists, Songs, Radio, Discover, Browse) on the same inline icon/thumbnail-left + big-label-right layout that Home and Music already use, with a bigger label font, by consolidating four tile components into one shared `MediaRowTile` — without changing knob navigation.

**Architecture:** Create one shared inline tile (`MediaRowTile`) that renders a left visual (thumbnail bitmap, vector icon, app-package icon, or drawable) + a large label, with an optional trailing slot for Discover's ▶ button. Migrate each screen's call site to it, keeping each screen's existing async image loading at the call site and keeping the exact `FocusRequester` wiring the knob engine depends on. Delete the four old tile components.

**Tech Stack:** Kotlin, Jetpack Compose, Material3. Existing helpers: `KnobPagedGrid` (paged grid + knob focus engine, unchanged), `ScreenHeader`.

## Global Constraints

- **Work directly on `master`. Do NOT commit.** (User instruction — overrides the skill's per-task commit step.)
- **No build / no test execution.** The environment is a Linux container with no Android SDK; the app cannot be compiled or run here. Each task's verification is by **code inspection against the spec**, not by running anything. (Overrides the skill's TDD/red-green steps.) Georgian builds, runs, and knob-tests on his Mac / in the car after the change lands.
- **Knob engine is off-limits:** do not edit `KnobPagedGrid.kt` or `KnobGridNav.kt`. Each screen must keep the same `stopsPerItem` and attach the handed `FocusRequester`(s) to the same number of focusable targets, in the same order.
- Tile label font: `32.sp` size / `38.sp` line height, `SemiBold` (via `titleLarge.copy(...)`), `maxLines = 2`, `TextOverflow.Ellipsis`.
- Left visual: square `80.dp`. Busy spinner: `48.dp`.
- Spec: `docs/superpowers/specs/2026-06-20-readable-inline-tiles-design.md`.

---

## File Structure

- **Create:** `app/src/main/java/com/vladutu/copilot/ui/MediaRowTile.kt` — the one shared inline tile. Pure presentation, no IO.
- **Modify:** `ui/home/HomeScreen.kt`, `ui/music/MusicScreen.kt` — swap `HomeTile` → `MediaRowTile`.
- **Modify:** `ui/lists/SavedListScreen.kt` — swap `SavedTile` → `MediaRowTile` via a private loader composable.
- **Modify:** `ui/discover/BrowseResultsScreen.kt` — swap `PlaylistTile` → `MediaRowTile` via a private loader composable.
- **Modify:** `ui/discover/DiscoverScreen.kt` — swap `CategoryTile` → `MediaRowTile` + a private `PlayMixButton` trailing composable.
- **Delete:** `ui/home/HomeTile.kt`, `ui/lists/SavedTile.kt`, `ui/discover/CategoryTile.kt`, `ui/discover/PlaylistTile.kt`.

Task order is chosen so each task leaves the tree internally consistent: Browse (which depends on `focusBorder` defined in `CategoryTile.kt`) is migrated *before* Discover deletes `CategoryTile.kt`.

---

### Task 1: Create the shared `MediaRowTile`

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/ui/MediaRowTile.kt`

**Interfaces:**
- Consumes: nothing (foundation task).
- Produces:
  ```kotlin
  @Composable
  fun MediaRowTile(
      label: String,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      onLongPress: (() -> Unit)? = null,
      focusRequester: FocusRequester? = null,
      thumbnail: ImageBitmap? = null,
      fallbackIcon: ImageVector? = null,
      iconTint: Color? = null,
      @DrawableRes fallbackRes: Int? = null,
      packageName: String? = null,
      busy: Boolean = false,
      trailing: (@Composable () -> Unit)? = null,
  )
  ```
  Behavior: outer `Box` carries `modifier` (sizing/weight). Inner clickable `Surface` carries the `focusRequester` and the focus border, and renders `Row { leftVisual(80.dp) ; label }`. Left-visual precedence: `busy` spinner → `thumbnail` → app icon (`packageName`) → `fallbackIcon` → `fallbackRes`. When `trailing != null`, it is overlaid `CenterEnd` as a **sibling** of the clickable card (so it is an independent focusable), and the label gets `end = 64.dp` padding to clear it.

- [ ] **Step 1: Create the file with the full component**

```kotlin
package com.vladutu.copilot.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap

/**
 * The one inline tile every grid uses: a square left visual (thumbnail / icon /
 * app icon / drawable) and a big label on the right. Optional [trailing] slot is
 * overlaid at the right edge as an independent focusable (Discover's ▶ mix button).
 *
 * Pure presentation: callers that need a network/disk image load it themselves and
 * pass the ready [thumbnail]. Knob focus: callers attach the FocusRequester they were
 * handed via [focusRequester]; when [trailing] is present it brings its own focus, so
 * the card is knob stop 0 and the trailing element is stop 1.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRowTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    thumbnail: ImageBitmap? = null,
    fallbackIcon: ImageVector? = null,
    iconTint: Color? = null,
    @DrawableRes fallbackRes: Int? = null,
    packageName: String? = null,
    busy: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName != null) loadAppIcon(context.packageManager, packageName) else null
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val border = if (isFocused) {
        BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = border,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LeftVisual(
                    busy = busy,
                    thumbnail = thumbnail,
                    appIcon = appIcon,
                    fallbackIcon = fallbackIcon,
                    iconTint = iconTint,
                    fallbackRes = fallbackRes,
                    label = label,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp, lineHeight = 38.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (trailing != null) 64.dp else 0.dp),
                )
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun RowScope.LeftVisual(
    busy: Boolean,
    thumbnail: ImageBitmap?,
    appIcon: Bitmap?,
    fallbackIcon: ImageVector?,
    iconTint: Color?,
    @DrawableRes fallbackRes: Int?,
    label: String,
) {
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            thumbnail != null -> Image(
                bitmap = thumbnail,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
            )
            appIcon != null -> Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
            fallbackIcon != null -> Icon(
                imageVector = fallbackIcon,
                contentDescription = label,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
            fallbackRes != null -> Image(
                painter = painterResource(fallbackRes),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun loadAppIcon(pm: PackageManager, packageName: String): Bitmap? {
    return try {
        pm.getApplicationIcon(packageName).toBitmap(192)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

private fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val bmp = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bmp
}
```

- [ ] **Step 2: Verify (inspection — no build available)**
  - The public signature matches the **Produces** block above exactly.
  - `loadAppIcon` / `toBitmap` are present (moved here from `HomeTile`, which is deleted in Task 2).
  - Left-visual `when` precedence is: `busy` → `thumbnail` → `appIcon` → `fallbackIcon` → `fallbackRes`.
  - Label style is `32.sp` / `38.sp`, `maxLines = 2`, ellipsize.
  - `trailing` is a sibling of the clickable `Surface` (independent focusable), not nested inside it; label has `end = 64.dp` padding only when `trailing != null`.
- [ ] **Step 3: Do NOT commit.** (Per Global Constraints.)

---

### Task 2: Migrate Home and Music; delete `HomeTile`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/ui/music/MusicScreen.kt`
- Delete: `app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt`

**Interfaces:**
- Consumes: `MediaRowTile` (Task 1). Note the focus change — `HomeTile` took the `FocusRequester` via `modifier.focusRequester(...)`; `MediaRowTile` takes it via the `focusRequester` param (because the focusable node is the inner card, not the outer Box). So move `.focusRequester(...)` out of the modifier and into the param.
- Produces: nothing downstream.

- [ ] **Step 1: HomeScreen — update import**

In `HomeScreen.kt` replace `import com.vladutu.copilot.ui.home.HomeTile` — there is no such import (same package). Add `import com.vladutu.copilot.ui.MediaRowTile`. Remove the now-unused `import androidx.compose.ui.focus.focusRequester` only if no other usage remains (the heart still uses `.focusRequester(heartFocus)` at line ~129, so **keep** that import).

- [ ] **Step 2: HomeScreen — swap the four tiles**

Replace each of the four `HomeTile(...)` calls (the Waze/Maps row and the Places/Music row) with `MediaRowTile(...)`, moving the focus requester to the param. The four become:

```kotlin
MediaRowTile(
    modifier = Modifier.weight(1f).fillMaxSize(),
    focusRequester = tileFocus[0],
    label = stringResource(R.string.home_waze),
    onClick = onOpenWaze,
    packageName = AppLauncher.WAZE_PKG,
    fallbackRes = R.drawable.ic_map_pin,
)
MediaRowTile(
    modifier = Modifier.weight(1f).fillMaxSize(),
    focusRequester = tileFocus[1],
    label = stringResource(R.string.home_maps),
    onClick = onOpenMaps,
    packageName = AppLauncher.MAPS_PKG,
    fallbackRes = R.drawable.ic_map_pin,
)
// bottom row
MediaRowTile(
    modifier = Modifier.weight(1f).fillMaxSize(),
    focusRequester = tileFocus[2],
    label = stringResource(R.string.home_destinations),
    onClick = onOpenDestinations,
    fallbackIcon = Icons.Filled.Place,
)
MediaRowTile(
    modifier = Modifier.weight(1f).fillMaxSize(),
    focusRequester = tileFocus[3],
    label = stringResource(R.string.home_music),
    onClick = onOpenMusic,
    fallbackIcon = Icons.Filled.LibraryMusic,
)
```

- [ ] **Step 3: MusicScreen — update import and swap the tile**

In `MusicScreen.kt` replace `import com.vladutu.copilot.ui.home.HomeTile` with `import com.vladutu.copilot.ui.MediaRowTile`. In the grid loop, replace the `HomeTile(...)` call with:

```kotlin
MediaRowTile(
    modifier = Modifier.weight(1f).fillMaxSize(),
    focusRequester = tileFocus[globalIndex],
    label = stringResource(tile.labelRes),
    onClick = tile.onClick,
    fallbackIcon = tile.icon,
    busy = tile.busy,
)
```

- [ ] **Step 4: Delete `HomeTile.kt`**

```bash
rm app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt
```

- [ ] **Step 5: Verify (inspection)**
  - No remaining reference to `HomeTile` in the repo: `grep -rn "HomeTile" app/src` returns nothing.
  - Both screens import `com.vladutu.copilot.ui.MediaRowTile`.
  - Every migrated call passes `focusRequester = tileFocus[...]` and the `modifier` no longer contains `.focusRequester(...)` for tiles. The heart's `.focusRequester(heartFocus)` in HomeScreen is untouched.
  - Home knob: 4 tile stops (+ heart) drive via the unchanged `focusedIndex`/`onPreviewKeyEvent`. Music knob: 6 stops via unchanged `focusedIndex`. No `stopsPerItem` involved (these screens don't use `KnobPagedGrid`).
- [ ] **Step 6: Do NOT commit.**

---

### Task 3: Migrate Places / Playlists / Songs / Radio; delete `SavedTile`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt`
- Delete: `app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt`

**Interfaces:**
- Consumes: `MediaRowTile` (Task 1), `KnobPagedGrid` (unchanged, `stopsPerItem = 1`).
- Produces: nothing downstream.

These four screens all flow through `SavedListScreen` + `KnobPagedGrid` at one stop per item. The async file→bitmap load currently inside `SavedTile` moves into a private loader composable in `SavedListScreen.kt`; `FormBadge` is dropped.

- [ ] **Step 1: Update imports in `SavedListScreen.kt`**

Add:
```kotlin
import android.graphics.BitmapFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.focus.FocusRequester
import com.vladutu.copilot.MediaRowTileImportPlaceholder // (remove — see note)
import com.vladutu.copilot.ui.MediaRowTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
```
(The `MediaRowTileImportPlaceholder` line is not real — do not add it; it is here only to flag: import `com.vladutu.copilot.ui.MediaRowTile`, nothing else from a placeholder.)

- [ ] **Step 2: Add the private loader composable at the bottom of `SavedListScreen.kt`**

```kotlin
@Composable
private fun SavedRow(
    item: SavedItem,
    artworkFile: File,
    focus: FocusRequester?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id) {
        if (artworkFile.exists()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(artworkFile.absolutePath) }.getOrNull()
            }
            bitmap = bmp?.asImageBitmap()
        }
    }
    MediaRowTile(
        modifier = Modifier.fillMaxSize(),
        label = item.title ?: "Untitled · ${item.id.take(8)}",
        onClick = onTap,
        onLongPress = onLongPress,
        focusRequester = focus,
        thumbnail = bitmap,
        // Fallback when there is no artwork: form-specific glyph.
        fallbackIcon = if (item.form == Form.RADIO) Icons.Filled.Radio else null,
        fallbackRes = when (item.form) {
            Form.DESTINATION -> R.drawable.ic_map_pin
            Form.PLAYLIST, Form.SONG -> R.drawable.ic_music_note
            Form.RADIO -> null
        },
    )
}
```
(`mutableStateOf`, `remember`, `getValue`, `setValue` are already imported in this file.)

- [ ] **Step 3: Use `SavedRow` in the `KnobPagedGrid` tile lambda**

Replace the `SavedTile(...)` block inside `KnobPagedGrid { item, requesters -> ... }` with:

```kotlin
SavedRow(
    item = item,
    artworkFile = artworkCache.fileFor(item.form, item.id),
    focus = requesters?.get(0),
    onTap = { onTap(item) },
    onLongPress = { pendingDelete = item },
)
```

- [ ] **Step 4: Delete `SavedTile.kt` (carries `FormBadge` with it)**

```bash
rm app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt
```

- [ ] **Step 5: Verify (inspection)**
  - `grep -rn "SavedTile\|FormBadge" app/src` returns nothing.
  - `KnobPagedGrid` is still called with default `stopsPerItem` (= 1) and `requesters?.get(0)` is attached to the one tile — knob contract unchanged.
  - Fallback mapping covers all four `Form` values (`when` is exhaustive); `RADIO` uses `fallbackIcon`, the rest use `fallbackRes`.
- [ ] **Step 6: Do NOT commit.**

---

### Task 4: Migrate Browse results; delete `PlaylistTile`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/discover/BrowseResultsScreen.kt`
- Delete: `app/src/main/java/com/vladutu/copilot/ui/discover/PlaylistTile.kt`

**Interfaces:**
- Consumes: `MediaRowTile` (Task 1), `KnobPagedGrid` (unchanged, `stopsPerItem = 1`).
- Produces: nothing downstream.
- Note: `PlaylistTile` referenced `focusBorder` (defined in `CategoryTile.kt`). After this task Browse no longer references `focusBorder`; `CategoryTile.kt` and `focusBorder` are deleted in Task 5.

- [ ] **Step 1: Update imports in `BrowseResultsScreen.kt`**

Add:
```kotlin
import android.graphics.BitmapFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vladutu.copilot.R
import com.vladutu.copilot.discover.FoundPlaylist
import com.vladutu.copilot.ui.MediaRowTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```
(`Modifier`, `LaunchedEffect`, `remember`, `mutableStateOf`, `getValue`, `setValue` may already be imported — keep one copy each.)

- [ ] **Step 2: Add the private loader composable at the bottom of `BrowseResultsScreen.kt`**

```kotlin
@Composable
private fun BrowsePlaylistRow(
    playlist: FoundPlaylist,
    okHttp: OkHttpClient,
    focus: FocusRequester?,
    onTap: () -> Unit,
) {
    var bitmap by remember(playlist.playlistId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(playlist.playlistId) {
        val url = playlist.thumbnailUrl ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }.getOrNull()
        }?.asImageBitmap()
    }
    MediaRowTile(
        modifier = Modifier.fillMaxSize(),
        label = playlist.title,
        onClick = onTap,
        focusRequester = focus,
        thumbnail = bitmap,
        fallbackRes = R.drawable.ic_music_note,
    )
}
```

- [ ] **Step 3: Use `BrowsePlaylistRow` in the `KnobPagedGrid` tile lambda**

Replace the `PlaylistTile(...)` block inside `KnobPagedGrid { playlist, requesters -> ... }` with:

```kotlin
BrowsePlaylistRow(
    playlist = playlist,
    okHttp = okHttp,
    focus = requesters?.get(0),
    onTap = {
        when (val r = launcher.launchYtMusic(YtMusicUrls.playlist(playlist.playlistId))) {
            is AppLauncher.Result.Ok -> onLaunched()
            is AppLauncher.Result.Failed ->
                Toast.makeText(context, r.reason, Toast.LENGTH_LONG).show()
        }
    },
)
```

- [ ] **Step 4: Delete `PlaylistTile.kt`**

```bash
rm app/src/main/java/com/vladutu/copilot/ui/discover/PlaylistTile.kt
```

- [ ] **Step 5: Verify (inspection)**
  - `grep -rn "PlaylistTile" app/src` returns nothing.
  - Browse no longer references `focusBorder` (it now lives only in `CategoryTile.kt`, deleted next task).
  - `KnobPagedGrid` still default `stopsPerItem` (= 1); `requesters?.get(0)` attached to the one tile.
  - `RetryBox` is untouched (still focus-on-entry).
- [ ] **Step 6: Do NOT commit.**

---

### Task 5: Migrate Discover; delete `CategoryTile` (and `focusBorder`)

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/discover/DiscoverScreen.kt`
- Delete: `app/src/main/java/com/vladutu/copilot/ui/discover/CategoryTile.kt`

**Interfaces:**
- Consumes: `MediaRowTile` (Task 1), `KnobPagedGrid` (unchanged, `stopsPerItem = 2`).
- Produces: nothing downstream.
- The Discover tile has **two** knob stops: the card (stop 0, opens Browse / long-press deletes) and the ▶ mix button (stop 1). The ▶ moves from an overlay inside `CategoryTile` into `MediaRowTile`'s `trailing` slot, supplied by a private `PlayMixButton` that brings its own `FocusRequester`.

- [ ] **Step 1: Update imports in `DiscoverScreen.kt`**

Add:
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale // remove if unused after edit
import com.vladutu.copilot.ui.MediaRowTile
```
(Keep `Modifier`, `size`, `Box`, `Alignment`, `dp` which already exist. Drop any import that becomes unused after the swap.)

- [ ] **Step 2: Replace the `CategoryTile(...)` block in the `KnobPagedGrid` tile lambda**

```kotlin
MediaRowTile(
    modifier = Modifier.fillMaxSize(),
    label = keyword,
    onClick = { onBrowse(keyword) },
    onLongPress = { pendingDelete = keyword },
    focusRequester = requesters?.get(0),
    fallbackIcon = Icons.Filled.Explore,
    trailing = {
        PlayMixButton(
            busy = mixBusyFor == keyword,
            focus = requesters?.get(1),
            onClick = { playMix(keyword) },
        )
    },
)
```

- [ ] **Step 3: Add the private `PlayMixButton` at the bottom of `DiscoverScreen.kt`**

This is the ▶ button lifted out of `CategoryTile`, keeping its always-enabled behavior and its own focus + border.

```kotlin
@Composable
private fun PlayMixButton(
    busy: Boolean,
    focus: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        modifier = Modifier
            .size(56.dp)
            .let { if (focus != null) it.focusRequester(focus) else it }
            // Always enabled: disabling would drop knob focus mid-launch. Reentry
            // while busy is guarded in playMix().
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (focused) BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play mix",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Delete `CategoryTile.kt`**

```bash
rm app/src/main/java/com/vladutu/copilot/ui/discover/CategoryTile.kt
```

- [ ] **Step 5: Verify (inspection)**
  - `grep -rn "CategoryTile\|focusBorder" app/src` returns nothing.
  - `KnobPagedGrid` is still called with `stopsPerItem = 2`.
  - Knob stop order within a tile is **card first, ▶ second**: `requesters?.get(0)` → `MediaRowTile.focusRequester` (the card); `requesters?.get(1)` → `PlayMixButton.focus`. This matches `KnobPagedGrid`'s item-major order, so the twist sequence is `card → ▶ → next card → ▶ …` exactly as before.
  - The ▶ remains an independent focusable (it is the `trailing` sibling, not nested in the card's clickable).
- [ ] **Step 6: Do NOT commit.**

---

### Task 6: Final regression sweep

**Files:** none modified — verification only.

- [ ] **Step 1: Confirm all four old tiles are gone**

```bash
grep -rn "HomeTile\|SavedTile\|CategoryTile\|PlaylistTile\|FormBadge\|focusBorder" app/src
```
Expected: no matches.

- [ ] **Step 2: Confirm every grid screen imports the shared tile**

```bash
grep -rln "com.vladutu.copilot.ui.MediaRowTile" app/src
```
Expected: `HomeScreen.kt`, `MusicScreen.kt`, `SavedListScreen.kt`, `BrowseResultsScreen.kt`, `DiscoverScreen.kt`.

- [ ] **Step 3: Confirm knob contract per screen (read, don't run)**
  - `SavedListScreen.kt`, `BrowseResultsScreen.kt`: `KnobPagedGrid` with default `stopsPerItem` (1); one focusable per item via `focusRequester = requesters?.get(0)`.
  - `DiscoverScreen.kt`: `KnobPagedGrid(stopsPerItem = 2, ...)`; card = `get(0)`, ▶ = `get(1)`.
  - `HomeScreen.kt` / `MusicScreen.kt`: own `focusedIndex` + Left/Right `onPreviewKeyEvent` unchanged; tiles take `focusRequester` param.
  - `KnobPagedGrid.kt` and `KnobGridNav.kt` are byte-for-byte unchanged: `git diff --stat app/src/main/java/com/vladutu/copilot/ui/KnobPagedGrid.kt app/src/main/java/com/vladutu/copilot/ui/KnobGridNav.kt` shows no changes.

- [ ] **Step 4: Spec coverage check** — re-read `docs/superpowers/specs/2026-06-20-readable-inline-tiles-design.md` and confirm each row of its per-screen table is implemented (Places/Playlists/Songs/Radio/Discover/Browse on `MediaRowTile`; Home/Music font bump; Liked untouched; FormBadge removed).

- [ ] **Step 5: Do NOT commit.** Hand off to Georgian to build, run, and knob-test on his Mac / in the car.

---

## Self-Review (performed while writing this plan)

- **Spec coverage:** Every per-screen row of the spec maps to a task — Home/Music (T2), Places/Playlists/Songs/Radio (T3), Browse (T4), Discover (T5); shared `MediaRowTile` (T1); font bump baked into T1's label style; `FormBadge` removed (T3); knob preservation verified per task + T6; Liked explicitly untouched.
- **Placeholder scan:** No TBD/TODO. The one `MediaRowTileImportPlaceholder` token in T3 Step 1 is explicitly flagged as *not real* with instructions to ignore it — kept only to call out the single correct import.
- **Type consistency:** `MediaRowTile`'s signature in T1's Produces block is used verbatim by T2–T5 (`focusRequester`, `thumbnail`, `fallbackIcon`, `fallbackRes`, `iconTint`, `packageName`, `busy`, `onLongPress`, `trailing`). `SavedRow` / `BrowsePlaylistRow` / `PlayMixButton` are each defined and consumed within the same task. `Form` fallback `when` is exhaustive over all four values.
