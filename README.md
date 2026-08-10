# The Oregon Trail — Wear OS

A native Kotlin reimplementation of *The Oregon Trail* (1985, Apple II), built for the
round screen and rotating crown of a Wear OS watch. Not an emulator — every screen,
control, and pixel-art asset here is original, hand-fit to a 1.2" circular display.

Personal project, built for and tested on a Pixel Watch 2. Sideload-only; not published
to the Play Store (see [ADR 0002](docs/adr/0002-sideload-only-distribution.md)).

## Screenshots

All captured on the `Wear_OS_Large_Round` emulator (454×454, API 33).

| | |
|---|---|
| ![Title screen](docs/images/title.png) | ![Choosing a profession](docs/images/profession.png) |
| Title screen | Choosing a profession |
| ![The general store](docs/images/store.png) | ![Store goods list](docs/images/store-list.png) |
| The general store | Store goods list |
| ![Buying a yoke of oxen](docs/images/store-buy.png) | ![On the trail](docs/images/trail.png) |
| Quantity stepper for purchases | On the trail |
| ![A trader encounter](docs/images/encounter-trader.png) | ![Supplies overview](docs/images/supplies.png) |
| A trail encounter | Checking supplies |
| ![Kansas River Crossing](docs/images/river.png) | ![Choosing how to cross](docs/images/river-choice.png) |
| Arriving at a river | Choosing how to cross |
| ![The hunting minigame](docs/images/hunting.png) | ![Confirming abandonment of a run](docs/images/abandon.png) |
| Hunting the prairie | Abandoning a run (no undo) |

## Design

- **The crown is the primary control.** Rotating it moves the selection in a list;
  tapping confirms; swiping right goes back. Nothing else is overloaded.
- **No free text anywhere.** Party names come from a curated period-appropriate list,
  hunting is aim-and-fire with the crown and a tap, and every decision — pace, rations,
  river crossings, purchases — is a picker or a stepper, never a keyboard.
- **Pixel art in six colours**, reminiscent of Apple II composite-NTSC rendering but
  drawn from scratch — no original MECC art is used or referenced pixel-for-pixel. See
  [`docs/art-brief.md`](docs/art-brief.md) for the full palette and asset pipeline.
- **UI text is set in Shaston**, the Apple IIGS system font, vendored under its free-use
  licence (see [`NOTICE.md`](NOTICE.md)).

The `docs/adr/` folder records the bigger decisions, including why this became a native
reimplementation instead of an Apple II emulator running the original 1985 disk image
([ADR 0005](docs/adr/0005-native-reimplementation.md)) — in short, the original expects
a keyboard, and no gesture scheme made typing tolerable on a watch.

## Building

Requires the Android SDK and a JDK 17 toolchain (Android Studio's bundled JBR works).

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum SDK is Wear OS 4 / API 33 (what the Pixel Watch 2 ships). There's no native
code and no NDK dependency, so the build is a plain Gradle/Kotlin/Compose project.

## Testing

The game engine (`core/`) is pure Kotlin with no Android dependencies, so it's covered
by ~150 JVM unit tests with no emulator needed:

```
./gradlew test
```

## Architecture

- `core/` — the game engine: trail state, the turn loop, the store, hunting, river
  crossings, scoring, RNG. Plain Kotlin, unaware Android exists.
- `ui/` — Jetpack Compose for Wear OS. `Screen.kt` is a small sealed navigation graph;
  `GameController` drives it from `core`'s state and handles save/resume.
- `ui/art/` — the pixel-art renderer, reading `.pix` assets from `assets/art/`.

## Status

A personal project, not affiliated with or endorsed by MECC or its successors.
