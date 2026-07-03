# Copilot — UI redesign build spec

Target: **Jetpack Compose** (this repo). The companion file `Copilot Redesign.html`
is a **visual reference only** — do not port its HTML/CSS. Reproduce the layouts as
Composables in the existing screens. Screen ids referenced below (`3a`, `3b`…) match
the labelled frames in that HTML.

Device: BMW X3 G01 (iDrive 6), **1440×540, landscape**, driven by the rotary knob
(DPAD left/right = twist, center = press) plus touch. Everything is sized for glancing
while driving: large tiles, high contrast, minimal reading.

---

## 0. Golden rule — DO NOT replace real assets

The mockup uses **placeholders**. The app already loads the real thing — keep it:

- **Playlists / Songs artwork** → real covers come from `ArtworkCache.fileFor(form, id)`
  (see `SavedListScreen.SavedRow`). The gradient square + music-note glyph in the mock
  is a stand-in. Keep loading real artwork; use `ic_music_note` only as the existing fallback.
- **Waze / Maps tiles** → real launcher icons via `loadAppIcon(packageManager, packageName)`
  (see `MediaRowTile`). The coloured Waze/Maps glyphs in the mock are stand-ins. Keep the
  real app icons; keep `ic_map_pin` as the existing fallback.
- **Places / Radio / Discover** → keep the current Material icons (`Place`, `Radio`, `Explore`).

Only the **layout, sizing, focus treatment, and the knob/now-playing model** change.

---

## 1. Tokens — already defined, reuse as-is

All in `ui/theme/Color.kt` (do not add new colors):
`PilotBackground #0E1116` · `PilotSurface #161B22` · `PilotSurfaceVariant #1E2530` ·
`PilotOutline #2A323D` · `PilotPrimary #FFB020` (amber, accent + focus) ·
`PilotOnSurface #E6EAF0` · `PilotOnSurfaceVariant #9AA4B2` · `PilotOk #4FCB66` · `PilotError #E5484D`.

Type: keep `PilotTypography`. Tile label uses `LocalTileAppearance.fontSize` (default 32sp) —
keep that user setting working. Focus border width also from `TileAppearance` (default 4dp).

---

## 2. The one shared idea: a **knob rail** of big tiles

Every screen is a **single horizontal row of large tiles** the knob steps through
left→right. Same bottom **dot strip** shows position/pages. This replaces the old 2×2
(Home) and 3×2 (`KnobPagedGrid`) grids.

### 2a. Tile — refactor `MediaRowTile` from row → column
Currently: `Row { 80dp left visual + label }`. Change to a **vertical** tile:

- `Column(center, verticalArrangement = Center, spacedBy ~16dp)`
- Visual on top: icon ~56–62dp, or artwork/app-icon square ~170dp for content tiles.
- Label below, centered, `titleLarge` @ `appearance.fontSize`, `maxLines` 1–2.
- **No subtitle / no "N songs" line** (removed per design).
- Keep the existing focus logic verbatim: focused → `BorderStroke(appearance.focusBorderWidth, primary)` + a soft amber glow (elevation/shadow ok); unfocused → `BorderStroke(1.dp, outline)`; `shape = shapes.large`, `color = surface`.
- Keep the `trailing` slot mechanism — Discover reuses it (see §3e).

### 2b. Rail container — adapt `KnobPagedGrid`
Keep the knob logic (item-major stops, always-consume L/R, page-edge wrap, clamp on
delete). Change the layout to **one row**:

- `pageSize = 5`, `columns = 5` (was 6 / 3). Tiles `weight(1f)`, `gap 20dp`, side padding 32dp.
- Fixed menus that never grow (Home, Music) render **all** their tiles in a single row
  with **no pager** (Home = 4, Music = 6). Data lists (Playlists, Songs, Places, Radio,
  Discover) use the paged rail at 5/page.
- `stopsPerItem` stays a parameter (Discover = 2, everything else = 1).

### 2c. Dot strip (bottom, all screens)
A centered row of dots showing knob position / page. Generalise the existing
`PageIndicator`: current page/stop = amber (`primary`, rendered as a short pill),
others = `outline` dots. On **Home** the strip shows the 4 tile stops + a small heart
glyph as the 5th/last stop (see §3a).

---

## 3. Per-screen

### 3a. Home — `ui/home/HomeScreen.kt` (frame 3a) — the master
- **Top bar** (~60dp): left = a green status dot + "Connected" (drive `dotColor` from
  `StatusPill`'s `ConnState`: green/amber/red). **No "[C]" logo square.** Right = time
  (`HH:mm`, muted) + settings gear (`onOpenSettings`). The old now-playing text label in
  the header is **removed** — it moves to the bottom bar.
- **Rail**: single row of the 4 tiles (Waze, Maps, Places, Music) — keep real Waze/Maps
  app icons, amber `Place` / `LibraryMusic` for the other two. Vertical tiles (§2a).
- **Dot strip**: 4 tile stops + heart as the last stop (matches `HomeKnob`: 4 base tiles,
  heart appended only while a song plays, always last).
- **Now-Playing + Like bar** (bottom, ~96dp, `surface` tint, top border `outline`) —
  **Home only**. Left: real album art thumb + title + artist (`nowPlayingLabel`). Right:
  the **Like heart** in an amber ring with a small "5" stop badge. **No animated equalizer
  bars. No "twist here / press to like" caption.** The heart is the last knob stop; press
  = `onLike`; fills solid (`Icons.Filled.Favorite`) when `isLiked`. Show the bar only when
  `nowPlaying != null`.
- Keep `HomeKnob` stop arithmetic and the L/R `onPreviewKeyEvent` clamp.

### 3b. Playlists / Songs / Places / Radio — `ui/lists/SavedListScreen.kt` (frames 3b, 3c)
- Paged rail (§2b), 5 tiles/page. Vertical tiles: **real artwork** (Playlists/Songs) or
  Material icon (Places = `Place`, Radio = `Radio`) on top + **title only** (no song count).
- `ScreenHeader`: back button (exists) + centered title + **right-aligned position count**
  e.g. `1–5 / 24` (add a trailing slot to `ScreenHeader`).
- Bottom dot strip = page dots. **No Like bar here** (Home only), so tiles take the full height.
- Keep `resetKey` behavior (jump to page 0 when the top item changes) and long-press-to-delete.

### 3c. Music — `ui/music/MusicScreen.kt`
- Fixed 6-item menu → **single non-paged row of 6** vertical tiles (Playlists, Songs, Time
  Machine, Discover, Radio, Liked), amber Material icons as today. `ScreenHeader` back +
  title, no count needed. No Like bar.

### 3d. Discover — `ui/discover/DiscoverScreen.kt` (frame 3d) — two stops per tile
- Paged rail, `stopsPerItem = 2`, 5 tiles/page. Vertical tile: `Explore` icon + keyword,
  and a **full-width "▶ Play mix" button pinned at the tile bottom** via the tile's
  `trailing`/second-stop slot.
- Knob order (unchanged from current `stopsPerItem=2`): **category (Browse) → its Play mix
  → next category**. Stop 1 (tile body) = `onBrowse(keyword)`; stop 2 (Play mix button) =
  play the radio mix (existing `playMix`). Both get the amber focus ring when they are the
  current stop (mock shows tile-1 body focused and tile-2's button focused to illustrate).
- Keep long-press-to-delete and the busy spinner on the Play mix button.

### Other screens
`LikedSongsScreen`, `BrowseResultsScreen`, `StatusScreen`, `SettingsScreen`, `LogsScreen`
are out of scope for this pass — leave them, but if they use `MediaRowTile`/`KnobPagedGrid`
they inherit the new tile/rail automatically. Verify they still read well after the tile
goes vertical; adjust `maxLines` if needed.

---

## 4. Interaction summary (knob)
- Twist L/R = move focus one **stop**; always consume L/R (clamp at list ends) — keep the
  existing rationale in `KnobPagedGrid`/`HomeScreen`.
- At a page edge, twisting advances/rewinds the pager (data lists only).
- Press (center) = activate the focused stop.
- Home only: last stop is the Like heart → press likes the current song.

## 5. Acceptance
- Every screen is one horizontal rail; focus ring is a 4dp amber border + glow; dot strip
  reflects position/pages.
- Home shows the Now-Playing/Like bar (no equalizer, no caption, no logo square); the heart
  is the last knob stop.
- No song-count subtitles anywhere.
- Real artwork and real Waze/Maps app icons are still loaded (placeholders NOT hard-coded).
- `TileAppearance` font-size / border-width settings still take effect.
