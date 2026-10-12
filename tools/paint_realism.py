#!/usr/bin/env python3
"""Paints the 16x16 textures of the 1.5 realism pass into tools/models/<block>/<texture>.png.

Those folders are the source that tools/assets_blockbench.py copies into the generated assets (and that the Blockbench
projects next to them use), so run this after changing a painter, then run generate_assets.py:

    python3 tools/paint_realism.py            # (re)paint every texture listed below
    python3 tools/paint_realism.py --check    # exit 1 if a checked-in PNG differs from what the painter draws now

House style (see the realism brief): light from the top left, a 1 px lit rim on the top/left of every stone, tile or panel and a
1 px shadow on its bottom/right, 1 px joints, two or three tones per material, noise of about +-4. Everything tiles seamlessly.
Fixed seeds keep the output reproducible.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_assets as g  # noqa: E402  (helpers only: rng, shade, noisy)

SOURCE = Path(__file__).resolve().parent / "models"
PAINTERS = {}  # texture name -> (folder, painter)


def paint(folder, *names):
    """Registers a painter for one or more texture names, stored in tools/models/<folder>/."""
    def wrap(fn):
        for name in names or (folder,):
            PAINTERS[name] = (folder, fn)
        return fn
    return wrap


# ---------------------------------------------------------------------------------------------------------------------
# Pixel helpers (all coordinates wrap, so every pattern tiles)
# ---------------------------------------------------------------------------------------------------------------------

def put(img, x, y, color, alpha=255):
    img.load()[x % 16, y % 16] = tuple(int(c) for c in color[:3]) + (alpha,)


def get(img, x, y):
    return img.load()[x % 16, y % 16][:3]


def tint(img, x, y, amount):
    put(img, x, y, g.shade(get(img, x, y), amount))


def mix(a, b, t):
    return tuple(g.clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def base(color, amount, name):
    return g.noisy(color, amount, name)


def clouds(img, name, count=6, strength=5, radius=4):
    """Soft large-scale tone variation (concrete bloom, stone colour drift)."""
    r = g.rng(name + "-clouds")
    for _ in range(count):
        cx, cy, d = r.randint(0, 15), r.randint(0, 15), r.choice((-strength, strength, -strength // 2, strength // 2))
        for dx in range(-radius, radius + 1):
            for dy in range(-radius, radius + 1):
                if dx * dx + dy * dy <= radius * radius:
                    tint(img, cx + dx, cy + dy, d)


def specks(img, name, count, tones):
    r = g.rng(name + "-specks")
    for _ in range(count):
        tint(img, r.randint(0, 15), r.randint(0, 15), r.choice(tones))


def units(img, rects, joint, name, tone=6, lit=10, shadow=-10, joint_jitter=3, bevel=1):
    """Stones, tiles or panels: each rect (x, y, w, h) gets its own tone, a lit top/left rim and a shadowed bottom/right
    rim inside a 1 px joint on its top and left edge (the next unit's joint closes the bottom and right)."""
    r = g.rng(name + "-units")
    for (x0, y0, w, h) in rects:
        t = r.randint(-tone, tone)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                dx, dy = x - x0, y - y0
                if dx == 0 or dy == 0:
                    if joint is not None:
                        put(img, x, y, g.shade(joint, r.randint(-joint_jitter, joint_jitter)))
                        continue
                c = g.shade(get(img, x, y), t)
                if dy <= bevel or dx <= bevel:
                    c = g.shade(c, lit)
                elif dy == h - 1 or dx == w - 1:
                    c = g.shade(c, shadow)
                put(img, x, y, c)
    return img


def courses(heights, lengths, offsets):
    """Running-bond rects: one course per height, stones of the given length, each course shifted by its offset."""
    rects, y = [], 0
    for h, length, off in zip(heights, lengths, offsets):
        x = -off
        while x < 16:
            rects.append((x, y, length, h))
            x += length
        y += h
    return rects


def grid(w, h, ox=0, oy=0):
    return [(x, y, w, h) for y in range(oy - h if oy else 0, 16, h) for x in range(ox - w if ox else 0, 16, w)]


def tie_hole(img, x, y, colour):
    """Formwork tie hole: a dark 2x2 plug with a lit lower-right lip."""
    put(img, x, y, g.shade(colour, -44))
    put(img, x + 1, y, g.shade(colour, -32))
    put(img, x, y + 1, g.shade(colour, -32))
    put(img, x + 1, y + 1, g.shade(colour, -14))
    put(img, x + 2, y + 2, g.shade(colour, 10))


# ---------------------------------------------------------------------------------------------------------------------
# Pilot batch: DE stations (Frankfurt sandstone, Munich concrete, S-Bahn platform), structural steel
# ---------------------------------------------------------------------------------------------------------------------

FRANKFURT_STONE = (204, 186, 152)   # buff Main sandstone of the Hbf front (brief 2e: #D8C7A3..#C9B48E, slightly warmer)
FRANKFURT_JOINT = (172, 160, 138)
FAIR_CONCRETE = (166, 163, 155)     # Munich fair-faced concrete, warm grey (brief 2g)
PLATFORM_GREY = (150, 151, 148)
COPING = (188, 188, 182)


def sandstone_bedding(img, name, strength=4):
    r = g.rng(name + "-bedding")
    for y in range(16):
        if r.random() < 0.4:
            d = r.randint(-strength, strength)
            for x in range(16):
                if r.random() < 0.85:
                    tint(img, x, y, d)


@paint("frankfurt_sandstone_ashlar")
def frankfurt_ashlar():
    """Dressed ashlar: two 8 px courses of long stones, thin joints, faint bedding and chisel tooling."""
    img = base(FRANKFURT_STONE, 3, "rl_frankfurt_ashlar")
    sandstone_bedding(img, "rl_frankfurt_ashlar")
    units(img, courses((8, 8), (16, 16), (6, 14)), FRANKFURT_JOINT, "rl_frankfurt_ashlar", tone=7, lit=7, shadow=-9)
    r = g.rng("rl_frankfurt_ashlar-tool")
    for x in range(16):
        s = r.choice((-3, -2, 0, 0, 2))
        for y in list(range(3, 7)) + list(range(11, 15)):
            tint(img, x, y, s)
    specks(img, "rl_frankfurt_ashlar", 7, (-16, -12, 9))
    return img


@paint("frankfurt_sandstone_rusticated")
def frankfurt_rusticated():
    """Rusticated base course: 8 px courses with deep V joints, a bright chamfer on top and a rough, pitted face."""
    stone = g.shade(FRANKFURT_STONE, -8)
    img = base(stone, 6, "rl_frankfurt_rustic")
    sandstone_bedding(img, "rl_frankfurt_rustic", 5)
    rects = courses((8, 8), (8, 8), (0, 4))
    units(img, rects, mix(g.shade(stone, -64), (92, 88, 82), 0.5), "rl_frankfurt_rustic", tone=8, lit=0, shadow=0)
    for (x0, y0, w, h) in rects:  # chamfer: two lit rows on top, a lit column on the left, two shadow rows at the bottom
        for x in range(x0 + 1, x0 + w):
            tint(img, x, y0 + 1, 18)
            tint(img, x, y0 + 2, 8)
            tint(img, x, y0 + h - 1, -22)
            tint(img, x, y0 + h - 2, -9)
        for y in range(y0 + 2, y0 + h - 1):
            tint(img, x0 + 1, y, 12)
            tint(img, x0 + w - 1, y, -16)
    r = g.rng("rl_frankfurt_rustic-pits")
    for _ in range(16):
        x, y = r.randint(0, 15), r.randint(0, 15)
        if x % 8 not in (0, 1, 7) and y % 8 not in (0, 1, 7):
            tint(img, x, y, -26)
            tint(img, x + 1, y + 1, 8)
    return img


@paint("frankfurt_sandstone_cornice", "frankfurt_sandstone_top")
def frankfurt_top():
    """Weathered upper face of the cornice: wide slabs with a joint every 8 px and darker rain staining."""
    img = base(g.shade(FRANKFURT_STONE, 2), 3, "rl_frankfurt_top")
    sandstone_bedding(img, "rl_frankfurt_top", 3)
    clouds(img, "rl_frankfurt_top", count=5, strength=6)
    units(img, [(0, 0, 8, 16), (8, 0, 8, 16)], FRANKFURT_JOINT, "rl_frankfurt_top", tone=5, lit=5, shadow=-6)
    return img


@paint("munich_exposed_concrete")
def munich_exposed():
    """Fair-faced concrete (Sichtbeton): two formwork panels per block with faint shadow joints, tie holes on an 8 px grid,
    air pores and soft cloudy bloom."""
    img = base(FAIR_CONCRETE, 3, "rl_munich_exposed")
    clouds(img, "rl_munich_exposed", count=7, strength=5)
    for x in range(16):  # horizontal panel joint (lip lit below)
        tint(img, x, 0, -18)
        tint(img, x, 1, 6)
    for y in range(16):  # vertical panel joint
        tint(img, 0, y, -14)
        tint(img, 1, y, 5)
    for hx, hy in ((3, 3), (11, 3), (3, 11), (11, 11)):
        tie_hole(img, hx, hy, FAIR_CONCRETE)
    r = g.rng("rl_munich_exposed-pores")
    for _ in range(10):
        x, y = r.randint(2, 15), r.randint(2, 15)
        tint(img, x, y, -26)
    for x in (6, 13):  # faint rain streaks running down from the ties
        for y in range(5, 10):
            tint(img, x, y, -3)
    return img


@paint("munich_board_formed_concrete")
def munich_boards():
    """Board-formed concrete: 2-3 px boards with their own tone, imprinted timber grain, a small ridge where boards met and
    tie holes on an 8 px grid."""
    img = base(FAIR_CONCRETE, 2, "rl_munich_boards")
    r = g.rng("rl_munich_boards-boards")
    y = 0
    for h in (3, 2, 3, 2, 3, 3):
        t = r.randint(-8, 8)
        for yy in range(y, y + h):
            for x in range(16):
                tint(img, x, yy, t)
        for x in range(16):  # ridge where two boards met: lit lip, shadow above it
            tint(img, x, y, 9)
            tint(img, x, y - 1, -10)
        for _ in range(3):  # timber grain runs
            gx, gy, gl = r.randint(0, 15), r.randint(y, y + h - 1), r.randint(3, 7)
            d = r.choice((-7, -5, 6))
            for i in range(gl):
                tint(img, gx + i, gy, d)
        y += h
    for hx, hy in ((4, 4), (12, 12)):
        tie_hole(img, hx, hy, FAIR_CONCRETE)
    for y in range(8):  # butt joint of two boards in the upper half
        tint(img, 9, y, -6)
    specks(img, "rl_munich_boards", 6, (-22, -16))
    return img


def platform_slabs(img, name):
    """German platform paving: 8 px (50 cm) concrete slabs with a fine exposed aggregate."""
    units(img, grid(8, 8), g.shade(PLATFORM_GREY, -30), name, tone=5, lit=6, shadow=-7)
    specks(img, name, 34, (-14, -9, 8, 12))
    return img


@paint("sbahn_platform_slab")
def sbahn_slab():
    return platform_slabs(base(PLATFORM_GREY, 3, "rl_sbahn_slab"), "rl_sbahn_slab")


def coping(img, name, rows=4):
    """Platform coping stones along the north (track) edge: lighter, 1 m long, with a lit arris at the very edge."""
    r = g.rng(name + "-coping")
    for y in range(rows):
        for x in range(16):
            put(img, x, y, g.shade(COPING, r.randint(-3, 3) + (12 if y == 0 else -6 if y == rows - 1 else 0)))
    for x in range(16):
        put(img, x, rows, g.shade(PLATFORM_GREY, -34))  # joint behind the coping
    for y in range(rows):
        put(img, 0, y, g.shade(COPING, -26))  # head joint between coping stones


@paint("sbahn_platform_edge", "sbahn_platform_edge_top")
def sbahn_edge_top():
    """Edge top: coping stones, then the white safety line on the paving."""
    img = platform_slabs(base(PLATFORM_GREY, 3, "rl_sbahn_edge"), "rl_sbahn_edge")
    coping(img, "rl_sbahn_edge")
    r = g.rng("rl_sbahn_edge-line")
    for y in (7, 8):
        for x in range(16):
            put(img, x, y, g.shade((234, 234, 228), r.randint(-4, 2) - (6 if y == 8 else 0)))
    return img


@paint("sbahn_platform_edge_tactile", "sbahn_platform_edge_tactile_top")
def sbahn_edge_tactile_top():
    """Edge top with the white ribbed guidance strip (DB Leitstreifen) behind the coping."""
    img = platform_slabs(base(PLATFORM_GREY, 3, "rl_sbahn_edge_t"), "rl_sbahn_edge_t")
    coping(img, "rl_sbahn_edge_t")
    r = g.rng("rl_sbahn_edge_t-strip")
    for y in range(6, 12):
        for x in range(16):
            rib = (y - 6) % 2 == 0
            c = (226, 226, 220) if rib else (196, 196, 190)
            put(img, x, y, g.shade(c, r.randint(-3, 3) + (6 if y == 6 else 0)))
    for x in range(16):
        put(img, x, 12, g.shade(PLATFORM_GREY, -26))
    return img


@paint("sbahn_platform_edge", "sbahn_platform_edge_face")
def sbahn_edge_face():
    """Track-side face of the platform edge: the coping's front lip, its shadow, then a weathered concrete wall."""
    img = base(g.shade(PLATFORM_GREY, -18), 3, "rl_sbahn_face")
    clouds(img, "rl_sbahn_face", count=5, strength=5)
    r = g.rng("rl_sbahn_face-streaks")
    for x in range(16):  # rain and brake-dust streaks running down from the coping
        d = r.choice((-6, -3, 0, 0, 0, 3))
        for y in range(4, 16):
            tint(img, x, y, d - (y // 6))
    for y in range(3):
        for x in range(16):
            put(img, x, y, g.shade(COPING, r.randint(-3, 3) + (10 if y == 0 else -10 if y == 2 else 0)))
    for x in range(16):
        put(img, x, 3, g.shade(PLATFORM_GREY, -58))  # shadow under the coping lip
        tint(img, x, 4, -16)
    for y in range(3):
        put(img, 0, y, g.shade(COPING, -26))  # head joint between coping stones
    return img


@paint("structural_beam", "structural_beam_steel")
def structural_steel():
    """Painted rolled steel: dark grey paint with faint mill streaks running along the member. No rims: beams run on across
    block boundaries, so the edges come from the model's own faces."""
    paint_ = (70, 76, 80)
    img = base(paint_, 2, "rl_beam_steel")
    r = g.rng("rl_beam_steel-streaks")
    for y in range(16):
        if r.random() < 0.5:
            d = r.choice((-4, -3, 3, 4))
            for x in range(16):
                if r.random() < 0.8:
                    tint(img, x, y, d)
    specks(img, "rl_beam_steel", 5, (-9, 8))
    return img


@paint("steel_column_square", "steel_column_square_steel")
def square_column():
    """Square hollow section column (the model shows columns 4..11 of this texture on each face): light grey paint, a lit
    arris on the left, a shaded one on the right and a faint longitudinal weld seam."""
    paint_ = (166, 171, 174)
    img = base(paint_, 2, "rl_shs")
    r = g.rng("rl_shs-scuffs")
    for y in range(16):
        put(img, 4, y, g.shade(paint_, 16 + r.randint(-2, 2)))
        put(img, 5, y, g.shade(paint_, 6))
        put(img, 10, y, g.shade(paint_, -6))
        put(img, 11, y, g.shade(paint_, -18 + r.randint(-2, 2)))
        tint(img, 9, y, -5 if y % 2 else -2)  # weld seam
    for y in (13, 14, 15):  # scuffs near the floor
        tint(img, r.randint(6, 9), y, -10)
    specks(img, "rl_shs", 4, (-9, 6))
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Pilot batch: shared platform materials (paving, tactile, coping, concrete) used by every platform edge and ramp
# ---------------------------------------------------------------------------------------------------------------------

PAVING_LIGHT = (184, 181, 173)
PAVING_DARK = (96, 99, 102)
TACTILE = (206, 170, 52)
GRANITE = (212, 210, 203)
CONCRETE_LIGHT = (198, 196, 189)
CONCRETE_DARK = (112, 114, 115)


def flags(colour, name, joint_amount=-26, aggregate=30):
    """Concrete paving flags, 8 px (50 cm) square: a bevelled arris, own tone per flag, fine exposed aggregate."""
    img = base(colour, 3, name)
    clouds(img, name, count=4, strength=4, radius=3)
    units(img, grid(8, 8), g.shade(colour, joint_amount), name, tone=5, lit=7, shadow=-7)
    specks(img, name, aggregate, (-13, -8, 8, 12))
    return img


@paint("platform_paving_light")
def paving_light():
    return flags(PAVING_LIGHT, "rl_paving_light")


@paint("platform_paving_dark")
def paving_dark():
    return flags(PAVING_DARK, "rl_paving_dark", joint_amount=-20, aggregate=26)


def blisters(img, y1, y2, name, colour=TACTILE, pitch=4, joint=True):
    """Blister (warning) paving in rows y1..y2: 2x2 domes lit from the top left with a cast shadow, flags 8 px wide."""
    r = g.rng(name + "-blister")
    for y in range(y1, y2):
        for x in range(16):
            put(img, x, y, g.shade(colour, r.randint(-4, 4)))
    for y0 in range(y1 + 1, y2 - 1, pitch):
        for x0 in range(1, 16, pitch):
            put(img, x0, y0, g.shade(colour, 38))
            put(img, x0 + 1, y0, g.shade(colour, 20))
            put(img, x0, y0 + 1, g.shade(colour, 14))
            put(img, x0 + 1, y0 + 1, g.shade(colour, -8))
            if y0 + 2 < y2:
                put(img, x0 + 1, y0 + 2, g.shade(colour, -24))
                put(img, x0 + 2, y0 + 2, g.shade(colour, -16))
            put(img, x0 + 2, y0 + 1, g.shade(colour, -20))
    if joint:
        for y in range(y1, y2):
            put(img, 0, y, g.shade(colour, -30))
            put(img, 8, y, g.shade(colour, -30))


@paint("tactile_warning_paving")
def tactile_paving():
    img = base(TACTILE, 4, "rl_tactile")
    blisters(img, 0, 16, "rl_tactile")
    for x in range(16):
        put(img, x, 0, g.shade(TACTILE, -30))
        put(img, x, 8, g.shade(TACTILE, -30))
    return img


def granite_coping(img, name, rows=4):
    """Granite coping stones along the north (track) edge: speckled, a lit arris at the very edge, 1 m stones with a joint."""
    r = g.rng(name + "-granite")
    for y in range(rows):
        for x in range(16):
            c = g.shade(GRANITE, r.randint(-5, 5) + (10 if y == 0 else -5 if y == rows - 1 else 0))
            if r.random() < 0.16:
                c = g.shade(c, r.choice((-26, -18, 12)))
            put(img, x, y, c)
    for y in range(rows):
        put(img, 0, y, g.shade(GRANITE, -30))
    for x in range(16):
        put(img, x, rows, g.shade(CONCRETE_DARK, 6))


@paint("platform_edge", "platform_edge_top")
def edge_top():
    img = flags(PAVING_LIGHT, "rl_paving_light")
    granite_coping(img, "rl_edge")
    return img


@paint("platform_edge_warning", "platform_edge_warning_top")
def edge_warning_top():
    img = flags(PAVING_LIGHT, "rl_paving_light")
    granite_coping(img, "rl_edge_w")
    r = g.rng("rl_edge_w-line")
    for y in (5, 6):
        for x in range(16):
            put(img, x, y, g.shade((236, 236, 231), r.randint(-4, 2) - (5 if y == 6 else 0)))
    blisters(img, 7, 12, "rl_edge_w", joint=False)
    for x in range(16):
        put(img, x, 12, g.shade(PAVING_LIGHT, -26))
    return img


@paint("platform_edge", "coping_side")
def coping_side():
    """Front of the coping stone (the edge model shows rows 0-3): lit arris, speckled granite face, a dark drip line, then
    plain granite for the curve pieces that show more of it."""
    img = base(GRANITE, 5, "rl_coping_side")
    r = g.rng("rl_coping_side")
    for y in range(16):
        for x in range(16):
            if r.random() < 0.15:
                tint(img, x, y, r.choice((-24, -16, 12)))
    for x in range(16):
        tint(img, x, 0, 16)
        tint(img, x, 3, -22)
        tint(img, x, 15, -26)
    for y in range(16):
        put(img, 0, y, g.shade(GRANITE, -30))
    return img


@paint("platform_edge", "concrete_dark")
def concrete_dark():
    """Dark concrete (platform wall under the coping, fascias): rain streaks, pores and a soft bloom."""
    img = base(CONCRETE_DARK, 3, "rl_concrete_dark")
    clouds(img, "rl_concrete_dark", count=6, strength=5)
    r = g.rng("rl_concrete_dark-streaks")
    for x in range(16):
        d = r.choice((-5, -3, 0, 0, 0, 2))
        for y in range(16):
            tint(img, x, y, d)
    specks(img, "rl_concrete_dark", 10, (-18, -12, 8))
    return img


@paint("platform_edge", "concrete_light")
def concrete_light():
    """Light smooth concrete: faint bloom, a few pores, the darker bottom row of the old texture kept as a plinth line."""
    img = base(CONCRETE_LIGHT, 3, "rl_concrete_light")
    clouds(img, "rl_concrete_light", count=6, strength=4)
    specks(img, "rl_concrete_light", 9, (-16, -10, 6))
    for x in range(16):
        tint(img, x, 15, -12)
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Batch A: station shells (brick, clinker, stone trim, marble)
# ---------------------------------------------------------------------------------------------------------------------

def brick_course(img, y0, offset, palette, mortar, r, dark_rate=0.07, iron=0.04, burnt=None):
    """One course: a 1 px bed joint on row y0, then 3 px of bricks 8 px long (7 + a 1 px head joint), shifted by offset.
    Each brick has its own colour, a lit top arris, a shaded bottom row and right end, and now and then a chipped corner."""
    for x in range(16):  # bed joint, shaded where the brick above overhangs it (light from the top)
        put(img, x, y0, g.shade(mortar, r.randint(-4, 4) - 6))
    for sx in range(-offset, 16, 8):
        colour = r.choice(palette)
        if burnt and r.random() < dark_rate:
            colour = burnt
        put(img, sx, y0 + 1, g.shade(mortar, r.randint(-4, 4)))
        put(img, sx, y0 + 2, g.shade(mortar, r.randint(-4, 4) - 3))
        put(img, sx, y0 + 3, g.shade(mortar, r.randint(-4, 4) - 5))
        for y in range(y0 + 1, y0 + 4):
            for x in range(sx + 1, sx + 8):
                c = g.shade(colour, r.randint(-6, 6))
                if y == y0 + 1:
                    c = g.shade(c, 9)
                elif y == y0 + 3:
                    c = g.shade(c, -7)
                if x == sx + 7:
                    c = g.shade(c, -5)
                elif x == sx + 1:
                    c = g.shade(c, 3)
                if r.random() < iron:
                    c = g.shade(c, -24)
                put(img, x, y, c)
        if r.random() < 0.1:  # chipped arris
            cx = sx + r.choice((1, 7))
            put(img, cx, y0 + r.choice((1, 3)), g.shade(mortar, -8))


def brick_wall(palette, mortar, name, rows=(0, 1, 2, 3), burnt=None, dark_rate=0.07):
    img = g.new()
    r = g.rng(name + "-bricks")
    for row in rows:
        brick_course(img, row * 4, 0 if row % 2 == 0 else 4, palette, mortar, r, burnt=burnt, dark_rate=dark_rate)
    return img


def stone_course(img, y0, h, length, offset, colour, joint, r, lit=10, drip=-16):
    """A stone band course of height h with long stones, a lit top, a drip shadow on its lowest row and thin joints."""
    for sx in range(-offset, 16, length):
        tone = r.randint(-5, 5)
        for y in range(y0, y0 + h):
            for x in range(sx, sx + length):
                c = g.shade(colour, tone + r.randint(-3, 3))
                if y == y0:
                    c = g.shade(joint, r.randint(-3, 3))
                elif y == y0 + 1:
                    c = g.shade(c, lit)
                elif y == y0 + h - 1:
                    c = g.shade(c, drip)
                if x == sx and y > y0:
                    c = g.shade(joint, r.randint(-3, 3))
                put(img, x, y, c)


AMSTERDAM_BRICK = [(146, 62, 50), (136, 56, 46), (156, 70, 54), (128, 54, 46), (150, 66, 48), (140, 60, 52)]
AMSTERDAM_MORTAR = (170, 162, 148)
AMSTERDAM_SANDSTONE = (214, 196, 160)
HAMBURG_CLINKER = [(86, 54, 48), (74, 46, 44), (102, 62, 50), (62, 48, 54), (94, 58, 44), (70, 56, 60)]
HAMBURG_MORTAR = (140, 134, 124)
HAMBURG_STONE = (190, 178, 150)
SBAHN_BRICK = [(148, 66, 50), (134, 58, 46), (160, 76, 56), (120, 56, 50), (144, 70, 44)]
SBAHN_MORTAR = (172, 164, 152)


@paint("amsterdam_red_brick", "nlbe_brick")
def amsterdam_brick():
    """Cuypers' red facing brick in running bond, warm grey lime mortar."""
    return brick_wall(AMSTERDAM_BRICK, AMSTERDAM_MORTAR, "rl_ams_brick", burnt=(104, 44, 40))


@paint("amsterdam_brick_sandstone_band", "nlbe_brick_band_side")
def amsterdam_band():
    """A course of brick, a two-course sandstone band (lit top, drip shadow), then brick again; brick courses line up with
    the plain brick block above and below."""
    img = brick_wall(AMSTERDAM_BRICK, AMSTERDAM_MORTAR, "rl_ams_band", rows=(0, 3), burnt=(104, 44, 40))
    r = g.rng("rl_ams_band-stone")
    stone_course(img, 4, 4, 16, 3, AMSTERDAM_SANDSTONE, g.shade(AMSTERDAM_SANDSTONE, -40), r)
    stone_course(img, 8, 4, 16, 11, AMSTERDAM_SANDSTONE, g.shade(AMSTERDAM_SANDSTONE, -40), r, lit=6, drip=-26)
    return img


@paint("hamburg_clinker_brick")
def hamburg_clinker():
    """Hamburg Backstein clinker: dark, partly burnt bricks in purple-brown tones, grey mortar."""
    return brick_wall(HAMBURG_CLINKER, HAMBURG_MORTAR, "rl_hh_clinker", burnt=(52, 40, 46), dark_rate=0.12)


@paint("hamburg_clinker_stone_trim")
def hamburg_clinker_trim():
    """Clinker with one sandstone string course (rows 4-7); the top of the block meets the stone cap."""
    img = brick_wall(HAMBURG_CLINKER, HAMBURG_MORTAR, "rl_hh_trim", rows=(0, 2, 3), burnt=(52, 40, 46), dark_rate=0.12)
    stone_course(img, 4, 4, 16, 5, HAMBURG_STONE, g.shade(HAMBURG_STONE, -44), g.rng("rl_hh_trim-stone"), lit=10, drip=-28)
    return img


@paint("hamburg_stone_trim")
def hamburg_trim():
    """Sandstone dressings: two 8 px courses of long stones with fine joints, a lit bed and a soft shadow under each."""
    img = base(HAMBURG_STONE, 3, "rl_hh_stone")
    sandstone_bedding(img, "rl_hh_stone", 4)
    units(img, courses((8, 8), (16, 16), (4, 12)), g.shade(HAMBURG_STONE, -34), "rl_hh_stone", tone=6, lit=8, shadow=-10)
    specks(img, "rl_hh_stone", 8, (-16, -10, 8))
    return img


@paint("sbahn_clinker_brick")
def sbahn_clinker():
    return brick_wall(SBAHN_BRICK, SBAHN_MORTAR, "rl_sbahn_clinker", burnt=(98, 46, 40))


def veins(img, name, count, colour_shift, length=22, wobble=0.45):
    """Meandering marble veins: random walks that drift diagonally and wrap round the tile edges."""
    r = g.rng(name + "-veins")
    for _ in range(count):
        x, y = r.uniform(0, 16), r.uniform(0, 16)
        dx, dy = r.choice((1, -1)) * 0.8, 0.6
        for _ in range(length):
            tint(img, int(x), int(y), colour_shift + r.randint(-3, 3))
            if r.random() < 0.25:
                tint(img, int(x) + 1, int(y), colour_shift // 2)
            dx += r.uniform(-wobble, wobble)
            dx = max(-1.2, min(1.2, dx))
            x, y = x + dx, y + dy


def polish(img, strength=8, band=(9, 13)):
    """A soft diagonal reflection, as on polished stone."""
    for y in range(16):
        for x in range(16):
            d = (x + y) % 16
            if band[0] <= d <= band[1]:
                tint(img, x, y, strength - abs(d - (band[0] + band[1]) // 2) * 2)


@paint("antwerp_polished_marble_wall", "nlbe_marble_wall")
def antwerp_marble_wall():
    """Polished cream marble panel (one per block, hairline joint), soft grey veining and a polish reflection."""
    cream = (230, 226, 216)
    img = base(cream, 2, "rl_antwerp_marble")
    clouds(img, "rl_antwerp_marble", count=6, strength=4)
    veins(img, "rl_antwerp_marble", 2, -20, length=26, wobble=0.18)
    veins(img, "rl_antwerp_marble_fine", 3, -8, length=14, wobble=0.25)
    polish(img, 7)
    for i in range(16):
        put(img, i, 0, g.shade(cream, -30))
        put(img, 0, i, g.shade(cream, -26))
        tint(img, i, 1, 6)
    return img


@paint("antwerp_marble_floor_tile", "nlbe_marble_floor")
def antwerp_marble_floor():
    """Chequered marble floor: cream and Belgian black 8 px tiles, each veined, polished and set with a hairline joint."""
    cream, black = (226, 220, 206), (54, 58, 60)
    img = g.new()
    for (x0, y0) in ((0, 0), (8, 8), (8, 0), (0, 8)):
        colour = cream if x0 == y0 else black
        tile = base(colour, 2, f"rl_antwerp_floor{x0}{y0}")
        veins(tile, f"rl_antwerp_floor{x0}{y0}", 2, -20 if colour == cream else 22, length=14)
        for y in range(8):
            for x in range(8):
                c = get(tile, x0 + x, y0 + y)
                if x == 0 or y == 0:
                    c = g.shade(colour, -26 if colour == cream else -12)
                elif x == 1 or y == 1:
                    c = g.shade(c, 7)
                put(img, x0 + x, y0 + y, c)
    polish(img, 8, band=(10, 13))
    return img


def terrazzo(ground, chips, name, count, strip=None, strip_dark=None):
    """Ground terrazzo: a fine cement matrix with small marble chips (mostly single pixels, a few 2 px), ground flat so the
    contrast stays low, and an optional dividing strip on the top/left edge."""
    img = base(ground, 2, name)
    clouds(img, name, count=4, strength=3)
    r = g.rng(name + "-chips")
    for _ in range(count):
        x, y, c = r.randint(0, 15), r.randint(0, 15), r.choice(chips)
        put(img, x, y, mix(get(img, x, y), c, 0.75))
        if r.random() < 0.25:
            put(img, x + 1, y, mix(get(img, x + 1, y), g.shade(c, -10), 0.5))
    if strip:
        for i in range(16):
            put(img, i, 0, g.shade(strip, r.randint(-6, 6)))
            put(img, 0, i, g.shade(strip_dark or strip, r.randint(-6, 6)))
    return img


@paint("munich_terrazzo_floor")
def munich_terrazzo():
    """Munich U-Bahn terrazzo: warm pale ground, white, grey and ochre chips, a dull brass divider every metre."""
    chips = [(244, 242, 236), (128, 128, 128), (184, 150, 108), (160, 96, 80), (98, 104, 104), (226, 218, 198)]
    return terrazzo((204, 198, 186), chips, "rl_munich_terrazzo", 40, strip=(168, 146, 96), strip_dark=(150, 128, 82))


# ---------------------------------------------------------------------------------------------------------------------
# Batch B: metro surfaces (the tile, ribbed, steel and azulejo sheets were already detailed and stay as generated)
# ---------------------------------------------------------------------------------------------------------------------

@paint("metro_terrazzo_floor")
def amsterdam_terrazzo():
    """Amsterdam metro terrazzo: anthracite ground with small pale and grey chips, ground flat, a fine joint every metre."""
    chips = [(196, 190, 178), (150, 132, 120), (222, 220, 212), (104, 112, 124), (170, 160, 150)]
    img = terrazzo((58, 60, 64), chips, "rl_ams_terrazzo", 44)
    for i in range(16):
        put(img, i, 0, (44, 46, 50))
        put(img, 0, i, (46, 48, 52))
    return img


@paint("metro_ceiling_strip", "metro_concrete_plain")
def metro_concrete():
    """Plain cast concrete for the unseen faces of the metro ceiling and floor blocks: soft bloom, pores, a faint pour line."""
    colour = (140, 140, 136)
    img = base(colour, 3, "rl_metro_concrete")
    clouds(img, "rl_metro_concrete", count=6, strength=5)
    specks(img, "rl_metro_concrete", 9, (-20, -14, 8))
    for x in range(16):
        tint(img, x, 10, -7)
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Batch C: roofs and canopies (shared canopy sheets, Utrecht wave ceiling, Rotterdam stainless, hall roof glazing)
# ---------------------------------------------------------------------------------------------------------------------

import math  # noqa: E402


def standing_seams(img, rows, colour, lit=18, shadow=-14):
    """Standing seams running along x on the given rows: a bright ridge with its shadow on the next row."""
    for y in rows:
        for x in range(16):
            put(img, x, y, g.shade(colour, lit + (2 if x % 5 == 0 else 0)))
            tint(img, x, y + 1, shadow)


def brushed_rows(img, name, amount=4, rate=0.5):
    r = g.rng(name + "-brush")
    for y in range(16):
        if r.random() < rate:
            d = r.choice((-amount, amount, -amount // 2, amount // 2))
            for x in range(16):
                if r.random() < 0.85:
                    tint(img, x, y, d)


@paint("canopy_flat", "canopy_top")
def canopy_top():
    """Platform canopy roof from above: grey standing-seam metal sheet, seams every 8 px along the canopy, light weathering."""
    colour = (128, 132, 134)
    img = base(colour, 2, "rl_canopy_top")
    brushed_rows(img, "rl_canopy_top", 3)
    clouds(img, "rl_canopy_top", count=4, strength=4)
    standing_seams(img, (7, 15), colour)
    return img


@paint("canopy_flat", "canopy_underside")
def canopy_underside():
    """Canopy soffit: white linear metal strip ceiling, 4 px strips with a lit edge and a dark gap between them."""
    colour = (222, 223, 219)
    img = base(colour, 2, "rl_canopy_under")
    for x in range(16):
        k = x % 4
        d = (-34, 8, 2, -4)[(k + 1) % 4]  # gap on x % 4 == 3, lit edge right after it
        for y in range(16):
            tint(img, x, y, d)
    return img


@paint("canopy_flat", "roof_glass")
def roof_glass():
    """Roof glazing (canopy skylights, Antwerp vault): patent glazing with an opaque glazing bar every 8 px, slightly dirty
    panes that are a little denser at their lower end, and a soft diagonal sheen. Alpha stays inside the glass convention."""
    tint_ = (150, 170, 176)
    bar = (64, 70, 76)
    img = g.new()
    for y in range(16):
        for x in range(16):
            a = 112 + (y // 4)  # grime collects towards the lower lap
            c = tint_
            d = (x + y) % 16
            if d in (11, 12):
                c, a = g.shade(c, 22), a + 14
            elif d == 4:
                c, a = g.shade(c, 10), a + 6
            put(img, x, y, c, g.clamp(a))
    for y in range(16):
        for bx in (0, 8):
            put(img, bx, y, g.shade(bar, 14))  # lit side of the bar
            put(img, bx + 1, y, bar, 232)
    for x in range(16):  # pane lap every block
        put(img, x, 15, g.shade(tint_, -30), 170)
    return img


@paint("hamburg_glass_roof", "hamburg_roof_glass")
def hamburg_roof_glass():
    """Hamburg Hbf hall roof: steel glazing bars every 4 px (blue-grey paint like the hall ironwork), clear panes."""
    tint_ = (160, 178, 186)
    bar = (74, 84, 94)
    img = g.new()
    for y in range(16):
        for x in range(16):
            d = (x + y) % 16
            c, a = tint_, 92 + (y % 8) * 2
            if d in (12, 13):
                c, a = g.shade(c, 24), a + 16
            put(img, x, y, c, g.clamp(a))
    for y in range(16):
        for bx in (0, 4, 8, 12):
            put(img, bx, y, g.shade(bar, 10 if y % 8 else 20))
    for x in range(16):
        put(img, x, 0, g.shade(bar, -6), 220)  # transverse bar
    return img


WHITE_STEEL = (236, 237, 233)


@paint("utrecht_wave_roof_rise", "nlbe_wave_under")
def utrecht_wave_under():
    """Utrecht Centraal ceiling under the wave roof: white panels, seams every 8 px and one LED line per block that follows
    the undulation (here along y), with a soft glow beside it."""
    img = base(WHITE_STEEL, 2, "rl_wave_under")
    for y in range(16):
        swell = 13 * math.sin(2 * math.pi * y / 16)
        for x in range(16):
            tint(img, x, y, -6 + swell * 0.5)
    for y in range(16):
        tint(img, 0, y, -20)
        tint(img, 8, y, -9)
        put(img, 4, y, (255, 255, 250))
        tint(img, 3, y, 4)
        tint(img, 5, y, 4)
    return img


@paint("utrecht_wave_roof_panel", "nlbe_wave_under_x")
def utrecht_wave_under_x():
    """The same ceiling for the panel and edge, whose undulation runs along x: seams and the LED line run along x."""
    img = base(WHITE_STEEL, 2, "rl_wave_under_x")
    for x in range(16):
        slope = math.cos(2 * math.pi * (x - 4 + 0.5) / 16)
        for y in range(16):
            tint(img, x, y, -8 + 3 * slope)
    for x in range(16):
        tint(img, x, 0, -9)
        tint(img, x, 8, -9)
        put(img, x, 4, (255, 255, 250))
        tint(img, x, 3, 4)
        tint(img, x, 5, 4)
    return img


@paint("rotterdam_stainless_roof_panel", "nlbe_stainless")
def rotterdam_stainless():
    """Rotterdam Centraal stainless cladding: brushed sheet with a soft sky reflection, standing seams every 8 px."""
    colour = (172, 178, 182)
    img = base(colour, 2, "rl_stainless")
    brushed_rows(img, "rl_stainless", 6, rate=0.7)
    for y in range(16):
        for x in range(16):
            tint(img, x, y, 6 * math.sin(2 * math.pi * x / 16))
    standing_seams(img, (7, 15), colour, lit=26, shadow=-20)
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Batches F and H: steel stair tread, utility cables, bench slats
# ---------------------------------------------------------------------------------------------------------------------

@paint("station_stair", "stair_tread")
def stair_tread():
    """Steel chequer plate (the stair uses uvlock, so the pattern has no direction): raised lozenges on a 4 px grid, turned
    90 degrees from one to the next, each lit on its upper left and shaded on its lower right."""
    plate = (78, 83, 88)
    img = base(plate, 2, "rl_tread")
    clouds(img, "rl_tread", count=4, strength=3, radius=3)
    for cy in range(0, 16, 4):
        for cx in range(0, 16, 4):
            if (cx // 4 + cy // 4) % 2 == 0:  # "/" lozenge
                put(img, cx + 2, cy, g.shade(plate, 30))
                put(img, cx + 1, cy + 1, g.shade(plate, 22))
                put(img, cx, cy + 2, g.shade(plate, 10))
                put(img, cx + 1, cy + 2, g.shade(plate, -18))
            else:  # "\" lozenge
                put(img, cx, cy, g.shade(plate, 30))
                put(img, cx + 1, cy + 1, g.shade(plate, 22))
                put(img, cx + 2, cy + 2, g.shade(plate, 10))
                put(img, cx + 3, cy + 2, g.shade(plate, -18))
    specks(img, "rl_tread", 6, (12, -10))
    return img


@paint("utility_run", "utility_cables")
def utility_cables():
    """Cable run: mostly black and grey sheathed cables, one red (fire alarm) and one yellow-green earth, each 2 px wide
    with a round highlight, a dark gap between them and a cable tie every 8 px."""
    img = base((30, 32, 36), 2, "rl_cables")
    cables = [(2, (46, 48, 52)), (4, (70, 72, 76)), (6, (176, 52, 44)), (8, (40, 42, 46)), (10, (96, 98, 100)),
              (12, (150, 170, 60)), (14, (52, 54, 58))]
    for x0, colour in cables:
        for y in range(16):
            put(img, x0 - 1, y, g.shade(colour, 18))
            put(img, x0, y, g.shade(colour, -6))
    for y in (3, 11):  # cable ties
        for x in range(16):
            put(img, x, y, (24, 26, 28) if x % 2 else (58, 60, 64))
    return img


@paint("wooden_bench", "wood_slats")
def wood_slats():
    """Hardwood bench slats: 3 px boards with a 1 px gap, each its own tone, grain running along the board, a lit top edge
    and a shaded lower edge, and the odd darker knot."""
    wood = (146, 108, 72)
    img = base(wood, 3, "rl_slats")
    r = g.rng("rl_slats-boards")
    for y0 in range(0, 16, 4):
        tone = r.randint(-10, 10)
        for y in range(y0, y0 + 3):
            for x in range(16):
                c = g.shade(get(img, x, y), tone + (8 if y == y0 else -6 if y == y0 + 2 else 0))
                put(img, x, y, c)
        for _ in range(4):  # grain streaks
            gx, gy, gl = r.randint(0, 15), y0 + r.randint(0, 2), r.randint(4, 9)
            d = r.choice((-12, -8, 6))
            for i in range(gl):
                tint(img, gx + i, gy, d)
        if r.random() < 0.5:
            put(img, r.randint(0, 15), y0 + 1, g.shade(wood, -40))
        for x in range(16):
            put(img, x, y0 + 3, g.shade(wood, -64 + r.randint(-4, 4)))
    return img


@paint("tactile_guidance_paving", "wf_guidance_paving")
def guidance_paving():
    """Guidance (corduroy) paving: four raised bars along the walking line (north-south), lit left, shaded right, with
    rounded ends at the joint of each 8 px flag so a run of blocks reads as laid flags."""
    img = base(TACTILE, 4, "rl_guidance")
    for x0 in (1, 5, 9, 13):
        for y in range(16):
            k = y % 8
            if k == 0:
                continue  # flag joint
            end = k in (1, 7)
            put(img, x0, y, g.shade(TACTILE, 30 if not end else 18))
            put(img, x0 + 1, y, g.shade(TACTILE, 12 if not end else 4))
            put(img, x0 + 2, y, g.shade(TACTILE, -28))
    for x in range(16):
        put(img, x, 0, g.shade(TACTILE, -32))
        put(img, x, 8, g.shade(TACTILE, -32))
    return img


# ---------------------------------------------------------------------------------------------------------------------

def render_all():
    out = {}
    for name, (folder, fn) in sorted(PAINTERS.items()):
        img = fn()
        assert img.size == (16, 16), name
        out[name] = (folder, img)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--check", action="store_true", help="only compare with the checked-in PNGs")
    args = parser.parse_args()
    stale = []
    for name, (folder, img) in render_all().items():
        path = SOURCE / folder / f"{name}.png"
        if args.check:
            if not path.is_file() or list(Image.open(path).convert("RGBA").getdata()) != list(img.getdata()):
                stale.append(str(path.relative_to(SOURCE.parent.parent)))
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
    if args.check:
        print("\n".join(stale) or "paint_realism: all PNGs up to date")
        sys.exit(1 if stale else 0)
    print(f"paint_realism: {len(PAINTERS)} textures")


if __name__ == "__main__":
    main()
