#!/usr/bin/env python3
"""Turn the authored art in `art-source/` into the assets the app ships.

The authored PNGs are enormous — a landmark scene is 1672x941, a supplies icon
1254x1254 — because they were made without a target size in mind. Shipping them
untouched costs 65MB of assets and an 85MB APK, for detail no watch can show:
that same icon is drawn 48 pixels across. This script does two things about
that, and nothing else.

**Crop.** Every sprite was authored on a generous canvas with a soft glow
bleeding out to the edges, so its raw bounding box is almost the whole file.
That would be fine for a picture shown on its own, but the app anchors sprites
by their box - a wagon sits on the ground by its bottom edge - and a box full of
empty glow puts the wagon somewhere in the air. So each sprite is cropped to the
pixels that are actually opaque enough to see.

`ALPHA_FLOOR` is what "opaque enough" means. The glow tails off below alpha 16
and the subject itself sits at 250+, so the exact cut-off barely matters:
measured across every asset, thresholds from 16 to 250 give bounding boxes
within a couple of pixels of each other. 16 is low enough to keep every visible
edge pixel and high enough to discard the glow.

**Downscale.** Each asset is capped at the largest size it is ever drawn at,
times `HEADROOM`. See `MAX_DRAWN_PX` for where those numbers come from. This is
a plain high-quality resample, not an attempt to recover a native pixel grid:
the art has block structure, but the blocks are non-integer and drift across
each image (~6.5px in the scenes, ~9.5px in the animals, ~16px in the icons), so
it is pixel-art-*styled* rendering rather than an upscaled small bitmap.
Snapping it to a grid produces uneven blocks; resampling it does not.

Usage:  python3 scripts/prepare-art.py
"""

from pathlib import Path
import shutil
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("This script needs Pillow: pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "art-source"
DEST = ROOT / "shared/src/androidMain/assets/art"

ALPHA_FLOOR = 16

# The widest Wear OS display on sale. The Pixel Watch 2 this game is built for is
# 384px and the emulator 454px, so sizing to 480 is already slack for both.
DISPLAY_PX = 480

# Wear OS tops out around density 3, which is what turns a dp-sized icon into pixels.
MAX_DENSITY = 3

# How much bigger than it will ever be drawn each asset is kept. Insurance against a
# layout change or a larger display, so a future tweak doesn't mean regenerating art
# from masters that live outside the repository.
HEADROOM = 1.5

# The largest each kind of asset is drawn, in device pixels, before headroom.
#
# Deliberately four coarse buckets rather than one per screen. Every number here is
# duplicated from layout code, and a stale one shows up as art that is quietly too
# small to be sharp, so the fewer of them there are the better. Finer buckets were
# measured and save about 1MB out of 16 — not worth the extra thing to keep in sync.
MAX_DRAWN_PX = {
    # A backdrop covers the display: the full-bleed scenes span the whole width and
    # crop into the round bezel, and a 16:9 backdrop covering that canvas overshoots
    # to about 1.14x the display width.
    "backdrop": round(DISPLAY_PX * 1.14),
    # The widest sprite footprint on a scene is the wagon, 56 of the 128 scene units
    # across, on a scene laid out at 0.85 of the display width.
    "sprite": round(DISPLAY_PX * 0.85 * 56 / 128),
    # The largest icon is a 24dp chip icon.
    "icon": 24 * MAX_DENSITY,
    # Portraits are 96dp, tombstone and dead-ox event art 120dp.
    "figure": 120 * MAX_DENSITY,
}

# `map_` tiles are backdrops for the sizing cap, but they also depend on not being
# cropped: `visible_box` only trims transparency, and the map tiles are fully opaque,
# so they pass through untouched. That has to stay true — landmark markers are drawn
# in tile coordinates, and a tile trimmed by even a few pixels would shift every
# marker on the map.
BACKDROPS = ("lm_", "river_", "terrain_", "map_")
# `raft_wreck` is a backdrop whose name begins "raft_" like the minigame's sprites, so it
# has to be named here or `role` files it as a sprite and caps it at 268px — a backdrop
# that is then upscaled across a 384px display. (The minigame's channel backdrop needs no
# such exception: it is `river_bank`, which the prefixes above already catch.)
BACKDROP_NAMES = {
    "store_interior", "title_banner", "wagon_arrival", "hunt_terrain", "raft_wreck",
}
FIGURE_NAMES = {"tombstone", "ox_dead"}

# Assets written as an indexed PNG with a small palette rather than full RGB.
#
# The map tiles look like flat 16-colour art but arrive with ~124,000 distinct
# colours: the generator anti-aliases, and the downscale above adds more. PNG
# compresses that badly, and six tiles cost 5.8MB against 23MB for the whole rest
# of the game. Quantised they cost 1.2MB, and an A/B at full asset size is
# indistinguishable — unsurprising for art that was only ever meant to hold about
# sixteen colours, and which the watch downscales again before drawing.
#
# Scoped to a prefix on purpose. The rest of the art is shipped and play-tested,
# and quietly re-encoding all of it to chase the same saving is a separate
# decision from getting the map in.
QUANTIZED = ("map_",)
QUANTIZE_COLOURS = 32

# The bullet was authored twice: an opaque version on a black field, and a
# transparent one. Only the transparent one can be composited over terrain.
RENAMES = {"hunt_bullet_square": "hunt_bullet"}
SKIP = {"hunt_bullet"}


def role(name: str) -> str:
    """Which of [MAX_DRAWN_PX]'s buckets an asset belongs to, by name."""
    if name.startswith(BACKDROPS) or name in BACKDROP_NAMES:
        return "backdrop"
    if name.startswith(("icon_", "weather_")):
        return "icon"
    if name.startswith("portrait_") or name in FIGURE_NAMES:
        return "figure"
    return "sprite"


def visible_box(image: Image.Image):
    """The bounding box of the pixels worth keeping, or None to keep the whole file."""
    if image.mode not in ("RGBA", "LA"):
        return None
    visible = image.convert("RGBA").getchannel("A")
    visible = visible.point(lambda a: 255 if a >= ALPHA_FLOOR else 0)
    box = visible.getbbox()
    return None if box in (None, (0, 0) + image.size) else box


def main() -> None:
    if not SOURCE.is_dir():
        sys.exit(f"No authored art at {SOURCE}")
    if DEST.exists():
        shutil.rmtree(DEST)
    DEST.mkdir(parents=True)

    for path in sorted(SOURCE.glob("*.png")):
        if path.stem in SKIP:
            continue
        name = RENAMES.get(path.stem, path.stem)
        image = Image.open(path)
        authored = image.size

        box = visible_box(image)
        if box is not None:
            image = image.convert("RGBA").crop(box)

        cap = round(MAX_DRAWN_PX[role(name)] * HEADROOM)
        scale = min(1.0, cap / max(image.size))
        if scale < 1.0:
            width, height = image.size
            size = (max(1, round(width * scale)), max(1, round(height * scale)))
            image = image.resize(size, Image.LANCZOS)

        if name.startswith(QUANTIZED):
            # After the resize, never before: resampling invents colours, so
            # quantising first would just have them back again.
            image = image.convert("RGB").quantize(
                colors=QUANTIZE_COLOURS, method=Image.MEDIANCUT, dither=Image.NONE
            )

        image.save(DEST / f"{name}.png", optimize=True)
        print(f"{name:24} {authored[0]:5d}x{authored[1]:<5d} -> "
              f"{image.size[0]:4d}x{image.size[1]:<4d}  {role(name)}")

    files = list(DEST.glob("*.png"))
    total = sum(f.stat().st_size for f in files)
    print(f"\n{len(files)} assets, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
