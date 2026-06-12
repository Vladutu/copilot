# Top Weekly — Design

**Date:** 2026-06-12
**Status:** Approved by Georgian (this session)
**Scope:** Copilot only. Pilot is not involved.

## Summary

A new home-screen tile, **Top Weekly** (icon: `Icons.AutoMirrored.Filled.TrendingUp`), that with a single tap plays the current week's biggest songs from the US and UK YouTube charts in YouTube Music. No YouTube Data API v3 — chart data comes from YouTube's own auto-updating chart playlists via the NewPipe extractor already in the app, and the queue is created with YouTube's anonymous `watch_videos` temp-playlist endpoint.

Unlike every other tile, Top Weekly does not navigate to a screen inside Copilot. Tap → background work (~1–3 s) → YouTube Music opens playing US #1 with the rest queued.

## Verified facts (tested 2026-06-12)

- YouTube maintains official weekly chart playlists with **stable IDs** and weekly-refreshed contents:
  - US "Top 100 Songs United States": `PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI`
  - GB "Top 100 Songs United Kingdom": `PL4fGSI1pDJn6_f5P3MnzXg9l3GDfnSlXa`
- `https://www.youtube.com/watch_videos?video_ids=<id1>,<id2>,...` responds `303` with a
  `Location: https://www.youtube.com/watch?v=<first>&list=TLGG...` header. No login/cookies needed.
- The minted `TLGG...` playlist resolves for any anonymous client, and the YouTube Music **app** accepts
  `https://music.youtube.com/watch?v=<first>&list=TLGG...` (verified on Georgian's phone: queue plays).
- **Hard cap: 50 videos.** Sending 80 IDs produced a 50-video playlist. The merge must cap at 50.
- `TLGG` IDs encode their creation date and are presumed short-lived. Fine: a fresh one is minted per tap.
  Consequence: launches are **not saved to history** (the URL would go dead).

## Data flow (at tap time)

1. **Fetch charts** — two parallel NewPipe `PlaylistInfo.getInfo` calls (US + GB playlist URLs),
   extracting ordered video IDs from `StreamInfoItem.url` (`v=` query param, same parsing approach as
   `NewPipeMusicSearcher`). First page (~100 items) is enough; no pagination.
2. **Merge** — interleave by rank with US priority: US1, GB1, US2, GB2, …; dedupe by videoId
   (first occurrence wins); cap at 50. Pure function.
3. **Mint temp playlist** — one OkHttp GET to `watch_videos?video_ids=...` with
   `followRedirects(false)`; parse the `list=` param out of the `Location` header.
4. **Launch** — existing `AppLauncher.launchYtMusic("https://music.youtube.com/watch?v=<first>&list=<TLGG>")`;
   auto-switch-back behaves as for every other YT Music launch.

## Components

New package `com.vladutu.copilot.charts/`, mirroring the Discover containment pattern
(NewPipe types never leak past the boundary):

- **`ChartFetcher`** (interface): `suspend fun fetchVideoIds(playlistUrl: String): List<String>` —
  ordered, top first.
- **`charts/newpipe/NewPipeChartFetcher`**: implementation using `PlaylistInfo.getInfo` on
  `ServiceList.YouTube`; runs on `Dispatchers.IO`.
- **`TempPlaylistMinter`**: takes `List<String>` video IDs, returns the full
  `music.youtube.com/watch?v=...&list=TLGG...` launch URL; throws on failure. Uses a
  non-redirect-following variant of the shared OkHttp client (`okHttp.newBuilder().followRedirects(false)`).
- **`ChartsRepository`**: orchestrates fetch (parallel) → merge/dedupe/cap(50) → mint → returns launch URL.
  Holds the two hardcoded playlist IDs and the US-priority interleave logic.
- **ServiceLocator**: wires `NewPipeChartFetcher` + `TempPlaylistMinter` into `ChartsRepository`
  (NewPipe init already happens lazily there).

## UI

- 8th tile **"Top Weekly"** with `TrendingUp` icon in the existing knob-controlled home grid
  (`HomeScreen.kt`); grid layout adjusts from 7 to 8 tiles following the existing tile pattern.
- Tap → tile shows a loading state (spinner replaces icon, tile non-reclickable) while the repository
  works → YT Music launches. On total failure an error message (existing snackbar/toast pattern) is shown.
- No new navigation route.
- Deferred to a later session: home-screen restructure (Waze / Maps / Places / Music, where Music opens
  a submenu of Playlists, Songs, Discover, Top Weekly, Radio). Top Weekly will move there when that lands.

## Error handling

- Any failure in steps 1–3 (NewPipe extraction break, network error, `watch_videos` patched/removed,
  missing `Location` header) → **fallback to approach A**: launch
  `https://music.youtube.com/watch?list=PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI` (US chart plays from #1
  directly). The tap always ends in music.
- Fallback usage is logged (existing logging) so chronic breakage is visible in the Logs screen.
- Sub-case: if exactly one chart fetch fails, proceed with the other chart's top 50 (still mint a temp
  playlist) rather than falling back entirely.

## Testing

- **Merge logic** (interleave/dedupe/cap): plain JUnit, table-driven cases — duplicate across charts,
  duplicate within a chart, one empty list, both lists shorter than 25, cap boundary at exactly 50.
- **`TempPlaylistMinter`**: MockWebServer returning `303` + `Location` header (happy path, missing
  header, non-redirect status).
- **`ChartsRepository`**: fake `ChartFetcher`/minter — verifies parallel-fetch results merge, single-chart
  failure degrades to other chart, total failure returns fallback URL.
- No instrumented tests. Georgian builds and verifies on his Mac + real device (no Android SDK on this box).

## Risks

- `watch_videos` is undocumented and could be removed → fallback keeps the feature alive in degraded form.
- NewPipe extraction breaks when YouTube changes internals → pinned `v0.26.3` may need bumping; the
  `ChartFetcher` boundary contains the blast radius.
- Chart playlist IDs could in principle change → constants in one place; fallback URL would also 404,
  in which case the launch simply fails visibly in YT Music (acceptable, has never been observed).
