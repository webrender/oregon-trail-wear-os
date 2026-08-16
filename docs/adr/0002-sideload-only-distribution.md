# Sideload-only distribution, no Play Store

**Status:** accepted, still standing. Amended by
[ADR 0008](0008-signed-apk-releases.md), which publishes the sideloadable APK as a signed
GitHub release rather than leaving it to be built from source. The Play Store reasoning
below is unchanged; the "personal use, ADB only" framing is not.

This app is for personal use on the owner's own Pixel Watch 2, installed via ADB, never published to Play Store. This sidesteps Play Store's emulator/IP policy review, the app-quality bar Google mandates specifically for Wear OS listings, and any question of how "supply your own ROM" reads to a reviewer — none of that applies to a private sideload. It also means we don't need to design a ROM-acquisition disclaimer flow or public-facing legal copy. Revisit if we ever want to share this with other IIgs-owning users; that would require redesigning the file-supply flow and reviewing Google's Sept 2026 sideload developer-verification requirement, which doesn't clearly apply to personal ADB installs but is unconfirmed for certified devices going forward.
