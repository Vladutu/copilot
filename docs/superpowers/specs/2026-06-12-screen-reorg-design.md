# Copilot screen reorganization — design

Date: 2026-06-12
Status: approved by Georgian (conversation), pending implementation

## Goal

Split the current 8-tile home screen into a 4-tile home plus a dedicated Music
page, keeping car-knob (DPAD left/right twist + press) navigation working
everywhere.

## Current state

- `HomeScreen.kt` shows 8 knob-walkable tiles: Waze, Maps (top row) + a
  3-column media grid (Playlists, Songs, Discover / Places, Radio, Top Weekly).
- Navigation is a Compose `NavHost` in `MainActivity.kt` with routes `home`,
  `list/{form}`, `discover`, `discoverBrowse/{keyword}`, `status`, `logs`.
- Knob pattern on home: `TILE_COUNT` FocusRequesters, a `focusedIndex` state,
  and an `onPreviewKeyEvent` that ALWAYS consumes `DirectionLeft/Right`
  (returning false at the ends lets Compose's default focus search desync the
  visible focus from `focusedIndex`).

## Target state

### Home screen (2×2)

| | col 1 | col 2 |
|---|---|---|
| row 1 | Waze | Maps |
| row 2 | Places | Music |

- Knob walk order: Waze(0) → Maps(1) → Places(2) → Music(3). `TILE_COUNT = 4`.
- Waze/Maps tiles unchanged (real app icons via PackageManager).
- Places: `Icons.Filled.Place`, navigates to `list/destination` (same as today).
- Music: `Icons.Filled.LibraryMusic`, navigates to new route `music`.
  (`LibraryMusic`, not `MusicNote` — Songs on the music page uses `MusicNote`.)
- StatusPill stays on home, touch-only, outside the knob walk.
- All Top Weekly busy plumbing and music callbacks leave HomeScreen.

### Music screen (new, 2 rows × 3 columns)

| | col 1 | col 2 | col 3 |
|---|---|---|---|
| row 1 | Playlists | Songs | Top Weekly |
| row 2 | Discover | Radio | (empty) |

- New file `ui/music/MusicScreen.kt`, same skeleton as HomeScreen: 5
  FocusRequesters, linear knob walk 0→4 in reading order, DPAD left/right
  always consumed. Focus starts at Playlists.
- The empty slot is a `Spacer(Modifier.weight(1f))` so all tiles keep the same
  size (approved layout choice; leaves room for a future 6th tile).
- Tiles reuse `HomeTile`. Icons/labels/actions are exactly today's media
  tiles: Playlists → `list/playlist`, Songs → `list/song`, Top Weekly → async
  charts fetch + YT Music launch (busy spinner shows on this tile), Discover →
  `discover`, Radio → `list/radio`.

### MainActivity

- New `composable("music")` route rendering MusicScreen.
- The five music callbacks (including the `topWeeklyBusy` fetch logic) move
  from the home composable to the music composable. Home keeps Waze, Maps,
  Places, Music(navigate) callbacks.
- Back from the music page: nothing new — NavHost pop via the existing BACK
  handling (BackGrabberService broadcast), same as Discover/SavedList today.

### Strings

- Add `home_music` = "Music". All other labels already exist.

## Out of scope

- No changes to KnobPagedGrid, SavedListScreen, DiscoverScreen, charts logic.
- No persistence/model changes (`Form` enum untouched).

## Testing

No gradle on this Linux box (workflow rule); Georgian verifies on Mac + car:

1. Home knob walk hits all 4 tiles and stops at both ends (no bounce/desync).
2. Press on Music opens the music page with focus on Playlists.
3. Music page knob walk hits all 5 tiles; empty slot is skipped (not a stop).
4. Top Weekly spinner shows on the music page tile while fetching.
5. BACK from the music page returns to home.
6. Waze/Maps/Places still launch as before from home.
