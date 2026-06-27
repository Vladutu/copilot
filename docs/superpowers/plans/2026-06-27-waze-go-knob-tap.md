# Knob-press "Go now" tap for Waze — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When Waze shows its "Go now" route-preview in the car, let the driver press the hardware knob once and have Copilot tap "Go now" for them.

**Architecture:** All on-car logic lives in the existing `BackGrabberService` accessibility service (already granted, already filters key events, already tracks the foreground package via `AutoSwitchBack`). On a knob key-down while Waze is foreground and the feature is enabled, the service finds the on-screen node whose label matches the configured text ("Go now" by default), reads its screen bounds, and dispatches a tap gesture at the center. Pure decision/matching logic is split into small unit-tested helpers; the feature is gated behind a default-on Settings toggle with a configurable button label.

**Tech Stack:** Kotlin, Android AccessibilityService (`dispatchGesture`, `rootInActiveWindow`), Jetpack DataStore (Preferences), Jetpack Compose (Material3), JUnit4.

## Global Constraints

- **Build/test/commit happen on Georgian's Mac, not in this environment.** There is no Android SDK here. Write all code; do NOT run gradle and do NOT commit. The "Run" and "Commit" steps below are the instructions Georgian follows on the Mac. (Per the project build-workflow rule.)
- **Package:** `com.vladutu.copilot`. Min SDK 29, target SDK 34, compile SDK 37.
- **Feature default:** ON. Toggle and button label live in Settings; default label is exactly `Go now`.
- **Waze package id:** `com.waze`.
- **No new runtime permission / no new service.** Only two capability flags added to the existing accessibility-service XML; the user re-enables the accessibility service after install (existing routine).
- **Pure helpers stay Android-free** where unit-tested (so they run under plain JUnit without `android.jar`). `static final int` KeyEvent constants are compile-time inlined and therefore safe to reference from a constants object.
- **Follow existing patterns:** DataStore setting = `KEY_*` in companion + `Flow` getter + `suspend set*`; Settings UI = stateless `SettingsScreen` params + callbacks wired in `MainActivity` via `app.applicationScope.launch`.

---

### Task 1: Pure decision & matching helpers

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/waze/WazeGoDefaults.kt`
- Create: `app/src/main/java/com/vladutu/copilot/waze/GoTapDecider.kt`
- Create: `app/src/main/java/com/vladutu/copilot/waze/GoNodeMatcher.kt`
- Test: `app/src/test/java/com/vladutu/copilot/waze/GoTapDeciderTest.kt`
- Test: `app/src/test/java/com/vladutu/copilot/waze/GoNodeMatcherTest.kt`

**Interfaces:**
- Produces:
  - `object WazeGoDefaults { const val LABEL: String = "Go now"; val KNOB_KEYCODES: Set<Int> }`
  - `object GoTapDecider { const val WAZE_PKG = "com.waze"; fun shouldAttempt(enabled: Boolean, foregroundPkg: String?, keyCode: Int, knobKeyCodes: Set<Int>): Boolean }`
  - `object GoNodeMatcher { fun matches(label: String, text: String?, contentDescription: String?): Boolean }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/vladutu/copilot/waze/GoTapDeciderTest.kt`:

```kotlin
package com.vladutu.copilot.waze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoTapDeciderTest {

    private val knob = setOf(23, 66) // DPAD_CENTER, ENTER

    @Test fun `acts when enabled, waze foreground, knob key`() {
        assertTrue(GoTapDecider.shouldAttempt(true, "com.waze", 23, knob))
        assertTrue(GoTapDecider.shouldAttempt(true, "com.waze", 66, knob))
    }

    @Test fun `does not act when disabled`() {
        assertFalse(GoTapDecider.shouldAttempt(false, "com.waze", 23, knob))
    }

    @Test fun `does not act when foreground is not waze`() {
        assertFalse(GoTapDecider.shouldAttempt(true, "com.google.android.apps.maps", 23, knob))
        assertFalse(GoTapDecider.shouldAttempt(true, null, 23, knob))
    }

    @Test fun `does not act for a non-knob key`() {
        assertFalse(GoTapDecider.shouldAttempt(true, "com.waze", 4, knob)) // BACK
    }
}
```

`app/src/test/java/com/vladutu/copilot/waze/GoNodeMatcherTest.kt`:

```kotlin
package com.vladutu.copilot.waze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoNodeMatcherTest {

    @Test fun `matches exact text`() {
        assertTrue(GoNodeMatcher.matches("Go now", "Go now", null))
    }

    @Test fun `matches case-insensitively and trims`() {
        assertTrue(GoNodeMatcher.matches("Go now", "  GO NOW ", null))
        assertTrue(GoNodeMatcher.matches(" Go now ", "go now", null))
    }

    @Test fun `matches on contentDescription when text is null`() {
        assertTrue(GoNodeMatcher.matches("Go now", null, "Go now"))
    }

    @Test fun `ignores non-matching labels`() {
        assertFalse(GoNodeMatcher.matches("Go now", "Cancel", "Close"))
        assertFalse(GoNodeMatcher.matches("Go now", null, null))
    }

    @Test fun `blank target never matches`() {
        assertFalse(GoNodeMatcher.matches("   ", "Go now", null))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run (on Mac): `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.waze.*"`
Expected: FAIL — `WazeGoDefaults` / `GoTapDecider` / `GoNodeMatcher` unresolved.

- [ ] **Step 3: Write the implementations**

`app/src/main/java/com/vladutu/copilot/waze/WazeGoDefaults.kt`:

```kotlin
package com.vladutu.copilot.waze

import android.view.KeyEvent

/** Defaults for the knob-press "Go now" tap. Mirrors the TileAppearanceDefaults pattern. */
object WazeGoDefaults {
    /** Text Copilot looks for on Waze's route-preview confirm button. User-overridable. */
    const val LABEL: String = "Go now"

    /** Key codes a carbox knob press is expected to emit. The first build accepts both; the
     *  real code is confirmed from DiagnosticLog (every key event is already logged). */
    val KNOB_KEYCODES: Set<Int> = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
}
```

`app/src/main/java/com/vladutu/copilot/waze/GoTapDecider.kt`:

```kotlin
package com.vladutu.copilot.waze

/**
 * Pure predicate: should a key press trigger a "Go now" tap attempt? Android-free so it is
 * unit-testable. The accessibility service supplies the live foreground package and key code.
 */
object GoTapDecider {
    const val WAZE_PKG: String = "com.waze"

    fun shouldAttempt(
        enabled: Boolean,
        foregroundPkg: String?,
        keyCode: Int,
        knobKeyCodes: Set<Int>,
    ): Boolean = enabled && foregroundPkg == WAZE_PKG && keyCode in knobKeyCodes
}
```

`app/src/main/java/com/vladutu/copilot/waze/GoNodeMatcher.kt`:

```kotlin
package com.vladutu.copilot.waze

/**
 * Pure matcher for a node's label against the configured button text. Case-insensitive and
 * trimmed on both sides. Android-free so it is unit-testable; the service walks the real
 * AccessibilityNodeInfo tree and feeds each node's text/contentDescription here.
 */
object GoNodeMatcher {
    fun matches(label: String, text: String?, contentDescription: String?): Boolean {
        val target = label.trim()
        if (target.isEmpty()) return false
        return text?.trim().equals(target, ignoreCase = true) ||
            contentDescription?.trim().equals(target, ignoreCase = true)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run (on Mac): `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.waze.*"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit** (on Mac, when Georgian says so)

```bash
git add app/src/main/java/com/vladutu/copilot/waze app/src/test/java/com/vladutu/copilot/waze
git commit -m "feat: pure helpers for Waze Go-now knob tap"
```

---

### Task 2: Persist the two settings

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt`
- Test: `app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt`

**Interfaces:**
- Consumes: `WazeGoDefaults.LABEL` (Task 1).
- Produces (on `SettingsStore`):
  - `val wazeGoEnabledFlow: Flow<Boolean>` (default `true`)
  - `suspend fun setWazeGoEnabled(enabled: Boolean)`
  - `val wazeGoLabelFlow: Flow<String>` (default `WazeGoDefaults.LABEL`)
  - `suspend fun setWazeGoLabel(label: String)`

- [ ] **Step 1: Write the failing tests** — append inside `SettingsStoreTest` (before the closing brace):

```kotlin
    @Test fun `waze-go enabled defaults to true`() = runTest {
        assertEquals(true, store.wazeGoEnabledFlow.first())
    }

    @Test fun `setWazeGoEnabled round-trips`() = runTest {
        store.setWazeGoEnabled(false)
        assertEquals(false, store.wazeGoEnabledFlow.first())
        store.setWazeGoEnabled(true)
        assertEquals(true, store.wazeGoEnabledFlow.first())
    }

    @Test fun `waze-go label defaults to Go now`() = runTest {
        assertEquals("Go now", store.wazeGoLabelFlow.first())
    }

    @Test fun `setWazeGoLabel round-trips`() = runTest {
        store.setWazeGoLabel("Start")
        assertEquals("Start", store.wazeGoLabelFlow.first())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run (on Mac): `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.settings.SettingsStoreTest"`
Expected: FAIL — `wazeGoEnabledFlow` / `setWazeGoEnabled` / `wazeGoLabelFlow` / `setWazeGoLabel` unresolved.

- [ ] **Step 3: Implement in `SettingsStore.kt`**

Add the import near the other imports (after line 9 `import com.vladutu.copilot.ui.theme.TileAppearanceDefaults`):

```kotlin
import com.vladutu.copilot.waze.WazeGoDefaults
```

Add these members after `setTileBorderWidth` (after line 44, before the `topicFlow` block):

```kotlin
    /** Whether a knob press taps Waze's "Go now". Defaults on. */
    val wazeGoEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WAZE_GO_ENABLED] ?: true
    }

    suspend fun setWazeGoEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_WAZE_GO_ENABLED] = enabled }
    }

    /** Text Copilot taps on Waze's confirm screen; defaults to "Go now". */
    val wazeGoLabelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WAZE_GO_LABEL] ?: WazeGoDefaults.LABEL
    }

    suspend fun setWazeGoLabel(label: String) {
        dataStore.edit { prefs -> prefs[KEY_WAZE_GO_LABEL] = label }
    }
```

Add the two keys to the companion object (inside `private companion object`, after line 73):

```kotlin
        val KEY_WAZE_GO_ENABLED = booleanPreferencesKey("waze_go_enabled")
        val KEY_WAZE_GO_LABEL = stringPreferencesKey("waze_go_label")
```

- [ ] **Step 4: Run tests to verify they pass**

Run (on Mac): `./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.settings.SettingsStoreTest"`
Expected: PASS (all existing + 4 new).

- [ ] **Step 5: Commit** (on Mac)

```bash
git add app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt
git commit -m "feat: persist Waze Go-now toggle and button label"
```

---

### Task 3: Grant the accessibility service window-content + gesture capabilities

**Files:**
- Modify: `app/src/main/res/xml/accessibility_back_grabber.xml`

**Interfaces:** none (config only). Enables `rootInActiveWindow` (read Waze's nodes) and `dispatchGesture` (the tap) used in Task 4.

- [ ] **Step 1: Add the two capability flags**

Replace the file contents with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRequestFilterKeyEvents"
    android:canRequestFilterKeyEvents="true"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:description="@string/back_grabber_description"
    android:notificationTimeout="100" />
```

- [ ] **Step 2: Verify it compiles** (on Mac)

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Note for Georgian: changing accessibility capabilities can make Android drop the existing grant — **re-enable Copilot's accessibility service after installing this build.**

- [ ] **Step 3: Commit** (on Mac)

```bash
git add app/src/main/res/xml/accessibility_back_grabber.xml
git commit -m "feat: allow BackGrabber to read window content and perform gestures"
```

---

### Task 4: Wire the knob press → tap in BackGrabberService

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/back/BackGrabberService.kt`

**Interfaces:**
- Consumes: `GoTapDecider.shouldAttempt(...)`, `GoNodeMatcher.matches(...)`, `WazeGoDefaults.{LABEL,KNOB_KEYCODES}` (Task 1); `SettingsStore.{wazeGoEnabledFlow,wazeGoLabelFlow}` (Task 2); `AutoSwitchBack.foregroundForDiagnostics()` (existing) for the live foreground package; `CopilotApp.locator.settingsStore` (existing).

This task has no new unit test (the new code is Android-framework glue — `rootInActiveWindow`, `dispatchGesture`, `AccessibilityNodeInfo` traversal — which JUnit can't exercise; the pure logic it calls is already covered by Task 1). It is verified on-car via DiagnosticLog.

- [ ] **Step 1: Add imports**

Add to the import block (alphabetical-ish, alongside the existing ones):

```kotlin
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.vladutu.copilot.CopilotApp
import com.vladutu.copilot.waze.GoNodeMatcher
import com.vladutu.copilot.waze.GoTapDecider
import com.vladutu.copilot.waze.WazeGoDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Add service-scope + cached setting fields**

After the existing `private var switchBackPending = false` field (line 36), add:

```kotlin
    /** Live mirror of the Waze Go-now settings, kept current by collectors started in
     *  onServiceConnected. Read synchronously from onKeyEvent (which is not a coroutine). */
    @Volatile private var wazeGoEnabled = true
    @Volatile private var wazeGoLabel = WazeGoDefaults.LABEL

    /** True while we are consuming both halves of a knob press we acted on, so the foreground
     *  app never sees a half press (mirrors consumingThisPress for BACK). */
    private var consumingKnobPress = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
```

- [ ] **Step 3: Start the settings collectors in `onServiceConnected`**

At the end of `onServiceConnected` (after the `DiagnosticLog.i(...)` on line 49, before the closing brace), add:

```kotlin
        val store = (applicationContext as CopilotApp).locator.settingsStore
        serviceScope.launch { store.wazeGoEnabledFlow.collect { wazeGoEnabled = it } }
        serviceScope.launch { store.wazeGoLabelFlow.collect { wazeGoLabel = it } }
```

- [ ] **Step 4: Cancel the scope in `onDestroy`**

Add this method (e.g. right after `onInterrupt`, after line 125):

```kotlin
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
```

- [ ] **Step 5: Insert the knob handling into `onKeyEvent`**

In `onKeyEvent`, immediately after the diagnostic log call (after line 134, before `if (event.keyCode != KeyEvent.KEYCODE_BACK) return false`), add:

```kotlin
        // Waze "Go now" knob tap. Independent of BACK handling below. We claim both halves
        // of a press only when we actually dispatch the tap, so non-acting presses (feature
        // off, not Waze, no node yet) fall straight through to their normal behavior.
        when (event.action) {
            KeyEvent.ACTION_DOWN ->
                if (event.repeatCount == 0 && tryTapWazeGo(event.keyCode)) return true
            KeyEvent.ACTION_UP ->
                if (consumingKnobPress) {
                    consumingKnobPress = false
                    return true
                }
        }
```

- [ ] **Step 6: Add the tap helpers**

Add these private methods (e.g. after `bringCopilotToFront`, before the `private companion object`):

```kotlin
    /**
     * If the press is an enabled knob press while Waze is foreground and a node matching the
     * configured label is on screen, dispatch a tap at its center and claim the press.
     * Returns true only when a tap was dispatched (so the caller consumes the event).
     */
    private fun tryTapWazeGo(keyCode: Int): Boolean {
        val foreground = AutoSwitchBack.foregroundForDiagnostics()
        if (!GoTapDecider.shouldAttempt(wazeGoEnabled, foreground, keyCode, WazeGoDefaults.KNOB_KEYCODES)) {
            return false
        }
        val label = wazeGoLabel.ifBlank { WazeGoDefaults.LABEL }
        val root = rootInActiveWindow
        if (root == null) {
            DiagnosticLog.w(TAG, "waze-go: knob pressed but no active-window root")
            return false
        }
        val node = findGoNode(root, label)
        if (node == null) {
            DiagnosticLog.i(TAG, "waze-go: knob pressed, no '$label' node — ${describeNodes(root)}")
            return false
        }
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())
        DiagnosticLog.i(TAG, "waze-go: tapped '$label' at (${bounds.centerX()},${bounds.centerY()})")
        consumingKnobPress = true
        return true
    }

    /** Breadth-first search for the first node whose text/contentDescription matches [label]. */
    private fun findGoNode(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (GoNodeMatcher.matches(label, node.text?.toString(), node.contentDescription?.toString())) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) DiagnosticLog.w(TAG, "waze-go: dispatchGesture returned false")
    }

    /** Compact dump of labeled nodes, so the first car test reveals how Waze exposes "Go now"
     *  (text vs contentDescription, bounds, clickable) even when the configured label misses. */
    private fun describeNodes(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder("nodes=[")
        var shown = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && shown < MAX_DUMP_NODES) {
            val node = queue.removeFirst()
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            if (!text.isNullOrBlank() || !desc.isNullOrBlank()) {
                val b = Rect().also { node.getBoundsInScreen(it) }
                sb.append("{t='$text',d='$desc',b=${b.toShortString()},clk=${node.isClickable}} ")
                shown++
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.append(']').toString()
    }
```

- [ ] **Step 7: Add the two constants to the companion object**

Replace the `private companion object` body so it reads:

```kotlin
    private companion object {
        const val TAG = "BackGrabber"
        const val TAP_DURATION_MS = 50L
        const val MAX_DUMP_NODES = 40
    }
```

- [ ] **Step 8: Verify it compiles** (on Mac)

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit** (on Mac)

```bash
git add app/src/main/java/com/vladutu/copilot/back/BackGrabberService.kt
git commit -m "feat: tap Waze Go-now on knob press via BackGrabber"
```

---

### Task 5: Settings UI section

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/MainActivity.kt`

**Interfaces:**
- Consumes: `SettingsStore.{wazeGoEnabledFlow,setWazeGoEnabled,wazeGoLabelFlow,setWazeGoLabel}` (Task 2).
- Produces: four new params on `SettingsScreen`: `wazeGoEnabled: Boolean`, `onWazeGoEnabledChange: (Boolean) -> Unit`, `wazeGoLabel: String`, `onWazeGoLabelChange: (String) -> Unit`.

- [ ] **Step 1: Add strings** in `app/src/main/res/values/strings.xml` (next to `settings_tiles_label`, line 49):

```xml
    <string name="settings_waze_label">Waze</string>
    <string name="settings_waze_go_toggle">Tap “Go now” with the knob</string>
    <string name="settings_waze_go_button_label">Button label</string>
```

- [ ] **Step 2: Add the imports** in `SettingsScreen.kt`

Add to the import block:

```kotlin
import androidx.compose.material3.OutlinedTextField
```

- [ ] **Step 3: Add the params** to the `SettingsScreen` signature

Insert after `onTileBorderWidthChange: (Float) -> Unit,` (line 51):

```kotlin
    wazeGoEnabled: Boolean,
    onWazeGoEnabledChange: (Boolean) -> Unit,
    wazeGoLabel: String,
    onWazeGoLabelChange: (String) -> Unit,
```

- [ ] **Step 4: Add the section UI**

Insert this block right after the Tile appearance section (after line 101, the second `SliderRow(...)` closing `)`, before the `// Pairing section.` comment):

```kotlin
        // Waze "Go now" knob-tap section.
        Text(
            text = stringResource(R.string.settings_waze_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_waze_go_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
            Switch(checked = wazeGoEnabled, onCheckedChange = onWazeGoEnabledChange)
        }
        var labelDraft by remember(wazeGoLabel) { mutableStateOf(wazeGoLabel) }
        OutlinedTextField(
            value = labelDraft,
            onValueChange = {
                labelDraft = it
                onWazeGoLabelChange(it)
            },
            singleLine = true,
            enabled = wazeGoEnabled,
            label = { Text(stringResource(R.string.settings_waze_go_button_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
```

(`remember`, `mutableStateOf`, `getValue`, `setValue` are already imported in this file.)

- [ ] **Step 5: Wire it in `MainActivity.kt`**

In the `composable("settings")` block, after the `autoStart` collector (line 281), add:

```kotlin
            val wazeGoEnabled by app.locator.settingsStore.wazeGoEnabledFlow
                .collectAsStateWithLifecycle(initialValue = true)
            val wazeGoLabel by app.locator.settingsStore.wazeGoLabelFlow
                .collectAsStateWithLifecycle(initialValue = "Go now")
```

Then in the `SettingsScreen(...)` call, after the `onTileBorderWidthChange = { ... }` argument (line 297), add:

```kotlin
                wazeGoEnabled = wazeGoEnabled,
                onWazeGoEnabledChange = { enabled ->
                    app.applicationScope.launch { app.locator.settingsStore.setWazeGoEnabled(enabled) }
                },
                wazeGoLabel = wazeGoLabel,
                onWazeGoLabelChange = { label ->
                    app.applicationScope.launch { app.locator.settingsStore.setWazeGoLabel(label) }
                },
```

- [ ] **Step 6: Verify it compiles** (on Mac)

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit** (on Mac)

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt app/src/main/java/com/vladutu/copilot/MainActivity.kt
git commit -m "feat: Waze Go-now toggle and label in Settings"
```

---

## Post-test addendum (2026-06-27, after first key-probe log)

Knob key code **confirmed = `KEYCODE_DPAD_CENTER` (23)** — already in `WazeGoDefaults.KNOB_KEYCODES`, no change needed. The probe also revealed the carbox **double-injects** every knob press: the real `DPAD_CENTER` (device `gaei`) is followed ~45–200ms later by a synthetic `BUTTON_1` + `DPAD_CENTER` from a nameless device (the same duplication the BACK path already drops). Two adjustments were made to `BackGrabberService` beyond the plan above:

1. **Tap debounce** (`TAP_DEBOUNCE_MS = 800L`, field `lastWazeTapAt`): after a tap, a second knob press within the window is swallowed (consumed, not re-tapped) so the synthetic duplicate can't fire a second tap onto the post-nav screen.
2. **Per-keycode consume tracking** (`consumingKnobKeyCode: Int?` replaces the `consumingKnobPress` bool, and `tryTapWazeGo(keyCode, eventTime)` takes the event time): because the synthetic `BUTTON_1` interleaves between the duplicate `DPAD_CENTER` DOWN/UP, UP-matching must be per-keycode to pair the right halves.

Result: one knob press → exactly one "Go now" tap; Waze sees no stray `DPAD_CENTER`.

## On-car verification (Georgian, after install)

1. Re-enable Copilot's accessibility service (capabilities changed → Android may have dropped it).
2. Settings → confirm the new **Waze** section: toggle is **on**, label shows **Go now**.
3. Send a place (from Pilot, or pick locally) → Waze opens → wait for "Go now".
4. Press the knob once. Navigation should start.
5. If it doesn't: open Settings → Diagnostic log and look for `waze-go:` lines:
   - `tapped 'Go now' at (x,y)` → tap fired (if nav still didn't start, the node wasn't the live button; check bounds).
   - `no 'Go now' node — nodes=[…]` → read the dump: find the entry that is the button, note its `t=`/`d=` (set that as the label in Settings) or its `b=` bounds.
   - No `waze-go:` line at all on knob press → the knob's key code isn't in `KNOB_KEYCODES`; find the `key kc=…` line logged at the moment of the press and report that code (it gets added to `WazeGoDefaults.KNOB_KEYCODES`).

## Self-review notes

- **Spec coverage:** trigger in `onKeyEvent` (T4); one-live-attempt + pass-through (T4 Step 5/6, `tryTapWazeGo` returns false → not consumed); launch-source-agnostic (keys off `foregroundForDiagnostics()`, no launch path referenced); lives in `BackGrabberService`, no new permission (T4); config flags (T3); node match via bounds+gesture (T4 `dispatchTap`); self-diagnosing logging — keycode via the existing per-key log line, node dump via `describeNodes` (T4); Settings toggle default-on + editable label default "Go now" (T2, T5); pure-helper unit tests (T1) + store tests (T2). All covered.
- **Type consistency:** `shouldAttempt`, `matches`, `WazeGoDefaults.{LABEL,KNOB_KEYCODES}`, the four `SettingsStore` members, and the four `SettingsScreen` params are referenced with identical names/signatures across tasks.
- **No placeholders:** every code step contains complete code.
