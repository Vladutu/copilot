# Pilot Theme Port + 5-Tile Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Pilot's `4387a43` "dark automotive-cockpit" theme into Copilot and redesign the Home screen as 5 inline-icon tiles (Waze, Maps, Playlists, Songs, Destinations) for at-a-glance readability on the carbox display.

**Architecture:** Compose-based Android app. New `ui/theme/` matches Pilot's verbatim (palette + shapes + typography). A single `HomeTile` composable replaces `BigAppButton` + `LabelTile`, using inline `Row(icon, label)` instead of vertical `Column`. `AppLauncher` gets `openMapsApp()`. SavedTile / StatusPill / StatusScreen / PageIndicator move to theme tokens and Pilot's ring-dot style.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Compose Navigation. Existing JUnit + Robolectric for non-UI tests (`app/src/test/`). No instrumented UI tests in the project — Compose composables are validated by sideload-and-try on the carbox.

**Single-commit constraint:** The user wants ONE commit at the end so the change is revertable with a single `git revert <sha>`. Subagents executing tasks **must not commit between tasks** — they stage if helpful but the final task creates the one commit.

**Spec:** `docs/superpowers/specs/2026-06-04-pilot-theme-port-design.md`

---

## File map

**Create**
- `app/src/main/java/com/vladutu/copilot/ui/theme/Shape.kt`
- `app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt`

**Modify**
- `app/src/main/java/com/vladutu/copilot/ui/theme/Color.kt`
- `app/src/main/java/com/vladutu/copilot/ui/theme/Type.kt`
- `app/src/main/java/com/vladutu/copilot/ui/theme/Theme.kt`
- `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
- `app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt`
- `app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt`
- `app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt`
- `app/src/main/java/com/vladutu/copilot/ui/lists/PageIndicator.kt`
- `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt`
- `app/src/main/java/com/vladutu/copilot/MainActivity.kt`
- `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`
- `app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`

**Delete**
- `app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt`
- `app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt`

---

## Task 1: Theme port (Color, Shape, Type, Theme)

These four files form one atomic unit — the codebase only compiles when all four are consistent. Do them in one task.

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/theme/Color.kt`
- Create: `app/src/main/java/com/vladutu/copilot/ui/theme/Shape.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/ui/theme/Theme.kt`

- [ ] **Step 1: Replace `Color.kt`**

```kotlin
package com.vladutu.copilot.ui.theme

import androidx.compose.ui.graphics.Color

// Dark automotive-cockpit palette mirrored from Pilot's 4387a43. Dark-only —
// the carbox screen reads the same at noon and midnight, so no light variant.

val PilotBackground = Color(0xFF0E1116)       // near-black with a touch of blue
val PilotSurface = Color(0xFF161B22)          // cards / tiles sit on this
val PilotSurfaceVariant = Color(0xFF1E2530)   // status pill bg, etc.
val PilotOutline = Color(0xFF2A323D)          // 1dp tile borders, dividers

val PilotPrimary = Color(0xFFFFB020)          // warm amber — accent, focus
val PilotOnPrimary = Color(0xFF0E1116)

val PilotOnSurface = Color(0xFFE6EAF0)        // primary text
val PilotOnSurfaceVariant = Color(0xFF9AA4B2) // secondary text, muted

val PilotError = Color(0xFFE5484D)            // error states
val PilotOk = Color(0xFF4FCB66)               // healthy / connected
```

- [ ] **Step 2: Create `Shape.kt`**

```kotlin
package com.vladutu.copilot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PilotShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),         // tiles
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [ ] **Step 3: Replace `Type.kt`**

```kotlin
package com.vladutu.copilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PilotTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
```

- [ ] **Step 4: Replace `Theme.kt`**

```kotlin
package com.vladutu.copilot.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PilotColorScheme = darkColorScheme(
    primary = PilotPrimary,
    onPrimary = PilotOnPrimary,
    secondary = PilotPrimary,
    onSecondary = PilotOnPrimary,
    tertiary = PilotPrimary,
    onTertiary = PilotOnPrimary,
    background = PilotBackground,
    onBackground = PilotOnSurface,
    surface = PilotSurface,
    onSurface = PilotOnSurface,
    surfaceVariant = PilotSurfaceVariant,
    onSurfaceVariant = PilotOnSurfaceVariant,
    outline = PilotOutline,
    outlineVariant = PilotOutline,
    error = PilotError,
    onError = PilotOnPrimary,
)

@Composable
fun CopilotDriveTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PilotBackground.toArgb()
            window.navigationBarColor = PilotBackground.toArgb()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = PilotColorScheme,
        typography = PilotTypography,
        shapes = PilotShapes,
        content = content,
    )
}
```

Note: `CopilotDriveTheme` keeps its name (used by `MainActivity.kt`). Internals swap; signature is unchanged. `FLAG_KEEP_SCREEN_ON` stays (Copilot needs the carbox screen awake).

---

## Task 2: Resources — colors, themes.xml, launcher icon

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`

- [ ] **Step 1: Replace `colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0E1116</color>
    <color name="window_background">#0E1116</color>
</resources>
```

- [ ] **Step 2: Replace `themes.xml`**

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Copilot" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/window_background</item>
    </style>
</resources>
```

This kills the white flash at cold start.

- [ ] **Step 3: Replace `ic_launcher_foreground.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFB020"
        android:pathData="M41,30 L41,78 L81,54 Z" />
</vector>
```

Only the `fillColor` changes (`#FFFFFF` → `#FFB020`). The path stays — Copilot's right-pointing play-arrow distinguishes it from Pilot's up-triangle even though both now sit on the same charcoal background.

---

## Task 3: AppLauncher — `openMapsApp()` + unit test

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`
- Modify: `app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`

`MAPS_PKG` is already declared in `AppLauncher.kt` companion (was added for the `cmd=maps` envelope handler). We're just adding a `openMapsApp()` method that mirrors `openWazeApp()`.

- [ ] **Step 1: Add `openMapsApp()` to `AppLauncher.kt`**

In `AppLauncher.kt`, immediately after the existing `openWazeApp()` function (around line 21):

```kotlin
    /** Open Google Maps app (no nav target). */
    fun openMapsApp(): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(MAPS_PKG)
            ?: return Result.Failed("Google Maps not installed")
        return startNewTask(launch)
    }
```

The existing `startNewTask(intent: Intent)` private helper is reused. `MAPS_PKG = "com.google.android.apps.maps"` is already in the companion object.

- [ ] **Step 2: Add a unit test in `AppLauncherTest.kt`**

After the existing `launches waze destination message` test (the test class uses Robolectric + `shadowOf` — match the existing pattern):

```kotlin
    @Test fun `openMapsApp launches Google Maps package`() {
        val res = launcher.openMapsApp()
        // Maps may not be installed under the Robolectric environment — accept either
        // Ok (intent fired) or Failed (no launch intent). The point of this test is to
        // pin the package name, not to assert installation state.
        when (res) {
            is AppLauncher.Result.Ok -> {
                val intent = shadowOf(context as android.app.Application).nextStartedActivity
                assertEquals(AppLauncher.MAPS_PKG, intent.`package`)
            }
            is AppLauncher.Result.Failed -> {
                assertTrue(res.reason.contains("Google Maps", ignoreCase = true))
            }
        }
    }
```

The test pins the package constant — if someone later renames `MAPS_PKG` or forgets to use it, this fails.

Note: don't run the test in this task. The repo has no `gradlew` wrapper and `gradle` isn't on PATH in the worker environment. User runs all tests once in Android Studio at the end (see Task 14).

---

## Task 4: Add `home_maps` string

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the string**

Inside the existing `<resources>` block, after `home_waze`:

```xml
    <string name="home_maps">Maps</string>
```

The new `home_waze` and `home_maps` order in the file should be:

```xml
    <string name="home_waze">Waze</string>
    <string name="home_maps">Maps</string>
    <string name="home_playlists">Playlists</string>
    <string name="home_songs">Songs</string>
    <string name="home_destinations">Destinations</string>
```

---

## Task 5: Create `HomeTile` composable

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt`

This single composable replaces both `BigAppButton` and `LabelTile`. Inline icon-left + label-right layout. Inputs are either a `packageName` (load the real app icon via `PackageManager`) or an `ImageVector` (Pilot's Material form icons). One or the other — caller's choice.

- [ ] **Step 1: Write `HomeTile.kt`**

```kotlin
package com.vladutu.copilot.ui.home

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap

/**
 * One Home-screen tile. Inline icon-left + label-right layout.
 *
 * Either pass a [packageName] (loads the real app icon via PackageManager,
 * with [fallbackRes] painted if the package isn't installed) OR pass
 * [fallbackIcon] (a Material vector icon tinted with [iconTint]) for the
 * label-only tiles that aren't tied to an installed app.
 */
@Composable
fun HomeTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    @DrawableRes fallbackRes: Int? = null,
    fallbackIcon: ImageVector? = null,
    iconTint: Color? = null,
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

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
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
            when {
                appIcon != null -> Image(
                    bitmap = appIcon.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(96.dp),
                )
                fallbackIcon != null -> Icon(
                    imageVector = fallbackIcon,
                    contentDescription = label,
                    tint = iconTint ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp),
                )
                fallbackRes != null -> Image(
                    painter = painterResource(fallbackRes),
                    contentDescription = label,
                    modifier = Modifier.size(96.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

The `loadAppIcon` + `Drawable.toBitmap` helpers are lifted directly from the old `BigAppButton.kt` (they'll be deleted when that file goes away in Task 8).

---

## Task 6: Rewrite `HomeScreen` for 5 tiles

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`

The screen grows from a 2×2 grid (4 tiles) to a two-row layout: top row 2 tiles (Waze, Maps), bottom row 3 tiles (Playlists, Songs, Destinations). Knob walks left-to-right, top row then bottom — 5 stops total.

- [ ] **Step 1: Replace `HomeScreen.kt`**

```kotlin
package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
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

private const val TILE_COUNT = 5

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    // Knob twist (DPAD_LEFT/RIGHT) walks the five tiles linearly in reading
    // order: Waze → Maps → Playlists → Songs → Destinations. StatusPill is
    // touch-only — it's not in the knob rotation by design.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusedIndex < TILE_COUNT - 1) { focusedIndex++; true } else false
                    }
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) { focusedIndex--; true } else false
                    }
                    else -> false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top row — outbound nav apps (2 tiles).
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
            // Bottom row — saved-content lists (3 tiles).
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[2]),
                    label = stringResource(R.string.home_playlists),
                    onClick = onOpenPlaylists,
                    fallbackIcon = Icons.Filled.PlaylistPlay,
                )
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[3]),
                    label = stringResource(R.string.home_songs),
                    onClick = onOpenSongs,
                    fallbackIcon = Icons.Filled.MusicNote,
                )
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[4]),
                    label = stringResource(R.string.home_destinations),
                    onClick = onOpenDestinations,
                    fallbackIcon = Icons.Filled.Place,
                )
            }
        }
        StatusPill(
            state = state,
            onClick = onOpenStatus,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
    }
}
```

Notable changes vs the old file:
- `TILE_COUNT` is 5 (was 4).
- Top padding drops from 72dp to 24dp — bigger tiles, status pill still fits in the 16dp-offset corner.
- New parameter `onOpenMaps`.
- All tiles use the new unified `HomeTile`.

---

## Task 7: Wire `onOpenMaps` through `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/MainActivity.kt`

Only the home `composable("home")` block changes — add the `onOpenMaps` callback alongside the existing `onOpenWaze`.

- [ ] **Step 1: Update the `composable("home")` block in `MainActivity.kt`**

Find the existing block (around line 123):

```kotlin
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = uiState,
                onOpenWaze = {
                    if (launcher.openWazeApp() is AppLauncher.Result.Ok) onLeftToOtherApp()
                },
                onOpenPlaylists = { nav.navigate("list/playlist") },
                onOpenSongs = { nav.navigate("list/song") },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenStatus = { nav.navigate("status") },
                onBackFromHome = onLeftToOtherApp,
            )
        }
```

Replace with:

```kotlin
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = uiState,
                onOpenWaze = {
                    if (launcher.openWazeApp() is AppLauncher.Result.Ok) onLeftToOtherApp()
                },
                onOpenMaps = {
                    if (launcher.openMapsApp() is AppLauncher.Result.Ok) onLeftToOtherApp()
                },
                onOpenPlaylists = { nav.navigate("list/playlist") },
                onOpenSongs = { nav.navigate("list/song") },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenStatus = { nav.navigate("status") },
                onBackFromHome = onLeftToOtherApp,
            )
        }
```

Nothing else in `MainActivity.kt` changes.

---

## Task 8: Delete `BigAppButton.kt` and `LabelTile.kt`

**Files:**
- Delete: `app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt`
- Delete: `app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt`

Both are now unreferenced — `HomeScreen` uses `HomeTile` exclusively. Confirm no other file imports them before deleting.

- [ ] **Step 1: Verify no references remain**

Run from the Copilot repo root:

```bash
grep -rn "BigAppButton\|LabelTile" /home/geo/projects/Copilot/app/src/
```

Expected: zero matches (after Task 6 landed). If anything matches, that file must be updated before deleting.

- [ ] **Step 2: Delete the files (using `git rm` so the deletion is staged too)**

```bash
cd /home/geo/projects/Copilot
git rm app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt
git rm app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt
```

This both removes from working tree and stages the deletion. Task 14 then doesn't need to re-stage these (its `git rm` lines for these files become idempotent / no-ops — leave them in for clarity).

---

## Task 9: Restyle `StatusPill` (home)

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt`

Restyle the dot to Pilot's ring style (12dp outer at 18% alpha, 8dp solid inner) and use theme tokens instead of hardcoded green/amber/red.

- [ ] **Step 1: Replace `StatusPill.kt`**

```kotlin
package com.vladutu.copilot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.theme.PilotOk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusPill(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor: Color = when (state.conn) {
        is ConnState.Connected -> PilotOk
        is ConnState.Reconnecting -> MaterialTheme.colorScheme.primary
        is ConnState.Error -> MaterialTheme.colorScheme.error
    }
    val lastTime = state.recent.firstOrNull()?.timeSec?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000))
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 12dp ring (color at 18%) wrapping an 8dp solid inner dot — mirrors Pilot.
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.18f))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        if (lastTime != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = lastTime,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

---

## Task 10: Update `SavedTile`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt`

Three changes: form badge over artwork, `titleSmall` title (was `bodyMedium`), permanent 1dp outline swapped for 4dp amber on focus.

- [ ] **Step 1: Replace `SavedTile.kt`**

```kotlin
package com.vladutu.copilot.ui.lists

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedTile(
    item: SavedItem,
    artworkFile: File,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val border = if (isFocused) {
        BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    Card(
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onTap,
            onLongClick = onLongPress,
        ),
        shape = RoundedCornerShape(16.dp),
        border = border,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val bm = bitmap
                if (bm != null) {
                    Image(
                        bitmap = bm,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(
                            id = if (item.form == Form.DESTINATION) R.drawable.ic_map_pin else R.drawable.ic_music_note
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                FormBadge(
                    form = item.form,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                )
            }
            Text(
                text = item.title ?: "Untitled · ${item.id.take(8)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FormBadge(form: Form, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = formIcon(form),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun formIcon(form: Form): ImageVector = when (form) {
    Form.PLAYLIST -> Icons.Filled.PlaylistPlay
    Form.SONG -> Icons.Filled.MusicNote
    Form.DESTINATION -> Icons.Filled.Place
}
```

`FormBadge` and `formIcon` are file-private — copied from Pilot's `Tile.kt`.

---

## Task 11: `SavedListScreen` typography polish

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt`

Two small text-style swaps. Everything else stays.

- [ ] **Step 1: Title typography**

Find the title `Text` in the screen's top `Row` (around line 74):

```kotlin
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
```

Replace with:

```kotlin
            Text(text = title, style = MaterialTheme.typography.titleLarge)
```

- [ ] **Step 2: Empty-state typography**

Find the empty-state `Text` (around line 79):

```kotlin
                Text(text = emptyText, style = MaterialTheme.typography.titleMedium)
```

Replace with:

```kotlin
                Text(text = emptyText, style = MaterialTheme.typography.titleLarge)
```

No other changes to `SavedListScreen.kt`.

---

## Task 12: `PageIndicator` color

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/lists/PageIndicator.kt`

Drop the hardcoded `Color.Gray` for inactive dots.

- [ ] **Step 1: Replace inactive color**

Find:

```kotlin
        val color = if (idx == currentPage) MaterialTheme.colorScheme.primary else Color.Gray
```

Replace with:

```kotlin
        val color = if (idx == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
```

Then remove the now-unused `import androidx.compose.ui.graphics.Color` line at the top of the file.

---

## Task 13: `StatusScreen` polish

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt`

Bring it visually in line — drop hardcoded colors, swap to ring-style connection dot, switch typography.

- [ ] **Step 1: Replace `StatusScreen.kt`**

```kotlin
package com.vladutu.copilot.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.config.Config
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.ui.BackHomeButton
import com.vladutu.copilot.service.RecentEvent
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.theme.PilotOk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun StatusScreen(state: UiState, onBack: () -> Unit, onOpenLogs: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackHomeButton(onBack)
        // Connection state — ring-dot matches StatusPill.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = state.conn.color()
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f))
                    .padding(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Box(Modifier.size(12.dp))
            Text(state.conn.label(), style = MaterialTheme.typography.titleLarge)
        }

        // Clock skew (only after we've observed one message)
        state.skewSec?.let { skew ->
            val sign = if (skew >= 0) "+" else ""
            val skewColor = if (abs(skew) > Config.MAX_MESSAGE_AGE_SEC) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "Clock skew: $sign${skew}s (phone − box)",
                style = MaterialTheme.typography.titleMedium,
                color = skewColor,
            )
        }

        // Recent events
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recent events", style = MaterialTheme.typography.titleMedium)
            if (state.recent.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (event in state.recent) {
                    EventRow(event)
                }
            }
        }

        Text(
            text = "topic: ${Config.NTFY_TOPIC.take(16)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onOpenLogs) { Text("Diagnostic log") }
    }
}

@Composable
private fun EventRow(event: RecentEvent) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        .format(Date(event.timeSec * 1000))
    Text(
        text = "$time  ${event.text}",
        style = MaterialTheme.typography.bodyLarge,
        color = if (event.ok) PilotOk else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ConnState.color(): Color = when (this) {
    is ConnState.Connected -> PilotOk
    is ConnState.Reconnecting -> MaterialTheme.colorScheme.primary
    is ConnState.Error -> MaterialTheme.colorScheme.error
}

private fun ConnState.label(): String = when (this) {
    is ConnState.Connected -> "Connected"
    is ConnState.Reconnecting -> "Reconnecting"
    is ConnState.Error -> "Error: ${this.message}"
}
```

Notes:
- `ConnState.color()` becomes `@Composable` (needs `MaterialTheme.colorScheme.*`).
- `bodySmall` isn't in `PilotTypography` — it'll fall back to Material default, which is fine (and was the existing behavior anyway).

---

## Task 14: Final review + single commit

**Files:** all of the above.

The whole change ships as one commit.

- [ ] **Step 1: Verify no stale references remain**

```bash
grep -rn "BigAppButton\|LabelTile\|DDBlack\|DDSurface\|DDAccent\|DDError\|TilePalette\|DriveDeckTypography" /home/geo/projects/Copilot/app/src/
```

Expected: zero matches. The only file that should still mention `CopilotDriveTheme` is `Theme.kt` (definition) and `MainActivity.kt` (usage).

- [ ] **Step 2: Ask the user to build + sideload**

The repo has no `gradlew` wrapper and `gradle` isn't on PATH in this environment. The user builds in Android Studio. Tell them:

> "Plan implemented. Please build in Android Studio (`./gradlew assembleDebug` from the IDE's terminal, or the Build menu) and sideload the APK onto the carbox to visually confirm: (1) home shows five tiles, (2) Waze and Maps labels are now both fully visible, (3) bottom row tiles show PlaylistPlay / MusicNote / Place icons in amber, (4) knob walks Waze → Maps → Playlists → Songs → Destinations, (5) launcher icon is amber play-arrow on charcoal, (6) saved-tile titles show a small form badge in the top-left of the artwork."

Wait for confirmation before committing.

- [ ] **Step 3: Stage all changes**

```bash
cd /home/geo/projects/Copilot
git add app/src/main/java/com/vladutu/copilot/ui/theme/Color.kt \
        app/src/main/java/com/vladutu/copilot/ui/theme/Shape.kt \
        app/src/main/java/com/vladutu/copilot/ui/theme/Type.kt \
        app/src/main/java/com/vladutu/copilot/ui/theme/Theme.kt \
        app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt \
        app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt \
        app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt \
        app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt \
        app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt \
        app/src/main/java/com/vladutu/copilot/ui/lists/PageIndicator.kt \
        app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt \
        app/src/main/java/com/vladutu/copilot/MainActivity.kt \
        app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt \
        app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt \
        app/src/main/res/values/colors.xml \
        app/src/main/res/values/themes.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/drawable/ic_launcher_foreground.xml \
        docs/superpowers/specs/2026-06-04-pilot-theme-port-design.md \
        docs/superpowers/plans/2026-06-04-pilot-theme-port.md
git rm app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt \
       app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt
```

- [ ] **Step 4: Verify staged set**

```bash
git status
```

Expected: a clean staged list of modifications, two deletions (`BigAppButton.kt`, `LabelTile.kt`), and three new files (`Shape.kt`, `HomeTile.kt`, plus the two docs). No unrelated working-tree changes.

- [ ] **Step 5: Create the single commit**

```bash
git commit -m "$(cat <<'EOF'
feat(ui): adopt PilotTheme + 5-tile inline home (Waze, Maps, Playlists, Songs, Destinations)

Brings Copilot's visual language in line with Pilot's 4387a43 cockpit
redesign and addresses two carbox-screen problems: clipped Waze label
and three text-only home tiles that look identical at a glance.

- Theme: PilotColorScheme/Typography/Shapes ported into Copilot (palette
  swap, dark windowBackground for startup, 20dp tile shape).
- Home: 5 tiles in two rows. HomeTile collapses BigAppButton+LabelTile
  into an inline icon+label (96dp icon, titleLarge label). Real app
  icons for Waze and Maps; PlaylistPlay/MusicNote/Place form icons for
  the three saved-list entries. 1dp outline border, swapped for 4dp
  amber on focus. Linear knob walk extends to 5 tiles.
- Maps launcher: AppLauncher.openMapsApp + onOpenMaps wired through
  MainActivity. <queries> already declares the package (0af567e).
- SavedTile: form badge over artwork, titleSmall title, permanent
  outline. SavedListScreen + PageIndicator + StatusScreen + StatusPill
  move to theme tokens and Pilot's ring-dot style.
- Launcher icon: amber play-arrow on charcoal (was white on dark green).
  Distinct from Pilot's up-triangle.

Revertable as a single commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Confirm**

```bash
git log -1 --stat
```

Done. Revert with `git revert HEAD` or `git reset --hard HEAD~1`.
