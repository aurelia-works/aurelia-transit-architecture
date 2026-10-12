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
