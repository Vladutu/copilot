# Liked Songs — capture the currently-playing song to a local list

**Date:** 2026-06-14
**Scope:** Copilot only. Pilot and the ntfy wire protocol are untouched.

## Problem

While a shuffled playlist plays in YouTube Music, the driver hears a song they
like and wants to remember it for later. Today there is no way to capture it
without unlocking the phone and fumbling in YT Music while driving.

## Goal

A **local "Liked" list inside Copilot** holding just **title + artist** of songs
the user marks while they play. The list is:

- separate from every other list (history playlists/songs/destinations/radio),
- **not playable** — it is a memo, not a queue,
- manageable: delete one entry, or clear the whole list.

Explicitly **out of scope** (decided during brainstorming):
- No playback from the Liked list.
- No re-resolving title→videoId, no NewPipe, no artwork.
- No real YouTube Music playlist editing (would need Google OAuth + Data API —
  rejected as against the no-accounts spirit of these apps).

## Key technical fact

The music plays inside YouTube Music, not inside Copilot. The only thing
observable from outside is the **active media session's metadata**
(`MediaMetadata.METADATA_KEY_TITLE`, `METADATA_KEY_ARTIST`), read via a
`NotificationListenerService` + `MediaSessionManager.getActiveSessions(...)`.
This metadata is readable **even while Copilot is in the foreground**, because
YT Music keeps playing in the background — which is exactly why the "switch back
to Copilot, then like" flow works. There is no reliable way to get the YouTube
`videoId` of the playing track, which is why the list stores text only.

## User flow

1. A liked song is playing in YT Music (foreground).
2. Driver knob/BACKs over to Copilot (existing BackGrabber path; the bubble's
   role is unchanged — it just returns to Copilot).
3. Copilot's Home header shows the live `♪ Title — Artist  [♥]`. Selecting the
   heart appends the song to the Liked list. A short confirmation toast fires.
4. Later, the driver opens **Music → … → Liked** (last tile) to review the list
   and delete entries or clear all.

## Capture mechanism

New `MediaListenerService : NotificationListenerService`:

- Requires the one-time **Notification access** special grant
  (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).
- On connect, registers a `MediaSessionManager.OnActiveSessionsChangedListener`
  and, for the active **YouTube Music** controller
  (`com.google.android.apps.youtube.music`), a `MediaController.Callback` to
  track metadata changes.
- Publishes a process-scoped `StateFlow<NowPlaying?>` (mirrors the existing
  `ListenerService.state` companion-flow pattern). `null` = nothing playing /
  no YT Music session / permission not granted.
- Filtered to YT Music only, so radio (VLC) and other audio don't leak in.

`NowPlaying(title: String, artist: String?)` — `artist` null when YT Music
doesn't supply one.

**Permission is NOT a hard gate.** Unlike the overlay permission (which the
bubble needs and which `PermissionGate` blocks the whole app on), Notification
access is optional: if ungranted, `NowPlaying` stays `null`, the header element
never appears, and the feature is simply dormant. A grant entry point lives in
the **Status screen** (reached via the ntfy pill) — a row that, when access is
missing, offers a button to open notification-listener settings.

## Storage — independent of history

- `LikedSong(title: String, artist: String?, savedAt: Long)` — `@Serializable`.
- `LikedSongStore` — its own DataStore (`copilot_liked`), single string key,
  JSON-encoded list. Mirrors `HistoryStore`'s decode/mutate shape but has no
  `Form` keying and is entirely separate from `copilot_history`.
- `LikedSongsRepository`:
  - `items(): Flow<List<LikedSong>>` — sorted by `savedAt` descending.
  - `like(now: NowPlaying)` — **upsert/dedup** by `(title, artist)` compared
    case-insensitively/trimmed; a repeat like refreshes `savedAt` (same upsert
    behavior `HistoryRepository.save` already uses) rather than adding a row.
  - `delete(song: LikedSong)`.
  - `clearAll()`.
- Wired in `ServiceLocator` as `likedSongsRepository` with a `copilot_liked`
  DataStore, alongside the existing stores.

## UI changes

### Home header (`HomeScreen.kt`)

- The header `Row` currently holds only `StatusPill` (flush right). Add the
  now-playing chunk on the **left** of that row: `♪ Title — Artist` as a single
  line, **ellipsized** when too long, followed by a heart button.
- The entire chunk is **hidden when `NowPlaying` is null**.
- Knob navigation: the heart is the **last stop**. `TILE_COUNT` becomes dynamic:
  4 when nothing is playing, 5 when a song is playing (heart = index 4).
  - Default focus stays index 0 (Waze).
  - Left-twist from Waze still clamps at Waze — it never reaches the heart.
  - The heart is reachable only by twisting right past Music (index 3 → 4).
  - When the song stops (count drops 5→4) while focus is on the heart, clamp
    focus back to the last tile.
- The heart is also touch-tappable.
- `HomeScreen` gains `nowPlaying: NowPlaying?` and `onLike: () -> Unit` params.

### Music submenu (`MusicScreen.kt`)

- Add a **"Liked" tile as the LAST tile** (index 5). `TILE_COUNT` 5 → 6, which
  conveniently fills the empty 6th slot in the existing 3-column grid (row 2
  becomes Discover / Radio / Liked).
- New `onOpenLiked: () -> Unit` param.

### Liked list screen (`LikedSongsScreen.kt`, new)

- Mirrors `SavedListScreen` structure: `ScreenHeader` + `KnobPagedGrid` of
  tiles showing `title` (primary) and `artist` (secondary). Empty-state text
  when the list is empty.
- Per-item delete: long-press → confirm `AlertDialog` (same pattern as
  `SavedListScreen`).
- **Clear all**: a touch button in the header area with its own confirm dialog.
- No tap-to-play (the tile tap is a no-op or simply not wired to a launch).

### Navigation (`MainActivity.kt` `CopilotNav`)

- `home` composable: collect `MediaListenerService.nowPlaying`, pass it +
  `onLike` (calls `app.applicationScope.launch { likedSongsRepository.like(...) }`
  then shows a "Saved" toast) into `HomeScreen`.
- `music` composable: pass `onOpenLiked = { nav.navigate("liked") }`.
- New `liked` route → `LikedSongsScreen`, wired to `likedSongsRepository`
  (`items`, `delete`, `clearAll`).

### Manifest

- Register `MediaListenerService` with
  `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the
  `android.service.notification.NotificationListenerService` intent-filter.

### Strings

- New `strings.xml` entries: Liked tile label, Liked screen title, empty-state,
  clear-all label + confirm dialog title/message, "Saved" toast, now-playing
  access prompt (Status screen).

## Error / edge handling

- Like with nothing playing: impossible — heart isn't shown and isn't a stop.
- Missing artist: show and store title only (`artist == null`).
- Duplicate like: upsert refreshes `savedAt`, no duplicate row.
- Notification access ungranted: feature dormant (header element absent);
  grantable from Status screen.
- Corrupt stored JSON: `LikedSongStore.decode` resets to empty (same as
  `HistoryStore`).

## Testing (JVM unit tests; no Android SDK on the Linux dev box — Georgian
builds/tests on his Mac)

- `LikedSongStore` / `LikedSongsRepository`: add, dedup-by-(title,artist),
  savedAt refresh on re-like, delete, clearAll, descending sort.
- Dynamic knob-count arithmetic for Home (4↔5, clamp on song stop while focused
  on heart).
- `MediaMetadata` → `NowPlaying` mapping, including missing-artist.
- `MediaListenerService` system wiring is thin and verified on-device.

## Components summary

| Component | Type | Responsibility |
|---|---|---|
| `NowPlaying` | data class | title + nullable artist of the live track |
| `MediaListenerService` | NotificationListenerService | read YT Music media session → `StateFlow<NowPlaying?>` |
| `LikedSong` | @Serializable data class | persisted entry (title, artist, savedAt) |
| `LikedSongStore` | DataStore wrapper | JSON list in `copilot_liked`, decode/mutate |
| `LikedSongsRepository` | repository | items/like(upsert+dedup)/delete/clearAll |
| `HomeScreen` (edit) | composable | now-playing header chunk + heart as last knob stop |
| `MusicScreen` (edit) | composable | Liked tile last |
| `LikedSongsScreen` | composable | review list, per-item delete, clear all |
| `CopilotNav` (edit) | composable | wire nowPlaying/onLike, `liked` route |
| `StatusScreen` (edit) | composable | grant entry for Notification access |
