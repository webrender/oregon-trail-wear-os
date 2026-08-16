# ADR 0007 — A web port, sharing everything but the widgets

**Status:** accepted, 2026-08-15

## Context

The game exists only as a sideloaded APK on one person's watch (ADR 0002). That is a fine
place for it to live and a poor place for it to be *seen*: showing it to anyone means
either handing them a wrist or showing them screenshots, and screenshots do not convey a
game whose whole argument is about a control scheme.

A browser build fixes that, and Kotlin compiles to WebAssembly, so the engine can go over
unchanged. The question this decision answers is how much *else* goes over.

## Decision

**One shared module, built for both targets, with `expect`/`actual` only at the widget
layer.** `:shared` holds the engine, the controller, the art renderer, and every screen;
`:app` is reduced to an activity, a manifest, and a launcher icon.

Three things were considered and rejected on the way:

- **Sharing only the engine, and writing a second UI for the web.** This is the obvious
  shape, and it is wrong here for a specific reason: the interesting part of this project
  is not the engine — the 1985 rules are published — it is the twenty-four screens fitted
  to a round display. A second UI would be a second set of layout decisions to keep in
  step with the first, and the two would drift on exactly the details (a chip's label
  budget, a scene's leading padding) that took the most measuring to get right.

- **Rewriting the watch UI on multiplatform Compose Material, so both targets run the same
  widgets.** Cheaper to maintain and it would have changed the watch build. Every
  measurement in `docs/` and every screenshot in the README is a metric of
  `androidx.wear.compose` 1.3.1's `Chip` and `Button`. Swapping those out is a layout
  change to be re-measured on hardware, and the web port is not a good reason to have to
  do it.

- **Reflowing the layout for a browser window.** Rejected as the thing that would make it
  not the same game. See below.

### The screen stays 192dp round

The browser gets a 192dp circular display — the Pixel Watch 2's, exactly — centred in a
bezel, magnified by a whole number to fill the window. Not a responsive layout.

Everything about this game is a consequence of a 1.2" circle: three type sizes because a
bitmap font has no sizes in between, an eleven-character chip label budget, a `Back` chip
on screen because there is no back gesture (ADR 0004, and [`no-back-stack`]), scenes
authored at 150x96 so the bezel can crop their corners. A version that filled a laptop
screen would keep the artwork and discard the design, and would be a worse advertisement
for the project than a screenshot.

The magnification is a **whole number** — 1x, 2x, 3x — because the type is pinned to the
pixel grid: Shaston's 8-pixel cell is one em, so text is only crisp at font sizes that are
whole multiples of 8 device pixels (see `ui/theme/Typography.kt`). At 2.5x every glyph
edge lands between pixels and the font turns to the grey mush the whole art style exists
to avoid. The window is letterboxed around whichever step fits, which is what the bezel is
for.

Density follows from the magnification rather than from the browser: at 2x the watch, the
density is 4, and the screen is still 192dp. Every layout above that line measures exactly
what it measures on the wrist, and simply gets more pixels to be drawn with.

### What is `expect`/`actual`, and what isn't

The list is deliberately short, and its length is the health check on this decision. Eight
declarations:

| Declaration | Watch | Browser |
|---|---|---|
| `RotaryColumn` | `ScalingLazyColumn` | `LazyColumn` plus a `graphicsLayer` that mimics the scaling |
| `RotaryScrollColumn` | `Scaffold` + `PositionIndicator` | `Column` + a hand-drawn `ScrollArc` |
| `MenuChip`, `CompactActionChip`, `StepperButton` | Wear `Chip`/`CompactChip`/`Button` | the same shapes redrawn from `AppleII`'s palette |
| `Modifier.rotaryInput` | `onRotaryScrollEvent` + a focus request | wheel, arrow keys |
| `OregonTrailTheme`, `shastonFontFamily` | Wear `MaterialTheme`, a font asset | nothing, and a fetched `.ttf` |
| `ArtLoader` | `BitmapFactory` over the APK's assets | `fetch` plus Skia |
| `displayWidthPx` | the device's real width | the simulated watch's |
| `seedFromClock`, `storageDispatcher`, `StorageLock` | `System.nanoTime`, IO, a monitor | the clock, the main thread, nothing |

Everything else — all twenty-four screens, the controller, `Scene`, `TrailMap`, the
twinkling sky — is written once. `core/` did not change at all.

The browser's chips copy the watch's measurements rather than improving on them: a 52dp
chip, 14dp of horizontal padding, a 24dp icon with 6dp of spacing. Deliberately, so that
the copy budget worked out on the watch is the same budget in both. The watch is the one
with no room to spare, and two builds that disagreed about what fits would mean fitting
copy twice.

### Art is fetched, not bundled

20MB of PNGs. A browser that downloaded all of it before the title screen appeared would
take most of a minute to show one picture, so each asset is fetched when a screen first
asks for it and the browser's own HTTP cache makes the second visit free.

The cost is that art can be *late*, which the watch's synchronous loader never is. That is
what `ArtLoader.generation` is for: a Compose snapshot counter, bumped when a new asset
lands, used as a `remember` key by every caller. Without it a `remember` that captured a
null would keep it forever and the picture would never appear. It is a constant on the
watch and nothing re-runs there.

### Saves go in `localStorage`

Synchronous, per-origin, permanent, and three orders of magnitude larger than a run needs.
`SaveRepository` keeps its logic; only the four operations underneath it changed, behind a
`Storage` interface. There is no atomic write-then-rename in the browser version because
there is nothing to defend against: `setItem` either stores the whole string or throws.

## Consequences

- **Kotlin 1.9.24 → 2.4.10, AGP 8.5.2 → 8.13.2, Compose Multiplatform 1.11.1.** Required:
  Kotlin/Wasm and Compose Multiplatform do not exist at the old versions. Wear Compose is
  pinned at 1.3.1 regardless, so the watch's widgets are unchanged.
- **`compileSdk` 34 → 37**, which is what was installed alongside 34. The minimum is still
  API 33.
- **The ~150 JVM unit tests did not move to `commonTest`.** They stayed as JUnit in the
  Android target's unit tests, which is the only target here with a JVM to run them on.
  Rewriting them against `kotlin.test` would have been churn with nothing to show.
- **Three characters had to change.** Shaston has no U+2212 minus sign and no filled or
  hollow circle, and Android had been quietly borrowing all three from the system font —
  so the store's `−` and the rafting readout's `●○` were rendering in Roboto on the watch
  and rendered as *nothing* in the browser, which has no fallback chain. They are now
  `–`, `•` and `·`, all of which Shaston has. `FontCoverageTest` scans the source and
  fails on any character the font cannot draw.
- **The browser bundle is about 12MB before compression**, most of it Skia. That is the
  price of Compose Multiplatform on the web and there is no version of this port that
  avoids it.

[`no-back-stack`]: 0004-unified-input-scheme.md
