# Auto-start on Boot (Toggle + Settings Screen) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore auto-start of Copilot on carbox boot, gated behind a default-off toggle on a new Settings screen, using a lean foreground-service-free boot path.

**Architecture:** A DataStore-backed `SettingsStore` holds one boolean. A new `SettingsScreen` (reached via a gear icon on the Home header) reads/writes it and absorbs the config buttons that currently live on Status. A restored, hardened `BootReceiver` reads the flag on `BOOT_COMPLETED` and, if on, launches `MainActivity` directly via the existing overlay BAL exemption — no foreground service, no delay.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose, Jetpack DataStore (Preferences), JUnit + Turbine, AndroidManifest broadcast receiver.

## Global Constraints

- **No gradle in this workspace** — there is no Android SDK on the Linux box. All `make check` / gradle commands in this plan are run by the user on their Mac. Do not attempt to build here.
- **Do not commit at code-writing time.** The user reviews and tests on their Mac, then says "commit". The final commit step is gated on that.
- **Persistence idiom:** one `DataStore<Preferences>` per concern, declared as a `Context` extension in `ServiceLocator.kt`, wrapped by a small store class exposing a `Flow` + suspend setter. Mirror `BubblePositionStore` / `CategoryStore`.
- **Toggle default = `false`** (auto-start off on fresh install).
- **Boot path:** restore `BootReceiver` only — **never** re-add `BootLaunchService` or any foreground service for boot. Launch the activity directly; immediate (no defer).
- **Knob:** the gear and status dot stay tap-only; do not add them to the Home focus ring.
- Verification command for the whole app: `make check` (assembleDebug + unit tests + lint).

---

### Task 1: SettingsStore + ServiceLocator wiring

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt`
- Test: `app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt`

**Interfaces:**
- Produces:
  - `class SettingsStore(dataStore: DataStore<Preferences>)`
  - `val SettingsStore.autoStartFlow: Flow<Boolean>` (defaults `false`)
  - `suspend fun SettingsStore.setAutoStart(enabled: Boolean)`
  - `val ServiceLocator.settingsStore: SettingsStore`
  - `val Context.settingsDataStore: DataStore<Preferences>` (`internal` extension in ServiceLocator.kt, datastore name `"copilot_settings"`) — `internal` (not `private`) so `BootReceiver` can reuse the same instance in Task 4.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt`:

```kotlin
package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore
    private val scope = TestScope(UnconfinedTestDispatcher())

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        store = SettingsStore(dataStore)
    }

    @Test fun `auto-start defaults to false`() = runTest {
        assertEquals(false, store.autoStartFlow.first())
    }

    @Test fun `setAutoStart round-trips`() = runTest {
        store.setAutoStart(true)
        assertEquals(true, store.autoStartFlow.first())
        store.setAutoStart(false)
        assertEquals(false, store.autoStartFlow.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails** *(on Mac)*

Run: `./gradlew testDebugUnitTest --tests "com.vladutu.copilot.settings.SettingsStoreTest"`
Expected: FAIL — `SettingsStore` is unresolved (does not compile yet).

- [ ] **Step 3: Create SettingsStore**

Create `app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt`:

```kotlin
package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val autoStartFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START] ?: false
    }

    suspend fun setAutoStart(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_START] = enabled }
    }

    private companion object {
        val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")
    }
}
```

- [ ] **Step 4: Run test to verify it passes** *(on Mac)*

Run: `./gradlew testDebugUnitTest --tests "com.vladutu.copilot.settings.SettingsStoreTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Wire into ServiceLocator**

In `app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt`, add the import and the datastore extension + lazy property. Add alongside the existing `import` block:

```kotlin
import com.vladutu.copilot.settings.SettingsStore
```

Add next to the other `Context.*DataStore` extensions (after `likedDataStore`). Use `internal` (not `private`) so `BootReceiver` can reuse the same instance in Task 4:

```kotlin
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_settings")
```

Add inside the `ServiceLocator` class, next to the other `by lazy` members:

```kotlin
val settingsStore: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }
```

- [ ] **Step 6: (deferred) Commit** — do NOT commit now. Leave for the batched commit after the user reviews (see end of plan).

---

### Task 2: SettingsScreen composable + strings

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier)` from `com.vladutu.copilot.ui`; `PermissionHelpers.isNotificationAccessGranted(context)` and `PermissionHelpers.openNotificationAccessSettings(context)` from `com.vladutu.copilot.ui.permissions`; existing string `R.string.grant_now_playing_access`.
- Produces:
  - `@Composable fun SettingsScreen(autoStart: Boolean, onAutoStartChange: (Boolean) -> Unit, onOpenLogs: () -> Unit, onBack: () -> Unit)`
  - New strings: `settings_title`, `settings_autostart_label`, `settings_diagnostic_log`.

This screen is pure UI — it takes the toggle value and callbacks as parameters and never touches `SettingsStore` directly. Wiring happens in Task 3.

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, add (near the other screen strings):

```xml
<string name="settings_title">Settings</string>
<string name="settings_autostart_label">Start Copilot when the car turns on</string>
<string name="settings_diagnostic_log">Diagnostic log</string>
```

- [ ] **Step 2: Create SettingsScreen**

Create `app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt`:

```kotlin
package com.vladutu.copilot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.permissions.PermissionHelpers

@Composable
fun SettingsScreen(
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)

        // Auto-start toggle row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_autostart_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Switch(checked = autoStart, onCheckedChange = onAutoStartChange)
        }

        // Now-playing (notification access) grant — shown only while access is missing.
        if (!PermissionHelpers.isNotificationAccessGranted(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openNotificationAccessSettings(ctx) }) {
                Text(stringResource(R.string.grant_now_playing_access))
            }
        }

        OutlinedButton(onClick = onOpenLogs) {
            Text(stringResource(R.string.settings_diagnostic_log))
        }
    }
}
```

- [ ] **Step 3: Verify it compiles** *(on Mac)*

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (screen not yet referenced anywhere — that is Task 3).

- [ ] **Step 4: (deferred) Commit** — do NOT commit now.

---

### Task 3: Navigation wiring — Home gear, Settings route, Status read-only

**Files:**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/MainActivity.kt`

**Interfaces:**
- Consumes: `SettingsScreen(...)` from Task 2; `ServiceLocator.settingsStore` (`autoStartFlow`, `setAutoStart`) from Task 1; `CopilotApp.applicationScope`, `CopilotApp.locator`.
- Produces:
  - `HomeScreen(...)` gains parameter `onOpenSettings: () -> Unit`.
  - `StatusScreen(state: UiState, onBack: () -> Unit)` — `onOpenLogs` parameter removed.

This task changes three signatures that must stay consistent so the app compiles; do them together.

- [ ] **Step 1: Add the gear to HomeScreen**

In `app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`:

Add imports:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
```

(`androidx.compose.material.icons.Icons`, `androidx.compose.material3.Icon`, `androidx.compose.foundation.layout.Row`, and `size` may already be imported — keep the file's imports de-duplicated.)

Add the parameter to the function signature, after `onOpenStatus`:

```kotlin
    onOpenStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackFromHome: () -> Unit,
```

Replace the single `StatusPill(...)` call at the end of the header `Row` (currently the last child) with the pill plus a gear, kept together:

```kotlin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusPill(state = state, onClick = onOpenStatus)
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
```

Note: the gear is inside the header `Row` only and is not added to `tileFocus`, `heartFocus`, or the `onPreviewKeyEvent` handler — it stays tap-only by construction.

- [ ] **Step 2: Make StatusScreen read-only**

In `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt`:

Change the signature from:

```kotlin
fun StatusScreen(state: UiState, onBack: () -> Unit, onOpenLogs: () -> Unit) {
```

to:

```kotlin
fun StatusScreen(state: UiState, onBack: () -> Unit) {
```

Delete this block (the diagnostic-log button):

```kotlin
        OutlinedButton(onClick = onOpenLogs) { Text("Diagnostic log") }
```

Delete this block (the now-playing grant button) and the `ctx` line that only it uses:

```kotlin
        // Now-playing (notification access) grant — shown only while access is missing.
        val ctx = LocalContext.current
        if (!PermissionHelpers.isNotificationAccessGranted(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openNotificationAccessSettings(ctx) }) {
                Text(stringResource(R.string.grant_now_playing_access))
            }
        }
```

Then remove the now-unused imports from StatusScreen.kt: `OutlinedButton`, `LocalContext`, `stringResource`, `R`, and `PermissionHelpers`. (Leave imports that other code in the file still uses.)

- [ ] **Step 3: Wire the Settings route and update Home/Status call sites in MainActivity**

In `app/src/main/java/com/vladutu/copilot/MainActivity.kt`:

Add imports:

```kotlin
import com.vladutu.copilot.ui.settings.SettingsScreen
```

In the `composable("home")` block, add the new argument to the `HomeScreen(...)` call, next to `onOpenStatus`:

```kotlin
                onOpenStatus = { nav.navigate("status") },
                onOpenSettings = { nav.navigate("settings") },
                onBackFromHome = onLeftToOtherApp,
```

Change the `composable("status")` block from:

```kotlin
        composable("status") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            StatusScreen(
                state = uiState,
                onBack = { nav.popBackStack() },
                onOpenLogs = { nav.navigate("logs") },
            )
        }
```

to:

```kotlin
        composable("status") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            StatusScreen(
                state = uiState,
                onBack = { nav.popBackStack() },
            )
        }
```

Add a new `composable("settings")` block (place it next to the `status` block):

```kotlin
        composable("settings") {
            val autoStart by app.locator.settingsStore.autoStartFlow
                .collectAsStateWithLifecycle(initialValue = false)
            SettingsScreen(
                autoStart = autoStart,
                onAutoStartChange = { enabled ->
                    app.applicationScope.launch { app.locator.settingsStore.setAutoStart(enabled) }
                },
                onOpenLogs = { nav.navigate("logs") },
                onBack = { nav.popBackStack() },
            )
        }
```

(`app`, `launch`, and `collectAsStateWithLifecycle` are already imported and in scope in this file.)

- [ ] **Step 4: Build and run the full check** *(on Mac)*

Run: `make check`
Expected: BUILD SUCCESSFUL; all unit tests pass; lint clean.

- [ ] **Step 5: Manual UI smoke test** *(on Mac/device)*

- Open Copilot. Confirm a gear icon sits to the right of the status dot on Home.
- Tap the gear → Settings opens with the auto-start switch (off), the diagnostic-log button, and (if access not granted) the now-playing grant button.
- Toggle on, leave Settings, return → switch is still on (persisted).
- Tap the status dot → Status opens and no longer shows the two buttons.

- [ ] **Step 6: (deferred) Commit** — do NOT commit now.

---

### Task 4: Restore + harden BootReceiver, manifest, docs

**Files:**
- Create: `app/src/main/java/com/vladutu/copilot/boot/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `ONBOARDING.md`
- Modify: `docs/RELEASING.md`

**Interfaces:**
- Consumes: `SettingsStore` + the `Context.settingsDataStore` extension from Task 1, `MainActivity`, `DiagnosticLog`.

The original bounced through a `dataSync` foreground service that is disallowed from `BOOT_COMPLETED` on Android 14+. This version drops the service and launches the activity directly, relying on the existing `SYSTEM_ALERT_WINDOW` grant as the BAL exemption.

- [ ] **Step 1: Create the hardened BootReceiver**

Create `app/src/main/java/com/vladutu/copilot/boot/BootReceiver.kt`:

```kotlin
package com.vladutu.copilot.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.vladutu.copilot.MainActivity
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        DiagnosticLog.i("Boot", "received action=$action")
        if (action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        // Single-key read; brief blocking in onReceive is acceptable.
        val enabled = runCatching {
            runBlocking { SettingsStore(appContext.settingsDataStore).autoStartFlow.first() }
        }.getOrDefault(false)
        if (!enabled) {
            DiagnosticLog.i("Boot", "auto-start disabled, skipping")
            return
        }

        // "Display over other apps" is our Background-Activity-Launch exemption.
        // If it is somehow missing, the launch below will be dropped silently —
        // log it so the failure is diagnosable rather than invisible.
        if (!Settings.canDrawOverlays(appContext)) {
            DiagnosticLog.e("Boot", "overlay permission missing — launch may be dropped by BAL")
        }

        try {
            val launch = Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launch)
            DiagnosticLog.i("Boot", "startActivity(MainActivity) called")
        } catch (t: Throwable) {
            DiagnosticLog.e("Boot", "boot startActivity failed (${t.javaClass.simpleName})", t)
        }
    }

    private companion object {
        // QUICKBOOT_POWERON is fired by many headunits / Chinese ROMs when resuming
        // from sleep instead of a full cold boot — include both so the carbox has a
        // chance regardless of which lifecycle it uses on ignition.
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
```

Note: this references `appContext.settingsDataStore`, the `internal Context` extension declared in `ServiceLocator.kt` (Task 1) — already `internal` so it is visible here in the same module. A `DataStore` for a given file name must be created once per process; both `ServiceLocator` and `BootReceiver` go through this single `settingsDataStore` extension, so they share the one instance.

- [ ] **Step 2: Restore manifest entries**

In `app/src/main/AndroidManifest.xml`:

Add the permission next to the other `uses-permission` lines (after `SYSTEM_ALERT_WINDOW`):

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Add the receiver inside `<application>` (e.g. after the last `<service>`):

```xml
<receiver
    android:name=".boot.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

Do **not** add a `<service>` for boot — there is no `BootLaunchService`.

- [ ] **Step 3: Update docs**

In `ONBOARDING.md`, restore the note that the app must be opened once before broadcasts (including `BOOT_COMPLETED`) are delivered. Change the current paragraph under "## 2. Open Copilot once":

```
Android keeps an app that has never been opened by a human in a stopped state
(no services, no broadcasts). Tap the icon.
```

to:

```
Android does not deliver broadcasts (including `BOOT_COMPLETED`) to an app that
has never been opened by a human. Tap the icon once. Auto-start on boot is then
available as an opt-in toggle under Settings (the gear on the Home screen).
```

In `docs/RELEASING.md`, restore "boot launch" to the inherited-permissions list. Change:

```
> granted permissions (overlay, accessibility). Keep the repo private and keep
```

to:

```
> granted permissions (overlay, accessibility, boot launch). Keep the repo private and keep
```

- [ ] **Step 4: Build the full check** *(on Mac)*

Run: `make check`
Expected: BUILD SUCCESSFUL; unit tests pass; lint clean.

- [ ] **Step 5: Manual boot test** *(on carbox)*

- With the toggle **off**: reboot the carbox → Copilot does **not** launch (same as today). Diagnostic log shows `received action=...` then `auto-start disabled, skipping`.
- With the toggle **on**: reboot the carbox → Copilot pops to the foreground. Diagnostic log shows `received action=...` then `startActivity(MainActivity) called`.
- Re-grant the accessibility service after install if needed (force-stop/reinstall disables it).

- [ ] **Step 6: (deferred) Commit** — do NOT commit now.

---

### Final: Commit (gated on user approval)

Only after the user has reviewed and tested on their Mac and explicitly says "commit":

```bash
git add app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt \
        app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt \
        app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/vladutu/copilot/boot/BootReceiver.kt \
        app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt \
        app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt \
        app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt \
        app/src/main/java/com/vladutu/copilot/MainActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/AndroidManifest.xml \
        ONBOARDING.md docs/RELEASING.md
git commit -m "feat: auto-start on boot behind a Settings toggle (FGS-free boot path)"
```

(Single commit is appropriate: the feature does not compile in partial states because Task 3 changes interlocking signatures.)
