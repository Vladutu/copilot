# Auto-return after YouTube Music launch — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After Copilot launches YouTube Music for a song/playlist (from an in-app tap or a Pilot ntfy event), automatically return to whatever app was in the foreground beforehand once YT Music has loaded.

**Architecture:** A new pure-Kotlin singleton `AutoSwitchBack` holds the "expect a YT Music launch" state and snapshots the return target at arm-time (immune to the bubble-overlay window-event race). `AppLauncher` arms it just before launching YT Music. The existing `BackGrabberService` accessibility service (already listening to `typeWindowStateChanged`) feeds it foreground packages, detects YT Music appearing, waits a settle delay, and restores the target — aborting if the driver navigated to a third app.

**Tech Stack:** Kotlin, Android AccessibilityService, JUnit4 + Robolectric (existing test setup).

> **Note on git:** Georgian builds and commits himself in Android Studio. The "Commit" steps below are written for completeness; if you are an agent executing this plan, **do not run gradle or git** — leave the working tree staged-ready and let Georgian build/test/commit. Run logic checks by reading code, not by invoking the build.

---

## File Structure

- **Create:** `app/src/main/java/com/vladutu/copilot/autoswitch/AutoSwitchBack.kt`
  — pure-JVM state machine: foreground tracking, arm/disarm, target snapshot, abort decision.
- **Create:** `app/src/test/java/com/vladutu/copilot/autoswitch/AutoSwitchBackTest.kt`
  — unit tests for the state machine (no Android).
- **Modify:** `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`
  — call `AutoSwitchBack.arm()` for `cmd == "ytmusic"` before `startActivity`.
- **Modify:** `app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`
  — assert arming happens for ytmusic and not for waze/maps/radio.
- **Modify:** `app/src/main/java/com/vladutu/copilot/back/BackGrabberService.kt`
  — fill in `onAccessibilityEvent`, add settle-timer scheduling and `restoreApp`.

No manifest, permission, or `<queries>` changes are needed: `typeWindowStateChanged` is
already declared, `SYSTEM_ALERT_WINDOW` is already held, and an enabled accessibility
service has broad package visibility.

---

## Task 1: `AutoSwitchBack` state machine

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/autoswitch/AutoSwitchBack.kt`
- Test: `app/src/test/java/com/vladutu/copilot/autoswitch/AutoSwitchBackTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/vladutu/copilot/autoswitch/AutoSwitchBackTest.kt`:

```kotlin
package com.vladutu.copilot.autoswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoSwitchBackTest {

    private val ytMusic = AutoSwitchBack.YT_MUSIC_PKG

    @Before fun reset() {
        // Deterministic, controllable clock and a clean slate between tests.
        now = 1_000L
        AutoSwitchBack.clock = { now }
        AutoSwitchBack.ownPackage = "com.vladutu.copilot"
        AutoSwitchBack.disarm()
        AutoSwitchBack.onForeground("com.android.launcher") // a benign default
    }

    private var now = 1_000L

    @Test fun `not armed by default`() {
        AutoSwitchBack.disarm()
        assertFalse(AutoSwitchBack.isArmed())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `arm snapshots current foreground as target`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        assertTrue(AutoSwitchBack.isArmed())
        assertTrue(AutoSwitchBack.shouldScheduleOnYtMusicShown())
        assertEquals("com.waze", AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `arm with yt music in foreground yields no target`() {
        AutoSwitchBack.onForeground(ytMusic)
        AutoSwitchBack.arm()
        assertTrue(AutoSwitchBack.isArmed())
        assertFalse(AutoSwitchBack.shouldScheduleOnYtMusicShown())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `in-app case returns to copilot`() {
        AutoSwitchBack.onForeground("com.vladutu.copilot")
        AutoSwitchBack.arm()
        AutoSwitchBack.onForeground(ytMusic) // YT Music came up
        assertEquals("com.vladutu.copilot", AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `copilot overlay reading does not count as user moving away`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        AutoSwitchBack.onForeground(ytMusic)
        AutoSwitchBack.onForeground("com.vladutu.copilot") // bubble overlay window event
        assertEquals("com.waze", AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `user moving to a third app aborts`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        AutoSwitchBack.onForeground(ytMusic)
        AutoSwitchBack.onForeground("com.android.chrome") // user opened something else
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `arm expires after ttl`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        now += 8_001L
        assertFalse(AutoSwitchBack.isArmed())
        assertFalse(AutoSwitchBack.shouldScheduleOnYtMusicShown())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }

    @Test fun `disarm clears state`() {
        AutoSwitchBack.onForeground("com.waze")
        AutoSwitchBack.arm()
        AutoSwitchBack.disarm()
        assertFalse(AutoSwitchBack.isArmed())
        assertNull(AutoSwitchBack.resolveTargetAtFire())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.autoswitch.AutoSwitchBackTest"`
Expected: FAIL — `AutoSwitchBack` unresolved reference. *(Agent: do not run gradle — confirm by reading that the file does not yet exist.)*

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/vladutu/copilot/autoswitch/AutoSwitchBack.kt`:

```kotlin
package com.vladutu.copilot.autoswitch

/**
 * Process-wide coordinator for "after YouTube Music loads, switch back to where we were".
 *
 * Pure Kotlin (no Android imports) so the decision logic is unit-testable. The
 * accessibility service ([com.vladutu.copilot.back.BackGrabberService]) feeds it foreground
 * packages and performs the actual app switch; [com.vladutu.copilot.launch.AppLauncher] arms
 * it right before launching YT Music for a song/playlist.
 *
 * The return target is snapshotted at [arm] time — when the real foreground is still the
 * previous app — so it is immune to later window events from Copilot's own bubble overlay.
 */
object AutoSwitchBack {

    const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"

    /** Delay after YT Music's window appears before we pull focus back, giving YT Music
     *  time to process the deep link and start playback (mirrors Waze deep-link timing). */
    const val SETTLE_MS = 1_200L

    /** How long an arm stays valid if YT Music never appears (not installed / launch failed). */
    private const val ARM_TTL_MS = 8_000L

    /** Copilot's own application id, set once by the service (handles the .debug suffix). */
    @Volatile var ownPackage: String? = null

    /** Injectable for deterministic tests; defaults to wall-clock. */
    @Volatile var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile private var currentForeground: String? = null
    @Volatile private var armedAtMillis: Long? = null
    @Volatile private var targetPackage: String? = null

    /** Record the latest foreground package (called from the accessibility service). */
    fun onForeground(pkg: String) {
        currentForeground = pkg
    }

    /** Snapshot the return target and start the arm window. Call just before launching YT Music. */
    fun arm() {
        val fg = currentForeground
        targetPackage = if (fg == null || fg == YT_MUSIC_PKG) null else fg
        armedAtMillis = clock()
    }

    fun isArmed(): Boolean {
        val armedAt = armedAtMillis ?: return false
        return clock() - armedAt < ARM_TTL_MS
    }

    /** True when YT Music appearing should schedule a switch-back. */
    fun shouldScheduleOnYtMusicShown(): Boolean = isArmed() && targetPackage != null

    /**
     * The package to restore, or null to abort. Aborts when the driver has moved to a
     * third app — foreground is non-null and is neither YT Music nor Copilot itself (the
     * latter covers the bubble-overlay window reading, which must not look like "moved").
     */
    fun resolveTargetAtFire(): String? {
        val target = if (isArmed()) targetPackage else null
        target ?: return null
        val fg = currentForeground
        // "Moved away" means the foreground is some OTHER app: not YT Music, not Copilot
        // itself (covers the bubble-overlay reading), and not the target we'd restore
        // (covers resolve being called while still on the target before YT Music shows).
        val movedAway = fg != null && fg != YT_MUSIC_PKG && fg != ownPackage && fg != target
        return if (movedAway) null else target
    }

    fun disarm() {
        armedAtMillis = null
        targetPackage = null
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.autoswitch.AutoSwitchBackTest"`
Expected: PASS (8 tests). *(Agent: do not run gradle — verify by re-reading the implementation against each test assertion.)*

- [ ] **Step 5: Commit** *(Georgian does this in Android Studio)*

```bash
git add app/src/main/java/com/vladutu/copilot/autoswitch/AutoSwitchBack.kt \
        app/src/test/java/com/vladutu/copilot/autoswitch/AutoSwitchBackTest.kt
git commit -m "feat: add AutoSwitchBack state machine for post-YT-Music return"
```

---

## Task 2: Arm from `AppLauncher`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt:67-82`
- Test: `app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these imports to the top of `AppLauncherTest.kt` (alongside the existing imports):

```kotlin
import com.vladutu.copilot.autoswitch.AutoSwitchBack
```

Add these tests inside `AppLauncherTest`:

```kotlin
@Test fun `arms autoswitch for ytmusic launch`() {
    AutoSwitchBack.disarm()
    AutoSwitchBack.onForeground("com.vladutu.copilot")
    launcher.launch(msg("ytmusic", Form.PLAYLIST, "https://music.youtube.com/watch?list=X"))
    assertTrue(AutoSwitchBack.isArmed())
}

@Test fun `does not arm autoswitch for waze launch`() {
    AutoSwitchBack.disarm()
    AutoSwitchBack.onForeground("com.vladutu.copilot")
    launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
    assertFalse(AutoSwitchBack.isArmed())
}

@Test fun `does not arm autoswitch for radio launch`() {
    AutoSwitchBack.disarm()
    AutoSwitchBack.onForeground("com.vladutu.copilot")
    launcher.launch(msg("radio", Form.RADIO, "https://live.example.ro/europafm.mp3"))
    assertFalse(AutoSwitchBack.isArmed())
}
```

Add the import for `assertFalse` if not present:

```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.launch.AppLauncherTest"`
Expected: `arms autoswitch for ytmusic launch` FAILS (not armed). *(Agent: confirm by reading that `AppLauncher` does not yet reference `AutoSwitchBack`.)*

- [ ] **Step 3: Add the arm call**

In `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`, add the import near the other imports:

```kotlin
import com.vladutu.copilot.autoswitch.AutoSwitchBack
```

Then in `launchUrl`, arm immediately before `startActivity`. Change this block (currently lines 67-74):

```kotlin
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (targetPkg != null) setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Result.Ok
```

to:

```kotlin
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (targetPkg != null) setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Song/playlist launches go to YT Music's foreground; arm the auto-return so the
        // accessibility service brings us back once YT Music has loaded. cmd=="ytmusic"
        // implies form is SONG or PLAYLIST (enforced by Message validation). Radio (VLC,
        // background) and maps/waze (nav stays foreground) are deliberately not armed.
        if (cmd == "ytmusic") AutoSwitchBack.arm()

        return try {
            context.startActivity(intent)
            Result.Ok
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.launch.AppLauncherTest"`
Expected: PASS (all, including the 3 new arming tests). *(Agent: verify by reading that `arm()` is only reachable when `cmd == "ytmusic"`.)*

- [ ] **Step 5: Commit** *(Georgian does this in Android Studio)*

```bash
git add app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt \
        app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt
git commit -m "feat: arm AutoSwitchBack on YT Music song/playlist launch"
```

---

## Task 3: Detect + restore in `BackGrabberService`

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/back/BackGrabberService.kt`

This task is on-device behavior (an `AccessibilityService` reacting to real window events
and calling `startActivity`); it is verified manually on the carbox via `DiagnosticLog`,
not by unit tests. The accessibility callback cannot be meaningfully unit-tested, and the
decision logic it depends on is already covered by Task 1.

- [ ] **Step 1: Wire foreground tracking, scheduling, and restore**

In `app/src/main/java/com/vladutu/copilot/back/BackGrabberService.kt`:

Add imports (near the existing imports):

```kotlin
import android.os.Handler
import android.os.Looper
import com.vladutu.copilot.autoswitch.AutoSwitchBack
```

Add fields and set `ownPackage` in `onServiceConnected`. Replace the existing
`onServiceConnected` (lines 30-41) so it also seeds `AutoSwitchBack.ownPackage`:

```kotlin
    private val handler = Handler(Looper.getMainLooper())

    /** Guards against scheduling multiple settle-timers from repeated YT Music window events. */
    private var switchBackPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Belt-and-suspenders: also set the filter-key-events flag programmatically. Some
        // Android variants on aftermarket head units honor the runtime value but ignore the
        // XML one, or vice-versa.
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        }
        AutoSwitchBack.ownPackage = applicationContext.packageName
        DiagnosticLog.i(TAG, "onServiceConnected flags=0x${serviceInfo?.flags?.toString(16)} caps=0x${serviceInfo?.capabilities?.toString(16)} ownPkg=${applicationContext.packageName}")
    }
```

Replace the no-op `onAccessibilityEvent` (line 43) with:

```kotlin
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        AutoSwitchBack.onForeground(pkg)

        if (pkg == AutoSwitchBack.YT_MUSIC_PKG &&
            AutoSwitchBack.shouldScheduleOnYtMusicShown() &&
            !switchBackPending
        ) {
            switchBackPending = true
            DiagnosticLog.i(TAG, "YT Music shown while armed — switch-back in ${AutoSwitchBack.SETTLE_MS}ms")
            handler.postDelayed({ fireSwitchBack() }, AutoSwitchBack.SETTLE_MS)
        }
    }

    private fun fireSwitchBack() {
        switchBackPending = false
        val target = AutoSwitchBack.resolveTargetAtFire()
        AutoSwitchBack.disarm()
        if (target == null) {
            DiagnosticLog.i(TAG, "switch-back aborted (user moved away or no target)")
            return
        }
        restoreApp(target)
    }

    private fun restoreApp(pkg: String) {
        val intent = if (pkg == applicationContext.packageName) {
            // REORDER_TO_FRONT preserves Copilot's nav back stack (driver returns to the
            // screen they were last on rather than resetting to Home).
            Intent(applicationContext, MainActivity::class.java)
        } else {
            applicationContext.packageManager.getLaunchIntentForPackage(pkg)
        }
        if (intent == null) {
            DiagnosticLog.w(TAG, "no launch intent for $pkg — staying in YT Music")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { applicationContext.startActivity(intent) }
            .onSuccess { DiagnosticLog.i(TAG, "switched back to $pkg") }
            .onFailure { DiagnosticLog.w(TAG, "switch-back startActivity failed for $pkg", it) }
    }
```

Note: the existing `bringCopilotToFront()` (BACK-button path) is unchanged and remains in
use. `restoreApp` intentionally duplicates only the small intent-building snippet for the
Copilot case rather than refactoring the BACK path, to keep that path untouched.

- [ ] **Step 2: Build (Georgian, in Android Studio)**

Build the debug variant and install on the carbox. Confirm it compiles with no unused-import
or unresolved-reference warnings for the new code.

- [ ] **Step 3: On-device verification (Georgian, on the carbox)**

Use `DiagnosticLog` output on the box to confirm each scenario:

1. **In-app song/playlist:** open Copilot, tap a saved song. Expect YT Music to start
   playing, then Copilot to return to the foreground after ~1.2s. Log shows
   `YT Music shown while armed` then `switched back to com.vladutu.copilot(.debug)`.
2. **ntfy from Pilot while a third app is foreground:** with Waze (or any app) in front,
   share a song/playlist from Pilot. Expect YT Music to start, then a return to that app.
   Log shows `switched back to com.waze` (or whichever app).
3. **Abort on manual move:** trigger a launch, then immediately open a different app during
   the settle window. Expect to stay where you moved. Log shows `switch-back aborted`.
4. **Radio not affected:** play a radio stream; VLC plays in background, no switch-back log
   line, nothing brought to the foreground.
5. **Playback not interrupted:** confirm the song actually starts playing (not cut off by
   the switch-back). If it is cut off, increase `AutoSwitchBack.SETTLE_MS` (e.g. to 1800).
6. **Watch for false aborts from system windows:** if a switch-back that should happen does
   not, check the log for `switch-back aborted — foreground=<pkg>`. If `<pkg>` is a system
   package (e.g. `com.android.systemui`, a volume HUD popping when playback starts) rather
   than an app you actually opened, apply the ready mitigation: in `onAccessibilityEvent`,
   skip `AutoSwitchBack.onForeground(pkg)` for packages starting with `com.android.systemui`.

- [ ] **Step 4: Commit** *(Georgian does this in Android Studio)*

```bash
git add app/src/main/java/com/vladutu/copilot/back/BackGrabberService.kt
git commit -m "feat: auto-return to previous app after YT Music loads"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- "Restore whatever was foreground; in-app→Copilot, ntfy→prior app" → Task 1 (`arm` snapshot, `resolveTargetAtFire`) + Task 3 (`restoreApp`). ✓
- "Abort if user moved" → Task 1 (`resolveTargetAtFire` third-app check) + Task 3 (`fireSwitchBack`). ✓
- "SETTLE_MS = 1200, ARM_TTL_MS = 8000" → Task 1 constants. ✓
- "Arm only for ytmusic song/playlist; radio/maps/waze untouched" → Task 2 (`cmd == "ytmusic"`) + tests. ✓
- "Bubble/moveTaskToBack left untouched" → no changes to MainActivity/BubbleController; `bringCopilotToFront()` BACK path unchanged. ✓
- "Bubble-overlay race avoided via arm-time snapshot + ownPackage exemption" → Task 1 (`arm`, `resolveTargetAtFire`). ✓
- "getLaunchIntentForPackage null → log and stay" (known limitation) → Task 3 (`restoreApp`). ✓
- "No manifest/permission/queries changes needed" → stated in File Structure. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code; every test shows assertions. ✓

**Type consistency:** `arm()`, `isArmed()`, `onForeground(pkg)`, `shouldScheduleOnYtMusicShown()`, `resolveTargetAtFire()`, `disarm()`, `ownPackage`, `clock`, `YT_MUSIC_PKG`, `SETTLE_MS` are named identically across Tasks 1, 2, and 3. ✓
