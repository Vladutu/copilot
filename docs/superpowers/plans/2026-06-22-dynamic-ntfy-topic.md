# Copilot — Dynamic ntfy topic / device pairing (TDD implementation plan)

**Date:** 2026-06-22
**Repo:** Copilot (`/home/geo/projects/Copilot`)
**Spec:** `/home/geo/projects/Pilot/docs/superpowers/specs/2026-06-22-dynamic-ntfy-topic-design.md` (Sections 1 & 2)
**Scope:** Copilot only — the receiver that **owns and generates** the topic. Pilot and Wingman have their own separate plans.

> **Commit reality (read first).** Georgian builds and tests every change on his Mac, then says "commit". Each task below ends with a normal `git commit` step for structure, but **do not actually run `git commit` at code-writing time** — only when Georgian says so. Likewise **do not run gradle** here (no Android SDK on this Linux box); the "run test" commands document the executor's intent and the expected result Georgian will see on his Mac.

---

## Goal

Replace the single hardcoded `Config.NTFY_TOPIC` with a per-install **dynamic topic** that Copilot mints on first run, persists in DataStore, exposes as a `Flow`, subscribes to live (regenerate with no app restart), and surfaces in a new **Pairing** UI section (truncated topic + Copy, Show-QR dialog, Regenerate-with-confirm).

## Architecture

- **`SettingsStore`** (existing, DataStore `copilot_settings`) gains a `ntfy_topic` string key, a `topicFlow: Flow<String?>`, an `ensureTopic(): String` (generate-and-save once), and a `regenerateTopic(): String`.
- **`TopicGenerator`** — a new pure object: `SecureRandom` → 16 bytes → lowercase hex → `copilot-` prefix, plus a `validate` / regex helper. Pure logic ⇒ fully unit-tested.
- **`PairingUri`** — a new pure object building `pilot://pair?topic=<topic>`. Pure logic ⇒ unit-tested.
- **`ListenerService.runLoop()`** consumes `settingsStore.topicFlow` via `collectLatest { topic -> subscribe(topic) }`; `ensureTopic()` runs first so a fresh install has a topic. `collectLatest` cancels the in-flight subscription and resubscribes on change ⇒ regenerate is live.
- **`StatusScreen`** reads the dynamic topic (passed in as a parameter from `MainActivity`).
- **`SettingsScreen`** gains a Pairing section; QR rendered with zxing `BarcodeEncoder` into a Compose `Image`.
- **`Config.NTFY_TOPIC`** removed (keep `NTFY_BASE`, `MAX_MESSAGE_AGE_SEC`).

## Tech Stack

- Kotlin 2.4.0, AGP 9.2.1, Compose BOM 2026.06.00, Material3, Navigation-Compose 2.9.8.
- DataStore Preferences 1.2.1; kotlinx-coroutines 1.11.0.
- New dependency: **`com.google.zxing:core`** (QR bitmap generation; no Play Services).
- Tests: JUnit4 (`junit` 4.13.2) + `kotlinx-coroutines-test` + `TemporaryFolder` + `PreferenceDataStoreFactory` (matches existing `SettingsStoreTest` / `CategoryStoreTest`).

## Global Constraints (verbatim)

- Package: **`com.vladutu.copilot`**
- Topic format: **`copilot-` followed by 32 lowercase hex characters** (16 secure-random bytes → hex).
- Topic regex: **`^copilot-[0-9a-f]{32}$`**
- QR payload URI: **`pilot://pair?topic=copilot-<32hex>`**
- minSdk **29** / targetSdk **34** (compileSdk 37, Java 17).
- **Do NOT run gradle — no Android SDK on this Linux box; Georgian builds/tests on his Mac.**
- **Do NOT commit at code-writing time — only when Georgian says commit.**

---

## File Structure

| Action | Path |
|---|---|
| Create | `app/src/main/java/com/vladutu/copilot/settings/TopicGenerator.kt` |
| Create | `app/src/test/java/com/vladutu/copilot/settings/TopicGeneratorTest.kt` |
| Create | `app/src/main/java/com/vladutu/copilot/settings/PairingUri.kt` |
| Create | `app/src/test/java/com/vladutu/copilot/settings/PairingUriTest.kt` |
| Modify | `app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt` |
| Modify | `app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt` |
| Modify | `app/src/main/java/com/vladutu/copilot/service/ListenerService.kt` |
| Modify | `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt` |
| Modify | `app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt` |
| Modify | `app/src/main/java/com/vladutu/copilot/MainActivity.kt` |
| Modify | `app/src/main/res/values/strings.xml` |
| Modify | `app/src/main/java/com/vladutu/copilot/config/Config.kt` |
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |

> **Note on `PairingUri`/`TopicGenerator` package:** kept in `settings` (not a new `pairing` package) so they sit next to `SettingsStore` and import without ceremony. `PairingUri` builds the URI with plain string concatenation (no `android.net.Uri`) so it is a pure JVM unit testable without Robolectric — the topic is already `^copilot-[0-9a-f]{32}$`, which needs no percent-encoding.

---

## Task 1 — `TopicGenerator`: generate + validate (pure unit, TDD)

**Files**
- Create (Test): `app/src/test/java/com/vladutu/copilot/settings/TopicGeneratorTest.kt`
- Create: `app/src/main/java/com/vladutu/copilot/settings/TopicGenerator.kt`

**Interfaces**
- Produces:
  - `object TopicGenerator`
  - `val REGEX: Regex` = `Regex("^copilot-[0-9a-f]{32}\$")`
  - `fun generate(random: java.security.SecureRandom = SecureRandom()): String`
  - `fun isValid(topic: String?): Boolean`

**Steps**

1. Write the failing test file.

```kotlin
package com.vladutu.copilot.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class TopicGeneratorTest {

    @Test fun `generate matches the topic regex`() {
        val topic = TopicGenerator.generate()
        assertTrue("'$topic' should match ^copilot-[0-9a-f]{32}\$", TopicGenerator.REGEX.matches(topic))
    }

    @Test fun `generate produces copilot prefix and 32 hex chars`() {
        val topic = TopicGenerator.generate()
        assertTrue(topic.startsWith("copilot-"))
        val hex = topic.removePrefix("copilot-")
        assertEquals(32, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun `generate is deterministic for a seeded SecureRandom`() {
        // Two SecureRandoms seeded identically must yield the same 16 bytes → same topic.
        val a = SecureRandom().apply { setSeed(byteArrayOf(1, 2, 3, 4)) }
        val b = SecureRandom().apply { setSeed(byteArrayOf(1, 2, 3, 4)) }
        assertEquals(TopicGenerator.generate(a), TopicGenerator.generate(b))
    }

    @Test fun `generate is overwhelmingly unique across calls`() {
        val set = (1..200).map { TopicGenerator.generate() }.toSet()
        assertEquals(200, set.size)
    }

    @Test fun `isValid accepts a generated topic`() {
        assertTrue(TopicGenerator.isValid(TopicGenerator.generate()))
    }

    @Test fun `isValid rejects null empty wrong-prefix wrong-length and uppercase`() {
        assertFalse(TopicGenerator.isValid(null))
        assertFalse(TopicGenerator.isValid(""))
        assertFalse(TopicGenerator.isValid("pilot-0123456789abcdef0123456789abcdef"))
        assertFalse(TopicGenerator.isValid("copilot-0123"))
        assertFalse(TopicGenerator.isValid("copilot-0123456789ABCDEF0123456789abcdef"))
        assertFalse(TopicGenerator.isValid("copilot-0123456789abcdef0123456789abcdefgh"))
    }
}
```

2. Run the test (expected **FAIL** — `TopicGenerator` does not exist / unresolved reference):

```
./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.settings.TopicGeneratorTest"
```

3. Create the implementation.

```kotlin
package com.vladutu.copilot.settings

import java.security.SecureRandom

/**
 * Mints and validates the per-install ntfy topic.
 *
 * Format (shared across Copilot/Pilot/Wingman): `copilot-` + 32 lowercase hex chars,
 * derived from 16 secure-random bytes. The `copilot-` prefix keeps topics recognizable
 * and drop-in compatible with the old hardcoded value's shape.
 */
object TopicGenerator {

    const val PREFIX = "copilot-"

    /** Single validation rule used at every entry point. */
    val REGEX = Regex("^copilot-[0-9a-f]{32}\$")

    /** 16 random bytes → lowercase hex → prefixed. `random` is injectable for tests. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val hex = buildString(32) {
            for (b in bytes) append("%02x".format(b))
        }
        return PREFIX + hex
    }

    fun isValid(topic: String?): Boolean = topic != null && REGEX.matches(topic)
}
```

4. Run the test (expected **PASS**, 6 tests green).

5. Commit (only when Georgian says so):

```
git add app/src/main/java/com/vladutu/copilot/settings/TopicGenerator.kt \
        app/src/test/java/com/vladutu/copilot/settings/TopicGeneratorTest.kt
git commit -m "Copilot: add TopicGenerator (secure-random topic + validation)"
```

---

## Task 2 — `PairingUri`: build `pilot://pair?topic=...` (pure unit, TDD)

**Files**
- Create (Test): `app/src/test/java/com/vladutu/copilot/settings/PairingUriTest.kt`
- Create: `app/src/main/java/com/vladutu/copilot/settings/PairingUri.kt`

**Interfaces**
- Produces:
  - `object PairingUri`
  - `fun forTopic(topic: String): String` → `"pilot://pair?topic=$topic"`

**Steps**

1. Write the failing test.

```kotlin
package com.vladutu.copilot.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingUriTest {

    @Test fun `forTopic builds the pilot pair URI`() {
        val topic = "copilot-0123456789abcdef0123456789abcdef"
        assertEquals("pilot://pair?topic=$topic", PairingUri.forTopic(topic))
    }

    @Test fun `forTopic round-trips a freshly generated topic`() {
        val topic = TopicGenerator.generate()
        val uri = PairingUri.forTopic(topic)
        assertEquals("pilot://pair?topic=", uri.substringBefore(topic))
        assertEquals(topic, uri.substringAfter("topic="))
    }
}
```

2. Run the test (expected **FAIL** — `PairingUri` unresolved):

```
./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.settings.PairingUriTest"
```

3. Create the implementation.

```kotlin
package com.vladutu.copilot.settings

/**
 * The QR / pairing payload. Encoding a URI (not the bare topic) lets Pilot's scanner
 * confirm a QR is genuinely a pairing code and reject unrelated QRs.
 *
 * No percent-encoding needed: a valid topic is `^copilot-[0-9a-f]{32}$`, all
 * URI-safe characters.
 */
object PairingUri {
    const val SCHEME = "pilot"
    const val HOST = "pair"

    fun forTopic(topic: String): String = "$SCHEME://$HOST?topic=$topic"
}
```

4. Run the test (expected **PASS**, 2 tests green).

5. Commit (only on Georgian's say-so):

```
git add app/src/main/java/com/vladutu/copilot/settings/PairingUri.kt \
        app/src/test/java/com/vladutu/copilot/settings/PairingUriTest.kt
git commit -m "Copilot: add PairingUri builder for pilot://pair?topic="
```

---

## Task 3 — `SettingsStore`: persist topic + ensureTopic + regenerate (TDD)

**Files**
- Modify (Test): `app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt`

**Interfaces**
- Consumes: `TopicGenerator.generate()`, `TopicGenerator.isValid(...)`.
- Produces (new members on `class SettingsStore(private val dataStore: DataStore<Preferences>)`):
  - `val topicFlow: Flow<String?>`
  - `suspend fun ensureTopic(): String`
  - `suspend fun regenerateTopic(): String`

**Steps**

1. Add failing tests to the existing `SettingsStoreTest` (append these methods inside the class; existing imports already cover `first`, `runTest`, `assertEquals`). Add these imports at the top: `import org.junit.Assert.assertNotEquals`, `import org.junit.Assert.assertNull`, `import org.junit.Assert.assertTrue`, and `import com.vladutu.copilot.settings.TopicGenerator`.

```kotlin
    @Test fun `topicFlow is null before any topic exists`() = runTest {
        assertNull(store.topicFlow.first())
    }

    @Test fun `ensureTopic generates a valid topic and persists it`() = runTest {
        val topic = store.ensureTopic()
        assertTrue("'$topic' should be valid", TopicGenerator.isValid(topic))
        assertEquals(topic, store.topicFlow.first())
    }

    @Test fun `ensureTopic is idempotent - second call returns the same topic`() = runTest {
        val first = store.ensureTopic()
        val second = store.ensureTopic()
        assertEquals(first, second)
        assertEquals(first, store.topicFlow.first())
    }

    @Test fun `regenerateTopic replaces the stored topic with a new valid one`() = runTest {
        val original = store.ensureTopic()
        val fresh = store.regenerateTopic()
        assertNotEquals(original, fresh)
        assertTrue(TopicGenerator.isValid(fresh))
        assertEquals(fresh, store.topicFlow.first())
    }
```

2. Run (expected **FAIL** — `topicFlow` / `ensureTopic` / `regenerateTopic` unresolved):

```
./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.settings.SettingsStoreTest"
```

3. Edit `SettingsStore.kt` to its full new contents.

```kotlin
package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private val topicMutex = Mutex()

    val autoStartFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START] ?: false
    }

    suspend fun setAutoStart(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_START] = enabled }
    }

    /** Null until [ensureTopic] (or [regenerateTopic]) has minted one. */
    val topicFlow: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_NTFY_TOPIC] }

    /**
     * Returns the persisted topic, minting + saving one on first call. Idempotent.
     * The mutex serializes the read-then-write so two concurrent first-run callers
     * (e.g. the service and the UI) can't mint two different topics.
     */
    suspend fun ensureTopic(): String = topicMutex.withLock {
        val existing = topicFlow.first()
        if (existing != null) return existing
        val minted = TopicGenerator.generate()
        dataStore.edit { prefs -> prefs[KEY_NTFY_TOPIC] = minted }
        minted
    }

    /** Mints and saves a new topic unconditionally (destructive re-pair). */
    suspend fun regenerateTopic(): String = topicMutex.withLock {
        val minted = TopicGenerator.generate()
        dataStore.edit { prefs -> prefs[KEY_NTFY_TOPIC] = minted }
        minted
    }

    private companion object {
        val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val KEY_NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
    }
}
```

4. Run (expected **PASS** — 4 new + 2 existing auto-start tests green).

5. Commit (Georgian's say-so):

```
git add app/src/main/java/com/vladutu/copilot/settings/SettingsStore.kt \
        app/src/test/java/com/vladutu/copilot/settings/SettingsStoreTest.kt
git commit -m "Copilot: persist dynamic ntfy topic in SettingsStore (ensureTopic/regenerate)"
```

---

## Task 4 — `ListenerService.runLoop()`: subscribe to the dynamic topic live

**Files**
- Modify: `app/src/main/java/com/vladutu/copilot/service/ListenerService.kt`

**Interfaces**
- Consumes: `CopilotApp.locator.settingsStore` (`topicFlow`, `ensureTopic()`), `NtfySubscriber(base, topic, maxAgeSec)`.
- Produces: a `runLoop()` that resubscribes on topic change with no restart.

> **Verification:** no unit test (the loop is an infinite network coroutine; the project's `NtfySubscriberTest` covers the subscriber itself). Verified **manually on device** per the manual checklist at the end. The change is a mechanical hoist of the existing subscribe loop into a `collectLatest` over `topicFlow`.

**Steps**

1. Add imports near the other `kotlinx.coroutines.flow` imports:

```kotlin
import kotlinx.coroutines.flow.collectLatest
```

2. Replace the whole `runLoop()` body. The existing per-result `when` block is unchanged — it is extracted verbatim into a private `subscribe(topic)` so `collectLatest` can cancel/restart it cleanly.

Replace lines 68–129 (`private suspend fun runLoop() { ... }`) with:

```kotlin
    private suspend fun runLoop() {
        val app = applicationContext as CopilotApp
        val settings = app.locator.settingsStore
        // Make sure a freshly installed Copilot has a topic before it subscribes.
        settings.ensureTopic()

        // collectLatest cancels the in-flight subscribe() and starts a new one when the
        // topic changes (regenerate), so a re-pair takes effect with no app restart.
        settings.topicFlow.collectLatest { topic ->
            if (topic == null) return@collectLatest
            subscribe(topic)
        }
    }

    private suspend fun subscribe(topic: String) {
        val subscriber = NtfySubscriber(
            base = Config.NTFY_BASE,
            topic = topic,
            maxAgeSec = Config.MAX_MESSAGE_AGE_SEC,
        )
        val launcher = AppLauncher(applicationContext)
        val app = applicationContext as CopilotApp
        val history = app.locator.historyRepository
        val artwork = app.locator.artworkCache

        state.value = state.value.copy(conn = ConnState.Reconnecting)

        subscriber.subscribe().collect { result ->
            // Any result coming through means the stream is alive.
            state.value = state.value.copy(conn = ConnState.Connected)

            when (result) {
                is ParseResult.Accepted -> {
                    val msg = result.message
                    val outcome = withContext(Dispatchers.Main) { launcher.launch(msg) }
                    val ok = outcome is AppLauncher.Result.Ok
                    val label = when (msg.cmd) {
                        "ytmusic" -> "play"
                        "waze", "maps" -> "navigate"
                        "radio" -> "listen"
                        else -> msg.cmd
                    }
                    val text = when (outcome) {
                        AppLauncher.Result.Ok -> "▶ $label · launched"
                        is AppLauncher.Result.Failed -> {
                            Log.w(TAG, "launch failed: ${outcome.reason}")
                            "✗ $label · ${outcome.reason}"
                        }
                    }
                    if (ok) {
                        val savedAt = System.currentTimeMillis() / 1000L
                        val item = SavedItem.from(msg, savedAt)
                        if (msg.savesToHistory()) {
                            history.save(item)
                            msg.imageUrl?.let { imgUrl ->
                                scope.launch { artwork.download(imgUrl, item.form, item.id) }
                            }
                        }
                        // Always request the bubble so it appears once the target app is in front.
                        // If MainActivity is currently resumed, BubbleController suppresses the start
                        // and re-fires it from onPause() when launching Music/Waze backgrounds us.
                        BubbleController.requestShow(applicationContext)
                    }
                    appendRecent(text, ok = ok, skewSec = result.skewSec)
                }
                is ParseResult.Category -> {
                    app.locator.categoryStore.add(result.keyword)
                    appendRecent("✚ category · ${result.keyword}", ok = true, skewSec = result.skewSec)
                }
                is ParseResult.Rejected -> {
                    appendRecent("✗ ${result.reason}", ok = false, skewSec = result.skewSec)
                }
                ParseResult.Skipped -> Unit // never reaches us; subscriber filters
            }
        }
    }
```

3. Confirm the file still compiles in your head: `Config.NTFY_TOPIC` is no longer referenced anywhere in this file (only `Config.NTFY_BASE` and `Config.MAX_MESSAGE_AGE_SEC` remain). The unused `MutableStateFlow` import is still used (`state`).

4. Run the existing service/subscriber tests to confirm nothing regressed (expected **PASS** — these don't touch the loop wiring):

```
./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.service.*" --tests "com.vladutu.copilot.net.NtfySubscriberTest"
```

5. Commit (Georgian's say-so):

```
git add app/src/main/java/com/vladutu/copilot/service/ListenerService.kt
git commit -m "Copilot: ListenerService subscribes to dynamic topic via collectLatest"
```

---

## Task 5 — `StatusScreen` reads the dynamic topic

**Files**
- Modify: `app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/MainActivity.kt`

**Interfaces**
- Produces: `StatusScreen(state: UiState, topic: String?, onBack: () -> Unit)`.
- Consumes (in `MainActivity`): `app.locator.settingsStore.topicFlow`.

> **Verification:** manual on device (Compose display). The change is a parameter add + swapping `Config.NTFY_TOPIC` for the passed value.

**Steps**

1. In `StatusScreen.kt`, add `topic: String?` to the signature and drop the `Config.NTFY_TOPIC` read. Change the function header (line 35):

```kotlin
@Composable
fun StatusScreen(state: UiState, topic: String?, onBack: () -> Unit) {
```

2. Replace the topic `Text` block (lines 96–100) with:

```kotlin
        Text(
            text = "topic: " + (topic?.take(16)?.plus("…") ?: "—"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
```

3. `Config` is still imported and used by the clock-skew block (`Config.MAX_MESSAGE_AGE_SEC`), so leave the `import com.vladutu.copilot.config.Config` line.

4. In `MainActivity.kt`, update the `composable("status")` block (lines 249–255) to collect and pass the topic:

```kotlin
        composable("status") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            val topic by app.locator.settingsStore.topicFlow
                .collectAsStateWithLifecycle(initialValue = null)
            StatusScreen(
                state = uiState,
                topic = topic,
                onBack = { nav.popBackStack() },
            )
        }
```

5. (No automated test.) Run the full unit suite to confirm nothing references the removed call yet (it still does in `SettingsScreen`/`Config` until later tasks; this task compiles cleanly on its own because `Config.NTFY_TOPIC` still exists until Task 8):

```
./gradlew :app:testDebugUnitTest
```

6. Commit (Georgian's say-so):

```
git add app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt \
        app/src/main/java/com/vladutu/copilot/MainActivity.kt
git commit -m "Copilot: StatusScreen shows the dynamic topic"
```

---

## Task 6 — Add zxing dependency for QR generation

**Files**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces**
- Produces: `com.google.zxing:core` on the implementation classpath, exposing `com.google.zxing.qrcode.QRCodeWriter`, `BitMatrix`, `EncodeHintType`.

> **Note:** the design names zxing `BarcodeEncoder`. `BarcodeEncoder` lives in the Android-only `com.journeyapps:zxing-android-embedded` artifact (used by Pilot for *scanning*). Copilot only needs to *generate* a bitmap, which the pure-Java `com.google.zxing:core` does via `QRCodeWriter` (Task 7 includes the small bytes→Bitmap helper). This keeps Copilot off the embedded scanner dependency it has no use for.

**Steps**

1. In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
zxing = "3.5.3"
```

   and to `[libraries]`:

```toml
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
```

2. In `app/build.gradle.kts`, add under the other `implementation(...)` lines (e.g. after `implementation(libs.newpipe.extractor)`):

```kotlin
    implementation(libs.zxing.core)
```

3. (No test.) Georgian's Mac will resolve the dependency on next build.

4. Commit (Georgian's say-so):

```
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Copilot: add com.google.zxing:core for QR generation"
```

---

## Task 7 — Settings "Pairing" section: Copy, Show QR, Regenerate

**Files**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/vladutu/copilot/MainActivity.kt`

**Interfaces**
- Produces: `SettingsScreen(autoStart, onAutoStartChange, topic: String?, onCopyTopic, onRegenerate, onOpenLogs, onBack)`.
- Consumes: `PairingUri.forTopic(topic)`, `com.google.zxing.qrcode.QRCodeWriter`, `app.locator.settingsStore.topicFlow` / `regenerateTopic()`, `ClipboardManager`.

> **Verification:** manual on device — Compose UI, clipboard, dialogs, and the QR `Bitmap` can't be meaningfully unit-tested. `PairingUri` (Task 2) and `TopicGenerator` (Task 1) carry the logic that *is* tested. The QR rendering helper uses no Compose, so it would in principle be Robolectric-testable, but a pixel assertion is low value; verify by eye on device.

**Steps**

1. Add strings to `app/src/main/res/values/strings.xml` (before `</resources>`):

```xml
    <string name="settings_pairing_label">Pairing</string>
    <string name="settings_topic_none">No topic yet</string>
    <string name="settings_copy_topic">Copy</string>
    <string name="settings_topic_copied">Topic copied</string>
    <string name="settings_show_qr">Show QR code</string>
    <string name="settings_qr_title">Scan with Pilot</string>
    <string name="settings_regenerate">Regenerate topic</string>
    <string name="settings_regenerate_title">Regenerate topic?</string>
    <string name="settings_regenerate_message">This will disconnect Pilot and Wingman until you re-pair them. Continue?</string>
    <string name="settings_regenerate_confirm">Regenerate</string>
    <string name="settings_regenerate_cancel">Cancel</string>
    <string name="settings_qr_close">Close</string>
```

2. Rewrite `SettingsScreen.kt` to its full new contents. The QR bitmap is produced by a private `qrBitmap(...)` helper using `QRCodeWriter` (pure zxing-core), wrapped in `remember(topic)`.

```kotlin
package com.vladutu.copilot.ui.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vladutu.copilot.R
import com.vladutu.copilot.settings.PairingUri
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.permissions.PermissionHelpers

@Composable
fun SettingsScreen(
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    topic: String?,
    onCopyTopic: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var confirmRegen by remember { mutableStateOf(false) }

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

        // Pairing section.
        Text(
            text = stringResource(R.string.settings_pairing_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = topic?.let { "${it.take(16)}…" } ?: stringResource(R.string.settings_topic_none),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCopyTopic, enabled = topic != null) {
                Text(stringResource(R.string.settings_copy_topic))
            }
            OutlinedButton(onClick = { showQr = true }, enabled = topic != null) {
                Text(stringResource(R.string.settings_show_qr))
            }
        }
        OutlinedButton(onClick = { confirmRegen = true }) {
            Text(stringResource(R.string.settings_regenerate))
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

    if (showQr && topic != null) {
        val bitmap = remember(topic) { qrBitmap(PairingUri.forTopic(topic), 600) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text(stringResource(R.string.settings_qr_close))
                }
            },
            title = { Text(stringResource(R.string.settings_qr_title)) },
            text = {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.settings_qr_title),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            },
        )
    }

    if (confirmRegen) {
        AlertDialog(
            onDismissRequest = { confirmRegen = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegen = false
                    onRegenerate()
                }) {
                    Text(stringResource(R.string.settings_regenerate_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegen = false }) {
                    Text(stringResource(R.string.settings_regenerate_cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_regenerate_title)) },
            text = { Text(stringResource(R.string.settings_regenerate_message)) },
        )
    }
}

/** Renders [content] (the pilot://pair URI) as a square QR [Bitmap] of [sizePx]. */
private fun qrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp
}
```

3. In `MainActivity.kt`, add imports near the top with the other `androidx`/android imports:

```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
```

4. Replace the `composable("settings")` block (lines 257–268) to collect the topic and wire Copy + Regenerate:

```kotlin
        composable("settings") {
            val autoStart by app.locator.settingsStore.autoStartFlow
                .collectAsStateWithLifecycle(initialValue = false)
            val topic by app.locator.settingsStore.topicFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val copiedMsg = stringResource(R.string.settings_topic_copied)
            SettingsScreen(
                autoStart = autoStart,
                onAutoStartChange = { enabled ->
                    app.applicationScope.launch { app.locator.settingsStore.setAutoStart(enabled) }
                },
                topic = topic,
                onCopyTopic = {
                    topic?.let { t ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Copilot topic", t))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    }
                },
                onRegenerate = {
                    app.applicationScope.launch { app.locator.settingsStore.regenerateTopic() }
                },
                onOpenLogs = { nav.navigate("logs") },
                onBack = { nav.popBackStack() },
            )
        }
```

5. Run the unit suite (expected **PASS** — no test touches this UI; this verifies the module still compiles in CI on Georgian's Mac):

```
./gradlew :app:testDebugUnitTest
```

6. **Manual on-device checks** (Georgian, on his Mac build):
   - Settings → Pairing shows truncated `copilot-…` topic.
   - Copy puts the full topic on the clipboard (paste elsewhere to confirm) + "Topic copied" toast.
   - Show QR code opens a dialog with a scannable QR encoding `pilot://pair?topic=copilot-<32hex>`.
   - Regenerate shows the confirm dialog with the exact disconnect copy; on confirm the topic changes and the listener reconnects without restarting the app (status pill goes Reconnecting → Connected).

7. Commit (Georgian's say-so):

```
git add app/src/main/res/values/strings.xml \
        app/src/main/java/com/vladutu/copilot/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/vladutu/copilot/MainActivity.kt
git commit -m "Copilot: Pairing settings (copy topic, QR dialog, regenerate)"
```

---

## Task 8 — Remove `Config.NTFY_TOPIC`

**Files**
- Modify: `app/src/main/java/com/vladutu/copilot/config/Config.kt`

**Interfaces**
- Produces: `Config` with `NTFY_BASE` and `MAX_MESSAGE_AGE_SEC` only.

> **Pre-req:** Tasks 4 and 5 already removed the only two `NTFY_TOPIC` references (ListenerService, StatusScreen). This task deletes the constant and confirms nothing else references it.

**Steps**

1. Search for any remaining references (expected: **none** after Tasks 4–5):

```
grep -rn "NTFY_TOPIC" app/src
```

2. Edit `Config.kt` to:

```kotlin
package com.vladutu.copilot.config

object Config {
    const val NTFY_BASE: String = "https://ntfy.sh"

    // Drop messages whose ts is more than this many seconds away from "now".
    // Defends against ntfy's ~12h cache replaying stale commands on reconnect.
    const val MAX_MESSAGE_AGE_SEC: Long = 30L
}
```

3. Run the full unit suite (expected **PASS**; confirms no broken references):

```
./gradlew :app:testDebugUnitTest
```

4. Commit (Georgian's say-so):

```
git add app/src/main/java/com/vladutu/copilot/config/Config.kt
git commit -m "Copilot: remove hardcoded NTFY_TOPIC constant"
```

---

## Final verification checklist (Georgian, on-device)

- Fresh install: a topic is minted on first launch (Settings → Pairing shows one; Status shows the truncated topic).
- A real Pilot/Wingman publish to the shown topic launches in the car (end-to-end).
- Regenerate disconnects the old topic and the listener resubscribes to the new one with no app restart.
- Unit suite green on the Mac: `./gradlew :app:testDebugUnitTest` (TopicGenerator, PairingUri, SettingsStore tests all pass).
