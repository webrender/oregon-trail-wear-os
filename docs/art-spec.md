# Art spec: generation prompts for the trail map

Three prompts, for three 2:1 images. `scripts/split-map.py` joins them into one long
strip and cuts it into the six tiles the map screen scrolls through.

## Why three and not six

Six separately generated tiles never line up — five seams, all of them wrong. One giant
image lines up perfectly but is painful to generate. Three 2:1 images split the
difference: cutting each in half gives six tiles, and three of the five seams fall
*inside* a source image and are therefore exact. Only two are joins between separate
generations, and both of those are placed where the composition hides them.

## How to use this

Add this sentence to your standard preamble, so it applies to all three:

```
A map of the American West seen from directly above, with mountains and trees drawn
as side-on symbols. No trails, roads, paths or writing.
```

Then paste one prompt below it. **Canvas 2:1** for all three. Save them anywhere and run:

```
python3 scripts/split-map.py west.png middle.png east.png
python3 scripts/prepare-art.py
```

The slicer writes `map_1.png` … `map_6.png` into `art-source/`, left to right. Order
matters: the images go **west first**, because each one is drawn in standard map
orientation with west on its own left — ocean on the left of the west image, mountains on
the left of the east image. So the finished strip runs Pacific to Missouri, and the party
travels right to left across it as the run progresses.

The images are joined with a **hard cut**, not a blend. Crossfading was tried and
measured against the real art and it lost: Apple II styling is flat colour with hard black
outlines and dense repeating symbols, so alpha-blending two such fields makes
semi-transparent phantom peaks rather than a gradient — clearly visible at watch scale at
8% overlap, worse at 20%. Butted together, two dense forests just read as more forest.
The upshot for the art is that shared edges need only be the *same kind* of terrain, not
aligned, and nothing near an edge is lost.

## Size

Each tile is capped at **821px on its long edge** (`DISPLAY_PX` 480 × 1.14 backdrop
coverage × 1.5 headroom, in `prepare-art.py`).

| Each source | Tile after slicing | Verdict |
|---|---|---|
| 3072 × 1536 | 1453 × 1536 → capped to 776 × 820 | Ideal |
| 2048 × 1024 | 969 × 1024 → capped to 776 × 820 | Comfortable, recommended |
| 1664 × 832 | 787 × 832 → capped to 776 × 820 | Exactly enough |
| 1024 × 512 | 484 × 512 | Too soft, visibly blurry on the watch |

Anything at or above 1664 × 832 hits the cap, so past about 2048 wide the extra pixels
are discarded. Three modest images are the point of this approach — there is no reason to
fight the generator for a huge one.

---

## 1. East — prairie and plains

> The Great Plains. Right to left: prairie, flat grassland, dry brown plains. Two narrow rivers
> run from top to bottom across the prairie on the right. A wider shallow river winds left
> to right through the dry plains. A lone rock spire near the middle. An unbroken wall of
> mountains covers the whole left edge, top to bottom.

## 2. Middle — the Rockies and the desert

> The Rocky Mountains and the desert west of them. Mountains fill the right edge, top to
> bottom, parting in the middle at a wide sagebrush gap with a river running top to bottom
> through it. West of the mountains, bare brown rocky desert, with a second river running top to
> bottom in a deep canyon. Forested mountains fill the left edge, top to bottom.

## 3. West — the mountains and the Pacific

> The Pacific Northwest. Forested mountains fill the right edge, top to bottom. A lone
> snow-capped volcano near the middle. A wide river runs left to right into an estuary,
> with a green valley below it and forested green hills above it. A narrow strip of ocean
> along the far left edge.

---

## What the prompts are doing

Worth knowing before editing them, because most of what looks like it could be cut
already has been.

**Mountains fill both shared edges, top to bottom.** This is the load-bearing
instruction and the reason those clauses are so specific. The joins are placed on the
Continental Divide and the Cascades, real barriers where the country changes character,
so the terrain either side is *supposed* to differ. Mountains running the full height of
the frame put busy vertical structure exactly where continuity fails, and give the eye
nothing horizontal to follow across the join.

It also keeps rivers off the seams, without having to ask for that directly. A river
arriving at 40% of the height on one side of a join and 55% on the other announces it
instantly — it is the only feature the eye tracks *across* a join, where terrain and
vegetation differ unnoticed. But "no river touches the left edge" is a negative
instruction, the weakest thing you can give an image generator and very likely to produce
one. An edge already full of mountains has no room for a river.

**Rivers run top-to-bottom if the trail crosses them, left-to-right if it follows them.**
This is the instruction most likely to be lost in editing, and the first version of this
spec lost it. The party *crosses* the Kansas, Big Blue, Green and Snake — each one is a
`LandmarkKind.River` in `Trail.kt` with a ford/caulk/ferry decision — and a river you
cross runs perpendicular to your travel, which on this strip is top to bottom. The party
*follows* the Platte, and rafts down the Columbia; those run parallel to travel, so left
to right. Getting this backwards puts a river-crossing marker on dry grass, which is the
map contradicting the game. Two vertical rivers in the east prairie, two vertical in the
middle image, one horizontal in each of east and west.

**"Brown" on every patch of dry ground.** This is the one place the prompts overrule the
generator's palette choices, and it is worth the two words. Left to itself it renders dry
plains and desert as *white* — measured at 26% and 27% near-white across the east and
middle images, against 0.0% in the existing `art-source/terrain_desert.png`. That
contradicts the house rule in [art-brief.md](art-brief.md): orange stands in for brown and
is what earth is made of, while white is for canvas, snow and bone. It also has a specific
cost — white ground abutting white snow-capped peaks merges, and a trail that reads as
snowbound undercuts a game whose whole tension is beating the winter.

**Every region needs something positive in it.** An early middle-image prompt left the
Pacific Northwest's northern half unspecified and the generator filled it with desert
borrowed from the neighbouring image. Empty space does not stay empty; name what belongs
there, hence "forested green hills above it".

**No volcanoes outside the west image.** "Volcanic desert" in an early draft of the middle
prompt produced a field of eight cinder cones — a repeated symbol meaning nothing, busy at
watch size, and competing with the one landmark that has to be unmistakable. The lone
snow-capped cone in the west image is Mount Hood and should be the only one on the map.

**Landscape only.** The app draws the trail, the landmark markers and every name over the
top as live graphics, which is what lets the art be re-sliced, re-overlapped or
repositioned without invalidating a single landmark coordinate. A generator asked for a
map of this region will draw a dotted route across it unless told not to, hence the
`No trails, roads, paths or writing` in the preamble.

**Few and large.** Each tile is drawn about 360px wide on the watch, so anything smaller
than about a fortieth of the image height disappears. This is not in the prompts — the
Apple II styling covers it — but it is the first thing to check on delivery, because busy
composition is the one flaw that cannot be fixed in post.

## Keep them short

These are 45–65 words each. Two earlier versions ran to 180 and then 115, and the
generator blended and dropped instructions at both lengths. Styling, palette and aspect
ratio all live in the preamble, so a prompt should be nothing but what makes this image
different from the other two. If one needs fixing, swap a sentence rather than add one.

They grew from 35–45 words when the river orientations went in, and that is the right
trade: a shorter prompt that produces a map contradicting the game is not the cheaper
option. Length is worth spending on what the game needs to be true, and worth cutting
everywhere else.

## Notes for the code side

The finished strip is about 5.7:1, against real geography of roughly 2.9:1 — so the map
is stretched about twice as wide as the ground is. The landmark coordinate table
therefore has to **compress the trail's north-south wander by about half**, or the route
will not fit inside its own map: Independence in the south-east and Fort Walla Walla in
the north-west are nearly 500 miles apart north-south, which is more than a 5.7:1 strip
can hold at true proportions. Drawing the route straighter than it was is the strip-map
convention and is what emigrant guidebooks did.

`prepare-art.py` files `map_` as a backdrop, so tiles are capped at 821px and not scaled
to a sprite footprint. They are also written as RGB rather than RGBA, which makes
`visible_box` return None and guarantees they pass through uncropped — landmark markers
are drawn in tile coordinates, and a tile trimmed by even a few pixels would shift every
marker on the map.
