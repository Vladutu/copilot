# Discover — YouTube Music discovery in Copilot

**Date:** 2026-06-11
**Repos affected:** Copilot (primary), Pilot (category authoring)

## Overview

A new **Discover** feature lets the driver play YouTube Music playlists and mixes they
don't already know, organized by keyword categories (e.g. "Workout", "Petrecere",
"80s rock"). Categories are authored in Pilot (where typing is easy) and pushed to
Copilot over the existing ntfy channel. On the headunit, each category offers:

- **Browse** — a paged grid of matching playlists (cover art + title); press one to
  play it in YT Music.
- **Mix (▶)** — instantly start a YT Music radio mix seeded from a popular song
  matching the keyword; endless queue, one press.

Search runs inside Copilot via **NewPipe Extractor** (community-maintained Java
library over YouTube's internal API). No Google API key, no quota. Nothing from
Discover is saved to history; categories persist until deleted by long-press.

## Decisions (agreed during brainstorming)

| Topic | Decision |
|---|---|
| Discovery backend | NewPipe Extractor in Copilot (option C1) — not the official Data API, not a self-hosted ytmusicapi service |
| Category actions | Both Browse and instant Mix, always visible (split tile) |
| Category authoring | In Pilot only; pushed via ntfy. Copilot can only delete (long-press) |
| Offline delivery | Live only — no backlog fetch; Pilot can re-send a category with one tap |
| History | Discover saves nothing to Copilot history |
| Home placement | Media grid becomes 3-wide: Playlists/Songs/Discover, then Places/Radio |
| Discover tile UX | Split tile: name press = browse, ▶ press = instant mix |

## Protocol (additive change, stays v3)

New message type on the existing envelope:

```json
{"v":3, "ts":1718100000, "cmd":"category", "form":"category", "title":"Workout"}
```

- `title` carries the keyword. Required; blank/missing → `Rejected`.
- `url` is omitted. The parser allows absent `url` for `cmd=category` only.
- The 30s staleness check applies unchanged (live-only delivery by design).
- `savesToHistory()` = false; the message routes to the category store instead.
- Backward/forward compatibility: an old Copilot rejects unknown `cmd` cleanly;
  both apps are updated together anyway. No version bump needed (additive).

## Pilot changes

- **Categories tile** on the HomeHub (alongside Playlists/Songs/Places/Radio) →
  a list screen of categories stored locally in DataStore.
- **Add**: dialog with a single text field (same pattern as the existing
  AddUrlDialog) → saves locally and publishes immediately.
- **Tap a category → re-send** it (covers the car-was-off case in one tap).
- **Delete** (long-press, with confirm): removes from Pilot's local list only.
  Pilot's list and Copilot's chips are independent by design.
- Publishing reuses `NtfyPublisher` and its existing retry/backoff path, with
  the new `cmd=category` envelope.

## Copilot changes

### Storage & routing

- **`CategoryRepository`** (DataStore, same pattern as `HistoryStore`): ordered
  list of category keywords. Add (case-insensitive dedupe), delete, observe as Flow.
- **`ListenerService`**: accepted `cmd=category` messages → `CategoryRepository.add()`
  instead of `AppLauncher`; surfaces in the status feed and diagnostics log like
  any other accepted message.

### Home screen

`HomeScreen.kt`: `MEDIA_COLUMNS` 2 → 3, media tile order becomes
**Playlists, Songs, Discover, Places, Radio** (5 tiles → rows
`[Playlists|Songs|Discover]`, `[Places|Radio|empty]`), `TILE_COUNT` 6 → 7.
Same three row-bands as today, so tile height is unchanged; media tiles get
narrower. Knob order: Waze → Maps → Playlists → Songs → Discover → Places → Radio.

### Shared knob grid (refactor)

Extract the paged 2×3 grid + knob focus machinery from `SavedListScreen` into a
reusable **`KnobPagedGrid`** composable parameterized by item count, tile content
slot, and per-page focus-stop count. It owns the already-debugged invariants:

- `focusedIndex` is the single source of truth; a `LaunchedEffect` pushes focus
  to `tileFocus[focusedIndex]`.
- `onPreviewKeyEvent` **always consumes** `DirectionLeft/Right` (clamping at the
  ends) so Compose's default directional focus search can never desync focus from
  state — the root cause of the old "knob jumps around" bug.
- Page-edge behavior: last stop + right → next page, first stop; first stop +
  left → previous page, last stop.
- Stale `focusedIndex` after deletions is clamped before requesting focus.

`SavedListScreen` is rewritten on top of it (behavior unchanged); the two new
Discover screens consume it.

### Discover screen

`ScreenHeader("Discover", back)` + `KnobPagedGrid` of category tiles, 6 per page,
2 rows × 3, page-indicator dots below.

- **Split tile**: name zone (press = open Browse results) + ▶ zone on the right
  (press = instant mix). Each category contributes **two knob stops** in reading
  order: `Workout → Workout▶ → Chill → Chill▶ → …` Focus highlight (the existing
  4dp primary border treatment) is drawn per-zone so the active zone is obvious.
- **Long-press** on the name zone → same delete-confirmation `AlertDialog` pattern
  as `SavedListScreen`; deletes from `CategoryRepository`.
- **Empty state**: centered hint ("Send categories from Pilot") when no categories.
- Physical BACK returns home (bubble — and therefore BackGrabber's interception —
  is suppressed while Copilot is foreground, so in-app BACK already works).

### Browse results screen

`ScreenHeader(category name, back)` + `KnobPagedGrid` of playlist tiles
(cover art + title, visually like `SavedTile`), 6 per page.

- Press a tile → `AppLauncher` launches
  `https://music.youtube.com/playlist?list=<ID>` in YT Music, mirroring exactly
  what replay-from-history does today (same bubble / auto-switch-back behavior).
- **Loading**: centered spinner while the search runs.
- **Failure / zero results**: inline message with a focusable "Retry" tile;
  failure also logged to diagnostics.
- Results are kept in memory per category for the app session (snappy
  back-navigation); no persistent cache — there is no quota to protect.

### Instant mix (▶)

1. Search songs for the keyword (`music_songs` filter), take the top ~10.
2. Pick one at random (different seed each press).
3. Launch `https://music.youtube.com/watch?v=<videoId>&list=RDAMVM<videoId>`
   via `AppLauncher` → YT Music plays an endless radio mix.
4. While the seed search runs, the tile's ▶ zone shows a spinner (as built — replaces
   the originally-sketched full-screen overlay); on search failure, a toast + diagnostics
   entry.

### Search layer (containment boundary)

The search backend is a swappable implementation detail. Requirement: replacing
NewPipe Extractor with the official YouTube Data API later must touch only one
package and the dependency declaration — zero changes to screens, repositories,
or `AppLauncher`.

- **`MusicSearcher`** interface (in `discover/`):
  `searchPlaylists(keyword): List<FoundPlaylist>`,
  `searchSongs(keyword): List<FoundSong>`. UI and screen logic depend only on this.
- **`FoundPlaylist`** (`playlistId`, `title`, `thumbnailUrl?`) and **`FoundSong`**
  (`videoId`, `title`) are our own plain data classes. **No NewPipe type, exception,
  or import may appear outside the implementation package** — the adapter catches
  library exceptions and rethrows a domain `SearchException`.
- The interface returns raw IDs, not URLs; building the
  `music.youtube.com/playlist?list=…` / radio URLs happens on the caller side,
  since that part is backend-independent.
- **`NewPipeMusicSearcher`** implementation lives alone in `discover/newpipe/`:
  NewPipe Extractor with the `music_playlists` / `music_songs` content filters,
  running on `Dispatchers.IO`. The one-time `NewPipe.init(downloader)` (OkHttp-backed
  `Downloader`) happens lazily and idempotently on first search inside the searcher
  itself — even `CopilotApp` knows nothing about the backend.
- The single construction site (where `NewPipeMusicSearcher` is instantiated and
  handed to the UI) is the only line that names the implementation. A future
  `DataApiMusicSearcher` (official API: `search?type=playlist|video`, API key,
  quota-aware caching) drops in at that line.

## Dependencies & maintenance

- `com.github.TeamNewPipe:NewPipeExtractor:v0.26.3` via JitPack (same coordinate the
  NewPipe app itself uses; the JitPack repo in `settings.gradle.kts` is restricted to
  the TeamNewPipe group).
- Copilot's Dependabot gradle config reads `libs.versions.toml`, but Dependabot does
  not reliably detect new JitPack tags — expect to bump `newpipeExtractor` manually
  from the NewPipeExtractor releases page.
- Expected failure mode: discovery searches start failing while everything else
  in Copilot keeps working (the library is isolated behind `MusicSearcher`).
  Remedy: bump the version, rebuild, reinstall, re-enable BackGrabber.

## Error handling summary

| Failure | Behavior |
|---|---|
| Category message with blank/missing title | `Rejected` with reason, visible in status UI |
| Search throws / times out | Inline error + Retry tile (Browse) or inline error (Mix); diagnostics entry |
| Zero search results | Empty-state message in Browse; "nothing found" error for Mix |
| YT Music not installed / launch fails | Existing `AppLauncher` failure path (status + diagnostics) |

## Testing

- **`Message` parser**: accept `cmd=category`; reject blank title; reject
  unknown cmd (compatibility); staleness still enforced.
- **`CategoryRepository`**: add / case-insensitive dedupe / delete / ordering.
- **Pilot `NtfyPublisher`**: envelope test for the category payload (MockWebServer),
  mirroring the existing per-cmd tests.
- **Discover screen logic**: drive with a fake `MusicSearcher` (loading, results,
  failure, retry, random-seed pick).
- **`KnobPagedGrid`**: unit tests for the index/page arithmetic (stop walking,
  page-edge transitions, clamp-after-delete); `SavedListScreen` keeps its
  existing behavior on top of it.
- **`NewPipeMusicSearcher`**: thin adapter, verified manually on-device (per the
  established workflow: built and tested on the Mac/headunit, knob walk-through
  of home → Discover → Browse → launch).

## Out of scope

- Free-text keyword search on the headunit.
- YT Music editorial Moods & Genres catalog (would require hand-rolled InnerTube
  browse calls or a self-hosted ytmusicapi service — possible later upgrade).
- Official YouTube Data API backend — deliberately not built now, but the
  `MusicSearcher` containment boundary above is designed so it can replace
  NewPipe Extractor later by adding one implementation class and swapping the
  construction site.
- Saving discovered playlists/mixes to history.
- Offline/backlog delivery of category messages.
- Category sync/reconciliation between Pilot and Copilot lists.
