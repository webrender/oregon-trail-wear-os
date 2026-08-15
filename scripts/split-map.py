#!/usr/bin/env python3
"""Stitch the authored map images into one strip and cut it into scrollable tiles.

The map screen shows the whole trail as a continuous strip that the crown scrolls
along. Generating that strip is the hard part: one enormous image is awkward to
produce, and separately generated tiles never line up, because a river leaves one
tile at one height and arrives in the next at another.

The compromise this script exists to serve: a handful of wide images, each of
which is internally perfect, joined at a small number of seams that are only
approximately continuous. Three 2:1 images cut into six tiles gives five seams in
the finished strip, and three of those — the ones falling inside a source image —
are exact. Only the two joins between images need help, and they get it from two
directions.

**Design.** The joins are meant to land on the Continental Divide and the
Cascades, which are north-south mountain barriers where the country genuinely
changes character. Vertical structure at a vertical seam gives the eye nothing to
follow across the join. The art brief also forbids a river touching the left or
right edge of any source image, because a river arriving at two different heights
is the one discontinuity nobody can miss.

**Not a crossfade.** `OVERLAP` defaults to 0 — a hard cut — because blending was
measured against the real art and lost. Apple II styling is flat colour with hard
black outlines and dense repeating symbols, and alpha-blending two such fields
cannot make a gradient the eye accepts: it makes semi-transparent phantom peaks,
clearly visible at watch scale at 8% and worse at 20%. Butt two dense forests
together instead and the join simply reads as more forest. The flag survives for
art that ever needs it, but the default is no blend at all.

The happy side effect is that edges need only be the same *kind* of terrain, not
aligned, and nothing near an edge is lost.

Tiles are written into `art-source/` as RGB, so `prepare-art.py` is still what
produces the shipped assets. RGB rather than RGBA is deliberate: `visible_box`
returns None for any mode it cannot read alpha from, so an opaque RGB tile is
guaranteed to pass through uncropped. Landmark markers are drawn in tile
coordinates, and a tile trimmed by even a few pixels would shift every marker.

Nothing downstream bakes in a landmark position — the app draws the trail, the
markers and the names as live graphics over the top — so tile count, overlap and
band are all flags here, and getting one wrong costs a re-run rather than a
regeneration.

Usage:  python3 scripts/split-map.py east.png middle.png west.png
        python3 scripts/split-map.py *.png --tiles 6 --overlap 0.08
"""

from pathlib import Path
import argparse
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("This script needs Pillow: pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "art-source"

TILES = 6

# How much of each image is blended into the next, as a fraction of its width. Zero
# by default: see the module docstring for the measurement. Values above about 0.02
# ghost visibly on this art.
OVERLAP = 0.0

# Optionally keep only a horizontal band. Defaults to the whole frame, because
# images authored 2:1 and cut into squares need no vertical trim — but if a map
# comes back with its interesting third somewhere other than the middle, this is
# the knob that fixes it without a redraw.
BAND_TOP = 0.0
BAND_HEIGHT = 1.0

# What `prepare-art.py` caps a backdrop's long edge at: DISPLAY_PX 480, times 1.14
# for a backdrop overshooting the round display, times 1.5 headroom. Tiles shorter
# than this are upscaled onto the watch and look soft, which is worth saying at
# slice time rather than discovering on the device.
TILE_EDGE_TARGET = 821


def fade_mask(width: int, height: int) -> Image.Image:
    """A left-to-right black-to-white ramp, for compositing one image over another."""
    mask = Image.new("L", (width, height))
    draw = ImageDraw.Draw(mask)
    for x in range(width):
        # +1 in the denominator so the last column is not fully opaque until it has
        # left the overlap: a ramp that reaches 255 early leaves a visible hard edge.
        draw.line([(x, 0), (x, height)], fill=round(255 * x / max(1, width - 1)))
    return mask


def stitch(images: list[Image.Image], overlap_fraction: float) -> Image.Image:
    """Lay the images left to right, crossfading each into the next."""
    height = min(image.height for image in images)
    scaled = [
        image if image.height == height else image.resize(
            (round(image.width * height / image.height), height), Image.LANCZOS
        )
        for image in images
    ]

    # Measured against the narrower of each adjacent pair, so an odd-sized image
    # cannot ask for a wider overlap than its neighbour can give.
    overlaps = [
        round(min(left.width, right.width) * overlap_fraction)
        for left, right in zip(scaled, scaled[1:])
    ]

    total = sum(image.width for image in scaled) - sum(overlaps)
    strip = Image.new("RGB", (total, height))

    x = 0
    for index, image in enumerate(scaled):
        if index == 0:
            strip.paste(image, (0, 0))
            x = image.width
            continue

        width = overlaps[index - 1]
        start = x - width
        if width > 0:
            under = strip.crop((start, 0, start + width, height))
            over = image.crop((0, 0, width, height))
            strip.paste(Image.composite(over, under, fade_mask(width, height)), (start, 0))
        strip.paste(image.crop((width, 0, image.width, height)), (start + width, 0))
        x = start + image.width

    return strip


def slice_strip(strip: Image.Image, tiles: int, band_top: float, band_height: float) -> None:
    width, height = strip.size

    top = round(height * band_top)
    band = round(height * band_height)
    if top + band > height:
        sys.exit(f"Band runs past the bottom: top {band_top} + height {band_height} > 1.0")

    # Trimmed to a multiple of `tiles` so every tile is identical to the pixel.
    # Tiles differing by one would put a seam a pixel out of place, and the trail
    # overlay is laid out in map coordinates that assume they are uniform.
    usable = width - (width % tiles)
    tile_width = usable // tiles

    print(f"  strip: {width}x{height}")
    if width % tiles:
        print(f"  trimming {width % tiles}px off the right edge to divide by {tiles}")
    if band_height < 1.0:
        print(f"  band: rows {top}-{top + band} ({band_height:.0%} of height)")

    DEST.mkdir(parents=True, exist_ok=True)
    for index in range(tiles):
        left = index * tile_width
        tile = strip.crop((left, top, left + tile_width, top + band))
        path = DEST / f"map_{index + 1}.png"
        tile.save(path, optimize=True)
        print(f"  {path.name}  {tile.size[0]}x{tile.size[1]}")

    edge = max(tile_width, band)
    if edge < TILE_EDGE_TARGET:
        shortfall = TILE_EDGE_TARGET / edge
        print(
            f"\nWarning: tiles are {tile_width}x{band} against a target long edge of "
            f"{TILE_EDGE_TARGET}px, so the map will be upscaled {shortfall:.1f}x onto "
            f"the watch and look soft. Sources about {shortfall:.1f}x wider would fix it."
        )
    print("\nNow run: python3 scripts/prepare-art.py")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "images", type=Path, nargs="+",
        help="the map images left to right: west (the Pacific) first, east (Missouri) last",
    )
    parser.add_argument("--tiles", type=int, default=TILES)
    parser.add_argument(
        "--overlap", type=float, default=OVERLAP,
        help=f"blend width between images, as a fraction (default {OVERLAP}, a hard cut)",
    )
    parser.add_argument("--top", type=float, default=BAND_TOP)
    parser.add_argument("--height", type=float, default=BAND_HEIGHT)
    args = parser.parse_args()

    for path in args.images:
        if not path.is_file():
            sys.exit(f"No such image: {path}")
    if args.tiles < 1:
        sys.exit("--tiles must be at least 1")
    if not 0.0 <= args.overlap < 0.5:
        sys.exit("--overlap must be at least 0 and less than 0.5")

    images = []
    for path in args.images:
        image = Image.open(path).convert("RGB")
        print(f"{path.name}: {image.width}x{image.height}")
        images.append(image)

    slice_strip(
        stitch(images, args.overlap), args.tiles, args.top, args.height
    )


if __name__ == "__main__":
    main()
