# DriveDeck → Copilot Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold the DriveDeck standalone Android app into Copilot so the carbox runs a single APK with a driver-facing UI plus the existing ntfy listener; bump the Pilot↔Copilot wire to v3 (title + artwork URL + form).

**Architecture:** Pure code-merge inside `Copilot/` repo (DriveDeck repo untouched as reference). Two physical apps remain (Pilot + Copilot); DriveDeck disappears. Each repo gets a SINGLE squashed commit at the end for easy revert.

**Tech Stack:** Kotlin · Jetpack Compose · Navigation-Compose · DataStore Preferences · kotlinx.serialization · OkHttp · Coroutines · Android 14 (minSdk 29 / targetSdk 34).

**Spec:** `Copilot/docs/superpowers/specs/2026-06-02-drivedeck-merge-design.md`

**Working directories:**
- `/home/geo/projects/Copilot` — main work
- `/home/geo/projects/Pilot` — wire-schema producer changes
- `/home/geo/projects/DriveDeck` — READ-ONLY reference for lifted files

---

## Phase 1 — Copilot build infra: deps, plugins, manifest

Goal: Get every dependency and manifest entry the merged app will need, without changing behavior yet. After this phase, Copilot still compiles and runs identically.

### Task 1.1: Extend version catalog

**Files:**
- Modify: `Copilot/gradle/libs.versions.toml`

- [ ] **Step 1: Read current state**

```bash
cat /home/geo/projects/Copilot/gradle/libs.versions.toml
```

- [ ] **Step 2: Replace the file with the extended catalog**

Write `/home/geo/projects/Copilot/gradle/libs.versions.toml` with:

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
coreKtx = "1.13.1"
activityCompose = "1.9.3"
composeBom = "2024.10.01"
okhttp = "4.12.0"
coroutines = "1.8.1"
junit = "4.13.2"
json = "20240303"
datastore = "1.1.1"
kotlinxSerialization = "1.7.3"
navigationCompose = "2.8.1"
lifecycle = "2.8.6"
robolectric = "4.13"
testCoreKtx = "1.6.1"
testExtJunit = "1.2.1"
turbine = "1.1.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
json = { group = "org.json", name = "json", version.ref = "json" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "testCoreKtx" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "testExtJunit" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 3: Verify Copilot still builds**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (Catalog additions are unreferenced, so no behavior change.)

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add gradle/libs.versions.toml
```

### Task 1.2: Wire deps into `app/build.gradle.kts`

**Files:**
- Modify: `Copilot/app/build.gradle.kts`

- [ ] **Step 1: Replace the file**

Write `/home/geo/projects/Copilot/app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vladutu.copilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vladutu.copilot"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // sideloaded; debug-signed is fine
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 2: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Existing tests still pass**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/build.gradle.kts
```

### Task 1.3: Extend `AndroidManifest.xml`

**Files:**
- Modify: `Copilot/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Replace the file**

Write `/home/geo/projects/Copilot/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <queries>
        <package android:name="com.waze" />
        <package android:name="com.google.android.apps.youtube.music" />
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
    </queries>

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:name=".CopilotApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Copilot">

        <activity
            android:name=".StatusActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTask"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:theme="@style/Theme.Copilot">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.ListenerService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

    </application>

</manifest>
```

(Note: we keep `StatusActivity` as the launcher activity for now; Task 7.1 renames it to `MainActivity`.)

- [ ] **Step 2: Build to verify**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/AndroidManifest.xml
```

---

## Phase 2 — Lift DriveDeck packages into Copilot (repackaged copy)

Goal: Move DriveDeck's lifted-as-is code into `com.vladutu.copilot.*` namespace. No UI hookup yet; new files just sit in the tree. Existing Copilot UI continues to work.

Files lifted: `bubble/*`, `di/ServiceLocator.kt`, `ui/theme/*`, `ui/home/BigAppButton.kt`, `ui/permissions/{PermissionGate, PermissionHelpers}.kt`, `launch/PlaylistIdParser.kt`, plus needed string and drawable resources.

### Task 2.1: Lift theme files

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/theme/{Color,Theme,Type}.kt`

- [ ] **Step 1: Copy and repackage each file**

For each of `Color.kt`, `Theme.kt`, `Type.kt`:

```bash
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/ui/theme/Color.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/theme/Color.kt
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/ui/theme/Theme.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/theme/Theme.kt
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/ui/theme/Type.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/theme/Type.kt
```

- [ ] **Step 2: Rewrite package declarations**

In each of the three copied files, replace the first line `package com.georgian.drivedeck.ui.theme` with `package com.vladutu.copilot.ui.theme`. Also replace any internal references to `R` from `com.georgian.drivedeck.R` to `com.vladutu.copilot.R`. Use Read+Edit; if any DriveDeck-specific theme name appears (e.g. `DriveDeckTheme`), rename to `CopilotDriveTheme`.

- [ ] **Step 3: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. The new theme files are present but unused.

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/theme
```

### Task 2.2: Lift PlaylistIdParser

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/launch/PlaylistIdParser.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/launch/PlaylistIdParserTest.kt`

- [ ] **Step 1: Copy file and repackage**

```bash
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/launch/PlaylistIdParser.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/launch/PlaylistIdParser.kt
```

Edit the file: replace `package com.georgian.drivedeck.launch` → `package com.vladutu.copilot.launch`.

- [ ] **Step 2: Copy the matching DriveDeck test**

```bash
cp /home/geo/projects/DriveDeck/app/src/test/kotlin/com/georgian/drivedeck/launch/PlaylistIdParserTest.kt \
   /home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/launch/PlaylistIdParserTest.kt 2>/dev/null || true
```
(If DriveDeck has no such test, skip; we'll write our own in step 3.)

- [ ] **Step 3: If no test was lifted, write a minimal one**

If `Copilot/app/src/test/java/com/vladutu/copilot/launch/PlaylistIdParserTest.kt` doesn't exist, write:

```kotlin
package com.vladutu.copilot.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistIdParserTest {
    @Test fun `parses watch list URL`() {
        assertEquals("OLAK5uy_xxx", PlaylistIdParser.idFromUrl("https://music.youtube.com/watch?list=OLAK5uy_xxx"))
    }
    @Test fun `parses playlist URL`() {
        assertEquals("OLAK5uy_yyy", PlaylistIdParser.idFromUrl("https://music.youtube.com/playlist?list=OLAK5uy_yyy"))
    }
    @Test fun `returns null on non-playlist URL`() {
        assertNull(PlaylistIdParser.idFromUrl("https://music.youtube.com/watch?v=abc"))
    }
}
```

Adjust API surface to whatever DriveDeck's `PlaylistIdParser` actually exposes (Read the lifted file first).

- [ ] **Step 4: Build + test**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/launch/PlaylistIdParser.kt \
    app/src/test/java/com/vladutu/copilot/launch/PlaylistIdParserTest.kt
```

### Task 2.3: Lift permission helpers and gate

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/permissions/PermissionHelpers.kt`
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/permissions/PermissionGate.kt`

- [ ] **Step 1: Copy both files**

```bash
mkdir -p /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/permissions
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/ui/permissions/PermissionHelpers.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/permissions/PermissionHelpers.kt
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/ui/permissions/PermissionGate.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/permissions/PermissionGate.kt
```

- [ ] **Step 2: Repackage and strip mic logic**

In both files:
1. Replace `package com.georgian.drivedeck.ui.permissions` → `package com.vladutu.copilot.ui.permissions`.
2. Replace `com.georgian.drivedeck.R` → `com.vladutu.copilot.R`.
3. Replace `DriveDeckTheme` → `CopilotDriveTheme` if referenced.
4. Remove any reference to `android.Manifest.permission.RECORD_AUDIO`, `hasMic`, microphone permission flows, and any composable that requests/displays mic permission. Read each file; find every mic-related symbol; delete it. Build will tell you if anything is still wired up.

- [ ] **Step 3: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If there's an unresolved reference, it means a mic-related symbol slipped through — find and remove.

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/permissions
```

### Task 2.4: Lift bubble package

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/bubble/{BubbleService, BubbleController, BubbleView}.kt`

- [ ] **Step 1: Copy three files**

```bash
mkdir -p /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/bubble
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/bubble/BubbleService.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubbleService.kt
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/bubble/BubbleController.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubbleController.kt
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/bubble/BubbleView.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubbleView.kt
```

- [ ] **Step 2: Repackage**

For each file: replace `package com.georgian.drivedeck.bubble` → `package com.vladutu.copilot.bubble`; replace all `com.georgian.drivedeck.*` imports with `com.vladutu.copilot.*` equivalents. In `BubbleService.kt`, replace the reference to `DriveDeckApp` with `CopilotApp`, and `MainActivity` with `StatusActivity` (it'll be renamed to MainActivity in Task 7.1; for now point at the existing activity so it still compiles).

In `BubbleService.kt` specifically: the `locator.playlistRepository.bubblePositionFlow / saveBubblePosition` line will need to point at whatever stores the bubble position. For now, stub by adding a temporary `BubblePositionStore` reference inside the locator (next task). To keep this task self-contained: comment out the `repo.bubblePositionFlow.firstOrNull()` and `repo.saveBubblePosition(x, y)` calls. We'll rewire in Task 3.6 once the locator exposes a bubble-position store.

Concretely in `BubbleService.kt`, replace the block:

```kotlin
val repo = (application as DriveDeckApp).locator.playlistRepository
val view = BubbleView(this) { x, y ->
    serviceScope.launch { repo.saveBubblePosition(x, y) }
}
bubbleView = view

serviceScope.launch {
    val pos = repo.bubblePositionFlow.firstOrNull()
    view.show(initialX = pos?.first, initialY = pos?.second)
}
```

with:

```kotlin
val view = BubbleView(this) { _, _ -> /* position persistence wired in Task 3.6 */ }
bubbleView = view
view.show(initialX = null, initialY = null)
```

- [ ] **Step 3: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. Unresolved references → fix them (likely import paths). The bubble service is not registered in the manifest yet; that's fine, it just sits there.

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/bubble
```

### Task 2.5: Lift BigAppButton + needed drawables + strings

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt`
- Create: `Copilot/app/src/main/res/drawable/ic_bubble.xml`
- Create: `Copilot/app/src/main/res/drawable/ic_map_pin.xml` (placeholder for destinations — see step 3)
- Modify: `Copilot/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Copy BigAppButton**

```bash
mkdir -p /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home
cp /home/geo/projects/DriveDeck/app/src/main/kotlin/com/georgian/drivedeck/ui/home/BigAppButton.kt \
   /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt
```

Repackage `com.georgian.drivedeck.ui.home` → `com.vladutu.copilot.ui.home`. Replace `com.georgian.drivedeck.R` → `com.vladutu.copilot.R`.

- [ ] **Step 2: Lift bubble drawable**

```bash
cp /home/geo/projects/DriveDeck/app/src/main/res/drawable/ic_bubble.xml \
   /home/geo/projects/Copilot/app/src/main/res/drawable/ic_bubble.xml
```

- [ ] **Step 3: Create map-pin placeholder drawable**

Write `/home/geo/projects/Copilot/app/src/main/res/drawable/ic_map_pin.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?android:attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z"/>
</vector>
```

- [ ] **Step 4: Lift any DriveDeck fallback drawables BigAppButton references**

Inspect `BigAppButton.kt` for `R.drawable.ic_fallback_waze`, `R.drawable.ic_fallback_ytm`, or any other resource it references that doesn't already exist in Copilot. For each missing one, copy the file from DriveDeck:

```bash
for f in $(grep -oP 'R\.drawable\.\K\w+' /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt); do
  src="/home/geo/projects/DriveDeck/app/src/main/res/drawable/${f}.xml"
  dst="/home/geo/projects/Copilot/app/src/main/res/drawable/${f}.xml"
  if [ -f "$src" ] && [ ! -f "$dst" ]; then cp "$src" "$dst"; fi
done
```

- [ ] **Step 5: Extend `values/strings.xml`**

Read `/home/geo/projects/Copilot/app/src/main/res/values/strings.xml`. Add these inside `<resources>`:

```xml
<string name="bubble_channel_name">Bubble</string>
<string name="bubble_notification_title">Copilot</string>
<string name="bubble_notification_body">Tap to return to Copilot</string>
<string name="stop_bubble">Stop</string>
<string name="home_waze">Waze</string>
<string name="home_playlists">Playlists</string>
<string name="home_songs">Songs</string>
<string name="home_destinations">Destinations</string>
<string name="back_home">Home</string>
<string name="empty_playlists">Send a playlist from Pilot to fill this list</string>
<string name="empty_songs">Send a song from Pilot to fill this list</string>
<string name="empty_destinations">Send a destination from Pilot to fill this list</string>
<string name="confirm_delete_title">Remove?</string>
<string name="confirm_delete_message">Remove \"%1$s\"?</string>
<string name="confirm_delete_yes">Remove</string>
<string name="confirm_delete_no">Cancel</string>
```

- [ ] **Step 6: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/home/BigAppButton.kt \
    app/src/main/res/drawable app/src/main/res/values/strings.xml
```

---

## Phase 3 — Copilot history package + ArtworkCache

Goal: New code that stores saved items per form, with unit tests. Fully isolated; nothing else depends on it yet.

### Task 3.1: Create `Form` enum

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/history/Form.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/history/FormTest.kt`

- [ ] **Step 1: Write failing test**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/history/FormTest.kt`:

```kotlin
package com.vladutu.copilot.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormTest {
    @Test fun `wire values match Pilot conventions`() {
        assertEquals("playlist", Form.PLAYLIST.wire)
        assertEquals("song", Form.SONG.wire)
        assertEquals("destination", Form.DESTINATION.wire)
    }

    @Test fun `fromWire round-trips`() {
        assertEquals(Form.PLAYLIST, Form.fromWire("playlist"))
        assertEquals(Form.SONG, Form.fromWire("song"))
        assertEquals(Form.DESTINATION, Form.fromWire("destination"))
        assertNull(Form.fromWire("unknown"))
        assertNull(Form.fromWire(null))
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.FormTest
```
Expected: FAIL (unresolved reference `Form`).

- [ ] **Step 3: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/history/Form.kt`:

```kotlin
package com.vladutu.copilot.history

import kotlinx.serialization.Serializable

@Serializable
enum class Form {
    PLAYLIST,
    SONG,
    DESTINATION;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): Form? = when (value) {
            "playlist" -> PLAYLIST
            "song" -> SONG
            "destination" -> DESTINATION
            else -> null
        }
    }
}
```

- [ ] **Step 4: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.FormTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/history/Form.kt \
    app/src/test/java/com/vladutu/copilot/history/FormTest.kt
```

### Task 3.2: Create `SavedItem` data class

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/history/SavedItem.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/history/SavedItemTest.kt`

- [ ] **Step 1: Write failing test**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/history/SavedItemTest.kt`:

```kotlin
package com.vladutu.copilot.history

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedItemTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun `round-trips through JSON`() {
        val item = SavedItem(
            form = Form.PLAYLIST,
            id = "OLAK5uy_xxx",
            title = "Morning Drive",
            imageUrl = "https://lh3.googleusercontent.com/abc",
            url = "https://music.youtube.com/watch?list=OLAK5uy_xxx",
            savedAt = 1_717_336_800L,
        )
        val encoded = json.encodeToString(SavedItem.serializer(), item)
        val decoded = json.decodeFromString(SavedItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test fun `nullable fields encode and decode as null`() {
        val item = SavedItem(
            form = Form.DESTINATION,
            id = "abc123",
            title = null,
            imageUrl = null,
            url = "https://waze.com/ul?ll=1,2",
            savedAt = 1L,
        )
        val encoded = json.encodeToString(SavedItem.serializer(), item)
        val decoded = json.decodeFromString(SavedItem.serializer(), encoded)
        assertEquals(item, decoded)
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.SavedItemTest
```
Expected: FAIL (unresolved `SavedItem`).

- [ ] **Step 3: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/history/SavedItem.kt`:

```kotlin
package com.vladutu.copilot.history

import kotlinx.serialization.Serializable

@Serializable
data class SavedItem(
    val form: Form,
    val id: String,
    val title: String?,
    val imageUrl: String?,
    val url: String,
    val savedAt: Long,
)
```

- [ ] **Step 4: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.SavedItemTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/history/SavedItem.kt \
    app/src/test/java/com/vladutu/copilot/history/SavedItemTest.kt
```

### Task 3.3: Implement `HistoryStore` + `HistoryRepository` (with tests)

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/history/HistoryStore.kt`
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/history/HistoryRepository.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/history/HistoryRepositoryTest.kt`

- [ ] **Step 1: Write failing test**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/history/HistoryRepositoryTest.kt`:

```kotlin
package com.vladutu.copilot.history

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: HistoryRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val ds = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("test_history") }
        )
        val store = HistoryStore(ds)
        repo = HistoryRepository(store)
    }

    @After fun tearDown() {
        context.preferencesDataStoreFile("test_history").delete()
    }

    private fun item(form: Form, id: String, savedAt: Long, title: String? = id) =
        SavedItem(form, id, title, null, "url-$id", savedAt)

    @Test fun `save then read returns the item`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(1, list.size)
        assertEquals("a", list[0].id)
    }

    @Test fun `duplicate save is no-op and keeps original savedAt`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L, "first"))
        repo.save(item(Form.PLAYLIST, "a", 200L, "second"))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(1, list.size)
        assertEquals(100L, list[0].savedAt)
        assertEquals("first", list[0].title)
    }

    @Test fun `items are sorted newest first`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        repo.save(item(Form.PLAYLIST, "b", 200L))
        repo.save(item(Form.PLAYLIST, "c", 150L))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(listOf("b", "c", "a"), list.map { it.id })
    }

    @Test fun `delete removes the entry`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        repo.save(item(Form.PLAYLIST, "b", 200L))
        repo.delete(Form.PLAYLIST, "a")
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(listOf("b"), list.map { it.id })
    }

    @Test fun `delete non-existent is no-op`() = runTest {
        repo.delete(Form.PLAYLIST, "nope")
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertTrue(list.isEmpty())
    }

    @Test fun `forms are isolated`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        repo.save(item(Form.SONG, "b", 100L))
        repo.save(item(Form.DESTINATION, "c", 100L))
        assertEquals(listOf("a"), repo.itemsFor(Form.PLAYLIST).first().map { it.id })
        assertEquals(listOf("b"), repo.itemsFor(Form.SONG).first().map { it.id })
        assertEquals(listOf("c"), repo.itemsFor(Form.DESTINATION).first().map { it.id })
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.HistoryRepositoryTest
```
Expected: FAIL (`HistoryStore`/`HistoryRepository` unresolved).

- [ ] **Step 3: Implement `HistoryStore`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/history/HistoryStore.kt`:

```kotlin
package com.vladutu.copilot.history

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HistoryStore(private val dataStore: DataStore<Preferences>) {

    fun itemsFor(form: Form): Flow<List<SavedItem>> =
        dataStore.data.map { prefs -> decode(prefs[keyFor(form)]) }

    suspend fun mutate(form: Form, transform: (List<SavedItem>) -> List<SavedItem>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[keyFor(form)])
            val updated = transform(current)
            prefs[keyFor(form)] = json.encodeToString(updated)
        }
    }

    private fun keyFor(form: Form) = when (form) {
        Form.PLAYLIST -> KEY_PLAYLISTS
        Form.SONG -> KEY_SONGS
        Form.DESTINATION -> KEY_DESTINATIONS
    }

    private fun decode(blob: String?): List<SavedItem> {
        if (blob.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(blob)
        } catch (e: Exception) {
            Log.w(TAG, "history JSON unreadable; resetting", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "HistoryStore"
        val KEY_PLAYLISTS = stringPreferencesKey("saved_playlists")
        val KEY_SONGS = stringPreferencesKey("saved_songs")
        val KEY_DESTINATIONS = stringPreferencesKey("saved_destinations")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
```

- [ ] **Step 4: Implement `HistoryRepository`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/history/HistoryRepository.kt`:

```kotlin
package com.vladutu.copilot.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository(private val store: HistoryStore) {

    private val mutex = Mutex()

    fun itemsFor(form: Form): Flow<List<SavedItem>> =
        store.itemsFor(form).map { list -> list.sortedByDescending { it.savedAt } }

    suspend fun save(item: SavedItem) = mutex.withLock {
        store.mutate(item.form) { current ->
            if (current.any { it.id == item.id }) current
            else current + item
        }
    }

    suspend fun delete(form: Form, id: String) = mutex.withLock {
        store.mutate(form) { current -> current.filterNot { it.id == id } }
    }
}
```

- [ ] **Step 5: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.HistoryRepositoryTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/history app/src/test/java/com/vladutu/copilot/history
```

### Task 3.4: Implement `ArtworkCache` (with tests)

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/history/ArtworkCache.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/history/ArtworkCacheTest.kt`

- [ ] **Step 1: Write failing test**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/history/ArtworkCacheTest.kt`:

```kotlin
package com.vladutu.copilot.history

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArtworkCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private lateinit var cache: ArtworkCache

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        cacheDir = Files.createTempDirectory("artwork").toFile()
        cache = ArtworkCache(client = OkHttpClient(), cacheDir = cacheDir)
    }

    @After fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun bytes(): Buffer = Buffer().writeUtf8("FAKE_JPEG_BYTES")

    @Test fun `downloads to expected path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bytes()))
        val file = cache.download(server.url("/img.jpg").toString(), Form.PLAYLIST, "abc")
        assertTrue(file != null && file.exists())
        assertEquals(File(cacheDir, "artwork/playlist-abc.jpg"), file)
    }

    @Test fun `write-once skips network on second call`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bytes()))
        cache.download(server.url("/img.jpg").toString(), Form.SONG, "vid")
        val before = server.requestCount
        cache.download(server.url("/img.jpg").toString(), Form.SONG, "vid")
        assertEquals(before, server.requestCount)
    }

    @Test fun `failure leaves no file and throws nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val file = cache.download(server.url("/missing.jpg").toString(), Form.PLAYLIST, "x")
        assertEquals(null, file)
        assertFalse(File(cacheDir, "artwork/playlist-x.jpg").exists())
    }

    @Test fun `cached file lookup`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bytes()))
        cache.download(server.url("/img.jpg").toString(), Form.DESTINATION, "d1")
        val found = cache.fileFor(Form.DESTINATION, "d1")
        assertTrue(found.exists())
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.ArtworkCacheTest
```
Expected: FAIL (unresolved `ArtworkCache`).

- [ ] **Step 3: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/history/ArtworkCache.kt`:

```kotlin
package com.vladutu.copilot.history

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ArtworkCache(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {
    fun fileFor(form: Form, id: String): File =
        File(File(cacheDir, "artwork"), "${form.wire}-$id.jpg")

    suspend fun download(imageUrl: String, form: Form, id: String): File? =
        withContext(Dispatchers.IO) {
            val target = fileFor(form, id)
            if (target.exists()) return@withContext target
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            return@withContext try {
                client.newCall(Request.Builder().url(imageUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    tmp.outputStream().use { sink ->
                        resp.body?.byteStream()?.copyTo(sink)
                    }
                }
                if (!tmp.renameTo(target)) {
                    tmp.delete(); null
                } else target
            } catch (e: Exception) {
                Log.w(TAG, "artwork download failed for $imageUrl", e)
                tmp.delete()
                null
            }
        }

    private companion object { const val TAG = "ArtworkCache" }
}
```

- [ ] **Step 4: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.history.ArtworkCacheTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/history/ArtworkCache.kt \
    app/src/test/java/com/vladutu/copilot/history/ArtworkCacheTest.kt
```

### Task 3.5: Implement `BubblePositionStore` (now needed by ServiceLocator)

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubblePositionStore.kt`

- [ ] **Step 1: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubblePositionStore.kt`:

```kotlin
package com.vladutu.copilot.bubble

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BubblePositionStore(private val dataStore: DataStore<Preferences>) {
    val positionFlow: Flow<Pair<Int, Int>?> = dataStore.data.map { prefs ->
        val x = prefs[KEY_X]
        val y = prefs[KEY_Y]
        if (x != null && y != null) x to y else null
    }

    suspend fun save(x: Int, y: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_X] = x
            prefs[KEY_Y] = y
        }
    }

    private companion object {
        val KEY_X = intPreferencesKey("bubble_x")
        val KEY_Y = intPreferencesKey("bubble_y")
    }
}
```

- [ ] **Step 2: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/bubble/BubblePositionStore.kt
```

### Task 3.6: Add `ServiceLocator` and re-wire `BubbleService`

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/CopilotApp.kt`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubbleService.kt`

- [ ] **Step 1: Implement `ServiceLocator`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/di/ServiceLocator.kt`:

```kotlin
package com.vladutu.copilot.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vladutu.copilot.bubble.BubblePositionStore
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.HistoryRepository
import com.vladutu.copilot.history.HistoryStore
import okhttp3.OkHttpClient

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_history")
private val Context.bubbleDataStore: DataStore<Preferences> by preferencesDataStore(name = "copilot_bubble")

class ServiceLocator(private val appContext: Context) {
    val okHttp: OkHttpClient by lazy { OkHttpClient() }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(HistoryStore(appContext.historyDataStore))
    }

    val artworkCache: ArtworkCache by lazy {
        ArtworkCache(okHttp, appContext.cacheDir)
    }

    val bubblePositionStore: BubblePositionStore by lazy {
        BubblePositionStore(appContext.bubbleDataStore)
    }
}
```

- [ ] **Step 2: Read `CopilotApp.kt`**

```bash
cat /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/CopilotApp.kt
```

- [ ] **Step 3: Add `locator` property to `CopilotApp`**

Edit `CopilotApp.kt` — add to the class body (use the Edit tool):

Find the class declaration line (likely `class CopilotApp : Application() {` or similar) and add immediately inside the class:

```kotlin
val locator: com.vladutu.copilot.di.ServiceLocator by lazy {
    com.vladutu.copilot.di.ServiceLocator(applicationContext)
}
```

- [ ] **Step 4: Re-wire `BubbleService` to use the locator**

Edit `Copilot/app/src/main/java/com/vladutu/copilot/bubble/BubbleService.kt`. Find the stubbed block from Task 2.4:

```kotlin
val view = BubbleView(this) { _, _ -> /* position persistence wired in Task 3.6 */ }
bubbleView = view
view.show(initialX = null, initialY = null)
```

Replace with:

```kotlin
val store = (application as com.vladutu.copilot.CopilotApp).locator.bubblePositionStore
val view = BubbleView(this) { x, y ->
    serviceScope.launch { store.save(x, y) }
}
bubbleView = view

serviceScope.launch {
    val pos = store.positionFlow.firstOrNull()
    view.show(initialX = pos?.first, initialY = pos?.second)
}
```

Ensure imports: `kotlinx.coroutines.flow.firstOrNull`, `kotlinx.coroutines.launch`.

- [ ] **Step 5: Build + test**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/di \
    app/src/main/java/com/vladutu/copilot/CopilotApp.kt \
    app/src/main/java/com/vladutu/copilot/bubble/BubbleService.kt
```

---

## Phase 4 — Copilot wire schema v3 + Message rewrite

Goal: Update `Message` to v3. Existing Pilot v2 messages now rejected with a clear reason. Existing Copilot tests updated.

### Task 4.1: Rewrite `Message.kt` to v3

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt`
- Modify: `Copilot/app/src/test/java/com/vladutu/copilot/net/MessageTest.kt` (if exists)

- [ ] **Step 1: Read existing test file**

```bash
cat /home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/net/MessageTest.kt 2>/dev/null || echo "no test file yet"
```

- [ ] **Step 2: Write new test file (replacing if exists)**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/net/MessageTest.kt`:

```kotlin
package com.vladutu.copilot.net

import com.vladutu.copilot.history.Form
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {
    private val now = 1_717_336_800L
    private val maxAge = 60L

    private fun envelope(body: String): String =
        """{"event":"message","message":${escape(body)}}"""

    private fun escape(s: String): String =
        '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"'

    @Test fun `accepts v3 with all fields`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"playlist","url":"https://music.youtube.com/watch?list=L","title":"Title","imageUrl":"https://img/x.jpg"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertEquals(3, msg.v)
        assertEquals("ytmusic", msg.cmd)
        assertEquals(Form.PLAYLIST, msg.form)
        assertEquals("Title", msg.title)
        assertEquals("https://img/x.jpg", msg.imageUrl)
    }

    @Test fun `accepts v3 with null title and imageUrl`() {
        val body = """{"v":3,"ts":$now,"cmd":"waze","form":"destination","url":"https://ul.waze.com/ul?ll=1,2"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertNull(msg.title)
        assertNull(msg.imageUrl)
        assertEquals(Form.DESTINATION, msg.form)
    }

    @Test fun `rejects v2`() {
        val body = """{"v":2,"ts":$now,"cmd":"ytmusic","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertTrue((res as ParseResult.Rejected).reason.contains("v=2"))
    }

    @Test fun `rejects missing form`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("unknown form", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects cmd-form mismatch`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"destination","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects waze cmd with playlist form`() {
        val body = """{"v":3,"ts":$now,"cmd":"waze","form":"playlist","url":"https://ul.waze.com/ul?ll=1,2"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects untrusted host (existing behavior)`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"playlist","url":"https://evil.example/"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("untrusted host", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects stale (existing behavior)`() {
        val body = """{"v":3,"ts":${now - 1000},"cmd":"ytmusic","form":"song","url":"https://music.youtube.com/watch?v=abc"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertTrue((res as ParseResult.Rejected).reason.contains("stale"))
    }
}
```

- [ ] **Step 3: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.net.MessageTest
```
Expected: FAIL (v3 fields not on `Message`).

- [ ] **Step 4: Rewrite `Message.kt`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt`:

```kotlin
package com.vladutu.copilot.net

import com.vladutu.copilot.history.Form
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

data class Message(
    val v: Int,
    val ts: Long,
    val cmd: String,
    val form: Form,
    val url: String,
    val title: String?,
    val imageUrl: String?,
) {
    companion object {
        private const val SCHEMA_VERSION = 3

        private val YT_MUSIC_ALLOWED_PREFIXES = listOf(
            "https://music.youtube.com/",
        )
        private val WAZE_ALLOWED_PREFIXES = listOf(
            "https://ul.waze.com/",
            "https://waze.com/",
            "https://www.waze.com/",
        )

        fun parseEnvelope(line: String, nowSec: Long, maxAgeSec: Long): ParseResult {
            val envelope = try { JSONObject(line) } catch (e: JSONException) {
                return ParseResult.Skipped
            }
            if (envelope.optString("event") != "message") return ParseResult.Skipped
            val payload = envelope.optString("message", "")
            if (payload.isEmpty()) return ParseResult.Skipped

            val body = try { JSONObject(payload) } catch (e: JSONException) {
                return ParseResult.Rejected("malformed inner JSON")
            }

            val v = body.optInt("v", -1)
            if (v != SCHEMA_VERSION) return ParseResult.Rejected("unknown schema v=$v")

            val ts = body.optLong("ts", -1)
            if (ts < 0) return ParseResult.Rejected("missing ts")
            val skew = nowSec - ts
            if (abs(skew) > maxAgeSec) return ParseResult.Rejected("stale (${skew}s)", skew)

            val cmd = body.optString("cmd")
            val allowedPrefixes = when (cmd) {
                "ytmusic" -> YT_MUSIC_ALLOWED_PREFIXES
                "waze" -> WAZE_ALLOWED_PREFIXES
                else -> return ParseResult.Rejected("unknown cmd=$cmd", skew)
            }

            val form = Form.fromWire(body.optString("form").takeIf { it.isNotBlank() })
                ?: return ParseResult.Rejected("unknown form", skew)

            val cmdFormConsistent = when (cmd) {
                "ytmusic" -> form == Form.PLAYLIST || form == Form.SONG
                "waze" -> form == Form.DESTINATION
                else -> false
            }
            if (!cmdFormConsistent) return ParseResult.Rejected("cmd/form mismatch", skew)

            val url = body.optString("url")
            if (url.isBlank()) return ParseResult.Rejected("missing url", skew)
            if (allowedPrefixes.none { url.startsWith(it) }) {
                return ParseResult.Rejected("untrusted host", skew)
            }

            val title = body.optString("title").takeIf { it.isNotBlank() }
            val imageUrl = body.optString("imageUrl").takeIf { it.isNotBlank() }

            return ParseResult.Accepted(
                Message(v = v, ts = ts, cmd = cmd, form = form, url = url, title = title, imageUrl = imageUrl),
                skew,
            )
        }
    }
}

sealed class ParseResult {
    object Skipped : ParseResult()
    data class Rejected(val reason: String, val skewSec: Long? = null) : ParseResult()
    data class Accepted(val message: Message, val skewSec: Long) : ParseResult()
}
```

- [ ] **Step 5: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.net.MessageTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Fix downstream compile errors**

Compile the whole app:

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```

If `ListenerService.kt` or `AppLauncher.kt` reference `msg.cmd` patterns that no longer compile (they should — `cmd` still exists), they should still build. If there's any error, read it and fix the call site in the simplest way that preserves existing behavior; Phase 5 and 6 will properly rewire them.

- [ ] **Step 7: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/net/Message.kt \
    app/src/test/java/com/vladutu/copilot/net/MessageTest.kt
```

---

## Phase 5 — Copilot AppLauncher fusion

Goal: A single launcher that handles both Pilot-driven (Message-based) and UI-driven (SavedItem-based or direct) launches with the fallback chain DriveDeck had.

### Task 5.1: Rewrite `AppLauncher.kt`

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`

- [ ] **Step 1: Write failing test**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`:

```kotlin
package com.vladutu.copilot.launch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {

    private lateinit var context: Context
    private lateinit var launcher: AppLauncher

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        launcher = AppLauncher(context)
    }

    private fun msg(cmd: String, form: Form, url: String) =
        Message(v = 3, ts = 0, cmd = cmd, form = form, url = url, title = null, imageUrl = null)

    @Test fun `launches ytmusic playlist message`() {
        val res = launcher.launch(msg("ytmusic", Form.PLAYLIST, "https://music.youtube.com/watch?list=X"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.`package` == AppLauncher.YT_MUSIC_PKG)
    }

    @Test fun `launches waze destination message`() {
        val res = launcher.launch(msg("waze", Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertTrue(intent.`package` == AppLauncher.WAZE_PKG)
    }

    @Test fun `replay from SavedItem works`() {
        val item = SavedItem(Form.SONG, "abc", null, null, "https://music.youtube.com/watch?v=abc", 0)
        val res = launcher.replay(item)
        assertTrue(res is AppLauncher.Result.Ok)
    }

    @Test fun `openWazeApp launches waze package`() {
        val res = launcher.openWazeApp()
        // Result depends on resolution; both Ok or Failed are acceptable in Robolectric. What matters is no exception thrown.
        assertTrue(res is AppLauncher.Result.Ok || res is AppLauncher.Result.Failed)
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.launch.AppLauncherTest
```
Expected: FAIL.

- [ ] **Step 3: Rewrite `AppLauncher.kt`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`:

```kotlin
package com.vladutu.copilot.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message

class AppLauncher(private val context: Context) {

    sealed class Result {
        object Ok : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Entry point for Pilot-driven launches via ListenerService. */
    fun launch(msg: Message): Result = launchUrl(msg.cmd, msg.form, msg.url)

    /** Entry point for UI-driven re-plays from a saved tile. */
    fun replay(item: SavedItem): Result = launchUrl(cmdForForm(item.form), item.form, item.url)

    /** Open Waze app (no nav target). */
    fun openWazeApp(): Result {
        val launch = context.packageManager.getLaunchIntentForPackage(WAZE_PKG)
            ?: return Result.Failed("Waze not installed")
        return startNewTask(launch)
    }

    private fun cmdForForm(form: Form) = when (form) {
        Form.PLAYLIST, Form.SONG -> "ytmusic"
        Form.DESTINATION -> "waze"
    }

    private fun launchUrl(cmd: String, form: Form, url: String): Result {
        val targetPkg = when (cmd) {
            "ytmusic" -> YT_MUSIC_PKG
            "waze" -> WAZE_PKG
            else -> return Result.Failed("unknown command: $cmd")
        }
        val missingMsg = when (cmd) {
            "ytmusic" -> "YouTube Music not installed"
            "waze" -> "Waze not installed"
            else -> "target app not installed"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "$targetPkg not installed", e)
            Result.Failed(missingMsg)
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    private fun startNewTask(intent: Intent): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent); Result.Ok
        } catch (e: ActivityNotFoundException) {
            Result.Failed("not installed")
        } catch (e: SecurityException) {
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }

    companion object {
        const val TAG = "AppLauncher"
        const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
        const val WAZE_PKG = "com.waze"
    }
}
```

- [ ] **Step 4: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.launch.AppLauncherTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt \
    app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt
```

---

## Phase 6 — Copilot ListenerService save-on-launch

Goal: After a successful launch, save the SavedItem to history; trigger artwork download for non-null `imageUrl`.

### Task 6.1: Modify `ListenerService` to save + cache artwork

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/service/ListenerService.kt`
- Create: `Copilot/app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt`

- [ ] **Step 1: Write failing test for `SavedItem.from(message)` mapping**

Write `/home/geo/projects/Copilot/app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt`:

```kotlin
package com.vladutu.copilot.service

import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.net.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerServiceMappingTest {

    private fun msg(form: Form, url: String, title: String? = null) =
        Message(v = 3, ts = 1_700_000_000L, cmd = if (form == Form.DESTINATION) "waze" else "ytmusic",
                form = form, url = url, title = title, imageUrl = null)

    @Test fun `playlist mapping uses parsed list id`() {
        val m = msg(Form.PLAYLIST, "https://music.youtube.com/watch?list=OLAK5uy_xxx", "Mix")
        val item = SavedItem.from(m, savedAt = 42L)
        assertEquals(Form.PLAYLIST, item.form)
        assertEquals("OLAK5uy_xxx", item.id)
        assertEquals("Mix", item.title)
        assertEquals(42L, item.savedAt)
    }

    @Test fun `song mapping uses parsed video id`() {
        val m = msg(Form.SONG, "https://music.youtube.com/watch?v=abc123")
        val item = SavedItem.from(m, savedAt = 1L)
        assertEquals("abc123", item.id)
    }

    @Test fun `destination mapping uses sha1 of url`() {
        val m = msg(Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2")
        val item = SavedItem.from(m, savedAt = 1L)
        assertEquals(40, item.id.length) // SHA-1 hex
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.service.ListenerServiceMappingTest
```
Expected: FAIL.

- [ ] **Step 3: Add `SavedItem.from(Message, savedAt)` extension**

Edit `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/history/SavedItem.kt`. Append at the bottom of the file (outside the data class):

```kotlin
import com.vladutu.copilot.launch.PlaylistIdParser
import com.vladutu.copilot.net.Message
import java.security.MessageDigest

fun SavedItem.Companion.from(message: Message, savedAt: Long): SavedItem {
    val id = when (message.form) {
        Form.PLAYLIST -> PlaylistIdParser.idFromUrl(message.url)
            ?: sha1(message.url)
        Form.SONG -> Regex("""[?&]v=([A-Za-z0-9_\-]+)""").find(message.url)?.groupValues?.get(1)
            ?: sha1(message.url)
        Form.DESTINATION -> sha1(message.url)
    }
    return SavedItem(
        form = message.form,
        id = id,
        title = message.title,
        imageUrl = message.imageUrl,
        url = message.url,
        savedAt = savedAt,
    )
}

private fun sha1(s: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
```

If kotlin complains about the data-class declaration not having a `companion object`, edit the `SavedItem` declaration to:

```kotlin
@Serializable
data class SavedItem(
    val form: Form,
    val id: String,
    val title: String?,
    val imageUrl: String?,
    val url: String,
    val savedAt: Long,
) {
    companion object
}
```

- [ ] **Step 4: Verify passing**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.copilot.service.ListenerServiceMappingTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Wire save + artwork download into `ListenerService`**

Read `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/service/ListenerService.kt`. Replace the `is ParseResult.Accepted ->` branch inside `runLoop` so the post-launch path saves and triggers artwork:

Before (existing):

```kotlin
is ParseResult.Accepted -> {
    val outcome = withContext(Dispatchers.Main) { launcher.launch(result.message) }
    val ok = outcome is AppLauncher.Result.Ok
    val label = when (result.message.cmd) {
        "ytmusic" -> "play"
        "waze" -> "navigate"
        else -> result.message.cmd
    }
    val text = when (outcome) {
        AppLauncher.Result.Ok -> "▶ $label · launched"
        is AppLauncher.Result.Failed -> {
            Log.w(TAG, "launch failed: ${outcome.reason}")
            "✗ $label · ${outcome.reason}"
        }
    }
    appendRecent(text, ok = ok, skewSec = result.skewSec)
}
```

After:

```kotlin
is ParseResult.Accepted -> {
    val msg = result.message
    val outcome = withContext(Dispatchers.Main) { launcher.launch(msg) }
    val ok = outcome is AppLauncher.Result.Ok
    val label = when (msg.cmd) {
        "ytmusic" -> "play"
        "waze" -> "navigate"
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
        val item = com.vladutu.copilot.history.SavedItem.from(msg, savedAt)
        history.save(item)
        msg.imageUrl?.let { imgUrl ->
            scope.launch { artwork.download(imgUrl, item.form, item.id) }
        }
    }
    appendRecent(text, ok = ok, skewSec = result.skewSec)
}
```

Also at the top of `runLoop`, add (after the `launcher = AppLauncher(applicationContext)` line):

```kotlin
val app = applicationContext as com.vladutu.copilot.CopilotApp
val history = app.locator.historyRepository
val artwork = app.locator.artworkCache
```

- [ ] **Step 6: Build + run all unit tests**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/history/SavedItem.kt \
    app/src/main/java/com/vladutu/copilot/service/ListenerService.kt \
    app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt
```

---

## Phase 7 — Copilot UI: rename activity, NavHost, HomeScreen, list screens, StatusPill

Goal: Activity renamed to `MainActivity`; NavHost added with 5 routes; HomeScreen with 2×2 + status pill; generic SavedListScreen; existing StatusScreen content becomes the `status` route.

### Task 7.1: Rename `StatusActivity` → `MainActivity`

**Files:**
- Rename: `Copilot/app/src/main/java/com/vladutu/copilot/StatusActivity.kt` → `MainActivity.kt`
- Modify: `Copilot/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Read current StatusActivity**

```bash
cat /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/StatusActivity.kt
```

- [ ] **Step 2: Rename file and class**

```bash
git -C /home/geo/projects/Copilot mv app/src/main/java/com/vladutu/copilot/StatusActivity.kt \
                                      app/src/main/java/com/vladutu/copilot/MainActivity.kt
```

Edit `MainActivity.kt`: replace every occurrence of `class StatusActivity` with `class MainActivity`. Update `StatusActivity::class` references inside the file.

- [ ] **Step 3: Update the manifest**

Edit `AndroidManifest.xml`: replace `android:name=".StatusActivity"` with `android:name=".MainActivity"`.

- [ ] **Step 4: Update references in `service/ListenerService.kt`**

The status notification uses `StatusActivity::class.java`. Replace with `MainActivity::class.java`.

- [ ] **Step 5: Update bubble service reference**

In `bubble/BubbleService.kt`, the `openAppIntent` pendingIntent points at `StatusActivity` (set in Task 2.4). Change to `MainActivity::class.java`.

- [ ] **Step 6: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/MainActivity.kt \
    app/src/main/AndroidManifest.xml \
    app/src/main/java/com/vladutu/copilot/service/ListenerService.kt \
    app/src/main/java/com/vladutu/copilot/bubble/BubbleService.kt
```

### Task 7.2: Move existing `StatusScreen` to `ui/status/`

**Files:**
- Move: `Copilot/app/src/main/java/com/vladutu/copilot/ui/StatusScreen.kt` → `ui/status/StatusScreen.kt`

- [ ] **Step 1: Move file**

```bash
mkdir -p /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/status
git -C /home/geo/projects/Copilot mv app/src/main/java/com/vladutu/copilot/ui/StatusScreen.kt \
                                      app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt
```

- [ ] **Step 2: Update package**

Edit `StatusScreen.kt`: replace `package com.vladutu.copilot.ui` → `package com.vladutu.copilot.ui.status`.

- [ ] **Step 3: Add a `BackHomeButton` row to StatusScreen**

Top of the `Column` in `StatusScreen`, before the connection row, insert a composable invocation `BackHomeButton(onBack)`. The composable itself we define in 7.3.

For now, change the `@Composable fun StatusScreen(state: UiState)` signature to `fun StatusScreen(state: UiState, onBack: () -> Unit)`. The `BackHomeButton` reference will be unresolved until Task 7.3 — that's expected.

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/status/StatusScreen.kt
```

(Don't compile yet — `BackHomeButton` is unresolved; Task 7.3 fixes it.)

### Task 7.3: Create `BackHomeButton` composable

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/BackHomeButton.kt`

- [ ] **Step 1: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/BackHomeButton.kt`:

```kotlin
package com.vladutu.copilot.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R

@Composable
fun BackHomeButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onBack,
        modifier = modifier.height(64.dp).padding(end = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(R.string.back_home))
        }
    }
}
```

- [ ] **Step 2: Add the import + usage in `StatusScreen.kt`**

In `StatusScreen.kt`, ensure `import com.vladutu.copilot.ui.BackHomeButton` is added, and at the top of the `Column`:

```kotlin
BackHomeButton(onBack = onBack)
```

- [ ] **Step 3: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (MainActivity still calls the old `StatusScreen(state)` — that'll fail to compile. Fix it temporarily by changing the call to `StatusScreen(state, onBack = {})` in `MainActivity.kt`.)

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/BackHomeButton.kt \
    app/src/main/java/com/vladutu/copilot/MainActivity.kt
```

### Task 7.4: Create `StatusPill`

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt`

- [ ] **Step 1: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt`:

```kotlin
package com.vladutu.copilot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.service.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusPill(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor = when (state.conn) {
        is ConnState.Connected -> Color(0xFF2E7D32)
        is ConnState.Reconnecting -> Color(0xFFF9A825)
        is ConnState.Error -> Color(0xFFC62828)
    }
    val lastTime = state.recent.firstOrNull()?.timeSec?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000))
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(14.dp).background(dotColor, CircleShape))
        if (lastTime != null) {
            Spacer(Modifier.width(8.dp))
            Text(text = lastTime, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/home/StatusPill.kt
```

### Task 7.5: Create `HomeScreen` (2×2 grid + status pill)

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`

- [ ] **Step 1: Implement**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`:

```kotlin
package com.vladutu.copilot.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.UiState

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BigAppButton(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    packageName = AppLauncher.WAZE_PKG,
                    fallbackRes = R.drawable.ic_map_pin,
                    label = stringResource(R.string.home_waze),
                    onClick = onOpenWaze,
                )
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.home_playlists),
                    onClick = onOpenPlaylists,
                )
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.home_songs),
                    onClick = onOpenSongs,
                )
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.home_destinations),
                    onClick = onOpenDestinations,
                )
            }
        }
        StatusPill(
            state = state,
            onClick = onOpenStatus,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
    }
}
```

- [ ] **Step 2: Create `LabelTile` helper**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt`:

```kotlin
package com.vladutu.copilot.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LabelTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp = 24.dp.value.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.displaySmall)
        }
    }
}
```

(Adjust the `shape =` line — that snippet is wrong-syntactically; use `shape = RoundedCornerShape(24.dp)` and add `import androidx.compose.ui.unit.dp`.)

Correct version:

```kotlin
package com.vladutu.copilot.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LabelTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.displaySmall)
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt \
    app/src/main/java/com/vladutu/copilot/ui/home/LabelTile.kt
```

### Task 7.6: Create `SavedTile`, `PageIndicator`, and generic `SavedListScreen`

**Files:**
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt`
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/PageIndicator.kt`
- Create: `Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt`

- [ ] **Step 1: Implement `SavedTile`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt`:

```kotlin
package com.vladutu.copilot.ui.lists

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SavedTile(
    item: SavedItem,
    artworkFile: File,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(item.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(item.id) {
        if (artworkFile.exists()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(artworkFile.absolutePath) }.getOrNull()
            }
            bitmap = bmp?.asImageBitmap()
        }
    }
    Card(
        modifier = modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val bm = bitmap
                if (bm != null) {
                    Image(bitmap = bm, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
                } else {
                    Image(
                        painter = painterResource(
                            id = if (item.form == Form.DESTINATION) R.drawable.ic_map_pin else R.mipmap.ic_launcher_round
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                text = item.title ?: "Untitled · ${item.id.take(8)}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Implement `PageIndicator`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/PageIndicator.kt`:

```kotlin
package com.vladutu.copilot.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Row(modifier = modifier) {
        repeat(pageCount) { idx ->
            val color = if (idx == currentPage) MaterialTheme.colorScheme.primary else Color.Gray
            Box(modifier = Modifier.padding(horizontal = 4.dp).size(10.dp).background(color, CircleShape))
        }
    }
}
```

- [ ] **Step 3: Implement `SavedListScreen`**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt`:

```kotlin
package com.vladutu.copilot.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.ui.BackHomeButton

@Composable
fun SavedListScreen(
    items: List<SavedItem>,
    form: Form,
    artworkCache: ArtworkCache,
    onTap: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }
    val title = when (form) {
        Form.PLAYLIST -> stringResource(R.string.home_playlists)
        Form.SONG -> stringResource(R.string.home_songs)
        Form.DESTINATION -> stringResource(R.string.home_destinations)
    }
    val emptyText = when (form) {
        Form.PLAYLIST -> stringResource(R.string.empty_playlists)
        Form.SONG -> stringResource(R.string.empty_songs)
        Form.DESTINATION -> stringResource(R.string.empty_destinations)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackHomeButton(onBack)
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = emptyText, style = MaterialTheme.typography.titleMedium)
            }
            return
        }

        val pageSize = 6
        val pageCount = (items.size + pageSize - 1) / pageSize
        val pagerState = rememberPagerState(pageCount = { pageCount })

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val start = page * pageSize
            val end = minOf(start + pageSize, items.size)
            val pageItems = items.subList(start, end)
            // 2 rows × 3 cols
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(0 to 3, 3 to 6).forEach { (rowStart, rowEnd) ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in rowStart until rowEnd) {
                            if (i < pageItems.size) {
                                val it = pageItems[i]
                                SavedTile(
                                    item = it,
                                    artworkFile = artworkCache.fileFor(it.form, it.id),
                                    onTap = { onTap(it) },
                                    onLongPress = { pendingDelete = it },
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f).fillMaxSize())
                            }
                        }
                    }
                }
            }
        }

        PageIndicator(
            pageCount = pageCount,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, target.title ?: target.id)) },
            confirmButton = {
                TextButton(onClick = { onDelete(target); pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_no))
                }
            },
        )
    }
}
```

- [ ] **Step 4: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/ui/lists
```

### Task 7.7: Rewrite `MainActivity` to host NavHost

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`

- [ ] **Step 1: Rewrite the file**

Write `/home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt`:

```kotlin
package com.vladutu.copilot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vladutu.copilot.bubble.BubbleController
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.ListenerService
import com.vladutu.copilot.ui.home.HomeScreen
import com.vladutu.copilot.ui.lists.SavedListScreen
import com.vladutu.copilot.ui.permissions.PermissionGate
import com.vladutu.copilot.ui.status.StatusScreen
import com.vladutu.copilot.ui.theme.CopilotDriveTheme

class MainActivity : ComponentActivity() {

    private var showHomeTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startListenerService()
        handleIntent(intent)
        setContent {
            CopilotDriveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionGate {
                        CopilotNav(::leaveToOtherApp, showHomeTrigger)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_HOME, false) == true) {
            showHomeTrigger++
        }
    }

    private fun startListenerService() {
        startForegroundService(Intent(this, ListenerService::class.java))
    }

    override fun onResume() {
        super.onResume()
        BubbleController.onActivityResumed(this)
    }

    override fun onPause() {
        super.onPause()
        BubbleController.onActivityPaused(this)
    }

    private fun leaveToOtherApp() {
        BubbleController.requestShow(this)
        moveTaskToBack(true)
    }

    companion object {
        const val EXTRA_SHOW_HOME = "show_home"
    }
}

@Composable
private fun CopilotNav(onLeftToOtherApp: () -> Unit, showHomeTrigger: Int) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as CopilotApp
    val launcher = remember { AppLauncher(context) }

    LaunchedEffect(showHomeTrigger) {
        if (showHomeTrigger > 0) nav.popBackStack(route = "home", inclusive = false)
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = uiState,
                onOpenWaze = {
                    if (launcher.openWazeApp() is AppLauncher.Result.Ok) onLeftToOtherApp()
                },
                onOpenPlaylists = { nav.navigate("list/playlist") },
                onOpenSongs = { nav.navigate("list/song") },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenStatus = { nav.navigate("status") },
            )
        }

        composable("list/{form}") { entry ->
            val formArg = entry.arguments?.getString("form") ?: return@composable
            val form = Form.fromWire(formArg) ?: return@composable
            val items by app.locator.historyRepository.itemsFor(form).collectAsStateWithLifecycle(emptyList())
            SavedListScreen(
                items = items,
                form = form,
                artworkCache = app.locator.artworkCache,
                onTap = { item ->
                    if (launcher.replay(item) is AppLauncher.Result.Ok) onLeftToOtherApp()
                },
                onDelete = { item ->
                    app.applicationScope.launch {
                        app.locator.historyRepository.delete(item.form, item.id)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable("status") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            StatusScreen(state = uiState, onBack = { nav.popBackStack() })
        }
    }
}
```

The `app.applicationScope.launch { ... }` requires `CopilotApp` to expose an `applicationScope: CoroutineScope`.

- [ ] **Step 2: Add `applicationScope` to `CopilotApp.kt`**

Read `CopilotApp.kt` and add:

```kotlin
val applicationScope: kotlinx.coroutines.CoroutineScope =
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
```

Also add `import kotlinx.coroutines.launch` at the top of `MainActivity.kt`.

- [ ] **Step 3: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (Any unresolved is most likely a missing import — read the error and fix.)

- [ ] **Step 4: Run all unit tests**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/java/com/vladutu/copilot/MainActivity.kt \
    app/src/main/java/com/vladutu/copilot/CopilotApp.kt
```

### Task 7.8: Register `BubbleService` in manifest

**Files:**
- Modify: `Copilot/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add service declaration**

Edit `AndroidManifest.xml`. Inside `<application>`, after the existing `ListenerService` `<service>` block, add:

```xml
<service
    android:name=".bubble.BubbleService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="overlay_controller_for_driving" />
</service>
```

- [ ] **Step 2: Build**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Copilot add app/src/main/AndroidManifest.xml
```

---

## Phase 8 — Pilot CatalogEntry + Store

Goal: Add `imageUrl: String?` to Pilot's catalog so it can be forwarded over the wire.

### Task 8.1: Extend `CatalogEntry`

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt`

- [ ] **Step 1: Replace file**

Write `/home/geo/projects/Pilot/app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt`:

```kotlin
package com.vladutu.pilot.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogEntry(
    val form: Form,
    val id: String,
    val title: String,
    val imagePath: String?,
    val imageUrl: String? = null,
    val savedAt: Long,
)
```

The default `= null` allows existing serialized JSON (without `imageUrl`) to deserialize cleanly.

- [ ] **Step 2: Build**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Pilot add app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt
```

### Task 8.2: Extend `CatalogStore.updateMeta` to persist `imageUrl`

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/catalog/CatalogStore.kt`

- [ ] **Step 1: Replace the `updateMeta` method signature**

Edit `CatalogStore.kt`. Find:

```kotlin
suspend fun updateMeta(form: Form, id: String, title: String, imagePath: String?) = mutate { current ->
    current.map {
        if (it.form == form && it.id == id) it.copy(title = title, imagePath = imagePath) else it
    }
}
```

Replace with:

```kotlin
suspend fun updateMeta(form: Form, id: String, title: String, imagePath: String?, imageUrl: String?) = mutate { current ->
    current.map {
        if (it.form == form && it.id == id) it.copy(title = title, imagePath = imagePath, imageUrl = imageUrl) else it
    }
}
```

- [ ] **Step 2: Build will fail at callsite — find it**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug
```
Expected: FAIL with `updateMeta(... imagePath: String?)` missing-arg error at `MetadataFetcher.refresh`. Note the file/line.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Pilot add app/src/main/java/com/vladutu/pilot/catalog/CatalogStore.kt
```

---

## Phase 9 — Pilot MetadataFetcher persists `imageUrl`

### Task 9.1: Modify `MetadataFetcher.refresh`

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/meta/MetadataFetcher.kt`

- [ ] **Step 1: Edit `refresh`**

Edit `MetadataFetcher.kt`. Find the `refresh` method (currently lines ~50-61). Replace:

```kotlin
suspend fun refresh(share: ClassifiedShare.YtMusic, store: CatalogStore) {
    val meta = fetch(share) ?: return
    val title = meta.title?.takeIf { it.isNotBlank() }
    val imageFile = meta.imageUrl?.let { downloadImage(it, share.form, share.id) }
    if (title == null && imageFile == null) return
    store.updateMeta(
        form = share.form,
        id = share.id,
        title = title ?: share.provisionalTitle ?: "Untitled ${share.id}",
        imagePath = imageFile?.absolutePath,
    )
}
```

with:

```kotlin
suspend fun refresh(share: ClassifiedShare.YtMusic, store: CatalogStore) {
    val meta = fetch(share) ?: return
    val title = meta.title?.takeIf { it.isNotBlank() }
    val imageFile = meta.imageUrl?.let { downloadImage(it, share.form, share.id) }
    if (title == null && imageFile == null && meta.imageUrl == null) return
    store.updateMeta(
        form = share.form,
        id = share.id,
        title = title ?: share.provisionalTitle ?: "Untitled ${share.id}",
        imagePath = imageFile?.absolutePath,
        imageUrl = meta.imageUrl,
    )
}
```

- [ ] **Step 2: Build**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage**

```bash
git -C /home/geo/projects/Pilot add app/src/main/java/com/vladutu/pilot/meta/MetadataFetcher.kt
```

---

## Phase 10 — Pilot NtfyPublisher to v3

### Task 10.1: Rewrite `NtfyPublisher` to publish v3 envelopes

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt`
- Modify: `Pilot/app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt` (if exists)

- [ ] **Step 1: Read existing test (if any)**

```bash
cat /home/geo/projects/Pilot/app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt 2>/dev/null
```

- [ ] **Step 2: Write/extend failing test**

Write `/home/geo/projects/Pilot/app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt` (overwriting if present):

```kotlin
package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfyPublisherTest {

    private lateinit var server: MockWebServer
    private lateinit var publisher: NtfyPublisher

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        publisher = NtfyPublisher(
            client = OkHttpClient(),
            base = server.url("").toString().trimEnd('/'),
            topic = "topic",
            clock = { 12345L },
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun JSONObject.req() = this

    @Test fun `publishYtMusic playlist sends v3 envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishYtMusic(Form.PLAYLIST, "OLAK5uy_xxx", title = "Mix", imageUrl = "https://img/x.jpg")
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals(3, body.getInt("v"))
        assertEquals("ytmusic", body.getString("cmd"))
        assertEquals("playlist", body.getString("form"))
        assertEquals("Mix", body.getString("title"))
        assertEquals("https://img/x.jpg", body.getString("imageUrl"))
        assertTrue(body.getString("url").contains("watch?list=OLAK5uy_xxx"))
    }

    @Test fun `publishYtMusic song with null title and imageUrl omits or nulls them`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishYtMusic(Form.SONG, "abc123", title = null, imageUrl = null)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("song", body.getString("form"))
        // either absent or explicit null is acceptable; assertion is that v3 didn't include a real string
        assertTrue(!body.has("title") || body.isNull("title"))
        assertTrue(!body.has("imageUrl") || body.isNull("imageUrl"))
    }

    @Test fun `publishWaze sends destination envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishWaze(url = "https://ul.waze.com/ul?ll=1,2", title = "Home")
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("waze", body.getString("cmd"))
        assertEquals("destination", body.getString("form"))
        assertEquals("Home", body.getString("title"))
        assertFalse(body.has("imageUrl") && !body.isNull("imageUrl"))
    }
}
```

- [ ] **Step 3: Verify failure**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.pilot.net.NtfyPublisherTest
```
Expected: FAIL (signatures don't match).

- [ ] **Step 4: Rewrite `NtfyPublisher.kt`**

Write `/home/geo/projects/Pilot/app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt`:

```kotlin
package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class NtfyPublishException(message: String, cause: Throwable? = null) : IOException(message, cause)

open class NtfyPublisher(
    private val client: OkHttpClient,
    private val base: String,
    private val topic: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val json = "application/json".toMediaType()

    open suspend fun publishYtMusic(form: Form, id: String, title: String?, imageUrl: String?) {
        require(form == Form.PLAYLIST || form == Form.SONG) {
            "publishYtMusic only accepts PLAYLIST or SONG, got $form"
        }
        postEnvelope(
            cmd = "ytmusic",
            form = form,
            url = ytMusicUrl(form, id),
            title = title,
            imageUrl = imageUrl,
        )
    }

    open suspend fun publishWaze(url: String, title: String?) {
        postEnvelope(
            cmd = "waze",
            form = Form.DESTINATION,
            url = url,
            title = title,
            imageUrl = null,
        )
    }

    private suspend fun postEnvelope(
        cmd: String,
        form: Form,
        url: String,
        title: String?,
        imageUrl: String?,
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("ts", clock())
            put("cmd", cmd)
            put("form", form.wire)
            put("url", url)
            title?.let { put("title", it) }
            imageUrl?.let { put("imageUrl", it) }
        }.toString()

        val req = Request.Builder()
            .url("$base/$topic")
            .header("Title", "Copilot")
            .post(payload.toRequestBody(json))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw NtfyPublishException("ntfy returned HTTP ${resp.code}")
            }
        }
    }

    companion object {
        private const val SCHEMA_VERSION = 3

        fun ytMusicUrl(form: Form, id: String): String = when (form) {
            Form.PLAYLIST -> "https://music.youtube.com/watch?list=$id&shuffle=1"
            Form.SONG -> "https://music.youtube.com/watch?v=$id"
            Form.DESTINATION -> throw IllegalArgumentException(
                "DESTINATION is not a YouTube Music form; use publishWaze",
            )
        }
    }
}
```

- [ ] **Step 5: Build will fail at callers — note locations**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug
```
Expected: FAIL with callsite errors in `DestinationPipeline.kt` and possibly elsewhere. Task 11 fixes those callers.

- [ ] **Step 6: Verify NtfyPublisher tests pass**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest --tests com.vladutu.pilot.net.NtfyPublisherTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Stage**

```bash
git -C /home/geo/projects/Pilot add app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt \
    app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt
```

---

## Phase 11 — Pilot DestinationPipeline: await metadata before publishing

Goal: For YtMusic, await `MetadataFetcher.fetch` to resolve title+imageUrl before calling `publishYtMusic`. For destinations, no metadata fetch — just pass the synchronously-resolved title.

### Task 11.1: Restructure `DestinationPipeline.ingestYtMusic`

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt`

- [ ] **Step 1: Read current file**

```bash
cat /home/geo/projects/Pilot/app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt
```

- [ ] **Step 2: Replace `ingestYtMusic`**

Edit `DestinationPipeline.kt`. Replace the entire `ingestYtMusic` method (lines ~56-108) with:

```kotlin
private suspend fun ingestYtMusic(
    share: ClassifiedShare.YtMusic,
    manualTitle: String?,
): IngestResult {
    val provisionalTitle = manualTitle?.trim()?.takeIf { it.isNotBlank() }
        ?: share.provisionalTitle?.takeIf { it.isNotBlank() }
        ?: "Untitled ${share.id}"

    // Await metadata — if it fails, fall back to provisional title + null imageUrl.
    val meta = try {
        metadataFetcher?.fetch(share)
    } catch (e: Exception) {
        Log.w(TAG, "metadata fetch failed", e)
        null
    }
    val resolvedTitle = meta?.title?.takeIf { it.isNotBlank() } ?: provisionalTitle
    val resolvedImageUrl = meta?.imageUrl?.takeIf { it.isNotBlank() }

    val imageFile = if (resolvedImageUrl != null && metadataFetcher != null) {
        try { metadataFetcher.downloadImage(resolvedImageUrl, share.form, share.id) }
        catch (e: Exception) { Log.w(TAG, "image download failed", e); null }
    } else null

    val entry = CatalogEntry(
        form = share.form,
        id = share.id,
        title = resolvedTitle,
        imagePath = imageFile?.absolutePath,
        imageUrl = resolvedImageUrl,
        savedAt = clock(),
    )
    val saveOk = try {
        catalogStore.upsert(entry)
        true
    } catch (e: Exception) {
        Log.w(TAG, "catalog save failed", e)
        false
    }

    val publishResult = try {
        publisher.publishYtMusic(share.form, share.id, title = resolvedTitle, imageUrl = resolvedImageUrl)
        true
    } catch (e: NtfyPublishException) {
        Log.w(TAG, "publish failed", e)
        false
    }

    return when {
        saveOk && publishResult -> IngestResult.Success(resolvedTitle)
        saveOk && !publishResult -> IngestResult.PublishFailed(resolvedTitle)
        !saveOk && publishResult -> IngestResult.SaveFailed(resolvedTitle)
        else -> IngestResult.SaveAndPublishFailed(resolvedTitle)
    }
}
```

- [ ] **Step 3: Update the `publisher.publishWaze` call**

Find the line in `ingestDestination`:

```kotlin
publisher.publishWaze(wazeUrl)
```

Replace with:

```kotlin
publisher.publishWaze(wazeUrl, title = title)
```

- [ ] **Step 4: Build**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run all tests**

```bash
cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`. If `DestinationPipelineTest` exists and fails because of restructure, read the test and update its expectations — the change in behavior is: YtMusic publish now happens AFTER `metadataFetcher.fetch` returns. Tests that asserted publish-before-fetch ordering need flipping.

- [ ] **Step 6: Stage**

```bash
git -C /home/geo/projects/Pilot add app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt
```

(If DestinationPipelineTest needed updates, stage those too.)

---

## Phase 12 — Final verification and single squashed commit per repo

Goal: Both repos build, tests pass, then squash all staged-commit work into a single commit per repo. Also update Copilot's ONBOARDING.

### Task 12.1: Update Copilot ONBOARDING.md

**Files:**
- Modify: `Copilot/ONBOARDING.md`

- [ ] **Step 1: Append v3 + two-FGS notes**

Edit `Copilot/ONBOARDING.md`. At the end of the file, append:

```markdown

## v3 schema (this build)

Pilot and Copilot now exchange ntfy envelopes with schema `v: 3`. Both apps must be on
the same commit/build. v2 messages are rejected by Copilot with `unknown schema v=2`
in the status screen's recent-events list.

## Two persistent notifications

You will see TWO ongoing notifications in the shade after this build:

- **Copilot listening** — the ntfy subscriber (foreground service, `dataSync`).
- **Copilot** (bubble) — the overlay controller that brings the carbox UI back to the
  front after you leave for Waze or YouTube Music (foreground service, `specialUse`).

Both are expected. Dismissing them stops the service.

## Revert

If something breaks on the box:

```bash
cd /home/geo/projects/Copilot && git reset --hard HEAD~1
cd /home/geo/projects/Pilot && git reset --hard HEAD~1
```

Rebuild and sideload both APKs. Both repos revert in lockstep so v2 ↔ v2 interop is
restored.
```

- [ ] **Step 2: Stage**

```bash
git -C /home/geo/projects/Copilot add ONBOARDING.md
```

### Task 12.2: Add the spec + plan to staged set

**Files:**
- Already created: `Copilot/docs/superpowers/specs/2026-06-02-drivedeck-merge-design.md`
- Already created: `Copilot/docs/superpowers/plans/2026-06-02-drivedeck-merge.md`

- [ ] **Step 1: Stage docs**

```bash
git -C /home/geo/projects/Copilot add docs
```

### Task 12.3: Delete leftover unused files in Copilot

**Files:**
- Delete (if empty after moves): `Copilot/app/src/main/java/com/vladutu/copilot/ui/` directory should now only contain `BackHomeButton.kt`, `home/`, `lists/`, `status/`. If old `StatusScreen.kt` still exists at `ui/StatusScreen.kt`, remove it.

- [ ] **Step 1: Verify directory cleanliness**

```bash
find /home/geo/projects/Copilot/app/src/main/java/com/vladutu/copilot/ui -maxdepth 1 -type f -name '*.kt'
```
Expected: only `BackHomeButton.kt` listed.

If you see a stray `StatusScreen.kt` at this level (not under `status/`), remove it:

```bash
git -C /home/geo/projects/Copilot rm app/src/main/java/com/vladutu/copilot/ui/StatusScreen.kt
```

- [ ] **Step 2: Build + test final state**

```bash
cd /home/geo/projects/Copilot && ./gradlew :app:assembleDebug :app:testDebugUnitTest
cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL` for both.

### Task 12.4: Squash into a single commit per repo

**Files:** N/A — git operations.

- [ ] **Step 1: Check current state**

```bash
git -C /home/geo/projects/Copilot status
git -C /home/geo/projects/Copilot log --oneline -20
```
Capture the SHA of the last commit BEFORE this work began. (If incremental commits were made during phases, this is the parent of phase 1's first commit.)

- [ ] **Step 2: Identify the merge-base SHA**

If you committed incrementally during phases, identify the "before" SHA. If everything is just staged (no intermediate commits), skip this step.

To find the pre-work SHA in Copilot, look for the first commit added by this plan; its parent is the merge-base. Or:

```bash
git -C /home/geo/projects/Copilot log --oneline | head -30
```
and read until you find the commit prior to this plan's first.

- [ ] **Step 3: Soft-reset to merge-base (Copilot)**

If incremental commits exist:

```bash
git -C /home/geo/projects/Copilot reset --soft <MERGE_BASE_SHA>
```

All work returns to the staging area without losing changes.

- [ ] **Step 4: Single commit in Copilot**

```bash
cd /home/geo/projects/Copilot && git status
```
Confirm all expected files are staged. Then:

```bash
git -C /home/geo/projects/Copilot commit -m "$(cat <<'EOF'
feat: merge DriveDeck into Copilot; bump wire schema to v3

Fold the standalone DriveDeck driver UI into Copilot. The carbox now runs a single
APK with a 2×2 home (Waze + Playlists/Songs/Destinations), paginated lists of items
previously sent from Pilot, a floating bubble overlay to return after launching
another app, and the existing ntfy listener + status diagnostics behind a top-corner
status pill.

Wire schema bumped to v3: envelopes now carry form, title, imageUrl. v2 messages
are rejected. Pilot ships the matching change in the Pilot repo.

Spec: docs/superpowers/specs/2026-06-02-drivedeck-merge-design.md
Plan: docs/superpowers/plans/2026-06-02-drivedeck-merge.md

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 5: Soft-reset Pilot likewise**

Identify Pilot's merge-base SHA and:

```bash
git -C /home/geo/projects/Pilot reset --soft <PILOT_MERGE_BASE_SHA>
git -C /home/geo/projects/Pilot status
```

- [ ] **Step 6: Single commit in Pilot**

```bash
git -C /home/geo/projects/Pilot commit -m "$(cat <<'EOF'
feat: bump ntfy schema to v3 — send form, title, imageUrl

CatalogEntry persists imageUrl. NtfyPublisher emits v3 envelopes with explicit form
field plus optional title and imageUrl. ShareReceiverActivity and AddUrlDialog
(via DestinationPipeline) now await MetadataFetcher before publishing so tiles
arrive on Copilot with the title and artwork URL already resolved; on metadata
failure the publish still proceeds with null fields.

Companion to Copilot's DriveDeck merge — both repos must be installed together.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 7: Verify final state in both repos**

```bash
git -C /home/geo/projects/Copilot log --oneline -3
git -C /home/geo/projects/Pilot log --oneline -3
git -C /home/geo/projects/Copilot status
git -C /home/geo/projects/Pilot status
```
Expected: each repo's HEAD is the new merge commit; working trees clean.

### Task 12.5: Manual integration smoke test (carbox)

This is a manual checklist; the agent should report it to the user, not attempt to run it.

- [ ] Sideload Copilot APK on the carbox; sideload Pilot APK on phone.
- [ ] Open Copilot once; grant SYSTEM_ALERT_WINDOW + POST_NOTIFICATIONS; set battery-unrestricted; place in launcher widget slot.
- [ ] Verify the home screen shows 4 tiles and a green status pill within ~10 s.
- [ ] From Pilot, tap a YT Music playlist tile. On the box, verify: music starts AND a new tile appears in the Playlists list with title + artwork.
- [ ] On the box, navigate to Playlists; tap the tile; verify music starts (bubble appears).
- [ ] Tap bubble; verify return to home.
- [ ] Long-press a tile; confirm delete; verify it's removed.
- [ ] Share a Maps URL from phone → Pilot → verify destination appears in Destinations list with title.
- [ ] Tap destination; verify Waze takes over with navigation started.

---

## Self-review notes

After writing this plan, verified:
- Every spec section maps to at least one task (deps/manifest → Phase 1; package lift → Phase 2; history + ArtworkCache → Phase 3; wire schema → Phases 4 + 8-11; AppLauncher fusion → Phase 5; ListenerService save-on-launch → Phase 6; UI rewrite → Phase 7; ONBOARDING and revert + smoke test → Phase 12).
- Type/identifier consistency: `Form` enum lives in `com.vladutu.copilot.history`, used by `Message`, `SavedItem`, `AppLauncher`, `HistoryRepository`, UI. `SavedItem.from(message, savedAt)` consistent everywhere referenced. `ArtworkCache.fileFor` / `.download` consistent.
- No placeholders. Each step that changes code shows the full code.
- Failure mode for "no Form in payload" handled in Task 4.1 with `Form.fromWire(...) ?: Rejected("unknown form")`.

## Open risks for executor

- Robolectric + DataStore interplay: `HistoryRepositoryTest` uses `preferencesDataStoreFile` which writes to the Robolectric file system. The `@After` cleanup must run even if a test fails; ensure no cross-test state leakage.
- DriveDeck's `BigAppButton` may reference resources or APIs (e.g. `PackageManager.getApplicationIcon`) that throw on Robolectric — but it's not under unit test in this plan; only used in Compose. If a build error appears, read its body for missing R-class references and lift the matching drawable from DriveDeck.
- Pilot's `DestinationPipelineTest` (if present) likely needs adjustment: the publish path now awaits metadata. If tests stub `MetadataFetcher` to return null, behavior should still pass; if they assert "publish was called even when metadataFetcher is null," behavior is unchanged. Read the test before tweaking.
