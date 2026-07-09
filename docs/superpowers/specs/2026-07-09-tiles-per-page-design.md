# Configurable tiles-per-page — design

Approved in-session 2026-07-09.

## Goal

Two user-tweakable page sizes for the knob rails, in Settings:

- **Menu tiles per page** — the fixed menu screens (Home, Music). Default **4**.
- **List tiles per page** — content-driven tile screens (Playlists, Songs,
  Destinations, Radio, Discover, browse results). Default **5**. (Liked Songs is a
  scrolling text list, not a tile rail — unaffected.)

Both range 2–8, slider rows in the existing **Tiles** settings card. Defaults must
render an untouched install exactly as it looks today.

## Architecture

- `PageSizes(menuTiles, listTiles)` + `PageSizeDefaults` + `LocalPageSizes`
  CompositionLocal in `ui/theme` — same pattern as `TileAppearance`.
- Two `Int` settings in `SettingsStore` (DataStore), collected in `MainActivity`'s
  `CopilotNav` and provided via `LocalPageSizes` next to `LocalTileAppearance`.
- `KnobPagedGrid`'s `pageSize` default changes from `5` to
  `LocalPageSizes.current.listTiles` — list screens pick it up with zero changes.
- `MusicScreen` passes `LocalPageSizes.current.menuTiles` (replaces `MUSIC_PAGE_SIZE`).
- **Home migrates onto `KnobPagedGrid`** (the same move Music made in b09354f), so it
  pages with the same slide animation, finger-swipe support, and fast-twist fix as
  every other screen. Requires two grid extensions:
  - **Trailing stop**: an optional caller-owned extra knob stop after the last tile of
    the last page — Home's Like heart, which renders in the now-playing strip, not the
    rail. Nav math goes in `KnobGridNav` (`hasTrailingStop`), unit-tested.
  - **`bottom` slot**: Home passes the now-playing strip so the grid's knob key handler
    (moved from the pager to the grid's root Column) stays an ancestor of the focused
    heart — otherwise DPAD left/right on the heart would leak to Compose's default
    focus search.
  - `horizontalPadding` parameter (default 32dp, Home passes 0dp) keeps Home's rail
    flush as today.
- `HomeKnob.kt` + `HomeKnobTest.kt` are deleted; the grid + trailing stop subsume them.

## Invariants that must hold (knob + pill)

- Fast-twist page bounce stays fixed: `KnobPos` remains the single source of truth;
  nothing reads the pager's transient page (KnobPagedGrid.kt comment, b09354f).
- Pill rules unchanged: per-item dots when the list fits one page or `perItemDots`
  (Music, Home); otherwise one pill per page, hidden when single-page.
- Home: heart is the last stop overall; default focus never lands on it; left-twist
  from Waze never reaches it; heart focus clamps back to the last tile when the song
  stops; dot strip shows 4 dots + trailing heart tracking the focused stop.
- Existing callers with `trailingStop = null` behave bit-identically.

## Out of scope

Per-screen page sizes; tile size/typography changes; Pilot/Wingman.
