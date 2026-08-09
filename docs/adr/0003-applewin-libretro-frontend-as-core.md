# Emulator core: AppleWin, built as a new libretro-style frontend

> **SUPERSEDED (2026-08-09) by [0005](0005-native-reimplementation.md).** The emulator
> approach worked technically — AppleWin built and ran on the watch — but failed on
> playability: the 1985 game requires typing, and no gesture scheme made that tolerable
> on a 1.2" screen (see 0004, also rewritten). The app is now a native Kotlin
> reimplementation with no emulator. The research below about the IIgs version being
> unmodified Apple II software remains accurate and is kept for the record. The
> AppleWin submodule has been removed; its hi-res palette values were extracted first
> and live in `docs/reference/game-mechanics-1985.md`.

We're building on AppleWin's core rather than a full Apple IIgs emulator (KEGS/GSplus) or writing a 6502 emulator from scratch. Research confirmed the "Apple IIgs version" of Oregon Trail is unmodified 1985 Apple II software running in the IIgs's backward-compatibility mode — it never touches SHR graphics, 65816 native mode, or the Ensoniq DOC chip — so full IIgs emulation is unneeded complexity.

AppleWin's upstream repo (audetto/AppleWin fork) already separates a platform-independent core (`source/`: CPU, Memory, Disk, NTSC composite-color rendering, YAML-based save state) from five pluggable frontends (`source/frontends/`: Windows, common2, ncurses, Qt, SDL, libretro), built via CMake. The libretro frontend is the smallest and most relevant template — no windowing-system assumptions, just video/audio/input callbacks — and has already been built and run on Android via RetroArch, proving NDK/ARM buildability.

We're writing a new, sixth frontend (Wear OS/JNI) modeled on the libretro one, rather than hand-porting the Windows frontend's DirectX/Win32 code. This means CPU accuracy, disk format support (including WOZ), composite-color rendering, and save-state are inherited for free; the only new code is the watch-specific presentation and input layer.

Reversing this later (e.g. to a different core) would mean re-deriving the JNI bridge and input-translation layer against a different API — a real cost, hence recording it.
