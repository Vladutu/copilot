# Auto-start on boot, behind a toggle — design

**Date:** 2026-06-21
**App:** Copilot (carbox)
**Status:** approved design, pending spec review

## Goal

Bring back auto-start on carbox boot — which was removed in commit `97f241f` — but
put it behind a user toggle that defaults **off**, and rebuild the boot path in a
leaner, crash-resistant way. While here, introduce a dedicated **Settings** screen and
move the config/admin actions off the **Status** screen so Status becomes a pure
read-out.

## Background

- The original feature (`61979af`) launched `MainActivity` on `BOOT_COMPLETED` /
  `QUICKBOOT_POWERON` by bouncing through a one-shot foreground service
  (`BootLaunchService`, type `dataSync`) to satisfy Background Activity Launch (BAL).
- That commit's own message noted *"BAL is satisfied by the existing
  `SYSTEM_ALERT_WINDOW` grant"* — i.e. the foreground service was defensive, not
  required. Copilot already requires "Display over other apps" (enforced by
  `PermissionGate`), and that grant is itself a BAL exemption.
- It was removed (`97f241f`) as a deliberate simplification. Separately, the user
  observed the carbox *sometimes* crashed and rebooted at power-on and suspected (but
  never confirmed) the auto-boot. Because it *worked* most boots and only *sometimes*
  rebooted the whole box, the likely cause is a boot-time resource race / fragile
  headunit firmware under load at `t=0` — not a deterministic code fault. This is
  unconfirmed.

## Design decisions (settled)

| Decision | Choice |
|---|---|
| Toggle default on fresh install | **Off** (opt-in) |
| Boot behavior | **Pop the full UI to the foreground**, immediately (no defer) |
| Settings entry point | **Gear icon on the Home header**, next to the existing status dot |
| Gear reachable by the iDrive knob | **No** — tap-only |
| Boot mechanism | **No foreground service.** `BootReceiver` calls `startActivity` directly via the overlay BAL exemption |

Rationale for no defer: the value of auto-start is being up the instant the car powers
on; a delay would defeat it (the user would just tap). Rationale for no FGS: it removes
the Android-14 `dataSync`-foreground-service-from-boot crash vector and does *less* work
at boot than the old path, while still launching immediately. The toggle (default off)
plus per-step diagnostics are the safety net: if the box still reboots, the user
disables it and the log shows how far boot got — only *then* would a small (2–3s) delay
be justified, as a follow-up backed by evidence.

## Components

### 1. `SettingsStore` (new) — `settings/SettingsStore.kt`

DataStore-backed, mirroring `BubblePositionStore`. Single source of truth for the toggle,
read by both the UI and `BootReceiver`.

- `val autoStartFlow: Flow<Boolean>` — maps `prefs[KEY_AUTO_START] ?: false`.
- `suspend fun setAutoStart(enabled: Boolean)` — `dataStore.edit { ... }`.
- Key: `booleanPreferencesKey("auto_start_on_boot")`.

Wired in `ServiceLocator`:

```kotlin
private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "copilot_settings")

val settingsStore: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }
```

### 2. `BootReceiver` (restore + harden) — `boot/BootReceiver.kt`

Restored from `97f241f^`, FGS removed, with three changes:

- **Toggle gate:** read `SettingsStore(context.applicationContext.settingsDataStore)
  .autoStartFlow.first()` (a brief `runBlocking` read in `onReceive` is acceptable for a
  single key). If `false`, log `"auto-start disabled, skipping"` and return.
- **Overlay diagnostic:** if `!Settings.canDrawOverlays(context)`, log a clear warning
  that auto-start needs "Display over other apps" (the BAL exemption). Still attempt.
- **Direct launch:** `startActivity(Intent(context, MainActivity::class).addFlags(
  FLAG_ACTIVITY_NEW_TASK))`, wrapped in try/catch, with success/failure diagnostics via
  `DiagnosticLog`. No `BootLaunchService`.

Keeps all three actions: `BOOT_COMPLETED`, `android.intent.action.QUICKBOOT_POWERON`,
`com.htc.intent.action.QUICKBOOT_POWERON`.

`BootLaunchService` is **not** restored.

### 3. `SettingsScreen` (new) — `ui/settings/SettingsScreen.kt`

`ScreenHeader(title = "Settings", onBack)` plus, in order:

- **Auto-start toggle row**: a `Switch` + label ("Start Copilot when the car turns on")
  bound to `autoStartFlow` (collected as state) and `setAutoStart` (via
  `applicationScope`).
- **"Grant now-playing access"** button — moved from Status; shown only while
  `!PermissionHelpers.isNotificationAccessGranted(ctx)` (same condition as today).
- **"Diagnostic log"** button — moved from Status; navigates to `logs`.

Tap-only; no knob focus wiring (consistent with the gear).

### 4. `StatusScreen` (modify) — `ui/status/StatusScreen.kt`

Becomes read-only: keep the connection dot, clock skew, recent events, topic, version.
**Remove** the "Diagnostic log" and "Grant now-playing access" buttons. Drop the
`onOpenLogs` parameter; new signature `StatusScreen(state, onBack)`.

### 5. `HomeScreen` (modify) — `ui/home/HomeScreen.kt`

Add an `Icons.Filled.Settings` icon button in the existing header `Row`, immediately
right of `StatusPill`. New `onOpenSettings: () -> Unit` parameter. Tap-only — **not**
added to the `tileFocus` / knob focus ring or the `onPreviewKeyEvent` navigation.

### 6. `MainActivity` / `CopilotNav` (modify)

- New `composable("settings")` that builds `SettingsScreen`, wiring the toggle to
  `app.locator.settingsStore` (read via `collectAsStateWithLifecycle`, write via
  `app.applicationScope.launch`) and `onOpenLogs = { nav.navigate("logs") }`.
- Home: pass `onOpenSettings = { nav.navigate("settings") }`.
- Status route: drop the `onOpenLogs` argument.

### 7. `AndroidManifest.xml` (modify)

Re-add:
- `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`
- the `<receiver android:name=".boot.BootReceiver" android:exported="true">` with the
  three actions.

Do **not** re-add the `BootLaunchService` `<service>` entry.

### 8. Strings & docs

- New strings: settings title, toggle label, (reuse existing `grant_now_playing_access`).
- `ONBOARDING.md` / `docs/RELEASING.md`: restore the boot-launch mentions removed in
  `97f241f`, noting auto-start is now opt-in via Settings.

## Data flow

```
Settings toggle ──setAutoStart──► copilot_settings DataStore ◄──autoStartFlow──┐
                                                                                │
boot ─► BootReceiver.onReceive ─► read autoStartFlow.first() ───────────────────┘
            │  off → log + return
            │  on  → canDrawOverlays? (log if not) → startActivity(MainActivity)
            └────────────────────────────────────────► UI to foreground
                                                         (MainActivity.onCreate also
                                                          starts ListenerService, as today)
```

## Error handling

- Toggle off → receiver no-ops (functionally identical to today; a no-op receiver fires
  briefly and exits).
- Overlay missing → logged explicitly; launch still attempted.
- `startActivity` throws → caught, logged with exception class; no crash.
- Vendor "autostart manager" blocks the boot broadcast → nothing fires; worst case ==
  today (manual tap). Unfixable from app code; noted, not handled.

## Testing

- **Unit:** `SettingsStoreTest` mirroring `CategoryStoreTest` (Turbine /
  `PreferenceDataStoreFactory` + `TemporaryFolder`): default is `false`; `setAutoStart(true)`
  then `setAutoStart(false)` round-trips through `autoStartFlow`.
- **Manual (device, on the user's Mac/carbox):** toggle off → reboot → Copilot does *not*
  launch (matches today). Toggle on → reboot → Copilot pops to foreground; diagnostic log
  shows the boot entries. Confirm the gear opens Settings and Status no longer shows the
  two buttons.
- Boot receiver itself is framework-bound; not unit-tested. Gradle is not run in this
  workspace (no Android SDK) — build/test happens on the user's Mac.

## Out of scope

- Any boot-launch delay/defer (explicitly rejected; revisit only with crash evidence).
- Adding the gear or status dot to the knob focus ring.
- Restructuring Home beyond the single gear icon.
