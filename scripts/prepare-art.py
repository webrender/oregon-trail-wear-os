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

# Every asset is written as an indexed PNG rather than full RGB(A).
#
# This began scoped to the map tiles, which look like flat 16-colour art but arrive
# with ~124,000 distinct colours: the generator anti-aliases, and the downscale
# above adds more. PNG compresses that badly. It was widened to everything on
# 2026-08-15 for the web build, where the whole set is downloaded over a wire
# rather than installed once, and the numbers turned out to be the same story
# everywhere — **19.1MB of assets became 3.4MB**, and the APK went from 22MB to 6MB
# with it.
#
# The reason it is nearly free is that this art only ever held six colours. Every
# asset is authored against the Apple II hi-res palette (see `ui/theme/AppleII.kt`);
# the tens of thousands of colours in the files are entirely resampling noise from
# the LANCZOS downscale above, concentrated on block edges that are then drawn
# smaller again. An A/B of a landmark scene at 64, 128 and 256 colours against the
# original, magnified 2x, is indistinguishable in all four panels — and so is a
# sprite composited over magenta, which is the test that would show alpha damage.
#
# 256 rather than the 32 the map tiles used, because it costs almost nothing:
# across the whole set 64 colours gives 2.7MB and 256 gives 3.7MB, and at 256 an
# 8-bit palette index is still one byte per pixel. Spend the megabyte — see
# [quantize] for what a too-small palette did to the map.
QUANTIZE_COLOURS = 256

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


def quantize(image: Image.Image) -> Image.Image:
    """The image as an indexed PNG's worth of colours. See [QUANTIZE_COLOURS].

    FASTOCTREE rather than MEDIANCUT, for two independent reasons, and the second one
    is why the map tiles were regenerated rather than left alone.

    **It is half the size.** An octree buckets colours by their high bits, so the
    result is spatially coherent — large flat runs of one index, which is exactly
    what PNG's filters compress. MEDIANCUT picks a better-fitting palette and then
    scatters it across the image. Measured through this pipeline at 256 colours, a
    landmark scene is 105KB one way and 238KB the other.

    **It keeps rare colours.** MEDIANCUT allocates palette entries by how many pixels
    want them, so a detail that covers a fraction of a percent of the image loses its
    entry to a gradient nobody is looking at. The map tiles' violet trail markings are
    262 pixels out of 672,400 — and the tiles shipped between the map landing and
    2026-08-15 had **zero** violet pixels in them, because MEDIANCUT at 32 colours had
    quietly thrown the trail away. FASTOCTREE keeps all of it.

    It also quantises the alpha channel rather than discarding it, which MEDIANCUT
    cannot: that one needs `convert("RGB")` first, and flattening a sprite's
    transparency puts a hard black box around it.
    """
    return image.convert("RGBA").quantize(colors=QUANTIZE_COLOURS, method=Image.FASTOCTREE)


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

        # After the resize, never before: resampling invents colours, so quantising
        # first would just have them back again.
        image = quantize(image)

        image.save(DEST / f"{name}.png", optimize=True)
        print(f"{name:24} {authored[0]:5d}x{authored[1]:<5d} -> "
              f"{image.size[0]:4d}x{image.size[1]:<4d}  {role(name)}")

    files = list(DEST.glob("*.png"))
    total = sum(f.stat().st_size for f in files)
    print(f"\n{len(files)} assets, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
