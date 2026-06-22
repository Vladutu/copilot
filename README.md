# Copilot

The **receiver** half of the Pilot/Copilot car-remote system. Copilot runs on the
car's Android head unit; [Pilot](../Pilot) runs on your phone. Pilot publishes
commands over [ntfy](https://ntfy.sh); Copilot subscribes, validates each message,
and launches the right app — YouTube Music, Waze/Maps, or a VLC radio stream —
then quietly brings itself back to the foreground so the next command is one tap
away. The on-screen UI is built for driving: a knob-navigable grid, no typing.

```
  Phone (Pilot)                 ntfy.sh                 Car box (Copilot)
  ─────────────                 ───────                 ─────────────────
  publish JSON ───────────────► topic  ───────────────► subscribe ──► launch app
                                                         + save history
                                                         + auto-return to home
```

## What it does

- **Receives & launches.** A long-lived ntfy stream (`ListenerService`) parses each
  message, validates it against the v3 schema (TTL + host allowlist), and routes it
  to `AppLauncher`, which opens YT Music / Waze / Maps / VLC.
- **History** of everything launched — playlists, songs, destinations, radio —
  searchable and tap-to-replay, with cached artwork.
- **Discover.** Pilot-authored keyword categories (synced over ntfy as
  `cmd=category`). Browse matching playlists/songs or fire an instant radio mix,
  powered by **NewPipe Extractor** behind a `MusicSearcher` boundary.
- **Top Weekly.** One tap fetches the US + GB weekly charts in parallel,
  rank-interleaves them, and mints an anonymous queue playlist.
- **Liked Songs.** Mark the now-playing track (read from the YT Music media
  session) to a local, text-only list — heart icon on the Home header, dedicated
  Liked screen.
- **Auto-return & overlay.** After launching music/nav, `AutoSwitchBack` brings
  Copilot back once playback settles; a floating bubble (`BubbleService`) and an
  accessibility service (`BackGrabberService`, intercepts the hardware BACK key)
  keep Copilot reachable.
- **Status screen** — connection health, recent events, clock skew vs. the phone,
  downloadable diagnostic logs, and grant buttons for missing permissions.

## Permissions

Copilot needs a few **powerful permissions** to act as a hands-free car remote.
They're listed in full here for transparency. Three of them must be enabled **by
hand in Android Settings** — Android won't let an app grant these itself — and the
app silently does nothing if they're missing:

| Permission | Enable via | Why Copilot needs it | If you skip it |
|---|---|---|---|
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Prompted automatically the first time you open the app | Launch other apps from the background and show the floating "return to Copilot" bubble | App launches and the overlay bubble silently do nothing |
| **Notification access** (`NotificationListenerService`) | In-app: **Settings → Enable now-playing access** | Read the now-playing title/artist from YT Music's media session (Liked Songs, history artwork) | Now-playing stays blank; Liked Songs can't capture the current track |
| **Accessibility service** (`BackGrabberService`) | In-app: **Settings → Enable auto-return**, then flip the toggle in Android's Accessibility list | Intercept the hardware BACK key so Copilot **auto-returns to the foreground** after a song/route starts | No automatic return — you're left in YT Music / Waze after each command |

> Re-enable the **accessibility service** after every reinstall — Android's
> force-stop disables it, and there are no logs when it's off (it just looks broken).

The rest are standard permissions, granted automatically at install / first run:

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Subscribe to the ntfy command stream |
| `FOREGROUND_SERVICE` (+ `_DATA_SYNC`, `_SPECIAL_USE`) | Keep the ntfy listener and the overlay bubble alive in the background |
| `POST_NOTIFICATIONS` | Show the persistent foreground-service notification (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Optional auto-start on boot (off by default; toggle in Settings) |

Also set **Battery optimization → Unrestricted** so Android doesn't kill the
listener. See [First-time carbox setup](#first-time-carbox-setup) for the
step-by-step grant order.

## Tech stack

Kotlin · Jetpack Compose (Material 3) + Navigation Compose · OkHttp (ntfy
subscriber + NewPipe downloader bridge) · DataStore + kotlinx.serialization
(history, liked, categories) · **NewPipe Extractor** (JitPack) for YouTube search
— not the official API · Android `MediaSessionManager` /
`NotificationListenerService` (now-playing) and `AccessibilityService` (BACK
interception). Tests use JUnit + Robolectric + OkHttp MockWebServer + Turbine.
Exact versions live in `gradle/libs.versions.toml` (kept current by Dependabot).

## Architecture

`CopilotApp` initializes crash logging, the notification channel, and the
diagnostic log; `ServiceLocator` wires the lazy singletons. `MainActivity` hosts
the Compose `NavHost` (Home → Music submenu → Discover / Liked / lists / Status).

Three foreground/system services do the work:

| Service | Type | Role |
|---------|------|------|
| `ListenerService` | `dataSync` | Long-lived ntfy stream; parses, validates, routes to launcher/category store; publishes connection `UiState`. |
| `BubbleService` | `specialUse` | Floating "return to Copilot" button, shown when backgrounded. |
| `MediaListenerService` | NotificationListener | Reads now-playing title/artist from the YT Music media session. |
| `BackGrabberService` | AccessibilityService | Intercepts hardware BACK, drops the carbox's synthetic duplicate, refocuses Copilot, feeds foreground state to `AutoSwitchBack`. |

| Package | Responsibility |
|---------|----------------|
| `net` | `Message` (v3 schema, TTL, host allowlist), `NtfySubscriber` (stream + backoff). |
| `launch` | `AppLauncher` — intents for YT Music / Waze / Maps / VLC, arms auto-switch. |
| `history` | `HistoryRepository`, `SavedItem` (form-keyed), `ArtworkCache`. |
| `liked` | `LikedSongsRepository`, `LikedSong` (local text-only list). |
| `nowplaying` | `MediaListenerService`, `NowPlaying`, pure `nowPlayingFrom()` mapper. |
| `discover` | `MusicSearcher` boundary, `DiscoverRepository`, `newpipe/NewPipeMusicSearcher`. |
| `charts` | `ChartsRepository` / Top Weekly merge + `TempPlaylistMinter`. |
| `autoswitch` | `AutoSwitchBack` — pure state machine (arm → settle → restore/abort). |
| `bubble` / `back` | Overlay controller + accessibility BACK handling. |
| `diagnostics` `config` `di` `ui/*` | Logging, constants (ntfy topic, max age), DI, Compose screens. |

### Wire protocol (v3)

Inner JSON (inside the ntfy envelope):

```json
{ "v": 3, "ts": 1718000000, "cmd": "ytmusic|waze|maps|radio|category",
  "form": "song|playlist|destination|radio|category",
  "url": "…", "title": "…", "imageUrl": "…" }
```

Validation rejects anything that isn't schema v3, is older than 30s (`ts` check
defends against ntfy's ~12h replay cache), or whose URL host isn't allowlisted
(`music.youtube.com`, `ul.waze.com`, `maps.google.com`, `maps.app.goo.gl`, http(s)
radio). The topic in `config/Config` **must match Pilot**.

## Build & run

```bash
make check               # assembleDebug + testDebugUnitTest + lintDebug
make version             # latest released tag
make release V=0.3.0     # test → signed build → tag → GitHub release
```

### First-time carbox setup

See **[`ONBOARDING.md`](ONBOARDING.md)** for the full walkthrough. The critical,
silent-failure-prone grants:

1. Sideload and **open the app once** (clears Android's stopped state).
2. **Display over other apps** (`SYSTEM_ALERT_WINDOW`) → Allow — without this the
   launcher/overlay silently does nothing.
3. **Notification access** for `MediaListenerService` (now-playing / Liked).
4. **Accessibility service** enabled for BACK handling (re-enable after every
   reinstall — force-stop disables it).
5. Battery optimization → **Unrestricted**.
6. Reboot; the status dot turns green within ~10s.

Declared permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`
(+`_DATA_SYNC`, `_SPECIAL_USE`), `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, plus
the bound accessibility and notification-listener services.

> **Signing:** the release keystore + `signing.properties` are **local-only and
> gitignored** — run `scripts/setup-signing.sh` once to generate them (a keyless
> clone still builds, falling back to debug signing). `scripts/release.sh` runs
> tests → builds the signed APK → tags → publishes the GitHub release; versionCode
> is `MAJOR*10000 + MINOR*100 + PATCH`. See [`docs/RELEASING.md`](docs/RELEASING.md)
> and the [GitHub setup checklist](docs/superpowers/github-setup-checklist.md).

CI (`.github/workflows/ci.yml`) runs `make check` on PRs/`master` with Dependabot
patch/minor auto-merge.

## Layout

```
Copilot/
├── ONBOARDING.md  Makefile
├── app/
│   ├── build.gradle.kts           # namespace com.vladutu.copilot
│   └── src/
│       ├── main/java/com/vladutu/copilot/
│       │   ├── CopilotApp.kt  MainActivity.kt
│       │   ├── net/ service/ launch/ history/ liked/ nowplaying/
│       │   ├── discover/ charts/ autoswitch/ bubble/ back/
│       │   ├── diagnostics/ config/ di/ ui/
│       │   └── res/xml/accessibility_back_grabber.xml
│       └── test/java/com/vladutu/copilot/   # Message, CategoryStore, charts, knob nav…
├── scripts/                       # release.sh, version.sh, bootstrap-wrapper.sh
├── keystore/                      # signing.properties.template (real key is gitignored, local-only)
├── docs/                          # RELEASING.md, github-setup-checklist.md, plans & specs
└── .github/                       # ci.yml, dependabot.yml
```

## Related

- [Pilot](../Pilot) — the phone app that sends commands.
