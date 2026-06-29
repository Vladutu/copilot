# Changelog

All notable changes to Copilot are documented here. Each entry corresponds to a
released version tag and is built from the commits between that tag and the
previous one. This project loosely follows [Keep a Changelog](https://keepachangelog.com)
and [Semantic Versioning](https://semver.org).

## [0.16.2] - 2026-06-29

### Build
- Auto-generate CHANGELOG.md on release

## [0.16.1] - 2026-06-29

### Changed
- Music Time Machine now randomises sweep direction and builds years up to the #1 track.

## [0.16.0] - 2026-06-28

### Changed
- Music Time Machine replaces Top Weekly — a chronological, random-years tour of the charts.

## [0.15.0] - 2026-06-27

### Added
- Tap Waze's "Go now" via the car knob (DPAD_CENTER) press.

### Docs
- Design spec and implementation plan for the Waze Go-now knob tap.

## [0.14.1] - 2026-06-25

### Changed
- Resolved Kotlin compiler warnings.

## [0.14.0] - 2026-06-25

### Added
- Configurable tile text size and highlight border in Settings.

### Build
- Bump Gradle wrapper 9.5.1 → 9.6.0 (#20).
- Bump com.google.zxing:core 3.5.3 → 3.5.4 (#19).
- Bump actions/checkout 6 → 7.

## [0.13.1] - 2026-06-22

### Added
- In-app accessibility grant flow and documented permissions.

## [0.13.0] - 2026-06-22

### Build
- Move the signing key out of the repo (local-only keystore).

### Fixed
- Make the topic-determinism test JVM-independent.

## [0.12.0] - 2026-06-22

### Added
- Dynamic per-install ntfy topic with QR pairing.

## [0.11.0] - 2026-06-21

### Added
- Auto-start on boot behind a Settings toggle (FGS-free boot path).

## [0.10.0] - 2026-06-20

### Changed
- Unify all tile grids onto one readable inline tile (icon/cover on the left, big label).

### Build
- Bump lifecycle 2.10.0 → 2.11.0 (#17).
- Bump androidx.compose:compose-bom (#16).

## [0.9.3] - 2026-06-15

### Fixed
- Show the focus ring on the Home heart for knob navigation.

### Docs
- Add README.

## [0.9.2] - 2026-06-14

### Changed
- Liked screen is now a readable list with Copy; dropped per-item delete.

## [0.9.1] - 2026-06-14

### Added
- Home heart reflects saved state (outline vs filled).

## [0.9.0] - 2026-06-14

### Added
- Liked Songs — mark the currently-playing track to a local list.

## [0.8.2] - 2026-06-13

### Changed
- Shuffle the Top Weekly playlist.

## [0.8.1] - 2026-06-12

### Fixed
- Music page uses the standard ScreenHeader (back + title) with sub-screen padding.

## [0.8.0] - 2026-06-12

### Added
- 2×2 home (Waze / Maps / Places / Music) plus a dedicated Music page hosting the five media tiles.

## [0.7.1] - 2026-06-12

### Fixed
- Shrink the Top Weekly busy spinner — progress ring was clipped at icon size.

## [0.7.0] - 2026-06-12

### Added
- Top Weekly — one-tap US+GB chart queue via an anonymous temp playlist.

## [0.6.2] - 2026-06-12

### Fixed
- Discover tiles get the sibling-tile look and keep knob focus across a mix launch.

### Build
- Add `make check` (assembleDebug + unit tests + lint).

## [0.6.1] - 2026-06-11

### Fixed
- Auto-start discovered playlists like Pilot-published ones.

## [0.6.0] - 2026-06-11

### Added
- Discover — keyword music discovery via the NewPipe Extractor.

## [0.5.0] - 2026-06-11

### Changed
- Remove auto-start on carbox boot.
- Read locale from `LocalConfiguration` in composables.

### Build
- Migrate to AGP 9.2.1 with built-in Kotlin, compileSdk 37 (interim: Kotlin 2.4.0, AGP 8.13.2, compileSdk 36, Gradle 9.5.1).
- Run `testDebugUnitTest` in release.sh (AGP 9 dropped release unit tests).
- Add Dependabot weekly updates + CI auto-merge for patch/minor.
- Dependency bumps: turbine 1.1.0 → 1.2.1 (#15), org.json 20240303 → 20260522,
  okhttp 4.12.0 → 5.4.0, compose-bom, activity-compose (#7), coroutines-test (#6),
  navigation-compose (#4), serialization-json (#13), lifecycle 2.8.6 → 2.10.0 (#5),
  datastore-preferences (#11), test core-ktx 1.6.1 → 1.7.0 (#9), test.ext:junit 1.2.1 → 1.3.0 (#8).

## [0.4.0] - 2026-06-08

### Added
- Show the app version on the Status screen.

## [0.3.1] - 2026-06-08

### Fixed
- Ignore non-app windows when tracking the foreground app for switch-back.

## [0.3.0] - 2026-06-08

### Added
- Auto-return to the previous app after YT Music loads.

### Build
- Add `scripts/version.sh` + Makefile (version/release/wrapper targets).

## [0.2.0] - 2026-06-07

Initial released version. Highlights of the work leading up to it:

### Added
- Receive and dispatch messages by `form`/`cmd`: songs (11-char video-id validation),
  Waze navigation (host-allowlist, `com.waze`), Google Maps (`cmd=maps`, App Links),
  and radio via VLC (`cmd=radio`).
- Wire schema evolution to v3; merged DriveDeck into Copilot.
- Shuffle YT Music playlists on launch.
- BMW iDrive knob navigation — linear walk with a bold focus border.
- Overlay bubble on Pilot events; bring Copilot to front on BACK while showing.
- Promote items to the top of the saved list on re-share/tap.
- Surface drop reasons and clock skew on the status screen; surface launch failures to the driver.
- Auto-start MainActivity on boot via a foreground-service bounce.
- 2×2 home grid with larger tiles; resume the last screen.

### Changed
- Adopt PilotTheme; amber-on-charcoal bubble icon and new launcher icon.

### Fixed
- Bind UI to a process-scoped state flow to avoid a cold-start race.
- Knob-nav desync; centered launcher play-triangle.
- Declare `com.waze`, YT Music, and Google Maps in `<queries>` for Android 11+.
- Drop `setPackage` for `cmd=maps` to support App Links.
- Crash logs to Downloads; scrollable status; home/list layout polish.

### Build
- Release automation — wrapper, signing, `release.sh`, docs.

[0.16.2]: https://github.com/Vladutu/copilot/compare/v0.16.1...v0.16.2
[0.16.1]: https://github.com/Vladutu/copilot/compare/v0.16.0...v0.16.1
[0.16.0]: https://github.com/Vladutu/copilot/compare/v0.15.0...v0.16.0
[0.15.0]: https://github.com/Vladutu/copilot/compare/v0.14.1...v0.15.0
[0.14.1]: https://github.com/Vladutu/copilot/compare/v0.14.0...v0.14.1
[0.14.0]: https://github.com/Vladutu/copilot/compare/v0.13.1...v0.14.0
[0.13.1]: https://github.com/Vladutu/copilot/compare/v0.13.0...v0.13.1
[0.13.0]: https://github.com/Vladutu/copilot/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/Vladutu/copilot/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/Vladutu/copilot/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/Vladutu/copilot/compare/v0.9.3...v0.10.0
[0.9.3]: https://github.com/Vladutu/copilot/compare/v0.9.2...v0.9.3
[0.9.2]: https://github.com/Vladutu/copilot/compare/v0.9.1...v0.9.2
[0.9.1]: https://github.com/Vladutu/copilot/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/Vladutu/copilot/compare/v0.8.2...v0.9.0
[0.8.2]: https://github.com/Vladutu/copilot/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/Vladutu/copilot/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/Vladutu/copilot/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/Vladutu/copilot/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/Vladutu/copilot/compare/v0.6.2...v0.7.0
[0.6.2]: https://github.com/Vladutu/copilot/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/Vladutu/copilot/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/Vladutu/copilot/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/Vladutu/copilot/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Vladutu/copilot/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/Vladutu/copilot/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/Vladutu/copilot/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Vladutu/copilot/releases/tag/v0.2.0
