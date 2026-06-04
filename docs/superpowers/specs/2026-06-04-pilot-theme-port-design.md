# Copilot — adopt PilotTheme, redesign Home for 5 tiles

Date: 2026-06-04
Scope: Copilot app (Android, carbox install). Single commit, revertable.

## Goal

Bring Copilot's visual language in line with Pilot's `4387a43` "dark
automotive-cockpit redesign" and make the in-car screens easier to read at a
glance. Two driver-visible problems today:

1. Three of the four Home tiles (Playlists, Songs, Destinations) are bright
   amber `primaryContainer` text-only slabs. Only the Waze tile carries an
   icon. Distinguishing the three text tiles requires *reading* — slow when
   you're not supposed to look.
2. Waze's label is partially clipped on the carbox screen because
   `BigAppButton` is a vertical column (160dp icon, spacer, label) that
   doesn't fit the available height.

A third goal arrived during brainstorming: a **Maps** entry point on Home.
Copilot already handles `cmd=maps` envelopes from Pilot (commits `5a0b5be`,
`e0c13b0`, `95b5dad`); adding a direct launcher means the user can open Maps
from the carbox without first sending a destination from the phone.

## Non-goals

- No change to the bubble overlay (`BubbleService` / `BubbleView`). It draws
  over other apps, not over Copilot, and isn't an in-driving Copilot surface.
- No change to permissions / diagnostics / logs screens beyond inheriting the
  new theme automatically.
- No change to knob-navigation model. Linear walk + bold focus border stays.
- No change to ntfy schema, history storage, AppLauncher result types, or any
  service-layer code.
- Not extracting a shared theme gradle module. Copilot and Pilot are sibling
  projects, not modules of one project. Theme files are duplicated and kept
  in lockstep manually.

## Design

### Theme port (`ui/theme/`)

Mirror Pilot's `ui/theme/` from commit `4387a43`:

- `Color.kt` — replace `DD*` constants and `TilePalette` with Pilot's palette
  using the `Pilot*` names verbatim, so future diffs against Pilot's
  `Color.kt` read clean even though the file lives in the Copilot package:
  `PilotBackground #0E1116`, `PilotSurface #161B22`,
  `PilotSurfaceVariant #1E2530`, `PilotOutline #2A323D`,
  `PilotPrimary #FFB020`, `PilotOnPrimary #0E1116`,
  `PilotOnSurface #E6EAF0`, `PilotOnSurfaceVariant #9AA4B2`,
  `PilotError #E5484D`, `PilotOk #4FCB66`.
- `Shape.kt` — new file. `Shapes(extraSmall 6dp, small 10dp, medium 16dp,
  large 20dp, extraLarge 28dp)`. Tiles will pull from `Shapes.large`.
- `Type.kt` — replace `DriveDeckTypography` with Pilot's `PilotTypography`
  (titleLarge 22sp SemiBold, titleMedium 16sp SemiBold, titleSmall 14sp
  SemiBold, labelLarge 14sp Medium, bodyLarge 16sp, bodyMedium 14sp).
- `Theme.kt` — `CopilotDriveTheme` becomes a thin wrapper applying the new
  `PilotColorScheme`, `PilotTypography`, `PilotShapes`. Status bar + nav bar
  painted with `PilotBackground`. `FLAG_KEEP_SCREEN_ON` retained (Copilot
  needs the carbox screen awake).

### Startup flash fix

- `res/values/themes.xml` — add `<item name="android:windowBackground">` set
  to `@color/window_background`.
- `res/values/colors.xml` — add `window_background = #0E1116` resource;
  change `ic_launcher_background` from `#0F3D2E` (dark green) to `#0E1116`.

Pilot made the equivalent change in `4387a43` so the boot/cold-start window
doesn't flash white before Compose paints.

### Launcher icon recolor

- `res/drawable/ic_launcher_foreground.xml` — `fillColor` from `#FFFFFF`
  (white) to `#FFB020` (amber, matches `PilotPrimary`). Keep the existing
  right-pointing play-arrow path (`M41,30 L41,78 L81,54 Z`). Pilot uses an
  up-triangle; preserving Copilot's play-arrow keeps the two apps visually
  distinguishable in the launcher even though they now share the same color
  family (amber on charcoal).

### Home screen redesign

Five tiles in two rows:

```
┌────────────────────┐ ┌────────────────────┐
│ [Waze]  Waze       │ │ [Maps]  Maps       │
└────────────────────┘ └────────────────────┘
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ [▶] Playlists│ │ [♪] Songs    │ │ [📍] Destin…│
└──────────────┘ └──────────────┘ └──────────────┘
```

**Layout.** Outer `Column` with two `Row`s, both `weight(1f)` (50/50 vertical
split). Top row: two children at `weight(1f)`. Bottom row: three children at
`weight(1f)`. Outer padding 24dp start/end, 24dp top (was 72dp — status pill
still fits in a smaller top margin now that the home tiles are larger), 24dp
bottom. Inter-tile spacing 16dp.

**`HomeTile` component.** Collapse the current `BigAppButton` + `LabelTile`
into one composable. Signature:

```kotlin
@Composable
fun HomeTile(
    label: String,
    modifier: Modifier = Modifier,
    packageName: String? = null,   // load real app icon via PackageManager
    fallbackIcon: ImageVector? = null,  // when packageName is null or unresolved
    iconTint: Color? = null,       // null = no tint (used for real app icons)
    onClick: () -> Unit,
)
```

- Surface: `colorScheme.surface`, `Shapes.large` (20dp), `tonalElevation 0dp`.
- Border: 1dp `outline` always; **replaced by** 4dp `primary` (amber) when
  focused. Replaced rather than stacked so total width doesn't jitter on
  focus.
- Inner layout: `Row` with `verticalAlignment = CenterVertically`,
  `horizontalArrangement = spacedBy(16.dp)`, padding 20dp.
- Icon box: 96dp square. If `packageName` resolves, load
  `PackageManager.getApplicationIcon(...)`. Else paint `fallbackIcon` tinted
  with `iconTint`.
- Label: `titleLarge` (22sp SemiBold), `onSurface`, `maxLines = 1`,
  `TextOverflow.Ellipsis`, takes remaining width.

**Tile wiring.**

| Tile         | packageName                       | fallbackIcon                  | iconTint              |
|--------------|-----------------------------------|-------------------------------|-----------------------|
| Waze         | `com.waze`                        | `R.drawable.ic_map_pin` (existing) | none — real icon |
| Maps         | `com.google.android.apps.maps`    | `R.drawable.ic_map_pin`       | none — real icon      |
| Playlists    | null                              | `Icons.Filled.PlaylistPlay`   | `colorScheme.primary` |
| Songs        | null                              | `Icons.Filled.MusicNote`      | `colorScheme.primary` |
| Destinations | null                              | `Icons.Filled.Place`          | `colorScheme.primary` |

`fallbackIcon` for the bottom-row tiles uses Material icons-extended (same as
Pilot). The dependency is already wired in Copilot's
`app/build.gradle.kts` — no build change needed.

**Knob navigation.** `TILE_COUNT` becomes 5. The existing
`DirectionLeft`/`DirectionRight` handler in `HomeScreen.onPreviewKeyEvent`
keeps its shape — just one more `FocusRequester` in the list. Walk order:
Waze → Maps → Playlists → Songs → Destinations (reading order). Up/Down
remain ignored. `StatusPill` stays out of the rotation (touch-only).

**`StatusPill`.** Stays in `TopEnd` corner, still a 48dp `surfaceVariant`
rounded chip with `HH:mm` of last event. Restyle the dot to Pilot's ring
style: 12dp outer at 18% alpha wrapping an 8dp solid inner. Colors from
theme:

| `ConnState`     | dot color                       |
|------------------|---------------------------------|
| `Connected`     | `PilotOk`                       |
| `Reconnecting`  | `colorScheme.primary` (amber)   |
| `Error`         | `colorScheme.error`             |

Time text: `bodyMedium`, `onSurfaceVariant`. Keep the time — useful at a
glance and Pilot has no equivalent surface to mirror here.

### Maps launcher

Add to `AppLauncher.kt`:

```kotlin
companion object {
    const val WAZE_PKG = "com.waze"
    const val MAPS_PKG = "com.google.android.apps.maps"
}

fun openMapsApp(): Result = launchByPackage(MAPS_PKG)
```

Refactor the existing `openWazeApp()` if needed to share a common
`launchByPackage(pkg: String): Result` helper that does
`getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK` and returns
`Result.Ok` / `Result.Err`. No change to error semantics.

`<queries>` already declares `com.google.android.apps.maps` (commit
`0af567e`), so package resolution works without manifest changes.

`MainActivity.CopilotNav` — add `onOpenMaps` callback wired identically to
`onOpenWaze`: on `Result.Ok`, call `onLeftToOtherApp()` (which triggers
`BubbleController.requestShow` + `moveTaskToBack`).

### Saved list polish

**`SavedTile`.**

- Add `FormBadge`, copied verbatim from Pilot's `Tile.kt`. 24dp circle,
  `Color.Black.copy(alpha = 0.55f)` background, 14dp Pilot form icon tinted
  `primary`. Positioned `Modifier.align(Alignment.TopStart).padding(8.dp)`
  over the artwork box.
- Title style: `bodyMedium` → `titleSmall` (matches Pilot).
- Border: 1dp `outline` always; replaced by 4dp `primary` when focused (same
  swap pattern as `HomeTile`).
- Shape stays `RoundedCornerShape(16.dp)` — slightly smaller than Home tiles,
  deliberate (saved tiles are 6-per-page, smaller scale).

**`SavedListScreen`.**

- Title row: `headlineMedium` → `titleLarge`. `headlineMedium` isn't in the
  new typography and would fall back to a Material default.
- Empty-state text: `titleMedium` → `titleLarge` (dominant text on an
  otherwise blank screen).
- Knob walk unchanged. Pagination unchanged. `LaunchedEffect`s unchanged.

**`PageIndicator`.** Hardcoded `Color.Gray` for inactive dots →
`MaterialTheme.colorScheme.outline`. Active dot already uses `primary`.

### Status screen polish

`StatusScreen` is a parked-only diagnostics surface — bring it visually in
line, don't redesign.

- Replace hardcoded `Color.Gray` / `Color(0xFF2E7D32)` / `Color(0xFFC62828)`
  / `Color(0xFFF9A825)` with theme tokens:
  - muted text → `onSurfaceVariant`
  - green → `PilotOk`
  - amber → `colorScheme.primary`
  - red → `colorScheme.error`
- 20dp connection dot uses the same ring style as `StatusPill` (scaled).
- `headlineMedium` titles → `titleLarge`.
- `OutlinedButton` for "Diagnostic log" inherits theme automatically.

### BackHomeButton

No changes. `OutlinedButton` picks up theme `outline` border and `primary`
content color automatically. 64dp height retained.

## File-by-file summary

Single commit. Files touched:

- `app/src/main/java/com/vladutu/copilot/ui/theme/Color.kt` — palette swap.
- `app/src/main/java/com/vladutu/copilot/ui/theme/Shape.kt` — new file.
- `app/src/main/java/com/vladutu/copilot/ui/theme/Type.kt` — typography swap.
- `app/src/main/java/com/vladutu/copilot/ui/theme/Theme.kt` — wire new
  colors/shapes/type, status+nav bar paint.
- `app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt` — delete.
- `app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt` — delete.
- `app/src/main/java/com/vladutu/copilot/ui/home/HomeTile.kt` — new
  composable replacing both.
- `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt` — 5-tile
  layout, knob walk extended to 5, padding tweak, wire `onOpenMaps`.
- `app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt` — ring-dot
  restyle + theme tokens.
- `app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt` — form
  badge, title style, permanent outline.
- `app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt` —
  title/empty-state typography.
- `app/src/main/java/com/vladutu/copilot/ui/lists/PageIndicator.kt` —
  inactive dot color from theme.
- `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt` —
  theme-token colors, ring-dot, headline typography.
- `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt` — add
  `MAPS_PKG`, `openMapsApp()`, share `launchByPackage` helper.
- `app/src/main/java/com/vladutu/copilot/MainActivity.kt` — wire
  `onOpenMaps` callback.
- `app/src/main/res/values/colors.xml` — recolor
  `ic_launcher_background`, add `window_background`.
- `app/src/main/res/values/themes.xml` — set `android:windowBackground`.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — fill color
  `#FFFFFF` → `#FFB020`.
- `app/src/main/res/values/strings.xml` — add `home_maps` string.
- `docs/superpowers/specs/2026-06-04-pilot-theme-port-design.md` — this
  document.

## Commit message

```
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
```
