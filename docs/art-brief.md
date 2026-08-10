# Art brief: every graphic this game needs

Self-contained brief for producing the game's artwork. You should not need any other
context to work from this.

## What you're making art for

A native Wear OS reimplementation of *The Oregon Trail* (1985, Apple II), running on a
Pixel Watch 2. The art should be **reminiscent of the Apple II original** — not a copy
of it. All artwork here is drawn from scratch; none of MECC's original art is used or
referenced pixel-for-pixel.

## The format

Art is plain text, one file per asset, at `app/src/main/assets/art/<name>.pix`.

```
# Comments start with a hash and are allowed in the header only.
name: icon_food
size: 16x16
pixels:
................
.....KKKKKK.....
....KWWWWWWK....
...KWOOOOOOWK...
```

Rules the parser enforces, and will fail the build over:

- `name:` **must match the file name** without its extension.
- `size:` is `<width>x<height>`, and the pixel rows must match it exactly — the right
  number of rows, each the right number of characters.
- Every character must be from the palette below. No spaces inside pixel rows.
- An entirely transparent asset is rejected as unfinished.

### Palette — these six colours and nothing else

| Char | Colour | Hex |
|---|---|---|
| `.` | transparent | — |
| `K` | black | `#000000` |
| `W` | white | `#FFFFFF` |
| `G` | green | `#38CB00` |
| `V` | violet | `#C734FF` |
| `O` | orange | `#F25E00` |
| `B` | blue | `#0DA1FF` |

These are the values AppleWin actually renders, extracted from its source. They are not
the naive full-saturation primaries — real Apple II hardware made colour through NTSC
artifacting, and a pure `#00FF00` green reads as wrong to anyone who remembers the
machine.

## House style

Three reference assets already exist. **Read them first** — `icon_food.pix`,
`icon_ox.pix`, and `wagon_reference.pix` establish the style:

- **One-pixel black outline** wherever a shape meets transparency. This is what makes
  sprites legible against any background, and the original leaned on it heavily.
- **No dithering and no anti-aliasing.** Every pixel is one of the six colours. Do not
  simulate intermediate shades by checkerboarding — the display is too small and it
  turns to mud.
- **Orange stands in for brown.** The palette has no brown, and timber, earth and hide
  are everywhere in this game. Orange is the established substitute; use it
  consistently so wagons, forts and oxen look like they belong to the same world.
- **Green for vegetation, blue for water and sky, white for canvas, snow and bone.**
- **Violet is the scarce colour.** It has no natural referent here, so reserve it for
  things that should catch the eye — distant mountains at dusk, a flash of danger.
- Keep large flat areas flat. Detail reads as noise at this size.

## Canvas sizes, and why they are what they are

The watch's framebuffer is 454x454, but the visible area is a **circle of radius 227** —
measured on the actual device. Anything in the corners is physically invisible behind
the bezel. The sizes below are chosen against that constraint:

| Kind | Size | Rendered at | Notes |
|---|---|---|---|
| Scene / landmark | **128x64** | x3 = 384x192 | A wide band centred vertically. The circle is at its widest across the middle, so 384 fits there even though the inscribed square is only ~321 across. |
| Sprite over a scene | up to **56x32** | x3 | Drawn on top of a scene, so it must have transparent margins. |
| Icon | **16x16** | x2 = 32x32 | Status readouts. Must read clearly at 32 device pixels. |
| Portrait | **24x32** | x3 | People you meet along the trail. |

Art is always scaled by a **whole number** with no smoothing, so a 128x64 scene is
exactly 384x192 device pixels. Do not design anything expecting fractional scaling.

## Full-bleed scenes (in progress — not yet applied beyond the store)

The standard 128x64 scene deliberately stops short of the bezel: it's centred in a
384x192 band with black on all sides, and only the *middle* of that band reaches the
edge of the visible circle. A prototype on the Store screen proved a scene can instead
bleed all the way to the true bezel edge — top, left, and right — with the round
display itself cropping whatever the wider/taller canvas overshoots into the correct
curved silhouette. It reads much better: no dead black gap between the art and the
title. The engineering side of this is done and generic (`Scene` sizes off whatever
backdrop it's given), but it only works if the *art* leaves the right margins — most
existing landmark/river art has mountains, forts, rock spires, or wagons that already
run close to the old 128-wide edge, which a full-bleed canvas would now expose rather
than hide. Redoing those is the blocker before rolling this out past the store.

**New canvas: 150x96** (up from 128x64), rendered at the same x3 scale = 450x288
device pixels. Shipped reference: `store_interior_tall.pix` — read it before doing
this for anything else.

- **Top 32 rows are the extension.** They should read as a natural continuation of
  whatever's at the top of the standard 128x64 version of the same scene (sky, in every
  existing asset) — not a hard seam. A cloud or gradient can continue up into this
  band; it doesn't have to be flat, unlike the mechanical padding used for the store's
  wall.
- **The outer ~13 columns on *both* sides, on every single row, must stay background
  only.** This is the one hard rule that's new relative to the standard format: in the
  128-wide version, a shape was allowed to run all the way to column 0 or 127 because
  the bezel hid it. In the 150-wide version, those columns are visible, so anything
  that used to touch the old edge (a fort wall, a mountain ridge, a rock spire) needs
  to be redrawn to stay clear of the new one, with only flat/background colour in that
  margin. Silhouettes can still fill the *original* 128-wide area edge to edge — the
  new columns are pure extension, not more room to draw in.
- Keep the existing 128x64 composition intact within the new canvas's centre — this is
  additive margin, not a redesign of the scene itself.

**Assets that need this treatment** (same filenames, replacing the 128x64 versions —
every screen that references them gets the same small layout update once they land):

`lm_independence`, `lm_kansas_river`, `lm_big_blue_river`, `lm_fort_kearney`,
`lm_chimney_rock`, `lm_fort_laramie`, `lm_independence_rock`, `lm_south_pass`,
`lm_green_river`, `lm_fort_bridger`, `lm_soda_springs`, `lm_fort_hall`,
`lm_snake_river`, `lm_fort_boise`, `lm_blue_mountains`, `lm_fort_walla_walla`,
`lm_the_dalles`, `lm_columbia_river`, `lm_oregon_city`, `river_ford`, `river_caulk`,
`river_ferry`, `river_capsize`, `wagon_arrival` — 24 in total.

## The assets

73 in total. Priorities are about unblocking work, not importance — **P0 makes the game
playable at all**, so do those first.

### P0 — travel screen and status readout (14)

The travel screen is where a player spends most of the game: an ox team hauling a wagon
rightward across a landscape, with supplies shown alongside.

| File | Size | What it is |
|---|---|---|
| `wagon_ox_1` | 56x28 | Ox team pulling a covered wagon, side view, facing right. Walk-cycle frame 1. The single most important asset in the game. |
| `wagon_ox_2` | 56x28 | Walk-cycle frame 2. Legs and wagon-body bob only — the silhouette must stay put so it reads as walking, not juddering. |
| `wagon_ox_broken` | 56x28 | The same wagon halted with a broken wheel, tilted. |
| `terrain_prairie` | 128x64 | Tall-grass prairie. Kansas to Nebraska. |
| `terrain_plains` | 128x64 | Flat, dry high plains. |
| `terrain_rockies` | 128x64 | Mountains on the horizon. |
| `terrain_desert` | 128x64 | Sagebrush and rock, Snake River country. |
| `terrain_forest` | 128x64 | Pine forest, the Pacific Northwest. |
| `terrain_snow` | 128x64 | Any of the above under snow, for a late run. |
| `icon_wagon` | 16x16 | Wagon, for the supplies readout. |
| `icon_bullets` | 16x16 | Cartridges or a powder horn. |
| `icon_clothing` | 16x16 | Folded clothing. |
| `icon_money` | 16x16 | Coins or a banknote. |
| `title_banner` | 128x64 | Title screen hero image. Wagon silhouetted against a big sky. |

`icon_food` and `icon_ox` already exist.

Terrain backgrounds are drawn behind the wagon sprite, so keep the **middle third
visually quiet** — busy detail there will fight the wagon. Horizon around a third of
the way down.

### P1 — landmarks and set pieces (27)

One scene per landmark, all **128x64**. These are what make the trail feel like a place
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

| File | Size | What it is |
|---|---|---|
| `river_ford` | 128x64 | Wagon fording, water up around the axles. |
| `river_caulk` | 128x64 | Wagon caulked and floating across. |
| `river_ferry` | 128x64 | Wagon aboard a ferry. |
| `river_capsize` | 128x64 | The wagon tipping. Should feel like a disaster. |
| `store_interior` | 128x64 | Shelves, barrels, a counter. |
| `tombstone` | 32x40 | A grave marker, for when a party member dies. |
| `wagon_arrival` | 128x64 | The wagon arriving in the Willamette Valley. |
| `trail_marker` | 16x20 | A small roadside grave or signpost. |

### P2 — hunting and flavour (32)

Hunting is a real-time minigame: a hunter moves around a terrain screen and shoots
animals. Everything here is a sprite over `hunt_terrain`, so all need transparent
margins.

| File | Size | What it is |
|---|---|---|
| `hunt_terrain` | 128x64 | Open ground with scattered cover, seen from a low angle. |
| `hunter_stand` | 16x20 | Hunter standing, rifle held. |
| `hunter_walk_1` | 16x20 | Walk frame 1. |
| `hunter_walk_2` | 16x20 | Walk frame 2. |
| `hunter_shoot` | 16x20 | Firing, rifle raised. |
| `animal_bison_1` / `_2` | 32x24 | Bison, two-frame run cycle. Biggest food yield. |
| `animal_deer_1` / `_2` | 24x20 | Deer, two-frame run cycle. |
| `animal_rabbit_1` / `_2` | 12x10 | Rabbit, two-frame hop. |
| `animal_squirrel_1` / `_2` | 10x8 | Squirrel. Smallest yield. |
| `animal_bear_1` / `_2` | 28x22 | Bear. Dangerous. |
| `hunt_carcass` | 16x10 | A downed animal, to be carried back. |
| `weather_sun` | 16x16 | Hot or warm. |
| `weather_cloud` | 16x16 | Cool or pleasant. |
| `weather_rain` | 16x16 | Rainy. |
| `weather_snow` | 16x16 | Snowy. |
| `weather_cold` | 16x16 | Very cold — frost, bare branches. |
| `icon_wheel` | 16x16 | Spare wagon wheel. |
| `icon_axle` | 16x16 | Spare axle. |
| `icon_tongue` | 16x16 | Spare wagon tongue. |
| `icon_health` | 16x16 | Party health. A heart is anachronistic — prefer a figure. |
| `portrait_pioneer` | 24x32 | Another traveller met on the trail. |
| `portrait_trader` | 24x32 | A trader at a fort. |
| `portrait_guide` | 24x32 | A guide or scout. |
| `ox_dead` | 24x16 | A fallen ox. |

**On the portraits.** The 1985 original included encounters with Native Americans, drawn
with the stereotyping typical of its era. Do not reproduce that. Portraits here are
individual people — a trader, a guide, a fellow traveller — drawn without ethnic
caricature, costume shorthand, or "type" signalling. If a portrait only reads as its
category through a stereotype, redraw it so it reads through what the person is *doing*
instead.

## Checking your work

The test suite parses every `.pix` file that ships and fails on any malformed or blank
one, with the file name, row and column of the problem:

```
cmd.exe /c "scripts\win-build.bat testDebugUnitTest --console=plain"
```

The relevant test is `PixelArtTest.every shipped asset parses and is not blank`. Run it
after adding assets — a sprite with one row the wrong length is invisible in a text
editor and obvious to the parser.

There is no on-device preview yet. Getting the pixel counts right is what the test is
for; getting the *art* right is a matter of reading the reference assets and keeping the
silhouettes bold.
