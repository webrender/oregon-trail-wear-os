# Art spec: generation prompts for the rafting minigame

Prompts for the nine P3 assets in [art-brief.md](art-brief.md) — the Columbia rafting
sequence. The brief is the contract (filenames, footprints, why each asset exists); this
file is just how to make them.

## How to use this

1. Paste the **style preamble** below, then the asset's prompt.
2. Set the **canvas ratio** given for that asset.
3. Save as `<filename>.png` into `art-source/`, then run `python3 scripts/prepare-art.py`.

**Two ratios are listed per asset and they do different jobs.** *Canvas ratio* is what you
ask the generator for. *Subject proportion* is the shape the drawn thing itself should
have — and that is the one that matters, because `prepare-art.py` crops every sprite to
its visible pixels, throwing the canvas away. The app then fits the cropped subject inside
a footprint of fixed proportions, so a subject drawn a different shape from its footprint
lands on screen smaller than intended, with dead space either side that the raft and the
rocks still collide in. Square canvases are fine for almost everything; just get the
*subject's* proportions right.

**The proportions here are what the shipped art turned out to be, not what was asked for
first time.** The generator drew all eight of these taller and narrower than the original
spec, and the footprints in `RaftScreen.kt` were re-measured to match rather than the art
being squeezed to fit them. If you regenerate one and it comes back a different shape
again, do the same thing: change the footprint, and update both this file and the
[art-brief](art-brief.md) table.

Backdrops are the exception — they are not cropped, and their canvas ratio is the real
constraint.

## Style preamble

Paste this in front of every prompt:

```
Retro video-game illustration in the spirit of a 1985 Apple II hi-res game — bold,
blocky, and flat, but rendered cleanly rather than as a literal low-resolution bitmap.
Heavy black outlines wherever a shape meets its background. A small, saturated palette:
a handful of flat bright colours, black outlines, white highlights, no gradients, no
texture noise, no painterly shading. Orange stands in for brown — timber, earth and hide
are all orange. Green for vegetation, blue for water, white for foam and canvas. Large
flat areas stay flat; fine detail reads as noise at watch size. No text, no lettering,
no numbers, no watermark, no UI elements, no border or frame.
```

## The overhead rule

Eight of these nine are seen **from directly above**, looking down at the water, with the
current running from the top of the image to the bottom. This is the only screen in the
game drawn that way — everything else is side-on — so it is worth stating in the prompt
every time, or the generator will quietly give you a boat seen from the shore.

`raft_wreck` is the exception: it is a normal side-on scene.

---

## `river_bank` — the channel

**Canvas 1:1.** The width is the exact fit — this backdrop is scaled to the display's
width and never cropped sideways, because the code puts the banks at fixed positions
regardless of what the art shows. The height is free, except that it may not be *shorter*
than the width, which a unit test enforces: the river scrolls, and one tile has to cover a
full screen or the same stretch of bank appears twice at once. Square is the safe answer.

**Water occupies the central 70% of the width** — scene units 19 to 109 of 128 — with
banks filling the outer 15% on each side, running the full height.

> **Delivered 2026-08-14 and measured at 74.6%**: water from 14–19 on the left to 109–116
> on the right, with the banks deliberately ragged. `RaftScreen`'s `CHANNEL_LEFT` and
> `CHANNEL_RIGHT` are set to the *innermost* of those, 19 and 109, so that nowhere in the
> art does rock reach past them. Because the backdrop scrolls, every row of it passes the
> raft in turn — an average would put the raft on the rocks a third of the time. If you
> regenerate this, keep the rock clear of 19–109 everywhere, or re-measure and update the
> constants.

**It scrolls, and the way it does shapes the art.** The strip is built by alternating this
image with a vertically flipped copy of itself, which is what lets it run forever without
being drawn to tile: the flipped copy's first row *is* the original's last row, so the
join is exact by construction, in both directions. The consequences:

- **Distinctive features on the banks are wanted now**, not forbidden. Boulders,
  driftwood stacks, grass — they travel with the water, and they are what makes the
  current legible.
- **Nothing with an up and a down.** Every other tile is upside down, so a beached canoe,
  a standing figure or a tree seen in profile will spend half the descent inverted.
  Gravel, rock and scrub read the same either way; that is why they suit this.

```
Top-down aerial view of a wide river channel running vertically from the top of the frame
to the bottom, filling the middle 70% of the image with flat bright blue water. Solid
rocky banks run down the left and right edges, each about 15% of the image width — dark
grey rock shelf and orange-brown gravel with a hard black edge where they meet the water,
scattered with pale boulders, stacked driftwood and tufts of green scrub. The water
surface is calm and near-empty: a few sparse white foam streaks, nothing else. No boats,
no rocks in the channel, no people, nothing that has an obvious top and bottom.
```

- **Keep the water quiet.** Every rock, sign and foam speck is drawn on top of this. A
  busy river bottom hides a boulder until it is too late to steer.
- **The banks must read as solid.** Hitting one is the most expensive mistake in the
  minigame; a gently shelving sandy beach would be a lie about that.

## `raft_1` — the raft

**Canvas 1:1. Subject proportion ~4:5, slightly taller than wide.** Transparent background.

```
Top-down aerial view of a pioneer river raft: a platform of lashed timber logs with a
covered wagon box strapped to the middle, white canvas cover facing up, and a yoke of oxen
standing at the front. Two figures with steering poles at the outer corners. The raft is
pointing down the frame — the bow is at the bottom edge of the image, the stern at the
top. Seen from straight above, no horizon, no water. Slightly taller than it is wide.
Transparent background.
```

- **Bow down.** The raft travels toward the bottom of the screen. A raft drawn bow-up runs
  the whole descent backwards.
- **The wagon has to be visible from above** — it is the thing that tells the player this
  is *their* wagon on that raft, and the white canvas is the only thing that reads at
  22 scene units across.

## `raft_2` — the raft, bobbing

**Canvas 1:1. Subject proportion ~4:5.** Transparent background.

Generate this as an *edit or variation of* `raft_1`, not from scratch — it is frame two of
a two-frame cycle running at 5fps, and two independently generated rafts will jitter
between different boats rather than read as one raft bobbing.

```
The same top-down raft, identical in size, outline and position, riding slightly lower and
tilted a few degrees. The logs and wagon shift together as one; the white water breaking
around the raft's edges changes shape. Everything else is unchanged. Transparent
background.
```

- **The silhouette must stay put.** Same rule as the wagon walk cycle: move the load and
  the wake, not the outline, or it judders instead of bobbing.

## `rock_small` — a midstream boulder

**Canvas 1:1. Subject proportion ~14:15, near square.** Transparent background.

```
Top-down aerial view of a single grey boulder breaking the surface of a river, seen from
straight above. Flat grey rock with a hard black outline and one white highlight on the
upstream face. A collar of white broken water foams around its base, heaviest at the top
edge where the current hits it. Nothing else in frame. Transparent background.
```

- **The white water is the tell.** Without foam at its base the rock reads as floating
  debris rather than something fixed in the current.
- **Spray is not rock, and the game knows the difference.** The foam is free to spread as
  far as it likes — `RockSize`'s `sprayInset` values in `RaftScreen.kt` are measured from
  the art and keep collisions to the boulder inside it. What that inset can't fix is a
  boulder drawn small in a large cloud: it lands on screen the size of the cloud and hits
  like the pebble in the middle. Keep the rock itself the bulk of the frame.

## `rock_large` — a bigger boulder

**Canvas 1:1. Subject proportion ~5:6.** Transparent background.

```
Top-down aerial view of a large grey boulder in a river, seen from straight above,
drawn in exactly the same style as the smaller boulder: flat grey rock, hard black
outline, white highlight upstream, a heavy collar of white broken water around its base.
Broader and blunter than the small one, with a second smaller rock shoulder fused to one
side. Transparent background.
```

- **It must read as the same *kind* of thing as `rock_small` at a glance.** The player has
  about half a second to judge which one is coming and how far to move; two rocks in
  different visual languages make that a guess.

## `raft_foam` — a speck of water

**Canvas 1:1. Subject proportion ~7:2, much wider than tall.** Transparent background.

Drawn eight at a time, scrolling, to sell a current that the static backdrop cannot.

```
A single small crest of white river foam seen from directly above: two or three flat white
horizontal dashes of slightly different lengths, with a hint of pale blue between them.
Almost abstract — a mark, not an object. Heavy black outline is NOT wanted here. Wider
than it is tall. Transparent background.
```

- **This is the one asset with no black outline.** Foam sits on water of nearly its own
  brightness; an outline turns eight of these into eight distracting blobs.

## `raft_sign` — a direction sign on the bank

**Canvas 1:1. Subject proportion ~3:5, tall and narrow.** Transparent background.

Three of these pass on the way down, and the third means *land now* — the original's only
pacing device, and ours.

```
A wooden direction sign staked on a riverbank, seen from above at a slight angle so the
board is readable as a board rather than as an edge-on line. Orange timber post, a plain
white plank nailed across it bearing a single simple black arrow glyph pointing to the
left. No words, no letters. Small patch of orange-brown gravel at the foot of the post.
Transparent background.
```

- **The arrow points left**, toward the bank the landing is on. It is a hint, not
  decoration.
- **No readable text.** The game's own font does the talking, and generated lettering at
  ten scene units is mush.

## `raft_landing` — the way out

**Canvas 1:1. Subject proportion ~4:5.** Transparent background.

The target the player has to steer into. When this appears the descent is nearly over.

```
Top-down aerial view of a river landing place: a small pale gravel beach at the water's
edge with a pale worn footpath zigzagging up and away from it through green scrub, seen
from straight above. The path is a bold light switchback — three or four clear zigzags —
against darker green. Bright, obvious, welcoming. Transparent background.
```

- **Make it the most obvious thing on the screen.** It appears once, has about three
  seconds on screen, and missing it costs the player two days. It should read instantly
  at a glance, from the far side of the channel.
- The beach edge goes at the **right-hand side** of the image — the landing sits against
  the left bank, so its water edge faces right, into the channel. That edge is what the
  code positions the whole landing by (`LANDING_EDGE`), pinning it just past the water
  line so the landing reads as a break in the cliff rather than an island: gravel hard
  against the right edge of the file, scrub filling the rest, nothing floating free.
- **Keep the left third expendable.** The bank it sits on is at the edge of a round
  display, so the far side of the landing is under the bezel when it matters. Anything the
  player has to see — the beach, the mouth of the path — belongs on the right.

## `raft_wreck` — the disaster

**Canvas 16:9 backdrop** (match the other backdrops — `river_capsize` is 820x461). Not
transparent. Keep the outer ~10% on every side clear of anything that must survive the
round display's crop.

This is the counterpart to `river_capsize` and it should land just as hard.

```
A pioneer log raft breaking apart on rocks in a fast river, seen from the bank at water
level. Lashings burst, orange timber logs pitching up at broken angles, the white canvas
wagon cover half-submerged and washing downstream. Grey boulders and heavy white
whitewater. Steep dark canyon walls behind, a strip of pale sky above. Bleak. No people
visible in the water.
```

- **No visible drowning figures.** The game names the dead in text, on the same screen,
  which is heavier than any picture of it — and one is a real risk of being tasteless
  where the other is not.

---

## Checking the results

```
python3 scripts/prepare-art.py
cmd.exe /c "scripts\win-build.bat testDebugUnitTest --console=plain"
```

`ArtNamesTest` fails if any of the nine filenames is missing, and separately if
`river_bank` is wider than it is tall. Everything else is a matter of looking at it — build,
install, and take the debug menu's **The Columbia River** entry, which drops straight into
the minigame.

**All nine are now the real thing.** The flat-colour placeholders they replaced — grey
ellipses for rocks, an orange rectangle for the raft — are gone, and nothing in the code
ever depended on what they looked like, only on the proportions above.

The trap that mattered while they were placeholders is worth knowing if you ever author a
tenth: `prepare-art.py` deletes the assets directory and rebuilds it from `art-source/`,
so anything with no file in `art-source/` disappears on the next run and `ArtNamesTest`
goes red. Add the source file before you add the name.
