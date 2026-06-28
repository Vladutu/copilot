# Music Time Machine — Design

**Date:** 2026-06-28
**Status:** Approved by Georgian (this session)
**Scope:** Copilot only. Pilot is not involved.
**Replaces:** Top Weekly (spec 2026-06-12-top-weekly). The Top Weekly tile and its US/GB
weekly-chart fetch code retire; the `watch_videos` temp-playlist minter is reused.

## Summary

A home-screen tile, **Time Machine**, that with one tap plays a chronological tour through
the biggest songs of random past years. Tap → pick ~5 random years from 1980 to the last
complete year → take each year's top 3 from that year's Billboard Year-End Hot 100 → resolve
each to a YouTube video → mint one anonymous queue → play in **year order** (oldest first) in
YouTube Music. ~1–4 s of background work (near-instant for years already cached), no new screen.

This replaces Top Weekly, whose current-charts songs Georgian rarely likes. The "time machine"
feel comes from moving *forward through eras* within one session (e.g. 1987 → 1996 → 2003 →
2011 → 2019), so the queue is played **in chronological order, not shuffled**.

## Verified facts (tested 2026-06-28, live Wikipedia)

- Chart data source: Wikipedia **Billboard Year-End Hot 100** pages, one per year, at the stable
  URL pattern `https://en.wikipedia.org/wiki/Billboard_Year-End_Hot_100_singles_of_<YEAR>`.
- Fetched as clean structured markup via the **MediaWiki API**, not HTML scraping:
  `https://en.wikipedia.org/w/api.php?action=parse&page=Billboard_Year-End_Hot_100_singles_of_<YEAR>&prop=wikitext&format=json`.
- **Full sweep 1980→2026 confirmed:** all **45 complete years (1980–2025)** parse to clean
  `Artist – Title` strings with the recipe below. 2026 returns an API `error` (page not yet
  created) and is skipped. No misaligned, empty, or junk rows remained after the two parser
  fixes (see Risks). Sampled correctness: 1980 `Blondie – Call Me`, 2003 `50 Cent – In da Club`,
  2025 `Lady Gaga and Bruno Mars – Die with a Smile`.
- Edge cases handled in-parser: inline `||` cells, `[[target|display]]` wikilinks, `<ref>` tags,
  `<!-- HTML comments -->` (2021), cell attributes like `rowspan="2"|` (1995), parenthetical
  artist groups (1986), and double-A-side titles `A / B` (1997).
- `watch_videos` minting + the YT Music app launch are unchanged from Top Weekly (still verified):
  15 IDs is well under the 50-entry cap.

## Validated parse recipe

Containment-boundaried (no MediaWiki/HTML/JSON types leak out). Per year:

1. Strip `<!--…-->` then `<ref…>…</ref>` from the **whole** wikitext (before any splitting —
   comments can straddle a `||` cell delimiter, which broke 2021 until fixed).
2. Take the first `wikitable`; cut at the closing `\n|}`.
3. Split rows on `\n|-`; skip header rows (start with `!`).
4. Normalise newline-leading-`|` cells to inline `||`, split on `||`, require ≥3 cells.
5. **Rank** = digits of cell 1. **Title** = cell 2, **Artist** = cell 3, each: strip any cell-attribute
   prefix (`rowspan=…|`), delink `[[a|b]]→b` / `[[a]]→a`, drop `''italics''`, `{{templates}}`,
   quotes. Title → take text before ` / ` (double A-side). Artist → take text before `featuring`/
   `feat.` and before `(` (group list).
6. Collect the top **N successfully parsed** rows. A `rowspan` artist makes its continuation row
   have <3 cells, so it is skipped and the next rank taken (e.g. #1, #2, #4) — all still top hits.
   **Accepted; not worth parser complexity to recover.**
7. **Missing year** (API `error`) or **stub** (<N parsed rows) → return empty; the selector draws
   another year. Self-correcting; covers the "Jan 1, this year's page is still a stub" case.

## Data flow (at tap time)

1. **Pick years** — `YearSelector` draws `YEARS_PER_TOUR` (5) distinct random years from
   `1980 … (deviceYear − 1)`. For each pick, if the resolved set is empty (missing/stub), discard
   and draw another (bounded retries). Sort ascending.
2. **Resolve each year → video IDs** (in parallel across years):
   - **Cache hit:** read `year → [videoId×3]` straight from disk. No network.
   - **Cache miss:** MediaWiki fetch + parse → top 3 `SongRef(artist, title)` →
     `MusicSearcher.searchSongs("$artist $title")`, take the first result's `videoId` →
     write `year → [ids]` to cache.
3. **Assemble** — concatenate years in ascending order, top-3 within each in chart order
   (chronological tour). ~15 IDs.
4. **Mint** — reuse `TempPlaylistMinter.mint(ids)` → `watch_videos` → `list=…`.
5. **Launch** — build an **ordered** YT Music URL (no `shuffle=1`) and
   `AppLauncher.launchYtMusic(url)`. Auto-switch-back behaves as for every YT Music launch.

## Components

New package `com.vladutu.copilot.timemachine/`. The `charts` package's weekly-specific code
(`ChartsRepository`, `ChartFetcher`, `NewPipeChartFetcher`, `ChartMerger` + their tests) is
**deleted**; `TempPlaylistMinter`/`PlaylistMinter` move into `timemachine` (or a shared spot)
and gain only the ordered-URL change.

- **`SongRef`** (data class): `artist`, `title`; `query` = `"$artist $title"`.
- **`YearEndChartSource`** (interface): `suspend fun topSongs(year: Int, n: Int): List<SongRef>` —
  empty list for missing/stub years; throws `TimeMachineException` only on hard backend failure.
- **`wikipedia/WikipediaYearEndSource`**: MediaWiki fetch (descriptive User-Agent — Wikipedia API
  etiquette) + the validated parse recipe, on `Dispatchers.IO`. Containment boundary.
- **`TimeMachineCache`**: persists `year → List<videoId>` to a small file in app storage (same
  flavour as Liked Songs / SettingsStore). Immutable data → never invalidated. Bonus resilience:
  cached years still play when Wikipedia/search is down.
- **`YearSelector`**: draws K distinct random years in range, skipping years a probe reports
  empty; bounded retries. RNG injected for tests.
- **`TempPlaylistMinter`** (reused) + **`YtMusicUrls.orderedPlaylist(id)`** = `watch?list=<id>`
  with **no** `shuffle=1` (new builder alongside the existing shuffled `playlist(id)`).
- **`MusicSearcher`** (reused, unchanged) for song→videoId resolution.
- **`TimeMachineRepository`**: orchestrates select → resolve(cache|fetch) → assemble → mint →
  ordered URL. **Never throws** (degrades, see below).
- **ServiceLocator**: wires `WikipediaYearEndSource` + `TimeMachineCache` + reused
  `NewPipeMusicSearcher` + `TempPlaylistMinter` into `TimeMachineRepository`.

## UI

- The **Top Weekly** tile becomes **Time Machine** in the Music submenu grid: new string
  `home_time_machine` ("Time Machine"), icon `Icons.Filled.History` (or similar retro/clock
  glyph). Knob walk order and grid layout unchanged — same slot.
- Tap → existing busy-spinner pattern on the tile (`topWeeklyBusy` → `timeMachineBusy`) while the
  repository works → YT Music launches. Knob-BACK during the busy window cancels (pops the scope),
  same as today.
- No new navigation route. `MainActivity` swaps `chartsRepository.topWeeklyLaunchUrl()` for
  `timeMachineRepository.launchUrl()`.

## Error handling (never-throws, mirrors Top Weekly)

- A single year failing to resolve (Wikipedia down **and** not cached) → that year is dropped;
  the tour plays the remaining years. Logged.
- If fewer than `YEARS_PER_TOUR` years resolve, play whatever did (cache makes this rare and it
  shrinks over time). Logged.
- **Only** if zero songs resolve across all attempts → user-visible error (existing snackbar/toast),
  no launch. There is no longer a US-chart fallback URL (Top Weekly's), because the feature no longer
  has a single "always valid" playlist; the cache is the resilience mechanism instead.
- All degradations logged to the existing Logs screen so chronic breakage is visible.

## Testing

- **Parser** (the critical one): JUnit over inline wikitext fixtures captured from real years —
  must include the four edge cases proven this session: `rowspan` (1995), straddling HTML comment
  (2021), parenthetical artist group (1986), double-A-side title (1997), plus a clean year (2003).
  Asserts exact `SongRef` lists. Pure JVM, no network.
- **`YearSelector`**: seeded RNG + fake probe → deterministic picks; asserts stub years are skipped
  and K distinct years returned, range bounds respected.
- **`TimeMachineCache`**: write-then-read round trip; a cached year resolves without touching the
  source (verified via a throwing fake source).
- **`TimeMachineRepository`**: fake source/searcher/minter — chronological ordering, cache-hit path,
  one-year failure degrades, total failure surfaces the error signal (no launch).
- **`TempPlaylistMinter`**: existing MockWebServer test, plus the ordered-URL builder asserts no
  `shuffle=1`.
- No instrumented tests. Georgian builds + verifies on his Mac and real device (no Android SDK on
  the Linux box).

## Risks

- **Wikipedia format drift** — the year-end table layout could change. Contained behind
  `YearEndChartSource`; the parser fixtures fail loudly; the 45-year sweep shows current robustness.
- **Two parser fixes were required during validation and are mandatory in the port:** (a) strip HTML
  comments from the whole wikitext *before* cell-splitting (2021), (b) trim artist at `(` and title
  at ` / ` (1986, 1997). Without them those years emit junk.
- **`searchSongs` picks the wrong cut** (remix/live/cover as first result) — inherent to keyword
  resolution; same first-result trust the Discover feature already relies on. Accepted.
- **MediaWiki rate limiting / UA policy** — Wikipedia requires a descriptive User-Agent and throttles
  bursts (hit 429 during the sweep). Real use makes at most ~5 calls per tap, and the cache drives
  that toward zero, so this is a non-issue in practice; still, set a proper UA.
- **`watch_videos` undocumented / cap** — unchanged from Top Weekly; 15 IDs is well under 50.
- **Wrong device clock** — would shift the upper year bound; minor, and stub-skip absorbs it.
