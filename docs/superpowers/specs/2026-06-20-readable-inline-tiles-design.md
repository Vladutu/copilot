# Readable inline tiles — design

**Date:** 2026-06-20
**Status:** Approved, pre-implementation
**Scope:** Copilot Android app UI only. No Pilot changes, no networking/data changes.

## Problem

While driving, the **Places** and **Playlists** grids are hard to read at a glance. The
labels are too small to parse quickly. Home and Music — which use a different tile layout
(small icon left, big label right) — read instantly by comparison.

Investigation finding: the font size is **already identical** across both layouts
(`26.sp` in `HomeTile`, `SavedTile`, `CategoryTile`, `PlaylistTile`). The readability gap is
**layout-driven, not font-driven**:

- **Inline tile** (`HomeTile`): label is the dominant element, one line, full tile width → reads fast.
- **Card tile** (`SavedTile` / `CategoryTile` / `PlaylistTile`): a large image/icon fills ~75% of
  the tile and the label is squeezed into a narrow bottom strip in a 3-column grid, wrapping to
  two lines and ellipsizing → the eye goes to the artwork, not the words.

The fix is to put every grid on the inline layout, and bump the shared label font (the inline
layout gives the label the full tile width, so it can go bigger than the card layout ever allowed).

## Goals

- Convert Places, Playlists, Songs, Radio, Discover, and Browse-results to the inline
  icon/thumbnail-left + big-label-right layout.
- Raise the tile label font across all screens (Home and Music included).
- Consolidate the four divergent tile components into one shared `MediaRowTile`.
- **Preserve knob navigation behavior exactly.**

## Non-goals

- Liked screen — already a readable text list read while parked. Untouched.
- Home and Music **layout** — already inline. They only inherit the font bump and the shared component.
- Knob engine changes — `KnobPagedGrid` / `KnobGridNav` / `stopsPerItem` contract stay as-is.
- Any data, networking, launching, or Pilot-side change.

## Current state

| Component | Layout | Used by | Knob stops/item |
|---|---|---|---|
| `HomeTile` (ui/home) | inline (icon left, label right) | Home (2×2), Music (3×2) | n/a (screen drives focus by index) |
| `SavedTile` (ui/lists) | card (big image/icon, bottom label) | Places, Playlists, Songs, Radio via `SavedListScreen` | 1 |
| `CategoryTile` (ui/discover) | card + overlaid ▶ button | Discover via `DiscoverScreen` | 2 (card, ▶) |
| `PlaylistTile` (ui/discover) | card (URL thumbnail, bottom label) | Browse via `BrowseResultsScreen` | 1 |

All four use `26.sp` labels. The paged grids (`SavedListScreen`, `DiscoverScreen`,
`BrowseResultsScreen`) all render through the shared `KnobPagedGrid` at `pageSize = 6`,
`columns = 3`. Home and Music render their own grids and drive focus via a `focusedIndex`
state + `onPreviewKeyEvent` Left/Right handler.

## Target design

### 1. `MediaRowTile` — one shared inline tile

A single composable replacing `HomeTile`, `SavedTile`, `CategoryTile`, and `PlaylistTile`.

Row layout:

```
┌──────────────────────────────────────────────┐
│  [ left visual ]   Big Readable Label   <trail>│
└──────────────────────────────────────────────┘
```

API (conceptual — final signature decided during implementation):

- `label: String`
- `onClick: () -> Unit`
- `onLongPress: (() -> Unit)? = null` — delete dialogs on Places/Playlists/Songs/Radio/Discover
- **Left visual** — exactly one of:
  - `thumbnail: ImageBitmap?` (Playlists/Songs cover from file, Browse cover from URL)
  - `fallbackIcon: ImageVector?` + `iconTint` (Places=Place, Radio=Radio, Discover=Explore, Music tiles)
  - `packageName: String?` + `fallbackRes` (Home Waze/Maps app icons)
  - `fallbackRes: Int?` (drawable)
- `busy: Boolean = false` — centered spinner in the left-visual slot (Top Weekly tile, Discover ▶ uses its own)
- `trailing: (@Composable () -> Unit)? = null` — Discover's ▶ mix button slot
- focus handling so callers can attach `FocusRequester`(s) — see knob section.

Sizing:
- Left visual: square, ~80.dp.
- Label: `~32.sp` (up from 26), `SemiBold`. Single line + ellipsize on Home/Music (short labels).
  On the paged grids, allow **up to 2 lines** then ellipsize — a few place names are long
  (e.g. "Automobile Bavaria – Service autorizat BMW").
- Focus border: 4.dp primary when focused, 1.dp outline otherwise — same as today.

The async thumbnail loading currently in `SavedTile` (file → bitmap) and `PlaylistTile`
(URL → bitmap via OkHttp) stays **at the call site** (the screen-specific tile wrapper or the
screen), not inside `MediaRowTile`. `MediaRowTile` only receives a ready `ImageBitmap?`. This keeps
the shared component free of IO and lets each screen keep its own image source.

### 2. Per-screen application

| Screen | Left visual | Stops/item | Tap action | Long-press |
|---|---|---|---|---|
| Home | app/vector icon (unchanged) | n/a | open route | — |
| Music | vector icons (unchanged) | n/a | open route | — |
| Places | `Place` icon | 1 | launch destination | delete |
| Playlists | cover thumbnail (file) | 1 | launch playlist | delete |
| Songs | cover thumbnail (file) | 1 | launch song | delete |
| Radio | `Radio` icon | 1 | launch radio | delete |
| Discover | `Explore` icon | 2 | card → Browse | delete |
| Browse | cover thumbnail (URL) | 1 | launch in YT Music | — |

Grids stay at 6 per page (`KnobPagedGrid` defaults unchanged). `SavedListScreen` keeps mapping
`Form → icon/title/empty-text`. `DiscoverScreen` keeps `stopsPerItem = 2`.

### 3. Discover ▶ button

Moves from *overlaid on the artwork* (absolute-positioned `Surface` in a `Box`) to the
**`trailing` slot at the row's right edge**. Still a separate focusable, round, 56.dp, shows a
spinner while its mix loads. The card (whole row) remains stop 0; ▶ remains stop 1.

### 4. Font bump

Shared tile label rises to `~32.sp` / `~38.sp` line height. This lifts Home and Music too, as
requested. The base `PilotTypography.titleLarge` (22.sp) is unchanged — every tile already
overrides locally, and other screens (headers, empty states) should keep their current sizes.

### 5. Removals

- Card-style `Column`-with-large-image layout in `SavedTile`, `CategoryTile`, `PlaylistTile`.
- `FormBadge` (the small corner form-type badge in `SavedTile`) — redundant once each screen
  shows a single form type.

## Knob navigation — preservation contract

The knob engine does **not** change. The only contract between a tile and `KnobPagedGrid` is:
*attach the FocusRequesters it is handed, to the same number of focusable click targets, in the
same order.* The redesign keeps this per screen:

| Screen | Stops/item | Focusable targets in `MediaRowTile`, in order |
|---|---|---|
| Places / Playlists / Songs / Radio | 1 | whole row → `focus[0]` |
| Browse | 1 | whole row → `focus[0]` |
| Discover | 2 | row card → `focus[0]`, then ▶ → `focus[1]` |

- `KnobPagedGrid` hands tiles `stopsPerItem` requesters in item-major order; Discover's two
  stops live within one tile, so order is preserved.
- The ▶ moving into the row (vs overlaid) does not change focus order — it is still the second
  focusable node, after the card.
- Home and Music keep their own `focusedIndex` + `onPreviewKeyEvent` Left/Right logic; only the
  tile composable they call changes.
- `BrowseResultsScreen.RetryBox` keeps focus-on-entry so a knob press retries.

A regression check is part of the plan: confirm each screen passes the same `stopsPerItem` and
attaches requesters in the same order as today.

## Components / boundaries

- `MediaRowTile` (new, `ui/` shared) — pure presentation, no IO. One job: render an inline tile.
- `SavedListScreen` + per-form mapping — unchanged structure; swaps `SavedTile` → `MediaRowTile`
  (with file-thumbnail loading kept at the call site).
- `DiscoverScreen` — swaps `CategoryTile` → `MediaRowTile` with a ▶ `trailing` slot; keeps
  `stopsPerItem = 2`.
- `BrowseResultsScreen` — swaps `PlaylistTile` → `MediaRowTile` (URL-thumbnail loading kept at
  the call site).
- `HomeScreen`, `MusicScreen` — swap `HomeTile` → `MediaRowTile`; behavior identical, font bigger.
- Deleted: `SavedTile`, `CategoryTile`, `PlaylistTile`, `HomeTile`, `FormBadge`.

## Error handling

No new error paths. Thumbnail load failure falls back to the form icon exactly as today
(file missing → icon; URL fetch fails → music-note icon). Discover ▶ failure keeps its existing
Toast + spinner-reset behavior.

## Testing & verification

**Constraint: the implementation environment is a Linux container with no Android SDK — the app
cannot be built or run, and no instrumented/unit tests can be executed here.** Existing unit
tests (`discover`, `launch`, `charts`) don't cover UI composables and are unaffected.

Verification is therefore by **code review against this spec**, focusing on:

1. Each grid screen passes the same `stopsPerItem` as today (1, except Discover = 2).
2. `MediaRowTile` attaches handed `FocusRequester`(s) to the right number of focusable targets,
   in the documented order.
3. Thumbnail IO stays at call sites; `MediaRowTile` takes a ready `ImageBitmap?`.
4. No remaining references to the four deleted tile composables or `FormBadge`.
5. Label font raised consistently; long names ellipsize at 2 lines on paged grids.

Georgian builds, runs, and knob-tests on his Mac / in the car after the change lands.

## Out of scope / deferred

- Tuning exact `dp`/`sp` values to taste after seeing it in the car.
- Any change to page density (stays 6/page) or to the Liked screen.
