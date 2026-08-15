# The map is a crown-scrubbed strip, not a pannable canvas

The 1985 game's "size up the situation" menu had a **Look at map** option, and this is
its equivalent. Bouchard's own account of the travel screen is the design brief: he
refused to let a map *replace* the travel screen — "staring at the map while the daily
data updated would be just as boring" — but says plainly, "I certainly intended to
include a map in the product." So the map is somewhere the player goes and looks, not
something they watch. It is a `TrailMenu` entry and changes nothing about `TrailScreen`.

## The map does not pan by touch

The obvious design is a full-screen map you drag around. It is not available to us.

There is no back stack in this app: `GameController.go()` sets `screen`, `MainActivity`
is a plain `ComponentActivity` under `Theme.DeviceDefault`, and there is no
`SwipeDismissableNavHost`. A right swipe is therefore never seen by the app at all — it
hits the system dismiss gesture and kills the activity. Horizontal drag is the single
gesture we can least afford to claim, and a pannable map claims exactly it.

[0004](0004-unified-input-scheme.md) used to say the opposite — "swiping right goes back,
which is the Wear OS system gesture users already expect" — and this ADR flagged it as
stale. It was corrected on 2026-08-14 and now states the rule directly: no horizontal
gesture is available anywhere in this app.

Touch is wrong here for 0004's own reason as well: a finger dragging across a 1.2" map
covers most of the map it is dragging.

## The trail is one-dimensional, so the interaction is too

There is no need for two-axis panning, because the route is a path — there is only
further west and further east. So the crown scrubs along the trail, the map scrolls under
a fixed centre marker, and the wagon stays still while the continent moves past it. That
is the same metaphor `TrailScreen` already uses, and it is the control scheme 0004
committed to. Tap dismisses back to the trail menu.

The crown **detents at each landmark** rather than scrolling freely throughout. Free
scrolling over six screens of terrain gives the crown nothing to feel and makes "where am
I" a question of careful aiming. Detents also mean only the selected landmark's name need
be drawn, which is what frees the map from having to space nineteen labels far enough
apart to coexist.

## Consequences

**`GameState` gained `visited`.** The trail has three diamond branches — South Pass, the
Blue Mountains, The Dalles — so `at` alone cannot say which way the party came: standing
at Soda Springs is consistent with both Green River and Fort Bridger. Drawing the road
actually travelled requires the history. It is a serialised field and so carries a
default, or every existing save would fail to load (see `SaveGame`). Saves written before
it existed load with an empty one, and `GameState.journey` walks the trail table to invent
a plausible history instead — which can be wrong about a party that took a detour, so
`TurnEngine` writes the reconstruction down on the next arrival and the guess is made once
rather than every time the map is opened.

**The route is a diagram, not geography.** Six square tiles make a 6:1 strip against real
proportions of about 3:1, so the map is roughly twice as wide as the ground is, and the
trail's real north-south wander of nearly 500 miles cannot be drawn at true scale inside
it. That much was expected. What was not is that *longitude is unusable too*: the
generated tiles put the Rockies, the desert and the prairie where the composition wanted
them rather than where they are, and placing landmarks by true longitude put Chimney Rock
in the mountains. So `TrailMap` places each landmark on the terrain it belongs to — every
river crossing on a drawn river, Chimney Rock on the drawn spire — and the map's scale
varies along its length as a result, about two to one between the mountains and the
plains. North-south is compressed and the two branch detours are exaggerated beyond their
real offsets, so that a fork is legible at 1.2". Nothing quotes a scale and the screen
gives distances in words, so nothing depends on the difference. This is the strip-map
convention and what the emigrant guidebooks did; it belongs in code, not in the art.

**Tile order is the reverse of journey order.** The art is drawn in standard orientation
with north up and west left, so the strip runs Pacific to Missouri and `map_1` is the
*end* of the journey while `map_6` is Independence. The party therefore travels right to
left across the map as a run progresses. Mirroring the art to make travel read
left-to-right was rejected: it would put the Pacific on the right, which is wrong to
anyone who knows the geography.

**Nothing about a landmark's position lives in the art.** The trail, the markers and
every name are drawn as live graphics over plain terrain tiles. That is what lets the
tiles be re-sliced or replaced without invalidating a coordinate, and it is why
`prepare-art.py` must never crop them — see the notes in
[`../art-spec.md`](../art-spec.md).
