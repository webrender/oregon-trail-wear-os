#!/usr/bin/env python3
"""Rasterise the launcher icon's foreground from the wagon artwork.

The adaptive icon is a 432x432 foreground over a sky-and-grass background. What
makes the sizing non-obvious is that Wear crops it far harder than Android
documents: the platform's adaptive-icon safe zone is a circle about 61% of the
canvas across, but the circular avatar Wear uses for both the app list and the
"Recents" card keeps only the innermost **16.4%**, throwing the rest away. At
the documented safe-zone size the wagon's wheels were cropped off and what was
left read as flat horizontal bands rather than a wagon.

That 16.4% was measured on device, twice. A first pass put it at ~30%, which
turned out to be the *artwork's* extent in an older icon rather than the crop
itself, and sizing to it still lost the wheels. Re-measuring with a target of
concentric bands at 10% intervals settled it: the innermost 10% band rendered
44px across in the avatar, giving 4.4px per 1% of canvas, and the avatar itself
is 72px across — so 72/4.4 = 16.4%. Only the 10% and 20% bands appeared at all;
there was no 30% band anywhere in the avatar. Regenerate that target and look at
it before doubting this number.

So the artwork is scaled until its **minimal enclosing circle** matches that
crop, and positioned so that circle is concentric with the canvas. Fitting the
enclosing circle rather than the bounding box matters: a wagon is wider than it
is tall and its corners are empty, so the bbox diagonal overstates how much room
it needs and would shrink the art about 10% more than necessary.

Usage:  python3 scripts/make-launcher-icon.py
"""

from pathlib import Path
import math
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("This script needs Pillow: pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "art-source/wagon_reference.png"
DEST = ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_wagon.png"

CANVAS = 432

# Diameter of what survives Wear's circular avatar crop, as a fraction of the canvas.
# Measured on device; see the module docstring. Sized a little inside the measured
# 0.164 so the wheels don't sit right on the crop edge.
VISIBLE_FRACTION = 0.15

ALPHA_FLOOR = 16


def enclosing_circle(mask):
    """Centre and radius of the smallest circle covering [mask]'s opaque pixels."""
    w, h = mask.size
    px = mask.load()
    points = [(x, y) for y in range(0, h, 2) for x in range(0, w, 2) if px[x, y]]

    def radius(cx, cy):
        return max(math.hypot(x - cx, y - cy) for x, y in points)

    cx, cy = w / 2, h / 2
    best = radius(cx, cy)
    step = max(w, h) / 8
    while step > 0.5:                      # hill-climb the centre, then refine
        for dx, dy in ((step, 0), (-step, 0), (0, step), (0, -step)):
            r = radius(cx + dx, cy + dy)
            if r < best:
                cx, cy, best = cx + dx, cy + dy, r
                break
        else:
            step /= 2
    return cx, cy, best


def main() -> None:
    if not SOURCE.is_file():
        sys.exit(f"No wagon artwork at {SOURCE}")

    art = Image.open(SOURCE).convert("RGBA")
    mask = art.getchannel("A").point(lambda a: 255 if a >= ALPHA_FLOOR else 0)
    box = mask.getbbox()
    art, mask = art.crop(box), mask.crop(box)

    cx, cy, r = enclosing_circle(mask)
    scale = (CANVAS * VISIBLE_FRACTION / 2) / r
    size = (round(art.width * scale), round(art.height * scale))
    art = art.resize(size, Image.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.paste(art, (round(CANVAS / 2 - cx * scale), round(CANVAS / 2 - cy * scale)), art)
    canvas.save(DEST, optimize=True)

    bottom = (CANVAS / 2 - cy * scale + size[1]) / CANVAS
    print(f"wagon {size[0]}x{size[1]} on a {CANVAS}px canvas ({size[0]/CANVAS:.1%} wide)")
    print(f"wheels touch down at {bottom:.1%} of the canvas — the background's horizon "
          f"should sit at {bottom * 108:.1f} in its 108-unit viewport")


if __name__ == "__main__":
    main()
