# Releasing Copilot

## One-time setup (Mac)

1. **Gradle wrapper** — only if `gradlew` is missing (fresh clone usually has it committed).
   Needs a Gradle on PATH first: `sdk install gradle 8.9` (or `brew install gradle`), then
   `./scripts/bootstrap-wrapper.sh`. Commit the generated wrapper files.
2. **gh CLI:** `brew install gh && gh auth login`

That's it — the release keystore (`keystore/copilot-release.jks`) and its credentials
(`keystore/signing.properties`) are committed in the repo, so any clone can build a
signed release with no extra setup.

> **Security note (deliberate choice):** the signing key and its password are committed.
> The password is therefore not a secret. Anyone with read access to this repo can build
> an APK that installs as a legitimate *update* over an installed Copilot and inherits its
> granted permissions (overlay, accessibility, boot launch). Keep the repo private and keep
> the Obtainium PAT read-only. To rotate the key you must uninstall/reinstall on every device.

## Cut a release

```bash
./scripts/release.sh 0.2.0
```
Runs tests, builds a release-signed APK, pushes the branch, tags `v0.2.0`, and publishes it
as the latest release on `Vladutu/copilot` with `copilot-0.2.0.apk` attached.

`versionCode` = `major*10000 + minor*100 + patch` (so `0.2.0` -> `200`). Keep minor/patch < 100.

## Verify the installed version

Open Copilot → tap the status pill → the **Status** screen shows `vX.Y.Z` (the build's
`versionName`) beneath the ntfy topic line. Use it to confirm the device picked up a new
release. Debug builds read `vX.Y.Z-debug`.

## Obtainium (the car box device)

1. Install Obtainium (from its GitHub releases page).
2. Settings -> add a **fine-grained GitHub PAT**, repo access limited to `Vladutu/copilot`,
   permission **Contents: Read-only**.
3. Add App -> URL `https://github.com/Vladutu/copilot`:
   - APK filter regex: `copilot-.*\.apk`
   - Track only the latest release.

> The signing switch to the dedicated keystore means the first new build won't install
> over an old debug-signed Copilot — uninstall the old app once, then install.
