"""Pad captured 1080 x 2400 device shots to Play's 9:16 phone-screenshot spec.

Play's store-listing form says: 2-8 images, ratio 16:9 or 9:16, each side
320-3840 px, <= 8 MB. A 1080 x 2400 capture is 9:20, so it is rejected.
Cropping to 1080 x 1920 would delete 480 rows (status bar + bottom nav),
so we add 135 px on each side instead: 1350 x 2400 = exactly 9:16, both
sides >= 1080 px (the promo-eligibility line), nothing cropped away.

The side band reuses the promo image's background language: the app's own
near-black DialScreen gradient plus the 102 px blueprint grid measured out
of Logo.jpg, so screenshots and the 1024 x 500 promo image read as one set.

Usage:  python docs/icons/make_store_screenshots.py <in.png> [...]
        python docs/icons/make_store_screenshots.py --all
"""

import os
import sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
SRC_DIR = os.path.join(REPO, '_shots_store')
OUT_DIR = os.path.join(REPO, 'docs', 'store-screenshots')

TARGET_RATIO = 9 / 16.0
GRID_PERIOD = 102
GRID_DELTA = 6
# DialScreen (#0A0D10) with the same gentle top-to-bottom falloff as the promo image.
BG_TOP = (13, 16, 20)
BG_BOTTOM = (8, 10, 12)


def canvas_for(w, h):
    """Smallest 9:16 canvas that contains a w x h image without scaling."""
    need_w = int(round(h * TARGET_RATIO))
    need_h = int(round(w / TARGET_RATIO))
    if need_w >= w:
        return need_w, h
    return w, need_h


def background(w, h):
    img = Image.new('RGB', (w, h))
    dr = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
        dr.line([(0, y), (w, y)], fill=c)
    grid = tuple(min(255, BG_TOP[i] + GRID_DELTA) for i in range(3))
    for x in range(0, w, GRID_PERIOD):
        dr.line([(x, 0), (x, h)], fill=grid)
    for y in range(0, h, GRID_PERIOD):
        dr.line([(0, y), (w, y)], fill=grid)
    return img


def pad(path, out_dir):
    src = Image.open(path).convert('RGB')
    w, h = src.size
    ratio = h / w
    if abs(ratio - TARGET_RATIO) < 0.005:
        print('already 9:16, copied as-is:', os.path.basename(path))
        cw, ch = w, h
        out = background(cw, ch)
        out.paste(src, (0, 0))
    else:
        cw, ch = canvas_for(w, h)
        if cw < w or ch < h:
            raise RuntimeError('canvas smaller than source: %s %s' % ((cw, ch), (w, h)))
        out = background(cw, ch)
        out.paste(src, ((cw - w) // 2, (ch - h) // 2))
    name = os.path.splitext(os.path.basename(path))[0] + '.png'
    dest = os.path.join(out_dir, name)
    out.save(dest, 'PNG', optimize=True)
    kb = os.path.getsize(dest) / 1024.0
    ok_ratio = abs(out.size[1] / float(out.size[0]) - 16 / 9.0) < 0.002
    assert ok_ratio, out.size
    assert min(out.size) >= 320 and max(out.size) <= 3840, out.size
    assert kb <= 8 * 1024, kb
    print('wrote %-34s %s  %.0f KB  ratio=%s' % (name, out.size, kb, '9:16 OK' if ok_ratio else 'BAD'))
    return dest


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    args = sys.argv[1:]
    if args == ['--all']:
        files = [os.path.join(SRC_DIR, f) for f in sorted(os.listdir(SRC_DIR))
                 if f.lower().endswith('.png')]
    else:
        files = args
    if not files:
        raise SystemExit(__doc__)
    for f in files:
        pad(f, OUT_DIR)


if __name__ == '__main__':
    main()
