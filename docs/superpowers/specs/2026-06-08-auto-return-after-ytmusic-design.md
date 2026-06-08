# Auto-return after YouTube Music launch

**Date:** 2026-06-08
**App:** Copilot (`com.vladutu.copilot`)
**Status:** Approved design, ready for implementation plan

## Problem

When Copilot launches YouTube Music for a song or playlist, YT Music comes to the
foreground and stays there. The driver then has to manually get back to where they
were (tapping the bubble or pressing BACK). This happens via two triggers that are,
functionally, the same event:

1. **In-app selection** — the driver taps a saved song/playlist inside Copilot. After
   YT Music loads, they want to be returned to Copilot.
2. **ntfy event from Pilot** — Pilot publishes a song/playlist event. After YT Music
   loads, the driver wants to be returned to the app they were using before.

Both reduce to: *after YT Music has actually loaded, restore whatever app was in the
foreground beforehand.* In case 1 that app is Copilot itself; in case 2 it is whatever
was on screen when the event arrived. One mechanism covers both.

## Scope

In scope:
- `cmd = ytmusic` with `form ∈ {song, playlist}`, from both the in-app replay path and
  the ntfy listener path (both already converge on `AppLauncher.launch`).

Out of scope (deliberately unchanged):
- **Radio (VLC)** — plays in the background, never takes the foreground, nothing to
  return from.
- **Maps / Waze (destination)** — navigation should stay in the foreground; returning
  would defeat the purpose.

## Decisions (locked)

- **Switch-back target:** restore *whatever app was in the foreground* before YT Music
  opened. No special-casing — in-app naturally yields Copilot, ntfy yields the prior app.
- **User override:** if the driver has manually navigated away during the brief settle
  window (foreground is no longer YT Music when the switch-back is about to fire), the
  switch-back is **aborted**. Manual intent wins.
- **Settle delay:** `SETTLE_MS = 1200`. Long enough for YT Music to process the deep
  link and begin playback before focus is pulled (mirrors the timing sensitivity already
  observed with Waze deep-links); short enough to feel responsive. Single named constant.
- **Arm timeout:** `ARM_TTL_MS = 8000`. If YT Music never appears (not installed / launch
  failed), the armed state self-expires so a later unrelated YT Music launch does not
  trigger a stray switch-back.
- **Existing bubble / `moveTaskToBack` flow is left untouched.** Auto-return layers on
  top. For in-app song taps the bubble may flash briefly before Copilot returns; this is
  cosmetic and acceptable.

## Architecture

One mechanism, hosted in the existing accessibility service. The service already
subscribes to `typeWindowStateChanged` (`accessibility_back_grabber.xml:3`) but its
`onAccessibilityEvent()` is currently a no-op (`BackGrabberService.kt:43`) — this design
fills it in.

### Why the target is snapshotted at arm-time

The naive approach — "remember the last foreground package, restore it after YT Music
shows" — has a race: Copilot's **bubble overlay** belongs to Copilot's own package and
can emit `TYPE_WINDOW_STATE_CHANGED` after YT Music launches. That would overwrite the
real previous app (e.g. Waze) with Copilot just before the switch-back fires.

The fix: capture the target **at the moment of arming**, when the genuine foreground is
still the previous app (YT Music hasn't launched and no overlay has appeared yet). The
captured value is then immune to later overlay/intermediate window events. This also
keeps `AutoSwitchBack` a pure-JVM object (no Android dependencies) so its logic is unit
testable; the accessibility service is the only Android-touching piece.

### Components

- **`AutoSwitchBack` (new process-wide singleton, pure Kotlin — no Android imports)**
  - State (all `@Volatile`): `currentForeground: String?`, `armedAtMillis: Long?`,
    `targetPackage: String?`, plus `ownPackage: String?` (Copilot's own package id, set
    once by the service so the debug `.debug` suffix is handled) and an injectable
    `clock: () -> Long` (defaults to `System.currentTimeMillis`) for deterministic tests.
  - API:
    - `onForeground(pkg)` — record the latest foreground package (fed by the service).
    - `arm()` — snapshot `targetPackage` = current foreground (null if it's null or YT
      Music itself), and record `armedAtMillis = clock()`.
    - `isArmed()` — armed and within `ARM_TTL_MS`.
    - `shouldScheduleOnYtMusicShown()` — `isArmed() && targetPackage != null`.
    - `resolveTargetAtFire()` — returns the package to restore, or null to abort. Aborts
      when the user has moved to a **third** app: foreground is non-null and is neither
      YT Music, nor `ownPackage` (so the Copilot-overlay reading is not treated as "user
      moved"), nor the target itself (so calling resolve while still on the target — before
      YT Music's window appears — is not treated as "user moved").
    - `disarm()` — clear `armedAtMillis` and `targetPackage`.

- **`BackGrabberService` (extended `onAccessibilityEvent`, currently a no-op)**
  - On each `TYPE_WINDOW_STATE_CHANGED` with a non-null `packageName`:
    1. `AutoSwitchBack.onForeground(pkg)`.
    2. If `pkg == YT_MUSIC_PKG` and `AutoSwitchBack.shouldScheduleOnYtMusicShown()` and no
       switch-back is already pending: set a pending flag and `handler.postDelayed(SETTLE_MS)`.
  - On fire: read `AutoSwitchBack.resolveTargetAtFire()`, `disarm()`, clear pending flag,
    and if non-null call `restoreApp(target)`.
  - `restoreApp(pkg)`:
    - `pkg == applicationContext.packageName` → `Intent(this, MainActivity::class.java)`.
    - else → `packageManager.getLaunchIntentForPackage(pkg)`; if null, log and stop
      (driver stays in YT Music — see Known limitation).
    - add `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_NEW_TASK`, `startActivity`.
  - `onServiceConnected` sets `AutoSwitchBack.ownPackage = applicationContext.packageName`.
  - The accessibility service has broad package visibility, so `getLaunchIntentForPackage`
    resolves arbitrary apps (not just the four in `<queries>`).
  - Heavy `DiagnosticLog` usage, consistent with the rest of this service.

- **`AppLauncher` (one call)**
  - In `launchUrl`, immediately before `startActivity()`, call `AutoSwitchBack.arm()`
    **only** when `cmd == "ytmusic"` (which, per `Message` validation, always implies
    `form ∈ {song, playlist}`). Radio/maps/waze do not arm.

### Data flow

```
[in-app tap on saved song]          [ntfy song/playlist event]
        │                                   │
        └──────────► AppLauncher.launchUrl(cmd=ytmusic, …) ◄──────────┘
                            │
                            ├─ AutoSwitchBack.arm()   (snapshots current foreground as target)
                            └─ startActivity(YT Music)
                                        │
       YT Music window appears ──► BackGrabberService.onAccessibilityEvent
                                        │   onForeground(ytmusic)
                                        │   if shouldScheduleOnYtMusicShown() && !pending:
                                        │       postDelayed(SETTLE_MS):
                                        │          target = resolveTargetAtFire()  // null if user moved to a 3rd app
                                        │          disarm()
                                        │          if target != null: restoreApp(target)
```

## Known limitations / on-device risks

1. **Null launch intent:** if `getLaunchIntentForPackage` returns null for the previous app
   (rare — e.g. a launcher with no exported launch intent), the service disarms and does
   nothing; the driver stays in YT Music. Logged via `DiagnosticLog`.

2. **Transient system window during the settle delay (watch for this):** if a system window
   such as the volume HUD (`com.android.systemui`) emits a `TYPE_WINDOW_STATE_CHANGED` in the
   ~1.2s after YT Music appears — plausible when playback starts and changes volume — then
   `currentForeground` reads as that system package and `resolveTargetAtFire()` treats it as
   "user moved to a third app", producing a **false abort**. Whether this fires is specific to
   the carbox, so it is left un-hardened for the first on-device run; the abort log prints the
   offending foreground package to make it diagnosable in one run.
   **Ready mitigation if observed:** in `BackGrabberService.onAccessibilityEvent`, skip
   `AutoSwitchBack.onForeground(pkg)` for known non-app system packages (e.g. ignore `pkg`
   starting with `com.android.systemui`), so transient HUDs don't overwrite the real
   foreground. Keep YT Music and Copilot's own package always tracked.

## Testing

- **Unit (pure JVM):** `AutoSwitchBack` arm / expiry (`ARM_TTL_MS`) / disarm logic.
- **Unit (pure JVM):** the launch-path predicate — arms only for `ytmusic` +
  `song|playlist`, never for radio/maps/waze. Fits the existing `AppLauncher` /
  `NtfyPublisher` test style.
- **Manual on-device (carbox):** window-event detection, settle timing, abort-if-moved,
  and the actual foreground switch for both the in-app and ntfy triggers. The
  `AccessibilityService` behavior cannot be meaningfully unit-tested; verified via
  `DiagnosticLog` on the box.
