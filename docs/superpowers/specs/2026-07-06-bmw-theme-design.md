# BMW Theme + Theme Dropdown — Design

Date: 2026-07-06
Status: approved (design discussed in session; extensible ThemeSpec variant chosen)
Reference: `docs/img.png` (BMW M-style mock — colors/typography reference only; current layout is kept)

## Goal

A selectable visual theme for Copilot. A "Theme" dropdown in Settings offers **Default**
(today's look, unchanged) and **BMW** (dark navy cockpit look from the mock). No layout
changes anywhere — only colors, typography, and two small BMW-only tricolor accents.
Designed so future themes are a data addition, not a code change.

## Architecture

### ThemeSpec (new file `ui/theme/Themes.kt`)

Each theme is a self-contained spec object; screens stay theme-agnostic.

```kotlin
data class ThemeAccents(
    // BMW M tricolor; null on themes without decorative accents
    val stripes: List<Color>?,          // [lightBlue, darkBlue, red]
)

data class ThemeSpec(
    val id: String,                     // persisted in DataStore ("default", "bmw")
    val label: String,                  // dropdown text ("Default", "BMW")
    val colorScheme: ColorScheme,
    val typography: Typography,
    val accents: ThemeAccents,
)

val DefaultTheme: ThemeSpec            // current palette + PilotTypography, no accents
val BmwTheme: ThemeSpec
val AllThemes: List<ThemeSpec> = listOf(DefaultTheme, BmwTheme)

fun themeById(id: String?): ThemeSpec  // unknown/null -> DefaultTheme (safe downgrade)
```

- Dropdown options derive from `AllThemes`; adding a theme later = one new spec + list entry.
- A `LocalThemeSpec` CompositionLocal (same pattern as `LocalTileAppearance`) exposes the
  active spec so `MediaRowTile`/`TopBar` can render accents without knowing theme names.

### Theme plumbing

- `CopilotDriveTheme(theme: ThemeSpec = DefaultTheme)` applies `theme.colorScheme` +
  `theme.typography` and provides `LocalThemeSpec`. Status/navigation bar `SideEffect`
  uses `theme.colorScheme.background` instead of the hardcoded color.
- All existing screens keep reading `MaterialTheme.colorScheme.*` / `typography.*` — the
  amber focus border/wash/glow, heart ring, and now-playing accents automatically become
  ice blue under BMW because they read `colorScheme.primary`.

### Persistence (SettingsStore)

- `KEY_THEME = stringPreferencesKey("theme")`; `themeFlow: Flow<String>` (default
  `"default"`), `suspend fun setTheme(id: String)`.
- `MainActivity.CopilotNav()` collects `themeFlow` → `themeById(id)` → passes the spec to
  `CopilotDriveTheme`. Instant switch via recomposition; no activity recreate.

## BMW theme content

### Colors (sampled from mock)

| Role | Value |
|---|---|
| background | `#050A14` near-black navy |
| surface | `#0D1626` card navy |
| surfaceVariant | `#12203A` status pill bg, a step lighter than surface |
| primary | `#4FA8E8` ice blue (focus border/wash/glow, hearts, accents) |
| onSurface | `#EAF1F8` |
| onSurfaceVariant | `#8FA3BC` muted blue-grey |
| outline | `#1E2D45` subtle card border |
| error / ok | keep current `#E5484D` / `#4FCB66` |

### Typography (BMW only)

- Bundle in `res/font`: Saira Regular + Medium, Saira Condensed SemiBold + Bold
  (Google Fonts, OFL — license file included alongside).
- BMW `Typography`: Saira Condensed for titles/labels (tile labels, section headers,
  caps labels like "NOW PLAYING" with wide letter-spacing per mock), Saira for body.
- Default theme keeps `PilotTypography` (system font) byte-for-byte.
- User-adjustable tile font size continues to apply on top; only the typeface changes.

### Tricolor accents (render only when `accents.stripes != null`)

- New `MTricolor` composable: three skewed parallelogram stripes drawn with Canvas
  (light blue `#00A0E0`, dark blue `#1A3E8C`, red `#E30613`); no image assets.
- Placement 1: left edge of the Home top bar, inline before the connection status.
  On Default the composable renders nothing and the Row is identical to today.
- Placement 2: top-left corner of the **focused** tile in `MediaRowTile` (like the Waze
  tile in the mock).

## Settings UI

- New "Display" `SettingsSection` card placed above General.
- One row: "Theme" + current label; tapping opens a Material 3 `DropdownMenu` with
  `AllThemes` labels. First dropdown in the app → new reusable `DropdownRow` composable
  next to `SwitchRow`/`SliderRow`; knob-focusable like other rows.
- Selection → `applicationScope.launch { settingsStore.setTheme(id) }` (existing pattern).

## Explicitly out of scope

- Any layout change (tile row, dot strip, now-playing strip, settings cards unchanged).
- Red top hairline from the mock (not requested after review).
- Theme files on disk, JSON palettes, user-customizable colors, per-theme layouts.

## Error handling

- Unknown/legacy theme id in DataStore → `themeById` falls back to DefaultTheme.
- Missing font resources fail at build time (resource references), not runtime.

## Testing

- Unit tests (pure JVM): `themeById` fallback behavior; `AllThemes` ids unique & non-blank;
  specs' accent invariants (Default has none, BMW has 3 stripe colors).
- No Gradle on this Linux box — Georgian compiles/tests on Mac, then phone + car check.
- Manual checklist: dropdown knob navigation; caps letter-spacing at 32sp tile labels;
  theme switch while a song is playing (now-playing strip recolors); status bar color
  follows theme; Default theme pixel-identical to today.
