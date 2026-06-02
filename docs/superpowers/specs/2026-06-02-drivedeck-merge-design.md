# DriveDeck → Copilot merge design

**Date:** 2026-06-02
**Status:** Approved (brainstorming complete; implementation plan next)

## Goal

Fold the standalone DriveDeck app into Copilot so the carbox runs a single APK that:

1. Listens for ntfy commands published by Pilot and launches the right app (existing Copilot role).
2. Persists every successful command locally as a saved item, grouped by form.
3. Presents a driver-facing UI with quick-tap access to Waze, plus three lists of previously-played playlists, songs, and Waze destinations.

DriveDeck ceases to exist as a separate app. Pilot's role is unchanged in shape; its wire format bumps to v3 to carry `form`, `title`, `imageUrl`.

The whole change ships as a **single commit per repo** (Copilot + Pilot) for one-step revert.

## Non-goals

- Voice-driven playback ("play X by Y") — removed from DriveDeck's surface area.
- Editing saved items from the carbox — they're read-only there; Pilot is the source.
- A separate "settings" or "manage" UI on the carbox beyond the diagnostics screen.
- Cross-version interop between v2 and v3 — both apps installed in lockstep.

## High-level architecture

Three responsibilities; one app.

```
┌──────────────────────────── Copilot (carbox) ────────────────────────────┐
│                                                                          │
│  ListenerService ──► AppLauncher ──► (Waze | YT Music)                   │
│        │                  ▲                                              │
│        │                  │                                              │
│        ▼                  │                                              │
│  HistoryRepository ◄──── UI tap on a saved tile                          │
│        │                                                                 │
│        ▼                                                                 │
│  ArtworkCache (downloads imageUrl bytes → cacheDir/artwork/<form>-<id>)  │
│                                                                          │
│  MainActivity (NavHost: home, playlists, songs, destinations, status)    │
│  BubbleService (foreground overlay to return after leaving for another)  │
└──────────────────────────────────────────────────────────────────────────┘
              ▲ ntfy
              │
┌──────────── Pilot (phone) ───────────────────┐
│  CatalogStore → MetadataFetcher.fetch (await) │
│  NtfyPublisher (v3 envelope)                  │
└───────────────────────────────────────────────┘
```

## Package layout (Copilot, post-merge)

```
com.vladutu.copilot/
├─ CopilotApp.kt              (gains DI locator)
├─ MainActivity.kt            (renamed from StatusActivity; hosts NavHost)
├─ config/Config.kt           (unchanged)
├─ net/
│  ├─ Message.kt              (v3: + form, title, imageUrl)
│  └─ NtfySubscriber.kt       (unchanged)
├─ service/ListenerService.kt (saves to history after successful launch)
├─ launch/
│  ├─ AppLauncher.kt          (fused — covers Pilot-driven and UI-driven launches)
│  └─ PlaylistIdParser.kt     (lifted from DriveDeck)
├─ history/
│  ├─ SavedItem.kt
│  ├─ Form.kt                 (PLAYLIST | SONG | DESTINATION)
│  ├─ HistoryStore.kt         (DataStore Preferences, JSON-encoded lists)
│  ├─ HistoryRepository.kt    (Flow<List<SavedItem>> per form; save/delete; mutex)
│  └─ ArtworkCache.kt         (byte downloader → cacheDir/artwork/<form>-<id>.jpg)
├─ bubble/
│  ├─ BubbleService.kt        (lifted)
│  ├─ BubbleController.kt     (lifted)
│  └─ BubbleView.kt           (lifted)
├─ di/ServiceLocator.kt       (lifted)
└─ ui/
   ├─ theme/                  (lifted)
   ├─ home/
   │  ├─ HomeScreen.kt        (2×2 grid + status pill overlay)
   │  ├─ BigAppButton.kt      (lifted)
   │  └─ StatusPill.kt        (new)
   ├─ lists/
   │  ├─ SavedListScreen.kt   (generic; parameterized by Form)
   │  ├─ SavedTile.kt         (artwork + title; long-press → delete)
   │  └─ PageIndicator.kt     (dots for HorizontalPager pages)
   ├─ status/StatusScreen.kt  (existing content, now a route)
   └─ permissions/
      ├─ PermissionGate.kt    (lifted, mic logic removed)
      └─ PermissionHelpers.kt (lifted, RECORD_AUDIO removed)
```

Deleted vs DriveDeck: `launch/VoiceSearch.kt`, `launch/YtMusicLauncher.kt`, all of `ui/manage/`, `ui/playlists/` (replaced by generic lists), `data/Playlist.kt`, `data/PlaylistRepository.kt`, `data/PlaylistStore.kt` (replaced by `history/`), `data/ArtworkFetcher.kt`'s scraping half (only the byte downloader is kept, simplified into `ArtworkCache`).

## Wire protocol — v3

**v2 (today):** `{v: 2, ts, cmd, url}` with `cmd ∈ {"ytmusic","waze"}`.

**v3:**

```json
{
  "v": 3,
  "ts": 1717336800,
  "cmd": "ytmusic",
  "form": "playlist",
  "url": "https://music.youtube.com/watch?list=OLAK5uy_...",
  "title": "Morning Drive",
  "imageUrl": "https://lh3.googleusercontent.com/..."
}
```

- `form ∈ {"playlist", "song", "destination"}` — always present.
- `title: String?` — null if `MetadataFetcher` failed on Pilot.
- `imageUrl: String?` — null on failure or always for destinations.
- `cmd` retained for backwards-compatible host-prefix validation; redundant with `form` but cheap.

**Validation** (`Message.parseEnvelope`):

| Check | On fail |
|---|---|
| `v == 3` | `Rejected("unknown schema v=$v")` |
| `form ∈ {playlist, song, destination}` | `Rejected("unknown form")` |
| `(cmd, form)` consistent — `ytmusic` ↔ `playlist|song`, `waze` ↔ `destination` | `Rejected("cmd/form mismatch")` |
| URL host-prefix per cmd (existing rule) | `Rejected("untrusted host")` |
| Clock skew within `MAX_MESSAGE_AGE_SEC` (existing) | `Rejected("stale (${skew}s)")` |

v2 messages are explicitly rejected — both apps must be on this commit's build.

## Data flow

### Inbound (Pilot → carbox)

```
Pilot tap (CatalogScreen tile OR ShareReceiverActivity)
   │
   ├─ MetadataFetcher.fetch() — AWAITED; on failure, title/imageUrl null
   ▼
NtfyPublisher.publish → v3 envelope
   │  (ntfy)
   ▼
NtfySubscriber.subscribe() collects line
   │
   ▼
Message.parseEnvelope → ParseResult.Accepted(msg)
   │
   ▼
ListenerService:
   1. AppLauncher.launch(msg) → Result.Ok / Failed
   2. if Ok:
        HistoryRepository.save(SavedItem.from(msg))    // no-op if (form,id) exists
        if msg.imageUrl != null:
            ArtworkCache.download(imageUrl, form, id)  // background, write-once
   3. ListenerService.state ← appendRecent(...)        // for StatusScreen / pill
```

Failed launches are **not** saved to history. They still show in recent-events.

### Outbound (user tap on the box)

```
HomeScreen tile:
   ├─ Waze       → AppLauncher.openWazeApp(); show bubble; moveTaskToBack
   ├─ Playlists  → nav("playlists")
   ├─ Songs      → nav("songs")
   ├─ Destinations → nav("destinations")
   └─ Status pill → nav("status")

SavedListScreen tile tap → AppLauncher.replay(item); show bubble; moveTaskToBack
   ├─ playlist    → ACTION_VIEW music.youtube.com/watch?list=…, package=YT Music
   ├─ song        → ACTION_VIEW music.youtube.com/watch?v=…, package=YT Music
   └─ destination → ACTION_VIEW <waze url>, package=Waze
SavedListScreen tile long-press → confirm dialog → HistoryRepository.delete(form, id)
```

Replay does **not** call `HistoryRepository.save` — only the inbound path saves. Already-saved items keep their original `savedAt`.

### Bubble lifecycle (unchanged from DriveDeck)

```
UI-initiated launch → BubbleController.requestShow(activity) → BubbleService.start()
overlay shown; bubble tap → MainActivity intent (EXTRA_SHOW_HOME=true)
on MainActivity resume → BubbleController.onActivityResumed → hide bubble
NavHost pops back to "home" via EXTRA_SHOW_HOME trigger
```

`ListenerService`-initiated launches **do not** show the bubble (no foreground activity to return to).

## Persistence

### SavedItem

```kotlin
@Serializable
data class SavedItem(
    val form: Form,          // PLAYLIST | SONG | DESTINATION
    val id: String,          // dedup key
    val title: String?,
    val imageUrl: String?,
    val url: String,         // exact URL to replay
    val savedAt: Long,       // epoch seconds; set once, never updated
)
```

**Dedup key by form:**
- `PLAYLIST` → YT list id from `watch?list=<id>`
- `SONG` → YT video id from `watch?v=<id>`
- `DESTINATION` → SHA-1 of the URL (Pilot already normalizes Waze URLs via `WazeUrlNormalizer`, so equal Waze destinations produce equal hashes on the box)

Copilot's `Form` enum mirrors Pilot's `Form.wire` values exactly: `"playlist"`, `"song"`, `"destination"`. These same strings serve as the JSON `form` field on the wire and the `<form.wire>` prefix in `ArtworkCache` file paths.

### HistoryStore

Single DataStore Preferences file (`copilot_history.preferences_pb`); one key per form (`saved_playlists`, `saved_songs`, `saved_destinations`); value is JSON-encoded `List<SavedItem>` via kotlinx.serialization.

### HistoryRepository

- `itemsFor(form): Flow<List<SavedItem>>` — sorted by `savedAt` desc.
- `save(item)`: if `(form, id)` already exists, no-op (preserves original `savedAt`). Otherwise prepend.
- `delete(form, id)`: remove the entry; non-existent delete is a no-op.
- All mutations serialized through a single `Mutex` to prevent concurrent-write loss.

### ArtworkCache

- File path convention: `cacheDir/artwork/<form.wire>-<id>.jpg`.
- `download(imageUrl, form, id)`: write-once. If the target file exists, skip the HTTP call. Failure = no file; no retry.
- Tiles look up artwork as a file; render a placeholder if absent (no async UI fetch from the tile itself).

### Bubble position

Separate preferences file (`copilot_bubble.preferences_pb`), exactly as DriveDeck has today. Not mixed with history.

## UI behavior

### Home (`route: home`) — landscape, `sensorLandscape`

- 2×2 grid, equal-weight cells, ~16dp padding.
- **Top-left:** Waze tile — actual `PackageManager.getApplicationIcon("com.waze")` + label "Waze". Tap → `AppLauncher.openWazeApp()`.
- **Top-right:** Playlists tile (label "Playlists"). Tap → `nav("playlists")`.
- **Bottom-left:** Songs tile (label "Songs"). Tap → `nav("songs")`.
- **Bottom-right:** Destinations tile (label "Destinations"). Tap → `nav("destinations")`.
- **Status pill** anchored top-right of the screen (layered above the grid in a `Box`): colored dot (green/amber/red per `ConnState`) + last-event `HH:mm` if any. ~48dp tall. Tap → `nav("status")`.

### Saved list (`route: playlists | songs | destinations`)

- Top bar: big back-to-home chip (left, ~64dp tall, arrow + "Home" label), screen title (center).
- Body: `HorizontalPager`, one page per group of 6 items; each page is a static 2×3 grid. `PageIndicator` (dots) below the pager.
- `SavedTile`: 1:1 artwork on top (placeholder if no cached file), title underneath (2 lines max, ellipsis). Tap → replay. Long-press → confirm dialog ("Remove '<title>'?") → delete.
- Empty state: centered text — "Send a [playlist/song/destination] from Pilot to fill this list".
- Destination placeholder: generic map-pin vector drawable.

### Status (`route: status`)

- Big back-to-home chip top-left.
- Existing `StatusScreen` content verbatim: conn dot + label, clock skew, recent events list, topic suffix.

### Bubble

- Behavior unchanged from DriveDeck.
- Shown on `MainActivity.onPause` after a UI-initiated launch; hidden on `MainActivity.onResume`.
- Tap → reopens MainActivity with `EXTRA_SHOW_HOME=true` → NavHost pops to `home`.
- Position persisted; drag to reposition.

### Permission gate

- Lifted from DriveDeck, with mic checks removed.
- Required perms re-checked on every cold start. Gate blocks the home route until granted: `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`. Battery-unrestricted is a hint (informational, not blocking) since it's user-driven from system settings.

## Error handling

| Condition | Behavior |
|---|---|
| Network drop / ntfy disconnect | Existing `NtfySubscriber` reconnect logic; `ConnState` flips amber/red; status pill mirrors. |
| v2 envelope received | `Rejected("unknown schema v=2")`; red recent-events row; service stays subscribed. |
| Missing/invalid `form`, or `cmd/form` mismatch | `Rejected` with explanatory reason; logged in recent events. |
| Launch fails (app not installed) | `Result.Failed("...not installed")`; red recent row; **not saved to history**. |
| Launch fails (BAL `SecurityException`) | `Result.Failed("background launch blocked — grant Display over other apps")`; red recent row; **not saved**. |
| Pilot metadata fetch fails | Send anyway with nulls; saved tile shows "Untitled · <id>" + placeholder artwork. |
| Artwork download fails | No file written; tile shows placeholder forever; no retry-on-view. Resend from Pilot to retry (only effective if URL differs). |
| Stale URL on replay (playlist deleted on YT Music) | `AppLauncher` runs fallback chain (watch → playlist → generic). User lands somewhere in YT Music; nothing throws. |
| `HistoryStore` JSON corruption | `runCatching` → empty list; next save overwrites. Logged via `Log.w`. |
| Concurrent saves | Serialized through `HistoryRepository`'s `Mutex`. |
| `BubbleService` killed | `START_STICKY` re-creates; controller re-requests on next activity pause. |
| Two foreground services running | `ListenerService` (dataSync) + `BubbleService` (specialUse) coexist on Android 14; documented as expected in ONBOARDING. |

## Testing

JUnit4 + okhttp `mockwebserver` + Robolectric where DataStore is involved. Unit-first.

**`net/MessageTest`** (extends existing):

- v3 envelope with all fields accepted.
- v2 envelope rejected with `"unknown schema v=2"`.
- Missing `form` rejected.
- `(cmd=ytmusic, form=destination)` and symmetric mismatch rejected.
- `imageUrl` absent/null → `Message.imageUrl == null`, no rejection.

**`history/HistoryRepositoryTest`** (Robolectric):

- `save` → `itemsFor(form).first()` round-trips.
- Duplicate `(form, id)` does not duplicate and does not reshuffle; original `savedAt` preserved.
- Newest `savedAt` first.
- `delete` removes; non-existent delete is no-op.
- Three forms isolated from each other.
- Concurrent saves via two coroutines both persist (mutex test via Turbine).

**`history/ArtworkCacheTest`** (mockwebserver):

- `download` writes the expected file.
- Pre-existing file → no HTTP call.
- 4xx/5xx → no file, no exception.

**`launch/AppLauncherTest`** (Robolectric):

- Playlist / song / Waze nav / open-Waze-app intent chains.
- Same launcher behaves identically when called from `ListenerService` vs UI.
- BAL `SecurityException` → `Result.Failed("background launch blocked …")`.

**`service/ListenerServiceLaunchTest`** (Robolectric):

- Accepted v3 message → launch invoked → history saved.
- Failed launch → history **not** saved.
- `imageUrl != null` → `ArtworkCache.download` invoked once.

**Pilot side:**

- `NtfyPublisherTest`: v3 fields present for each `(form, hasTitle, hasImageUrl)` combination.
- `MetadataFetcherTest`: `refresh()` persists `imageUrl` on the store.
- `CatalogStoreTest`: round-trip `imageUrl`.

**Manual checklist (added to ONBOARDING)**:

- Bubble overlay renders and drags correctly on the carbox.
- Status pill visibility and colors.
- Two-FGS coexistence shows two persistent notifications without issue.
- End-to-end: Pilot tap → carbox tile appears in the list → tap → music plays.

## Migration & build plan

### Copilot repo — single commit contents

1. Build config: `gradle/libs.versions.toml` adds `navigation-compose`, `datastore-preferences`, `kotlinx-serialization-json` + plugin, `lifecycle-viewmodel-compose`, `lifecycle-service`, `material-icons-extended`. `app/build.gradle.kts` enables `buildConfig = true` and the `kotlin-serialization` plugin.
2. Manifest: add `FOREGROUND_SERVICE_SPECIAL_USE`; remove `RECORD_AUDIO` (not added); add `<queries>` entries for https VIEW and `MEDIA_PLAY_FROM_SEARCH`; register `BubbleService` with `foregroundServiceType="specialUse"`; rename `StatusActivity` → `MainActivity` and set `singleTask`, `sensorLandscape`, `configChanges`.
3. Lift from DriveDeck (repackaged from `com.georgian.drivedeck.*` → `com.vladutu.copilot.*`): `bubble/`, `di/ServiceLocator.kt`, `launch/PlaylistIdParser.kt`, `ui/theme/`, `ui/home/BigAppButton.kt`, `ui/permissions/` (mic logic stripped).
4. New code: `history/{SavedItem, Form, HistoryStore, HistoryRepository, ArtworkCache}`, `ui/home/{HomeScreen, StatusPill}`, `ui/lists/{SavedListScreen, SavedTile, PageIndicator}`, `ui/status/StatusScreen.kt` (existing content), `MainActivity.kt` (NavHost host).
5. Modified: `net/Message.kt` (v3 schema), `service/ListenerService.kt` (save-on-launch hook), `launch/AppLauncher.kt` (fused).
6. ONBOARDING.md: v3 schema note, two-FGS notification note, revert recipe.

### Pilot repo — single commit contents

1. `catalog/CatalogEntry.kt`: add `imageUrl: String?`.
2. `catalog/CatalogStore.kt`: round-trip the new field.
3. `meta/MetadataFetcher.kt`: persist `imageUrl` on `refresh()`.
4. `net/NtfyPublisher.kt`: v3 envelope with `form`, `title`, `imageUrl`.
5. Call-site updates in `ui/CatalogScreen.kt` and `share/ShareReceiverActivity.kt`: await `MetadataFetcher` before publish.
6. Tests.

### Sanity before committing

- `./gradlew :app:assembleDebug` passes in both repos.
- `./gradlew :app:testDebugUnitTest` passes in both repos.

### Revert recipe (ONBOARDING)

> If anything breaks on the box: `git reset --hard HEAD~1` in both `Copilot/` and `Pilot/`, rebuild, sideload both. Both schemas revert together (v2 ↔ v2).

### Order of work (matches review-friendly diff)

1. Lift DriveDeck files into Copilot under new packages (no behavior change; won't compile until step 5).
2. Add `history/` package and `ArtworkCache`.
3. Bump `Message` to v3; update `ListenerService` save hook; fuse `AppLauncher`.
4. Build new UI (`HomeScreen`, `SavedListScreen`, `StatusPill`); wire NavHost in `MainActivity`.
5. Pilot side: persist + publish v3.
6. Tests across both.
7. Run, verify, single commit per repo.

## Open questions

None at design time. Implementation may surface small UI polish decisions (exact tile dimensions, color tokens for the status pill) that don't need spec changes.
