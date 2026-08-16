# ADR 0008 — Signed APK releases on GitHub, still no Play Store

**Status:** accepted, 2026-08-15. Amends [ADR 0002](0002-sideload-only-distribution.md).

## Context

ADR 0002 settled that the game is sideloaded rather than listed on Play, and its reasoning
holds. But it was written when the game was an Apple II emulator that expected the user to
supply a ROM, and when the only person who would ever install it owned the repository. Both
changed: [ADR 0005](0005-native-reimplementation.md) removed the ROM question entirely, and
[ADR 0007](0007-web-port.md) put the game somewhere strangers can actually play it.

The consequence is that people ask for the watch build, and the only answer was "clone the
repository, install the Android SDK, run Gradle". That is a real barrier in front of a
one-tap install, and none of ADR 0002's reasons for avoiding Play argue for it — they argue
against a *store listing*, not against a downloadable file.

## Decision

Push a tag, get a signed APK on the repository's Releases page.
`.github/workflows/release.yml` builds `:app:assembleRelease` on any `v*` tag and attaches
the APK to a release named for that tag. Play Store is still out, for every reason ADR 0002
gave.

The APK is signed with a key made for this purpose, held in repository secrets, with the
only readable copy of its password beside the keystore on the owner's machine.

## Why a real key, and why that matters more than it looks

The release build used to be signed with the debug key, which was right when its only job
was to be installable on the owner's watch. It cannot survive contact with CI: there is no
debug keystore on a fresh runner, so Android Gradle Plugin generates a random one, and
every release would carry a different signature.

Android identifies an installed app by its signing certificate, not by its package name or
version. Two releases signed by different keys are, to the platform, two unrelated apps that
happen to collide — the second refuses to install over the first, and the only way through
is to uninstall, which takes the player's save with it. So the key has to be stable across
every release the project will ever cut.

That makes losing it unrecoverable in a way that is easy to underestimate. There is no Play
App Signing here to rotate a key through, precisely because of ADR 0002; if the keystore
goes, every existing installation is stranded and the only fix anyone can offer is "uninstall
and start over". The key is backed up off the machine that made it, and that backup is the
whole disaster-recovery plan.

## Consequences

- Installing the game is a download, and upgrading it is a download, for as long as the key
  survives.
- A locally built APK and a released one no longer install over each other, because they
  are signed differently. This costs the person developing the game one uninstall and costs
  everyone else nothing.
- versionCode comes from the workflow run number rather than from the tag. Android only
  cares that it increases; run numbers do, and a tag-derived scheme would have to be argued
  about at 0.9 → 0.10 for no gain.
- Google's September 2026 developer-verification requirement for sideloaded apps, flagged as
  unconfirmed in ADR 0002, is now the thing most likely to end this arrangement. It is aimed
  at installs on certified devices, which is what a Pixel Watch is. Nothing here depends on
  it having an answer today — the browser build (ADR 0007) needs no install at all and is
  the fallback if the answer turns out to be unfavourable.
