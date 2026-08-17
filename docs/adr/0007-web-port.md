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
bezel, magnified to fill the window. Not a responsive layout.

Everything about this game is a consequence of a 1.2" circle: three type sizes because a
bitmap font has no sizes in between, an eleven-character chip label budget, a `Back` chip
on screen because there is no back gesture (ADR 0004, and [`no-back-stack`]), scenes
authored at 150x96 so the bezel can crop their corners. A version that filled a laptop
screen would keep the artwork and discard the design, and would be a worse advertisement
for the project than a screenshot.

The magnification **prefers a whole number** — 1x, 2x, 3x — because the type is pinned to
the pixel grid: Shaston's 8-pixel cell is one em, so text is only crisp at font sizes that
are whole multiples of 8 device pixels (see `ui/theme/Typography.kt`). At 2.5x every glyph
edge lands between pixels and the font turns to the grey mush the whole art style exists
to avoid.

*Amended 2026-08-17.* Whole numbers **only** was the original rule, and it made the port
look bad on the devices most people were opening it on. The steps are 384 device pixels
apart, and a window is very often most of the way to the next one: a 360-CSS-pixel phone at
device pixel ratio 2 has 720 pixels, takes the 1x step, and draws a watch across half its
width. A 1964-pixel laptop window was stranded at 768 by a further cap that held the
magnification at 2x to keep the art sharp. Both read as a small picture on a big black
page, which is not what the bezel was for.

So the whole number is now preferred rather than required: it is taken when it leaves no
more than a tenth of the shorter side unused, and otherwise the watch is magnified to fit
exactly. Two things make the fraction cheap where it is reached for. `cellStyle` rounds the
cell back down to whole pixels, so the glyphs stay on the grid even when their origins do
not. And the windows small enough to need a fraction are overwhelmingly phones at device
pixel ratio 2 or 3, where half a device pixel is a sixth of a CSS pixel; a desktop at ratio
1, where the grid is visible, has a window big enough that a whole number is nearly always
within the slack.

The 2x cap is gone with it, and the art is what pays: `prepare-art.py` keeps each asset at
the largest size a *watch* draws it plus half again, so a full-bleed backdrop is 820 pixels
and is visibly soft above about a 768-pixel screen. That is the accepted cost of the watch
being the size of the window rather than a third of it — the type, which is a font and not
a bitmap, stays crisp all the way up. Regenerating the art from the masters at a larger cap
would fix the rest, at the price of those pixels on every download.

Density follows from the magnification rather than from the browser: at 2x the watch, the
density is 4, and the screen is still 192dp. Every layout above that line measures exactly
what it measures on the wrist, and simply gets more pixels to be drawn with.

### What is `expect`/`actual`, and what isn't

The list is deliberately short, and its length is the health check on this decision. Nine
declarations:

| Declaration | Watch | Browser |
|---|---|---|
| `RotaryColumn` | `ScalingLazyColumn` | `LazyColumn` plus a `graphicsLayer` that mimics the scaling |
| `RotaryScrollColumn` | `Scaffold` + `PositionIndicator` | `Column` + a hand-drawn `ScrollArc` |
| `MenuChip`, `CompactActionChip`, `StepperButton` | Wear `Chip`/`CompactChip`/`Button` | the same shapes redrawn from `AppleII`'s palette |
| `Modifier.rotaryInput` | `onRotaryScrollEvent` + a focus request | wheel, arrow keys |
| `Modifier.horizontalDragInput` | nothing — the gesture is the system dismiss | a drag, which on a phone is the only control there is |
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

A browser that downloaded the whole set before the title screen appeared would take most
of a minute to show one picture, so each asset is fetched when a screen first asks for it
and the browser's own HTTP cache makes the second visit free. (The set was 20MB when this
was decided and is 3.9MB now — see the last consequence below — which makes the choice
less load-bearing than it was, and still right: nothing needs the whole set.)

The cost is that art can be *late*, which the watch's synchronous loader never is. That is
what `ArtLoader.generation` is for: a Compose snapshot counter, bumped when a new asset
lands, used as a `remember` key by every caller. Without it a `remember` that captured a
null would keep it forever and the picture would never appear. It is a constant on the
watch and nothing re-runs there.

### Publishing

A GitHub Actions workflow builds `wasmJsBrowserDistribution` on every push to `master` and
deploys it to Pages. The output is already a self-contained static site and every URL in
`index.html` is relative, so it works unchanged under the `/oregon-trail-wear-os/` path a
project site is served from — nothing in the build knows the repository's name, which is
just as well: this account has a user-level custom domain, so the site is served from
`webrender.net/oregon-trail-wear-os/` rather than from `github.io` at all.

The runners need the Android SDK even though nothing Android is built, because `:shared`
applies `com.android.library` for its Wear OS target and AGP resolves an SDK during
configuration, before it knows the only task requested is a wasm one. That is what fixes
`compileSdk` at a level a runner can actually fetch.

### Saves go in `localStorage`

Synchronous, per-origin, permanent, and three orders of magnitude larger than a run needs.
`SaveRepository` keeps its logic; only the four operations underneath it changed, behind a
`Storage` interface. There is no atomic write-then-rename in the browser version because
there is nothing to defend against: `setItem` either stores the whole string or throws.

## Consequences

- **Kotlin 1.9.24 → 2.4.10, AGP 8.5.2 → 8.13.2, Compose Multiplatform 1.11.1.** Required:
  Kotlin/Wasm and Compose Multiplatform do not exist at the old versions. Wear Compose is
  pinned at 1.3.1 regardless, so the watch's widgets are unchanged.
- **`compileSdk` 34 → 36.** Forced: the Compose that Compose Multiplatform 1.11 resolves
  to on Android needs 35 or newer. 36 rather than the 37 this machine happens to have,
  because 37 is a preview-numbered platform that a CI runner will not have and cannot
  reliably fetch. The minimum is still API 33.
- **The ~150 JVM unit tests did not move to `commonTest`.** They stayed as JUnit in the
  Android target's unit tests, which is the only target here with a JVM to run them on.
  Rewriting them against `kotlin.test` would have been churn with nothing to show.
- **Three characters had to change.** Shaston has no U+2212 minus sign and no filled or
  hollow circle, and Android had been quietly borrowing all three from the system font —
  so the store's `−` and the rafting readout's `●○` were rendering in Roboto on the watch
  and rendered as *nothing* in the browser, which has no fallback chain. They are now
  `–`, `•` and `·`, all of which Shaston has. `FontCoverageTest` scans the source and
  fails on any character the font cannot draw.
- **The browser bundle is about 11MB of wasm before compression**, most of it Skia. That
  is the price of Compose Multiplatform on the web and there is no version of this port
  that avoids it. The art beside it is 3.9MB and is fetched per asset, not up front.
- ~~**The browser is capped at 2x.** A full-bleed backdrop is authored to 820 pixels; 2x
  asks 875 of it, a 7% upscale nobody sees, and 3x asks 1313, which is not. Raising the
  cap means regenerating the art larger and paying for it on every download.~~ *Removed
  2026-08-17*: the cap made the game a postage stamp on a laptop and on a phone, which
  costs more than the softness does. The art is now upscaled above 2x, and regenerating it
  larger remains the way to fix that. See the amendment above.
- **A phone can play it, which it could not.** Every menu in the game is a list and every
  choice is a chip, so touch always worked — apart from the two screens that are neither.
  The map and the rafting descent were driven by the crown's browser stand-ins, and a
  device with no wheel and no arrow keys could not steer the raft at all. Both now take a
  horizontal drag, which is free in a browser and impossible on the watch (ADR 0004: the
  gesture is the system dismiss). The briefing's control line asks `pointer: coarse` and
  says "Drag to steer." where it applies.
- **The whole asset set was re-encoded as indexed PNG**, which the port paid for and the
  watch benefits from more: 19.1MB to 3.9MB, and a release APK from 22MB to 5.9MB. See
  `scripts/prepare-art.py`, which also records what the previous quantiser had been
  silently doing to the map tiles.

[`no-back-stack`]: 0004-unified-input-scheme.md
