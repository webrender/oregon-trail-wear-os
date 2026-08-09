# Native Kotlin reimplementation, not emulation

Supersedes [0003](0003-applewin-libretro-frontend-as-core.md) and rewrites
[0004](0004-unified-input-scheme.md).

We are reimplementing Oregon Trail as a native Wear OS app in Kotlin, rather than
emulating the 1985 Apple II release.

The emulator approach was not abandoned because it failed to build — it built and ran.
It was abandoned after actually playing it. The 1985 game was designed for a machine
with a keyboard and expects the player to type: party names, `BANG` while hunting,
numeric menu choices committed with Return. ADR 0004 documented an elaborate gesture
vocabulary to route that typing through the watch's dictation sheet, and that scheme is
what play testing rejected. The problem was never the emulation layer; it was that the
original's input model cannot be squeezed onto a 1.2" round screen. No amount of work on
the emulator frontend would have fixed it, because the thing needing to change was the
game's own interaction design.

Reimplementing lets every prompt become a picker or a gesture, with **no free text
anywhere in the app**. Party names come from a curated list; hunting is aim-and-fire.
That is only possible when we own the game loop.

Three further consequences, all of which happen to be wins:

- The APK has no native code, so it is architecture-independent. The emulator build
  needed an explicit `armeabi-v7a` ABI because that is the only ABI the Pixel Watch 2's
  installer accepts; that constraint is now moot.
- The game core is pure Kotlin with no Android dependencies, so it is unit-testable on
  the JVM. Balance and rules can be iterated without an emulator or watch in the loop,
  which is the slow part of this project's feedback cycle.
- The player no longer has to supply a disk image, so there is no acquisition flow, no
  storage permission, and no network permission.

## What we are not doing

We are not decompiling the 1985 release, and we are not copying code from any community
reimplementation. Game rules and data tables are facts and not copyrightable, so the
mechanics in `docs/reference/game-mechanics-1985.md` are derived freely from research;
but the original's prose and art belong to MECC, so all display text and all artwork in
this app are written and drawn from scratch. Note that the best-sourced community
implementation found (`ayebear/oregon-trail`) carries no licence at all, making its code
all-rights-reserved — it was used strictly as a factual cross-reference.

ADRs [0001](0001-dedicated-single-game-build.md) (single-game build) and
[0002](0002-sideload-only-distribution.md) (sideload-only) are unaffected and still
hold, though 0002's reasoning about ROM-supply and emulator policy review is now
redundant rather than load-bearing.

## Cost of reversing

High, and deliberately so. Emulation gives you the real game's behaviour for free;
reimplementation means every rule is ours to get right, and "is this faithful?" becomes
a judgement call we answer rather than a property we inherit. We accept that in exchange
for a game that is actually playable on the target device.
