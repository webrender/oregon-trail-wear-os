# Art brief: every graphic this game needs

Self-contained brief for producing the game's artwork. You should not need any other
context to work from this.

## What you're making art for

A native Wear OS reimplementation of *The Oregon Trail* (1985, Apple II), running on a
Pixel Watch 2. The art should be **reminiscent of the Apple II original** — not a copy
of it. All artwork here is drawn from scratch; none of MECC's original art is used or
referenced pixel-for-pixel.

## The format

One PNG per asset, authored in `art-source/`, and prepared into the assets the app ships
by:

```
python3 scripts/prepare-art.py
```

That script does two things to the art: it crops each transparent-background sprite to
its visible pixels, and it downscales everything to the largest size the app will ever
draw it at, plus half again for headroom. **Author at whatever resolution suits the
work** — the masters run to 1672x941 — and let the script cut it down.

Three consequences worth knowing:

- **Aspect ratio is the thing that matters, not pixel count.** A sprite is fitted inside
  a footprint measured in scene units (below), so making a file twice as large changes
  nothing on screen; making it twice as wide changes everything.
- **Transparency must be real transparency.** A subject sitting on a black or white
  field is a subject with a black or white box around it once it's composited over
  terrain. A soft glow around the edges is fine — anything under 6% opacity is treated
  as empty and cropped away.
- **`art-source/` is not in the repository**, and after the downscale the shipped assets
  are no longer a full-resolution copy of it. Keep the masters backed up somewhere; they
  are the only thing a future re-run has to work from.

The downscale is a plain resample, not an attempt to recover a native pixel grid. The
art has block structure, but the blocks are non-integer and drift across each image
(~6.5px in the scenes, ~9.5px in the animals, ~16px in the icons) — it is
pixel-art-*styled* rendering rather than an upscaled small bitmap, and snapping it to a
grid produces uneven blocks. If a future asset genuinely is an integer upscale of a
small bitmap, it will survive this untouched; author it at its native size and the cap
will leave it alone.

Filenames are the contract. `ArtNames` maps game concepts to these names and a unit test
fails the build if one of them has no file, so a rename has to happen on both sides.

## House style

`icon_food`, `icon_ox`, and `wagon_reference` are the reference assets. **Look at them
first** — they establish the style:

- **Bold, blocky shapes with a heavy black outline** wherever a shape meets its
  background. This is what keeps a sprite legible over terrain at watch size, and the
  original leaned on it heavily.
- **A small, saturated palette per asset.** The Apple II's six colours are no longer
  enforced by the format, but the look still comes from restraint: a handful of flat,
  bright colours, black outlines, white highlights.
- **Orange stands in for brown**, as it did on the original hardware. Timber, earth and
  hide are everywhere in this game, and orange is the established substitute; use it
  consistently so wagons, forts and oxen look like they belong to the same world.
- **Green for vegetation, blue for water and sky, white for canvas, snow and bone.**
- Keep large flat areas flat. Detail reads as noise at this size.

The game's *text and chrome* are still strictly Apple II — see `ui/theme/AppleII.kt`.
The art is not bound by that palette, but it sits inside it, so anything muddy or
low-contrast will look out of place next to the green-on-black text.

## Sizes, and why they are what they are

The watch's framebuffer is 454x454, but the visible area is a **circle of radius 227**.
Anything in the corners is physically invisible behind the bezel.

Layout is expressed in **scene units** on a 128x64 grid (`SCENE_WIDTH`/`SCENE_HEIGHT`),
mapped across whatever the scene is given on screen — so positions hold at any size or
density. Two shapes matter to an artist:

| Kind | Shape | Notes |
|---|---|---|
| Backdrop | anything wide | Scaled to **cover** the frame, cropping the overshoot. A 16:9 landscape is the safe default. Keep the outer ~10% on every side free of anything that must stay visible. |
| Sprite over a backdrop | see footprints below | Fitted inside its footprint without distortion, **standing on the footprint's bottom edge**, centred across it. |
| Icon / portrait / event art | anything | Fitted inside a square box, centred. A very tall or very wide file will read small — an icon roughly square uses the space best. |

Because a sprite sits on the bottom of its footprint, a subject should be drawn **with
its feet at the bottom of the file**. Trailing empty space below the subject is cropped
away by the prepare script, so this mostly takes care of itself.

A footprint is a request, not a promise the art has to keep. Fitting without distortion
means a subject drawn a different shape from its box is drawn *smaller* than the box, and
in the two minigames the box is also the hit box — so a raft narrower than its footprint
collects hits in water it can be seen not to be in. When authored art comes back a
different shape, the footprint is re-measured to match it and the code follows; the
numbers below are the ones the shipped art actually has.

The full-bleed screens (landmarks, rivers, the store, the title) hand the backdrop the
whole display, cropping it into the round silhouette — the reason those want margin on
every side.

## The assets

83 in total. Priorities are about unblocking work, not importance — **P0 makes the game
playable at all**, so do those first. "Footprint" is the sprite's box in scene units;
backdrops have none, since they cover the frame.

### P0 — travel screen and status readout (14)

The travel screen is where a player spends most of the game: an ox team hauling a wagon
rightward across a landscape, with supplies shown alongside.

| File | Footprint | What it is |
|---|---|---|
| `wagon_ox_1` | 56x28 | Ox team pulling a covered wagon, side view, facing right. Walk-cycle frame 1. The single most important asset in the game. |
| `wagon_ox_2` | 56x28 | Walk-cycle frame 2. Legs and wagon-body bob only — the silhouette must stay put so it reads as walking, not juddering. |
| `wagon_ox_broken` | 56x28 | The same wagon halted with a broken wheel, tilted. |
| `terrain_prairie` | backdrop | Tall-grass prairie. Kansas to Nebraska. |
| `terrain_plains` | backdrop | Flat, dry high plains. |
| `terrain_rockies` | backdrop | Mountains on the horizon. |
| `terrain_desert` | backdrop | Sagebrush and rock, Snake River country. |
| `terrain_forest` | backdrop | Pine forest, the Pacific Northwest. |
| `terrain_snow` | backdrop | Any of the above under snow, for a late run. |
| `icon_wagon` | icon | Wagon, for the supplies readout. |
| `icon_bullets` | icon | Cartridges or a powder horn. |
| `icon_clothing` | icon | Folded clothing. |
| `icon_money` | icon | Coins or a banknote. |
| `title_banner` | backdrop | Title screen hero image. Wagon silhouetted against a big sky. |

The wagon stands on the **bottom edge** of the terrain, not on the horizon: the horizon
sits anywhere from a third of the way down (the Rockies) to four fifths (the plains),
and the bottom of the frame is the only part that is ground in all six. Keep the middle
third visually quiet — busy detail there will fight the wagon.

### P1 — landmarks and set pieces (27)

One scene per landmark, all backdrops. These are what make the trail feel like a place
rather than a progress bar.

| File | What it is |
|---|---|
| `lm_independence` | Independence, Missouri. A frontier town, wagons outfitting. |
| `lm_kansas_river` | A broad, calm river crossing with a ferry on the far bank. |
| `lm_big_blue_river` | A narrower, faster river. No ferry. |
| `lm_fort_kearney` | Army post, palisade and flag. |
| `lm_chimney_rock` | **The** landmark — a tall, thin spire of rock. Make this one memorable. |
| `lm_fort_laramie` | Larger adobe-walled fort. |
| `lm_independence_rock` | A huge rounded granite dome, names carved on it. |
| `lm_south_pass` | A broad windswept saddle through the Rockies. The halfway point. |
| `lm_green_river` | Deep, fast river. Ferry available. |
| `lm_fort_bridger` | Small, rough trading post. |
| `lm_soda_springs` | Bubbling mineral springs. |
| `lm_fort_hall` | Trading post on the Snake River plain. |
| `lm_snake_river` | Wide river in a rocky gorge. |
| `lm_fort_boise` | Small fort by the river. |
| `lm_blue_mountains` | Steep forested mountains — the hardest stretch. |
| `lm_fort_walla_walla` | A fort by the Columbia. |
| `lm_the_dalles` | Rapids on the Columbia, rafts being loaded. |
| `lm_columbia_river` | Rafting the river between canyon walls. |
| `lm_oregon_city` | Journey's end. Green, settled, welcoming. This is the reward — make it feel like one. |

| File | Footprint | What it is |
|---|---|---|
| `river_ford` | backdrop | Wagon fording, water up around the axles. |
| `river_caulk` | backdrop | Wagon caulked and floating across. |
| `river_ferry` | backdrop | Wagon aboard a ferry. |
| `river_capsize` | backdrop | The wagon tipping. Should feel like a disaster. |
| `store_interior` | backdrop | Shelves, barrels, a counter. |
| `tombstone` | event art | A grave marker, for when a party member dies. |
| `wagon_arrival` | backdrop | The wagon arriving in the Willamette Valley. |
| `trail_marker` | 16x20 | A small roadside grave or signpost. |

### P2 — hunting and flavour (33)

Hunting is a real-time minigame: a hunter stands at the bottom of the frame and shoots
animals crossing it. Everything here is a sprite over `hunt_terrain`, so all need
transparent backgrounds.

| File | Footprint | What it is |
|---|---|---|
| `hunt_terrain` | backdrop | Open ground with scattered cover, seen from a low angle. |
| `hunter_stand` | 16x20 | Hunter standing, rifle held, **facing right**. |
| `hunter_walk_1` | 16x20 | Walk frame 1. |
| `hunter_walk_2` | 16x20 | Walk frame 2. |
| `hunter_shoot` | 16x20 | Firing, rifle level and held out to the right. The muzzle must be the rightmost thing in the file — the shot is spawned from that edge. |
| `animal_bison_1` / `_2` | 32x24 | Bison, two-frame run cycle. Biggest food yield. |
| `animal_deer_1` / `_2` | 24x20 | Deer, two-frame run cycle. |
| `animal_rabbit_1` / `_2` | 12x10 | Rabbit, two-frame hop. |
| `animal_squirrel_1` / `_2` | 10x8 | Squirrel. Smallest yield. |
| `animal_bear_1` / `_2` | 28x22 | Bear. Dangerous. |
| `hunt_carcass` | 16x10 | A downed animal, to be carried back. |
| `hunt_bullet` | 1x1 | A shot in flight. A plain bright dot; it is drawn a few pixels across. |
| `weather_sun` | icon | Hot or warm. |
| `weather_cloud` | icon | Cool or pleasant. |
| `weather_rain` | icon | Rainy. |
| `weather_snow` | icon | Snowy. |
| `weather_cold` | icon | Very cold — frost, bare branches. |
| `icon_wheel` | icon | Spare wagon wheel. |
| `icon_axle` | icon | Spare axle. |
| `icon_tongue` | icon | Spare wagon tongue. |
| `icon_health` | icon | Party health. A heart is anachronistic — prefer a figure. |
| `portrait_pioneer` | portrait | Another traveller met on the trail. |
| `portrait_trader` | portrait | A trader at a fort. |
| `portrait_guide` | portrait | A guide or scout. |
| `ox_dead` | event art | A fallen ox. |

**Every animal must face right.** All ten run-cycle frames are drawn facing right and
mirrored in code when the animal runs the other way, so a frame drawn facing left will
run backwards for half its appearances.

**On the portraits.** The 1985 original included encounters with Native Americans, drawn
with the stereotyping typical of its era. Do not reproduce that. Portraits here are
individual people — a trader, a guide, a fellow traveller — drawn without ethnic
caricature, costume shorthand, or "type" signalling. If a portrait only reads as its
category through a stereotype, redraw it so it reads through what the person is *doing*
instead.

### P3 — rafting the Columbia (9)

The game's ending: a real-time run down the Columbia in which the player steers the raft
with the crown, dodging rocks, and lands at the path up the bank. It is the only screen
in the game seen from **directly overhead** rather than side-on — the 1985 original used
an abstract 45° angle for the same sequence, and the watch's round frame reads better
looking straight down at a channel than across one. See docs/reference/game-mechanics-1985.md.

**The river flows down the screen.** The raft sits near the bottom and moves only left
and right; rocks scroll from top to bottom past it. Nothing here is drawn side-on, and
nothing here faces right — draw everything as seen from above, with the current running
top to bottom.

| File | Footprint | What it is |
|---|---|---|
| `river_bank` | **backdrop, no wider than tall** | The channel from above: water down the middle, rock and timber banks up both sides. See the geometry note below — this one asset has hard constraints. |
| `raft_1` | 22x27 | The raft from above: lashed logs with the wagon box and an ox or two aboard, bow pointing **down** the screen. |
| `raft_2` | 22x27 | The same raft, bobbing. Frame 2 of a two-frame cycle — shift the load and the wake, not the outline. |
| `rock_small` | 14x15 | A midstream boulder from above, white water breaking around it. |
| `rock_large` | 22x26 | A bigger one. Must read as *the same kind of thing* as `rock_small` at a glance — the player has a half-second to judge which. |
| `raft_foam` | 10x3 | A speck of foam or a wave crest. Drawn many at once, scrolling, to break up the river's repeat and to throw spray where something is struck. Keep it nearly abstract. |
| `raft_sign` | 10x17 | A direction sign staked on the bank, seen from above at a slight lean so the board is legible. Three of these pass on the way down; the third means land. |
| `raft_landing` | 28x34 | The landing: a beach and a squiggly path up the bank. This is the target the player must steer into — make it the most obvious thing on the screen when it appears. |
| `raft_wreck` | backdrop | The raft breaking up on the rocks, for the result screen. The counterpart to `river_capsize`, and it should land just as hard. |

**The backdrop's geometry is load-bearing, unusually.** `river_bank` is the one backdrop
in the game that **moves**, and the only one that is not a wide landscape. It scrolls up
the display against the current as an endless strip of itself alternating with a
vertically flipped copy, so that consecutive tiles always meet exactly — a flipped copy's
first row *is* the original's last. Two things follow:

- It is scaled to the display's **width**, exactly, and never cropped sideways. The
  height is free, except that one tile has to cover at least a full screen or the river
  visibly repeats within a single view — so **no wider than it is tall**, which a unit
  test enforces.
- **Distinctive features on the banks are fine, and welcome.** Under the original static
  backdrop they were forbidden, because a memorable boulder would have sat frozen while
  the water appeared to move past it. Now the bank travels with the water. The one thing
  to avoid is anything with an up and a down — a standing figure, a beached boat — since
  every other tile is upside down.

Within the width:

- The **navigable channel is the central 70%** — scene units 19 to 109 of 128. Rocks
  spawn only inside it, and the raft is stopped at its edges. Because the art scrolls,
  *every* row of it passes the raft, so those are the innermost extents over the whole
  file rather than an average: no rock anywhere in the image may reach past them.
- The **outer ~19 units either side are bank**, and the raft hitting them is the most
  expensive mistake in the minigame. Make them read as *solid* — rock shelf, logjam,
  gravel — not as gently shelving beach.
- Keep the water itself quiet. Every rock, sign, and speck of foam is drawn on top of
  it, and a busy river bottom makes a boulder invisible until it is too late.
- The corners are behind the bezel, as always. The banks only need to read across the
  middle two thirds of the height.

### P4 — the encounters rework (3)

Added 2026-08-15 with the encounter rewrite, and **delivered the same day** — all three
are in `art-source/` and wired up. Prompts and the reasoning behind each clause are in
[art-spec.md](art-spec.md).

| Name | Size | What it is |
|---|---|---|
| `portrait_doctor` | portrait | A frontier doctor, met on the trail. Replaces the old advice-for-a-fee guide encounter. Asking for real money — $15–60, against a farmer's whole purse of $400 — so should look like someone worth it: a bag, a coat, the bearing of someone who has done this before. Not a quack. |
| `portrait_riders` | portrait | Riders shadowing the wagon, meaning to take what they can. Also shown on the outcome screen after a fight, so it works as a *threat* rather than a portrait of one person — several mounted figures at distance, faces not readable. |
| `river_guide` | backdrop | The Snake River crossing with a hired Shoshone guide leading the team through the water. The only new backdrop. |

`portrait_guide` is **retired.** It briefly moved to the Snake River crossing screen
beside the "Hire a guide" option, but a portrait there was doing no work the chip and its
price were not already doing — the crossing screen is a menu, not a meeting. The file is
kept but nothing in the game asks for it. The guide is still drawn where he is actually
doing something: in `river_guide`, the backdrop for the crossing itself.

**The riders are what the note above is about.** They descend from the 1978 text version's
"riders ahead", where they were Native American and the game asked whether to shoot them.
They are road agents here, and are drawn as such: no feathers, no war paint, no shorthand.
If the picture only reads as *dangerous* through ethnicity, it is the wrong picture. The
doctor has the same rule from the other side — a person with a trade, not a costume.

**`river_guide` is the deliberate exception, and it is not the same thing.** Its guide is
Shoshone and wears a single eagle feather, which is not shorthand but the documented
Great Basin headdress — the Plains war bonnet is the cliché, and a single feather is the
accurate choice. The Snake River pilots really were Shoshone; that fact is the whole
reason the crossing option exists. The rule above bans a figure who reads as their
category *only* through a stereotype. It does not ban drawing a real people accurately,
and erasing them from a crossing they historically piloted would be its own distortion.
The sourcing is set out under `river_guide` in [art-spec.md](art-spec.md) — read it before
changing that image, because the accurate choice here looks at first glance like the
inaccurate one.

## Checking your work

`ArtNamesTest` fails the build if any name the UI can ask for has no file behind it,
which is the failure worth catching automatically — a missing asset draws *nothing* at
runtime, with no crash and no log line to work back from:

```
cmd.exe /c "scripts\win-build.bat testDebugUnitTest --console=plain"
```

Everything else about art is a matter of looking at it. Build, install, and walk the
screens on the emulator — proportion and legibility at watch size are not things a test
can tell you about.
