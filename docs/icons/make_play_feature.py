"""Produce the Google Play promo image (1024 x 500) from docs/icons/Logo.jpg.

Every number below was measured out of Logo.jpg, not chosen by eye:
  background rgb  35,43,51 (top) -> 31,35,40 (bottom)   sampled in the badge-free margin
  grid period     102 px                                  dominant autocorrelation lag
  badge bbox      (128, 128, 895, 895)                    orange-pixel scan
  corner radius   0.225 * side                            circle fit of the corner profile

Rerun after changing Logo.jpg:  python docs/icons/make_play_feature.py
"""

import os
from PIL import Image, ImageDraw, ImageFont

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SRC = os.path.join(REPO, 'docs', 'icons', 'Logo.jpg')
OUT = os.path.join(REPO, 'docs', 'icons', 'play-feature-1024x500.png')

W, H = 1024, 500
BG_TOP = (35, 43, 51)
BG_BOTTOM = (31, 35, 40)
GRID_PERIOD = 102
GRID_DELTA = 6

BADGE_BOX = (128, 128, 895, 895)
BADGE_RADIUS = 0.225
BADGE_SIZE = 360
BADGE_X = 64
BADGE_Y = (H - BADGE_SIZE) // 2

ORANGE = (245, 124, 0)
GLOW_RADIUS = 250
GLOW_PEAK = 46

TEXT_X = BADGE_X + BADGE_SIZE + 52
RIGHT_MARGIN = 64
NAME_COLOR = (230, 234, 240)
NAME_SIZE = 66
TAG_SIZE = 30
GAP = 20

# Built from codepoints so a mistyped glyph cannot slip into the artwork.
NAME = "Jack's Compass"
TAG = '\u6307\u5357\u91dd \u00b7 \u6c34\u5e73\u5100 \u00b7 \u7d93\u7def\u5ea6'

FONT_DIR = 'C:/Windows/Fonts'


def background():
    img = Image.new('RGB', (W, H))
    dr = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
        dr.line([(0, y), (W, y)], fill=c)
    grid = tuple(min(255, BG_TOP[i] + GRID_DELTA) for i in range(3))
    for x in range(0, W, GRID_PERIOD):
        dr.line([(x, 0), (x, H)], fill=grid)
    for y in range(0, H, GRID_PERIOD):
        dr.line([(0, y), (W, y)], fill=grid)
    return img


def glow_layer(cx, cy):
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    r2 = GLOW_RADIUS * GLOW_RADIUS
    box = (cx - GLOW_RADIUS, cy - GLOW_RADIUS, cx + GLOW_RADIUS, cy + GLOW_RADIUS)
    px = Image.new('L', (GLOW_RADIUS * 2, GLOW_RADIUS * 2), 0)
    vals = px.load()
    for j in range(GLOW_RADIUS * 2):
        dy = j - GLOW_RADIUS
        for i in range(GLOW_RADIUS * 2):
            dx = i - GLOW_RADIUS
            d = dx * dx + dy * dy
            if d < r2:
                v = int(GLOW_PEAK * (1.0 - d / r2) ** 2)
                if v:
                    vals[i, j] = v
    solid = Image.new('RGBA', px.size, ORANGE + (255,))
    layer.paste(solid, box, px)
    return layer


def badge():
    src = Image.open(SRC).convert('RGBA').crop(BADGE_BOX)
    side = src.size[0]
    mask = Image.new('L', (side, side), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, side - 1, side - 1],
                                          radius=int(side * BADGE_RADIUS), fill=255)
    src.putalpha(mask)
    return src.resize((BADGE_SIZE, BADGE_SIZE), Image.LANCZOS)


def fit_font(path, text, size, max_width):
    while size > 10:
        f = ImageFont.truetype(path, size)
        if f.getbbox(text)[2] - f.getbbox(text)[0] <= max_width:
            return f
        size -= 2
    raise RuntimeError('text does not fit: ' + text)


def check_tag_against_app_strings():
    """The tagline's feature names must match the app's own verified zh-TW copy."""
    path = os.path.join(REPO, 'app', 'src', 'main', 'res', 'values-zh-rTW', 'strings.xml')
    text = open(path, encoding='utf-8').read()
    for key in ('tab_compass', 'tab_level'):
        value = text.split('<string name="%s">' % key)[1].split('</string>')[0]
        assert value in TAG, '%s=%s is not in the tagline' % (key, value)


def main():
    check_tag_against_app_strings()
    img = background()
    cx, cy = BADGE_X + BADGE_SIZE // 2, BADGE_Y + BADGE_SIZE // 2
    img = Image.alpha_composite(img.convert('RGBA'), glow_layer(cx, cy))
    mark = badge()
    img.paste(mark, (BADGE_X, BADGE_Y), mark)

    max_width = W - RIGHT_MARGIN - TEXT_X
    name_font = fit_font(os.path.join(FONT_DIR, 'arialbd.ttf'), NAME, NAME_SIZE, max_width)
    tag_font = fit_font(os.path.join(FONT_DIR, 'msjhbd.ttc'), TAG, TAG_SIZE, max_width)

    nb = name_font.getbbox(NAME)
    tb = tag_font.getbbox(TAG)
    block = (nb[3] - nb[1]) + GAP + (tb[3] - tb[1])
    top = (H - block) // 2

    dr = ImageDraw.Draw(img)
    dr.text((TEXT_X, top - nb[1]), NAME, font=name_font, fill=NAME_COLOR)
    dr.text((TEXT_X, top + (nb[3] - nb[1]) + GAP - tb[1]), TAG, font=tag_font, fill=ORANGE)

    out = img.convert('RGB')
    out.save(OUT, 'PNG', optimize=True)

    assert out.size == (W, H), out.size
    size_kb = os.path.getsize(OUT) / 1024.0
    assert size_kb < 15 * 1024, size_kb
    check = Image.open(OUT)
    assert check.format == 'PNG' and check.size == (W, H), (check.format, check.size)
    print('wrote', OUT, check.size, check.format, '%.1f KB' % size_kb)
    print('name font px', name_font.size, 'tag font px', tag_font.size, 'text width used', max_width)
    print('tag codepoints', ascii(TAG))


if __name__ == '__main__':
    main()
