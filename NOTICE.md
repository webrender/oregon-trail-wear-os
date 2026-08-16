# Third-party notices

## Shaston

**Shaston**, a font by **Kreative Software** (Rebecca Bettencourt), is vendored verbatim
and unmodified at `shared/src/androidMain/assets/fonts/shaston_320.ttf` — the Apple IIGS
(GS/OS) system font, in its 320x200 (1-by-1 pixel aspect ratio) variant.

Used under the **Kreative Software Relay Fonts Free Use License, version 1.2f**, which
permits embedding and redistribution free of charge, and forbids selling the font or
creating derivative works of it. The full licence text ships alongside the font, inside
the APK and in the web build's static output, at
`assets/licenses/kreative-relay-fonts-free-use-license.txt`.

Credit: Kreative Korporation / Kreative Software — <https://www.kreativekorp.com/>

Two conditions of that licence constrain this repository:

- **The font must not be modified.** Do not subset, re-compress, or run it through a
  font optimiser, and keep it out of any resource-shrinking pass.
- **The app must not be sold for a fee.** Both builds are free — the watch's is
  sideload-only (see `docs/adr/0002-sideload-only-distribution.md`) and the browser's is a
  static site — which satisfies that.

Note that the web build **serves the `.ttf` over HTTP**, since a browser cannot use a font
it has not been sent. That is redistribution, which this licence permits free of charge;
it does mean the file is directly downloadable rather than packed inside an APK.
