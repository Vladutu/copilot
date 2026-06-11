# Copilot — one-time setup on the car AI box

Do this once after installing or updating Copilot. Each step matters; skipping
the *Display over other apps* step is the most common reason "nothing happens"
when you tap a tile on the phone.

## 1. Install the APK

Sideload `app/build/outputs/apk/debug/app-debug.apk` onto the box.

## 2. Open Copilot once

Android keeps an app that has never been opened by a human in a stopped state
(no services, no broadcasts). Tap the icon.

## 3. Grant *Display over other apps*

Settings → Apps → Copilot → **Display over other apps** → Allow.

This is what lets the background service launch YouTube Music. Without it,
commands arrive but nothing happens — and there is no exception you can see
in logcat. This is the #1 silent failure mode.

## 4. Grant notification permission

On first launch Copilot asks for notification permission. Allow it — that is
how you see "Copilot listening" in the shade and confirm the service is alive.

## 5. Disable battery optimization

Settings → Apps → Copilot → **Battery** → **Unrestricted** (or "Don't optimize").

## 6. Place Copilot in the launcher's widget slot

Use the box launcher's widget mechanism to embed Copilot's status screen.
This is what brings Copilot back automatically after the box reboots or wakes.

## 7. Sanity-check

Reboot the box. Within ~10 seconds the status dot on the Copilot screen should
turn **green** (Connected). Then tap a tile in Pilot from your phone — YouTube
Music should open and start playing within a few seconds.

If anything fails, the Copilot status screen tells you which step:

- Status stuck on amber/red → network/relay problem.
- Command appears, `Last error: background launch blocked` → revisit step 3.
- Command appears but nothing happens and no error → revisit step 3 anyway;
  the `SecurityException` is not always thrown — silent failure is also possible.

## Configuration before first build

Both apps need the same ntfy topic. Before building:

1. Generate the topic:
   ```bash
   echo "copilot-$(openssl rand -hex 16)"
   ```
2. Paste the same value into:
   - `Pilot/app/src/main/java/be/doccle/pilot/config/Config.kt` → `NTFY_TOPIC`
   - `Copilot/app/src/main/java/be/doccle/copilot/config/Config.kt` → `NTFY_TOPIC`
3. Replace the three placeholder ids in `Pilot/.../catalog/Catalog.kt` with real
   YouTube Music playlist ids (Share → Copy link → strip the `&si=…` tail).

## v3 schema (this build)

Pilot and Copilot now exchange ntfy envelopes with schema `v: 3`. Both apps must be
on the same commit/build. v2 messages are rejected by Copilot with
`unknown schema v=2` in the status screen's recent-events list.

## Two persistent notifications

After this build you will see **two** ongoing notifications in the shade:

- **Copilot listening** — the ntfy subscriber (foreground service, `dataSync`).
- **Copilot** (bubble) — the overlay controller that brings the carbox UI back to
  the front after you leave for Waze or YouTube Music (foreground service,
  `specialUse`).

Both are expected. Dismissing them stops the service.

## Revert

If something breaks on the box:

```bash
cd /home/geo/projects/Copilot && git reset --hard HEAD~1
cd /home/geo/projects/Pilot && git reset --hard HEAD~1
```

Rebuild and sideload both APKs. Both repos revert in lockstep so v2 ↔ v2 interop
is restored.

