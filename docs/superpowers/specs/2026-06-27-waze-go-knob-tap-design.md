# Knob-press "Go now" tap for Waze (Copilot)

**Date:** 2026-06-27
**Status:** Design — awaiting review
**Repo:** Copilot (`com.vladutu.copilot`), branch `master`

## Problem

When a destination is opened in the car, Waze comes to the foreground showing its
route-preview screen with a single **"Go now"** button. Navigation does not start
until a human physically taps that button on the touchscreen. The driver wants to
avoid reaching for the screen.

The destination URL already contains `&navigate=yes` (built in Pilot:
`InAppMapsToWazeResolver.kt:100`, enforced by `WazeUrlNormalizer.kt:19-26`), so the
"auto-navigate" deep-link lever is already pulled. Recent Waze still shows the
"Go now" confirmation regardless. There is **no deep-link parameter** that removes
it — the only place left to act is on the car, after Waze is foreground.

Waze takes a **variable 5–10 seconds** from launch until "Go now" is on screen
(depending on whether Waze was already running and how long the route takes to
calculate), so the app cannot reliably guess *when* the screen is ready.

## Goal

Let the driver press the **car's hardware knob** once, when they see "Go now", and
have Copilot tap it for them. No reaching for the screen.

## Non-goals

- Removing/altering Waze's confirmation screen (not possible).
- Fully-automatic tapping (no human in the loop). Explicitly rejected: the driver
  wants to stay in control, and the variable 5–10s load makes auto-timing brittle.
- Handling Waze prompts other than the single "Go now" route-preview button (the
  driver confirms this is always the same screen). Other prompts (e.g. "replace
  existing route?") are out of scope for v1.

## Key decisions

- **Trigger model:** knob press = **one live attempt**. Each press taps "Go now"
  only if it is on screen at that instant; otherwise the press passes through
  untouched (press again when it appears). The variable load time is handled by the
  human, not by app timing logic.
- **Launch-source-agnostic:** the mechanism keys off on-screen state (Waze
  foreground + "Go now" present), not off how Waze was launched. It therefore works
  identically whether Waze was opened locally on the carbox or by Pilot pushing a
  place over ntfy (`ListenerService` → `AppLauncher`). No source-specific code.
- **Lives in the existing accessibility service** `BackGrabberService` — already
  granted by the user, already filters key events, already tracks the foreground
  package. No new service, no new runtime permission.
- **Configurable, on by default:** gated behind a Settings toggle (default **on** —
  the driver can disable it). The matched button text is a configurable string
  (default **"Go now"**), so the driver can adapt to localization or future Waze
  wording without a rebuild.

## Architecture

All on-car logic lives in `BackGrabberService`
(`app/.../back/BackGrabberService.kt`). Two pure, separately-testable helpers carry
the decision logic so the service stays thin:

1. **`GoTapDecider`** (new, pure Kotlin) — answers *"should this key press act?"*
   Inputs: the configured enabled flag, foreground package, key code (vs. the
   accepted knob key codes). No Android dependencies → unit-testable.
2. **`GoNodeFinder`** (new, pure-ish Kotlin) — given a root `AccessibilityNodeInfo`
   and the configured label, returns the first node whose `text` or
   `contentDescription` matches the label (case-insensitive, trimmed), or null.
   The tree-walk takes a small node-abstraction interface so the matching rule is
   unit-testable without a live tree.

### Accessibility config changes

`res/xml/accessibility_back_grabber.xml` gains two capability flags on the existing
service (no new grant prompt; user re-enables after install per usual):

- `android:canRetrieveWindowContent="true"` — to read Waze's on-screen nodes.
- `android:canPerformGestures="true"` — to dispatch the tap.

### Control flow (in existing `onKeyEvent`)

```
onKeyEvent(event):
    if not (feature enabled): return false            // pass through
    if event is not a knob key code (down):  return false
    if foreground package != "com.waze":     return false
    root = rootInActiveWindow ?: return false
    node = GoNodeFinder.find(root, configuredLabel)
    if node == null:
        log("knob pressed, no '<label>' node found"); return false   // press again later
    bounds = node.getBoundsInScreen()
    dispatchGesture(tap at bounds center)
    log("tapped '<label>' at <bounds>")
    return true                                        // consume the press
```

Every branch that does not tap returns `false`, so the knob behaves normally
everywhere else in the car. The existing BACK-key handling in `onKeyEvent` is left
untouched; the new logic is added alongside it.

## Self-diagnosing first build

Two facts are unknown until on-device, so the first build ships sensible defaults
**and** logs reality via the existing `DiagnosticLog`, so one drive either works or
yields the values to finalize — no guess-and-redeploy:

- **Knob key code:** unknown. Defaults accept `KEYCODE_DPAD_CENTER` and
  `KEYCODE_ENTER`. While Waze is foreground, every observed key event's code is
  logged, so the real knob code is captured even if it differs.
- **"Go now" node exposure:** unknown whether Waze exposes the button as a node with
  a readable label. On a knob press while Waze is foreground, the matching attempt
  logs a compact dump of candidate nodes (text / contentDescription / bounds /
  clickable) so we can confirm the label and bounds. If it turns out Waze exposes
  *no* "Go now" node at all, the fallback is a fixed-coordinate tap (a follow-up;
  not built in v1 unless the logs show it's needed).

## Settings section

New "Waze" section in `SettingsScreen.kt`, following the existing toggle/section
patterns, wired through `MainActivity` settings composable and persisted in
`SettingsStore` (DataStore), mirroring the auto-start boolean and editable-string
patterns already in the file.

- **Toggle:** "Tap Go now with knob" — boolean, default **on**
  (`KEY_WAZE_GO_ENABLED = booleanPreferencesKey("waze_go_enabled")`,
  `wazeGoEnabledFlow` default `true`, `setWazeGoEnabled(Boolean)`).
- **Editable label:** "Button label" — string, default **"Go now"**
  (`KEY_WAZE_GO_LABEL = stringPreferencesKey("waze_go_label")`,
  `wazeGoLabelFlow` default from `WazeGoDefaults.LABEL`, `setWazeGoLabel(String)`).
  Rendered as an `OutlinedTextField`; blank input falls back to the default at read
  time. (This is the first free-text editable field in Settings; the topic field is
  display-only.)

New defaults holder `WazeGoDefaults` (mirroring `TileAppearanceDefaults`):

```kotlin
object WazeGoDefaults {
    const val LABEL = "Go now"
    val KNOB_KEYCODES = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
}
```

The service reads `wazeGoEnabledFlow` / `wazeGoLabelFlow` from the same
`SettingsStore` instance it already has access to via the service locator.

## Edge cases

- **Press during the 5–10s load** (Go now not yet up): no node match → pass-through,
  harmless; press again when it appears.
- **Waze not foreground:** never acts.
- **Knob used anywhere else** (any other app/screen): pass-through, unaffected.
- **Feature disabled:** `onKeyEvent` returns `false` before any node work.
- **Blank configured label:** treated as the default "Go now".

## Testing

Pure-Kotlin unit tests (mirroring existing `AutoSwitchBack` / `SettingsStore` tests):

- `GoTapDecider`: acts only when enabled AND foreground == `com.waze` AND key code
  is an accepted knob code; rejects each missing condition.
- `GoNodeFinder`: matches "Go now" exactly, matches case-insensitively / trimmed,
  matches on `contentDescription` when `text` is null, ignores non-matching labels,
  returns null on empty tree.
- `SettingsStore`: defaults (`true`, `"Go now"`), write→read round-trip for both
  new keys, blank-label → default fallback.

On-car verification per the usual flow (driver installs on the carbox, **re-enables
the accessibility service after install**, drives, reads `DiagnosticLog`).

## Out of scope / follow-ups

- Fixed-coordinate fallback tap (only if logs prove Waze exposes no node).
- Handling additional Waze prompts ("replace route?", etc.).
- A fully-automatic (no-knob) mode — could later become a third setting if desired.
