#!/usr/bin/env python3
"""Builds the dot-matrix board font from glyphs.py (needs Pillow):
  python3 tools/ledfont/make.py src/main/resources/assets/aurelia_transit_architecture/textures/font/led.png \
      src/main/resources/assets/aurelia_transit_architecture/font/led.json
Each lit LED is a round dot; a faint glow joins neighbouring dots so letters stay whole when seen from afar."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import G
from PIL import Image, ImageDraw
P = 6           # texels per LED
GLOW = 90       # alpha of the glow between neighbouring LEDs
COLS, CW, CH = 16, 8, 9   # glyph cell in LEDs
chars = [c for c in map(chr, range(33, 127))] + [chr(0xE000 + i) for i in range(11)]
rows = [chars[i:i+COLS] for i in range(0, len(chars), COLS)]
rows[-1] += ['\u0000'] * (COLS - len(rows[-1]))
img = Image.new('RGBA', (COLS * CW * P, len(rows) * CH * P), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
for r, row in enumerate(rows):
    for c, ch in enumerate(row):
        if ch not in G:
            continue
        rows_ = G[ch]
        lit = lambda x, y: 0 <= y < len(rows_) and 0 <= x < len(rows_[y]) and rows_[y][x] == '#'
        for y, line in enumerate(rows_):
            assert len(line) <= CW, ch
            for x, v in enumerate(line):
                if v == '#':
                    x0 = (c * CW + x) * P; y0 = (r * CH + y) * P
                    # a faint glow joins neighbouring LEDs, so a letter seen from afar (where the texture is sampled
                    # coarsely) stays whole instead of losing the dots that fall between samples
                    if lit(x + 1, y):
                        d.rectangle([x0 + 1, y0 + 1, x0 + P + 2, y0 + P - 3], fill=(255, 255, 255, GLOW))
                    if lit(x, y + 1):
                        d.rectangle([x0 + 1, y0 + 1, x0 + P - 3, y0 + P + 2], fill=(255, 255, 255, GLOW))
                    if lit(x + 1, y + 1):
                        d.polygon([(x0 + 3, y0 + 2), (x0 + P + 2, y0 + P + 1), (x0 + P + 1, y0 + P + 2), (x0 + 2, y0 + 3)], fill=(255, 255, 255, GLOW))
                    if lit(x - 1, y + 1):
                        d.polygon([(x0 + 1, y0 + 2), (x0 - P + 2, y0 + P + 1), (x0 - P + 3, y0 + P + 2), (x0 + 2, y0 + 3)], fill=(255, 255, 255, GLOW))
        for y, line in enumerate(rows_):
            for x, v in enumerate(line):
                if v == '#':
                    x0 = (c * CW + x) * P; y0 = (r * CH + y) * P
                    d.ellipse([x0, y0, x0 + P - 2, y0 + P - 2], fill=(255, 255, 255, 255))
img.save(sys.argv[1])
json.dump({"providers": [
    {"type": "space", "advances": {" ": 4}},
    {"type": "bitmap", "file": "aurelia_transit_architecture:font/led.png", "height": 9, "ascent": 7,
     "chars": ["".join(r) for r in rows]},
    {"type": "reference", "id": "minecraft:default"}]}, open(sys.argv[2], 'w'), indent=2)
missing = [c for c in chars if c not in G]
assert not missing, missing
