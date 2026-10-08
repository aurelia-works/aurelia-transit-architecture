#!/usr/bin/env python3
"""
Generates every resource for Aurelia Transit Architecture V1: original 16x16 textures, block/item models,
blockstates, loot tables, recipes, tags and English localisation.

All art is produced here from a fixed seed, so output is reproducible and contains no third-party assets.
Models are authored with their front facing NORTH; blockstates rotate them clockwise (y = 90 per quarter turn),
matching FacingShapedBlock / AxisShapedBlock in the Java code.

Usage: python3 tools/generate_assets.py   (requires Pillow)
"""
import json
import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw

MOD = "aurelia_transit_architecture"
ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / MOD
DATA = ROOT / "data"

# ---------------------------------------------------------------------------------------------------------------------
# Palette: understated modern European transit
# ---------------------------------------------------------------------------------------------------------------------
PAVING_LIGHT = (184, 181, 173)
PAVING_DARK = (96, 99, 102)
CONCRETE_LIGHT = (198, 196, 189)
CONCRETE_DARK = (112, 114, 115)
GRANITE = (212, 210, 203)
TACTILE = (206, 170, 52)
WHITE = (236, 236, 231)
STEEL = (160, 166, 170)
STEEL_DARK = (60, 65, 70)
CHARCOAL = (44, 47, 51)
SIGN_BLUE = (29, 55, 90)
ACCENT_GREEN = (70, 108, 86)
WOOD = (146, 108, 72)
GLASS = (178, 206, 214)
LAMP = (255, 248, 228)
INSULATOR = (118, 84, 62)


def rng(name):
    return random.Random(f"aurelia:{name}")


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, amount):
    return tuple(clamp(c + amount) for c in color[:3])


def new(color=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), color if len(color) == 4 else color + (255,))


def noisy(color, amount, name, alpha=255):
    img = new()
    r = rng(name)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(color, r.randint(-amount, amount)) + (alpha,)
    return img


def rect(img, x1, y1, x2, y2, color, alpha=255):
    """Fill inclusive-exclusive pixel rectangle."""
    px = img.load()
    for y in range(max(0, y1), min(16, y2)):
        for x in range(max(0, x1), min(16, x2)):
            px[x, y] = color[:3] + (color[3] if len(color) == 4 else alpha,)


def pavers(base, joint, name, size=8):
    img = noisy(base, 5, name)
    r = rng(name + "-tiles")
    px = img.load()
    for ty in range(0, 16, size):
        for tx in range(0, 16, size):
            tone = r.randint(-6, 6)
            for y in range(ty, ty + size):
                for x in range(tx, tx + size):
                    if x == tx or y == ty:
                        px[x, y] = shade(joint, r.randint(-3, 3)) + (255,)
                    else:
                        px[x, y] = shade(px[x, y], tone)
                        if x == tx + 1 or y == ty + 1:
                            px[x, y] = shade(px[x, y], 6)
    return img


def tactile_rows(img, y1, y2, name):
    rect(img, 0, y1, 16, y2, TACTILE)
    r = rng(name)
    px = img.load()
    for y in range(y1, y2):
        for x in range(16):
            px[x, y] = shade(TACTILE, r.randint(-5, 5)) + (255,)
    for y in range(y1 + 1, y2 - 1, 4):
        for x in range(1, 16, 4):
            px[x, y] = shade(TACTILE, 34) + (255,)
            px[x + 1, y] = shade(TACTILE, 20) + (255,)
            if y + 1 < y2:
                px[x, y + 1] = shade(TACTILE, 12) + (255,)
                px[x + 1, y + 1] = shade(TACTILE, -26) + (255,)


def coping(img, rows=4):
    r = rng("coping")
    px = img.load()
    for y in range(rows):
        for x in range(16):
            px[x, y] = shade(GRANITE, r.randint(-7, 7) - (6 if y == 0 else 0)) + (255,)
    for x in range(16):
        px[x, rows] = shade(CONCRETE_DARK, 10) + (255,)


def brushed(color, name, vertical=True):
    img = noisy(color, 3, name)
    r = rng(name + "-brush")
    px = img.load()
    for i in range(16):
        streak = r.randint(-7, 7)
        for j in range(16):
            x, y = (i, j) if vertical else (j, i)
            px[x, y] = shade(px[x, y], streak)
    return img


def glass(name, frame=None, dots=False, tint=GLASS, alpha=64):
    img = new(tint + (alpha,))
    px = img.load()
    for i in range(16):
        # soft diagonal reflection streaks
        for x, y in ((i, 15 - i), (min(15, i + 1), 15 - i)):
            px[x, y] = shade(tint, 30) + (alpha + 36,)
        if 3 <= i <= 6:
            px[i, 9 - i] = shade(tint, 22) + (alpha + 20,)
    if dots:
        for x in range(1, 16, 2):
            px[x, 7] = WHITE + (215,)
            px[x - 1, 8] = WHITE + (215,)
    if frame:
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                px[x, y] = shade(frame, 8 if y == 0 or x == 0 else -6) + (255,)
    return img


def draw_textures():
    t = {}
    t["platform_paving_light"] = pavers(PAVING_LIGHT, shade(PAVING_LIGHT, -24), "paving_light")
    t["platform_paving_dark"] = pavers(PAVING_DARK, shade(PAVING_DARK, -18), "paving_dark")

    tactile = new()
    tactile_rows(tactile, 0, 16, "tactile")
    t["tactile_warning_paving"] = tactile

    t["concrete_light"] = noisy(CONCRETE_LIGHT, 5, "concrete_light")
    rect(t["concrete_light"], 0, 15, 16, 16, shade(CONCRETE_LIGHT, -14))
    t["concrete_dark"] = noisy(CONCRETE_DARK, 5, "concrete_dark")

    edge = pavers(PAVING_LIGHT, shade(PAVING_LIGHT, -24), "paving_light")
    coping(edge)
    t["platform_edge_top"] = edge

    edge_warning = pavers(PAVING_LIGHT, shade(PAVING_LIGHT, -24), "paving_light")
    coping(edge_warning)
    rect(edge_warning, 0, 5, 16, 7, WHITE)
    tactile_rows(edge_warning, 7, 12, "edge_tactile")
    t["platform_edge_warning_top"] = edge_warning

    granite = noisy(GRANITE, 7, "granite")
    rect(granite, 0, 15, 16, 16, shade(GRANITE, -30))
    t["coping_side"] = granite

    curb = noisy(CONCRETE_LIGHT, 5, "curb")
    rect(curb, 0, 0, 16, 1, shade(CONCRETE_LIGHT, -10))
    rect(curb, 0, 2, 16, 4, WHITE)
    rect(curb, 0, 15, 16, 16, shade(CONCRETE_LIGHT, -22))
    t["curb_top"] = curb

    t["steel"] = brushed(STEEL, "steel")
    t["steel_dark"] = noisy(STEEL_DARK, 3, "steel_dark")

    perforated = noisy(STEEL_DARK, 2, "perforated")
    for y in range(1, 16, 3):
        for x in range(1 + (y // 3) % 2, 16, 3):
            perforated.load()[x, y] = shade(CHARCOAL, -18) + (255,)
    t["steel_perforated"] = perforated

    wood = noisy(WOOD, 6, "wood")
    for y in range(3, 16, 4):
        rect(wood, 0, y, 16, y + 1, shade(WOOD, -58))
    r = rng("wood-grain")
    for _ in range(18):
        x, y = r.randrange(16), r.randrange(16)
        if y % 4 != 3:
            wood.load()[x, y] = shade(WOOD, -18) + (255,)
    t["wood_slats"] = wood

    t["glass_framed"] = glass("glass_framed", frame=STEEL_DARK)
    t["glass_clear"] = glass("glass_clear", alpha=52)
    t["shelter_glass"] = glass("shelter_glass", dots=True, alpha=56)
    t["roof_glass"] = glass("roof_glass", tint=(150, 170, 176), alpha=120)

    blue = noisy(SIGN_BLUE, 2, "sign_blue")
    t["sign_blue"] = blue
    t["sign_charcoal"] = noisy(CHARCOAL, 2, "sign_charcoal")

    number = noisy(SIGN_BLUE, 2, "platform_number")
    d = ImageDraw.Draw(number)
    d.rectangle((4, 5, 11, 12), outline=WHITE + (255,))
    t["platform_number"] = number

    # Information faces are blank: the editable text is drawn over them in the world.
    poster = noisy(STEEL, 3, "poster")
    rect(poster, 2, 2, 14, 13, (242, 242, 238))
    for y in (2, 12):
        rect(poster, 2, y, 14, y + 1, (226, 226, 221))
    t["information_poster"] = poster

    timetable = noisy(STEEL_DARK, 2, "timetable")
    rect(timetable, 2, 2, 14, 12, (240, 240, 234))
    rect(timetable, 2, 11, 14, 12, (224, 224, 217))
    t["timetable"] = timetable

    screen = noisy(STEEL_DARK, 2, "screen")
    rect(screen, 4, 1, 12, 15, (14, 17, 22))
    rect(screen, 4, 1, 12, 2, (24, 29, 36))
    rect(screen, 3, 1, 4, 15, shade(STEEL_DARK, 14))
    rect(screen, 12, 1, 13, 15, shade(STEEL_DARK, 14))
    t["information_screen"] = screen

    t["panel_fill"] = new((255, 255, 255, 255))

    display = noisy(STEEL_DARK, 2, "clock_display")
    rect(display, 1, 5, 15, 11, (12, 14, 18))
    rect(display, 1, 5, 15, 6, (22, 25, 30))
    t["clock_display"] = display
    wall_display = noisy(STEEL_DARK, 2, "clock_display_wall")
    rect(wall_display, 2, 5, 14, 11, (12, 14, 18))
    rect(wall_display, 2, 5, 14, 6, (22, 25, 30))
    t["clock_display_wall"] = wall_display

    dial = noisy(STEEL_DARK, 2, "clock_dial")
    dd = ImageDraw.Draw(dial)
    dd.ellipse((1.6, 1.6, 14.4, 14.4), fill=(244, 244, 240, 255), outline=(200, 202, 204, 255))
    for i in range(12):
        ang = math.radians(i * 30)
        x, y = 8 + 5.5 * math.sin(ang), 8 - 5.5 * math.cos(ang)
        dd.point((int(round(x - 0.5)), int(round(y - 0.5))), fill=(27, 31, 35, 255) if i % 3 == 0 else (120, 124, 128, 255))
    t["clock_dial"] = dial

    lamp = new(LAMP + (255,))
    rect(lamp, 0, 0, 16, 1, shade(LAMP, -30))
    rect(lamp, 0, 15, 16, 16, shade(LAMP, -30))
    t["lamp_diffuser"] = lamp

    bin_side = noisy(ACCENT_GREEN, 4, "bin_side")
    for x in range(1, 16, 3):
        rect(bin_side, x, 0, x + 1, 16, shade(ACCENT_GREEN, -22))
    t["bin_side"] = bin_side
    bin_front = bin_side.copy()
    rect(bin_front, 5, 4, 11, 7, (22, 24, 26))
    rect(bin_front, 5, 7, 11, 8, shade(ACCENT_GREEN, 25))
    t["bin_front"] = bin_front

    bollard = noisy(CHARCOAL, 3, "bollard")
    rect(bollard, 0, 4, 16, 6, (222, 224, 226))
    rect(bollard, 0, 5, 16, 6, (190, 194, 198))
    t["bollard"] = bollard

    canopy_top = noisy((128, 132, 134), 4, "canopy_top")
    rect(canopy_top, 0, 7, 16, 8, (146, 150, 152))
    rect(canopy_top, 0, 15, 16, 16, (146, 150, 152))
    t["canopy_top"] = canopy_top

    underside = noisy((222, 223, 219), 3, "canopy_underside")
    for x in range(3, 16, 4):
        rect(underside, x, 0, x + 1, 16, (196, 198, 196))
    t["canopy_underside"] = underside

    fascia = noisy(STEEL_DARK, 2, "fascia")
    rect(fascia, 0, 5, 16, 6, shade(STEEL, 20))
    rect(fascia, 0, 10, 16, 11, shade(STEEL_DARK, -14))
    t["fascia"] = fascia

    lattice = new()
    ld = ImageDraw.Draw(lattice)
    for x0 in range(-16, 16, 8):
        ld.line((x0, 15, x0 + 15, 0), fill=STEEL + (255,))
        ld.line((x0, 0, x0 + 15, 15), fill=shade(STEEL, -20) + (255,))
    for x in range(0, 16, 8):
        rect(lattice, x, 0, x + 1, 16, shade(STEEL, 8))
    t["lattice"] = lattice

    insulator = noisy(INSULATOR, 3, "insulator")
    for y in range(0, 16, 2):
        rect(insulator, 0, y, 16, y + 1, shade(INSULATOR, -26))
    t["insulator"] = insulator

    bus = new(WHITE + (255,))
    bd = ImageDraw.Draw(bus)
    bd.rectangle((0, 0, 15, 15), outline=ACCENT_GREEN + (255,))
    bd.rectangle((1, 1, 14, 1), fill=ACCENT_GREEN + (255,))
    # Generic bus pictogram (UV region 2..14 x 0..6 is shown on the flag)
    bd.rectangle((4, 1, 11, 4), fill=ACCENT_GREEN + (255,))
    bd.rectangle((5, 2, 10, 2), fill=WHITE + (255,))
    bd.point((5, 5), fill=CHARCOAL + (255,))
    bd.point((10, 5), fill=CHARCOAL + (255,))
    rect(bus, 2, 5, 14, 6, WHITE)
    rect(bus, 5, 5, 6, 6, CHARCOAL)
    rect(bus, 10, 5, 11, 6, CHARCOAL)
    rect(bus, 2, 0, 14, 1, ACCENT_GREEN)
    t["bus_stop_flag"] = bus

    white = noisy(WHITE, 2, "panel_white")
    t["panel_white"] = white

    return t


# ---------------------------------------------------------------------------------------------------------------------
# Model helpers
# ---------------------------------------------------------------------------------------------------------------------
FACES = ("down", "up", "north", "south", "west", "east")


def auto_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    uv = {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]
    uv = [round(max(0.0, min(16.0, v)), 4) for v in uv]
    # keep a non-zero sample area for faces squeezed by clamping
    if uv[0] == uv[2]:
        uv[2] = min(16, uv[0] + 1) if uv[0] < 16 else 16
        uv[0] = uv[2] - 1
    if uv[1] == uv[3]:
        uv[3] = min(16, uv[1] + 1) if uv[1] < 16 else 16
        uv[1] = uv[3] - 1
    return uv


def el(f, t, tex, faces=None, skip=(), rot=None, shade_=True, cull=True):
    """Cuboid. `tex` is the default texture variable; `faces` overrides per face with "#var" or ("#var", uv)."""
    f = [round(v, 4) for v in f]
    t = [round(v, 4) for v in t]
    out = {}
    for face in FACES:
        if face in skip:
            continue
        spec = (faces or {}).get(face, tex)
        if spec is None:
            continue
        texture, uv = (spec if isinstance(spec, tuple) else (spec, None))
        entry = {"uv": uv or auto_uv(face, f, t), "texture": texture}
        if cull and rot is None:
            boundary = {"down": f[1] == 0, "up": t[1] == 16, "north": f[2] == 0, "south": t[2] == 16, "west": f[0] == 0, "east": t[0] == 16}
            if boundary[face]:
                entry["cullface"] = face
        out[face] = entry
    element = {"from": f, "to": t, "faces": out}
    if rot:
        axis, angle, origin = rot
        element["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    if not shade_:
        element["shade"] = False
    return element


def model(textures, elements, parent="block/block", ao=True):
    m = {"parent": parent, "textures": {k: tx(v) for k, v in textures.items()}, "elements": elements}
    if not ao:
        m["ambientocclusion"] = False
    return m


def tx(name):
    return name if ":" in name or name.startswith("#") else f"{MOD}:block/{name}"


def strips(profile, thickness, faces, solid_below=False):
    """Stepped surface following profile(z) (underside height), one strip per pixel of depth."""
    out = []
    for z in range(16):
        bottom = profile(z + 0.5)
        y1 = 0 if solid_below else bottom
        y2 = bottom + thickness
        out.append(el([0, y1, z], [16, y2, z + 1], "#side", faces=faces))
    return out


SLOPE_LOWER = lambda z: z / 2
SLOPE_UPPER = lambda z: 8 + z / 2
WAVE_RISE = lambda z: 8 * (z / 16) ** 2
WAVE_CREST = lambda z: 8 + z - z * z / 16
WAVE_FLAT = lambda z: 8 + z - z * z / 32


# ---------------------------------------------------------------------------------------------------------------------
# Block definitions: id -> (kind, models dict, item model name)
# kind: "simple" | "facing" | "axis" | "sign" (facing + left/right joins) | "sign_single" (facing, no joins)
# ---------------------------------------------------------------------------------------------------------------------
def cube_bottom_top(top, side, bottom):
    return {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tx(top), "side": tx(side), "bottom": tx(bottom)}}


def platform_edge(top):
    return model({"particle": top, "top": top, "coping": "coping_side", "face": "concrete_dark", "side": "concrete_light"}, [
        el([0, 12, 0], [16, 16, 16], "#side", faces={"up": "#top", "north": "#coping", "down": "#face"}),
        el([0, 0, 2], [16, 12, 16], "#side", faces={"north": "#face", "up": None}),
    ])


def ramp(profile):
    return model({"particle": "platform_paving_light", "top": "platform_paving_light", "side": "concrete_light"},
                 strips(profile, 0, {"up": "#top"}, solid_below=True))


def canopy_strips(profile):
    return model({"particle": "canopy_top", "top": "canopy_top", "under": "canopy_underside", "side": "steel_dark"},
                 strips(profile, 2, {"up": "#top", "down": "#under"}))


def sign_variant(kind, left, right):
    """Joined signs: caps/legs only appear on unconnected ends (left = model -X, right = model +X)."""
    t = {"particle": "steel", "frame": "steel", "face": "sign_blue"}
    els = []
    if kind == "station_name_sign":
        els += [
            el([0, 7.5, 6.5], [16, 15.5, 9.5], "#frame", faces={"north": "#face", "south": "#face"}),
            el([0, 15.5, 6.25], [16, 16, 9.75], "#frame"),
            el([0, 7, 6.25], [16, 7.5, 9.75], "#frame"),
        ]
        if not left:
            els += [el([0, 7, 6.25], [1, 16, 9.75], "#frame"), el([1, 0, 7], [3, 7, 9], "#frame")]
        if not right:
            els += [el([15, 7, 6.25], [16, 16, 9.75], "#frame"), el([13, 0, 7], [15, 7, 9], "#frame")]
    elif kind == "hanging_station_sign":
        els += [
            el([0, 3.5, 6.5], [16, 10.5, 9.5], "#frame", faces={"north": "#face", "south": "#face"}),
            el([0, 10.5, 6.25], [16, 11, 9.75], "#frame"),
            el([0, 3, 6.25], [16, 3.5, 9.75], "#frame"),
        ]
        # at most two hanger rods per joined row: one near each free end
        if not left:
            els += [el([0, 3, 6.25], [1, 11, 9.75], "#frame"), el([3.5, 11, 7.5], [4.5, 16, 8.5], "#frame")]
        if not right:
            els += [el([15, 3, 6.25], [16, 11, 9.75], "#frame"), el([11.5, 11, 7.5], [12.5, 16, 8.5], "#frame")]
    elif kind == "direction_sign":
        t["face"] = "sign_charcoal"
        els += [
            el([0, 4.5, 7], [16, 10.5, 9], "#frame", faces={"north": "#face", "south": "#face"}),
            el([0, 10.5, 6.75], [16, 11, 9.25], "#frame"),
            el([0, 4, 6.75], [16, 4.5, 9.25], "#frame"),
        ]
        if not left:
            els += [el([0, 4, 6.75], [1, 11, 9.25], "#frame"), el([3.5, 11, 7.5], [4.5, 16, 8.5], "#frame")]
        if not right:
            els += [el([15, 4, 6.75], [16, 11, 9.25], "#frame"), el([11.5, 11, 7.5], [12.5, 16, 8.5], "#frame")]
    return model(t, els)


def blocks():
    b = {}

    # -- Platforms
    b["platform_paving_light"] = ("simple", {"": cube_bottom_top("platform_paving_light", "concrete_light", "concrete_light")})
    b["platform_paving_dark"] = ("simple", {"": cube_bottom_top("platform_paving_dark", "concrete_dark", "concrete_dark")})
    b["tactile_warning_paving"] = ("simple", {"": cube_bottom_top("tactile_warning_paving", "concrete_light", "concrete_light")})
    b["platform_edge"] = ("facing", {"": platform_edge("platform_edge_top")})
    b["platform_edge_warning"] = ("facing", {"": platform_edge("platform_edge_warning_top")})
    b["platform_ramp_lower"] = ("facing", {"": ramp(SLOPE_LOWER)})
    b["platform_ramp_upper"] = ("facing", {"": ramp(SLOPE_UPPER)})

    # -- Signage
    for kind in ("station_name_sign", "hanging_station_sign", "direction_sign"):
        variants = {}
        for left in (False, True):
            for right in (False, True):
                suffix = ("_l" if left else "") + ("_r" if right else "")
                variants[suffix] = sign_variant(kind, left, right)
        b[kind] = ("sign", variants)
    b["platform_number_sign"] = ("sign_single", {"": model(
        {"particle": "steel", "frame": "steel", "face": "platform_number"}, [
            el([3, 2, 7], [13, 12, 9], "#frame", faces={"north": "#face", "south": "#face"}),
            el([7.5, 12, 7.5], [8.5, 16, 8.5], "#frame"),
        ])})
    b["information_case"] = ("facing", {"": model(
        {"particle": "steel", "frame": "steel", "face": "information_poster"}, [
            el([1, 2, 14], [15, 15, 16], "#frame", faces={"north": "#face"}),
        ])})
    b["sign_pole"] = ("pole", {suffix: sign_pole(up, down) for suffix, up, down in POLE_VARIANTS}
                      | {"_post": model({"particle": "steel", "pole": "steel"}, [el([1, 0, 7], [3, 16, 9], "#pole")])})

    # -- Furniture
    def bench(seat_tex, slatted):
        """Thin-section European bench: 1px seat board, flat side frames, one low backrest panel (two slats when timber)."""
        els = []
        for x1, x2 in ((1, 2.5), (13.5, 15)):
            els += [
                el([x1, 0, 11], [x2, 13.5, 12.5], "#frame"),
                el([x1, 0, 4.5], [x2, 6.5, 6], "#frame"),
                el([x1, 5.25, 6], [x2, 6.5, 11], "#frame"),
            ]
        els.append(el([0, 6.5, 4.5], [16, 7.5, 11], "#seat"))
        if slatted:
            els += [el([0, 9, 10], [16, 10.75, 11], "#seat"), el([0, 11.25, 10], [16, 13, 11], "#seat")]
        else:
            els.append(el([0, 9, 10], [16, 13, 11], "#seat"))
        return model({"particle": "steel_dark", "frame": "steel_dark", "seat": seat_tex}, els)

    b["steel_bench"] = ("facing", {"": bench("steel_perforated", False)})
    b["wooden_bench"] = ("facing", {"": bench("wood_slats", True)})
    b["waste_bin"] = ("facing", {"": model({"particle": "bin_side", "body": "bin_side", "front": "bin_front", "lid": "steel_dark"}, [
        el([4, 0, 4], [12, 13, 12], "#body", faces={"north": "#front", "up": None}),
        el([3.5, 13, 3.5], [12.5, 14.5, 12.5], "#lid"),
    ])})
    b["bollard"] = ("simple", {"": model({"particle": "bollard", "body": "bollard", "cap": "steel"}, [
        el([6, 0, 6], [10, 13, 10], "#body", faces={"up": None}),
        el([5.5, 13, 5.5], [10.5, 14, 10.5], "#cap"),
    ])})
    b["platform_lamp"] = ("facing", {"": model({"particle": "steel", "pole": "steel", "head": "steel_dark", "light": "lamp_diffuser"}, [
        el([7, 0, 7], [9, 13, 9], "#pole", faces={"up": None}),
        el([5, 13, 3], [11, 15, 11], "#head", faces={"down": None}),
        el([5.5, 12.75, 3.5], [10.5, 13, 10.5], "#light", faces={"up": None}, shade_=False),
    ])})
    b["information_pillar"] = ("facing", {"": model({"particle": "steel_dark", "body": "steel_dark", "screen": "information_screen", "trim": "steel"}, [
        el([2.5, 0, 5.5], [13.5, 1.5, 10.5], "#trim"),
        el([3, 1.5, 6], [13, 14.5, 10], "#body", faces={"north": "#screen", "south": "#screen"}),
        el([2.5, 14.5, 5.5], [13.5, 16, 10.5], "#trim"),
    ])})

    # -- Architecture
    b["steel_column_square"] = ("simple", {"": model({"particle": "steel", "side": "steel"}, [el([4, 0, 4], [12, 16, 12], "#side")])})
    round_elements = []
    for i, angle in enumerate((0, 22.5, 45, -22.5)):
        inset = i * 0.02
        rot = None if angle == 0 else ("y", angle, [8, 8, 8])
        e = el([5, inset, 5], [11, 16 - inset, 11], "#side", rot=rot)
        round_elements.append(e)
    b["steel_column_round"] = ("simple", {"": model({"particle": "steel", "side": "steel"}, round_elements)})
    b["structural_beam"] = ("axis", {"": model({"particle": "steel_dark", "side": "steel_dark"}, [el([0, 11, 5], [16, 16, 11], "#side")])})
    strut = 8 / math.sin(math.radians(45))
    b["roof_support"] = ("axis", {"": model({"particle": "steel", "side": "steel", "dark": "steel_dark"}, [
        el([5, 0, 5], [11, 6, 11], "#side"),
        el([7, 6, 7], [9, 14, 9], "#side"),
        el([8, 5, 7], [8 + strut, 7, 9], "#side", rot=("z", 45, [8, 6, 8])),
        el([8 - strut, 5, 7], [8, 7, 9], "#side", rot=("z", -45, [8, 6, 8])),
        el([0, 14, 6], [16, 16, 10], "#dark"),
    ])})
    b["glass_wall"] = ("simple", {"": {"parent": "minecraft:block/cube_all", "textures": {"all": tx("glass_framed")}}})
    b["glass_panel"] = ("facing", {"": model({"particle": "glass_framed", "glass": "glass_framed", "frame": "steel_dark"}, [
        el([0, 0, 7], [16, 16, 9], "#frame", faces={"north": "#glass", "south": "#glass"}),
    ])})
    b["glass_barrier"] = ("facing", {"": model({"particle": "steel", "glass": "glass_clear", "rail": "steel", "shoe": "steel_dark"}, [
        el([0, 0, 6.75], [16, 1.5, 9.25], "#shoe"),
        el([0, 1.5, 7.25], [16, 13, 8.75], "#glass", faces={"up": None, "down": None}),
        el([0, 13, 6.75], [16, 15, 9.25], "#rail"),
        el([1.5, 3.5, 6.75], [3, 5.5, 9.25], "#shoe"),
        el([13, 3.5, 6.75], [14.5, 5.5, 9.25], "#shoe"),
        el([1.5, 9, 6.75], [3, 11, 9.25], "#shoe"),
        el([13, 9, 6.75], [14.5, 11, 9.25], "#shoe"),
    ])})
    b["canopy_flat"] = ("simple", {"": model({"particle": "canopy_top", "top": "canopy_top", "under": "canopy_underside", "side": "steel_dark"}, [
        el([0, 0, 0], [16, 2, 16], "#side", faces={"up": "#top", "down": "#under"}),
    ])})
    b["canopy_edge"] = ("facing", {"": model({"particle": "canopy_top", "top": "canopy_top", "under": "canopy_underside", "side": "steel_dark", "fascia": "fascia"}, [
        el([0, 0, 1.5], [16, 2, 16], "#side", faces={"up": "#top", "down": "#under"}),
        el([0, -3, 0], [16, 3, 1.5], "#side", faces={"north": ("#fascia", [0, 5, 16, 11]), "south": ("#fascia", [0, 5, 16, 11])}),
    ])})
    b["canopy_slope_lower"] = ("facing", {"": canopy_strips(SLOPE_LOWER)})
    b["canopy_slope_upper"] = ("facing", {"": canopy_strips(SLOPE_UPPER)})
    b["canopy_wave_rise"] = ("facing", {"": canopy_strips(WAVE_RISE)})
    b["canopy_wave_crest"] = ("facing", {"": canopy_strips(WAVE_CREST)})
    b["canopy_skylight"] = ("facing", {"": model({"particle": "steel_dark", "frame": "steel_dark", "glass": "roof_glass"}, [
        el([0, 0, 0], [1, 2, 16], "#frame"),
        el([15, 0, 0], [16, 2, 16], "#frame"),
        el([7.5, 0, 0], [8.5, 2, 16], "#frame"),
        el([1, 0.75, 0], [7.5, 1.25, 16], "#glass", faces={"north": None, "south": None, "east": None, "west": None}),
        el([8.5, 0.75, 0], [15, 1.25, 16], "#glass", faces={"north": None, "south": None, "east": None, "west": None}),
    ])})
    b["canopy_light"] = ("facing", {"": model({"particle": "canopy_underside", "top": "canopy_top", "under": "canopy_underside", "side": "steel_dark", "light": "lamp_diffuser"}, [
        el([0, 0, 0], [16, 2, 16], "#side", faces={"up": "#top", "down": "#under"}),
        el([3.5, -0.6, 0.5], [12.5, 0, 15.5], "#side", faces={"up": None}),
        el([4.25, -0.7, 1], [11.75, -0.6, 15], "#side", faces={"down": "#light", "up": None, "north": None, "south": None, "east": None, "west": None}, shade_=False),
    ])})

    # -- Catenary (all galvanised steel)
    b["catenary_mast"] = ("axis", {"": model({"particle": "steel", "side": "steel"}, [
        el([5, 0, 7.5], [11, 16, 8.5], "#side"),
        el([5, 0, 5], [6, 16, 11], "#side"),
        el([10, 0, 5], [11, 16, 11], "#side"),
    ])})
    stay = 7 / math.sin(math.radians(22.5))
    stay_rot = ("x", 22.5, [8, 5, 21])
    b["catenary_cantilever"] = ("facing", {"": model({"particle": "steel", "tube": "steel", "clamp": "steel_dark", "insulator": "insulator"}, [
        el([6, 3, 20], [10, 6, 21], "#clamp", cull=False),
        el([6, 11, 20], [10, 14.5, 21], "#clamp", cull=False),
        el([7.25, 12, 0], [8.75, 13.5, 20], "#tube", cull=False),
        el([6.5, 11.25, 15], [9.5, 14.25, 18], "#insulator", cull=False),
        el([7.25, 4.25, 21 - stay], [8.75, 5.75, 20], "#tube", rot=stay_rot),
        el([6.75, 3.5, 15], [9.25, 6.5, 18], "#insulator", rot=stay_rot),
        el([7.5, 8.5, 3], [8.5, 12, 4], "#clamp", cull=False),
        el([7.5, 7.5, 0], [8.5, 8.5, 4], "#tube", cull=False),
    ])})
    lattice_faces_z = {"north": ("#lattice", [0, 1, 16, 5]), "south": ("#lattice", [0, 1, 16, 5])}
    lattice_faces_y = {"up": ("#lattice", [0, 5, 16, 11]), "down": ("#lattice", [0, 5, 16, 11])}
    b["catenary_gantry"] = ("axis", {"": model({"particle": "steel", "chord": "steel", "lattice": "lattice"}, [
        el([0, 10, 4], [16, 11, 5], "#chord"),
        el([0, 15, 4], [16, 16, 5], "#chord"),
        el([0, 10, 11], [16, 11, 12], "#chord"),
        el([0, 15, 11], [16, 16, 12], "#chord"),
        el([0, 11, 4.5], [16, 15, 4.5], None, faces=lattice_faces_z, cull=False),
        el([0, 11, 11.5], [16, 15, 11.5], None, faces=lattice_faces_z, cull=False),
        el([0, 10.5, 5], [16, 10.5, 11], None, faces=lattice_faces_y, cull=False),
        el([0, 15.5, 5], [16, 15.5, 11], None, faces=lattice_faces_y, cull=False),
    ])})
    insulator_elements = [
        el([7.5, 12, 7.5], [8.5, 16, 8.5], "#rod"),
        el([7, 5.5, 7], [9, 12, 9], "#insulator"),
        el([7, 2.75, 7], [9, 5.5, 9], "#clamp"),
        el([4, 3, 7.5], [12, 4, 8.5], "#rod"),
    ]
    for y in (6.5, 8, 9.5, 11):
        insulator_elements.append(el([6, y, 6], [10, y + 0.75, 10], "#insulator"))
    b["catenary_insulator"] = ("simple", {"": model({"particle": "insulator", "rod": "steel", "clamp": "steel_dark", "insulator": "insulator"}, insulator_elements)})

    # -- Bus
    b["bus_stop_sign"] = ("sign_single", {"": model({"particle": "steel", "pole": "steel", "frame": "steel", "flag": "bus_stop_flag", "plate": "panel_white"}, [
        el([7, 0, 7], [9, 16, 9], "#pole"),
        el([1, 3, 6.75], [15, 12, 9.25], "#frame", faces={"north": ("#plate", [1, 4, 15, 13]), "south": ("#plate", [1, 4, 15, 13])}),
        el([3, 12, 6.75], [13, 16, 9.25], "#frame", faces={"north": ("#flag", [3, 0, 13, 4]), "south": ("#flag", [3, 0, 13, 4])}),
    ])})
    b["bus_timetable_case"] = ("facing", {"": model({"particle": "steel", "pole": "steel", "frame": "steel_dark", "face": "timetable"}, [
        el([7, 0, 7], [9, 16, 9], "#pole"),
        el([1, 3, 4], [15, 15, 7], "#frame", faces={"north": "#face"}),
    ])})
    b["bus_shelter_glass"] = ("facing", {"": model({"particle": "steel_dark", "frame": "steel_dark", "glass": "shelter_glass"}, [
        el([0, 0, 0], [16, 1.5, 1.5], "#frame"),
        el([0, 14.5, 0], [16, 16, 1.5], "#frame"),
        el([0, 1.5, 0], [1, 14.5, 1.5], "#frame"),
        el([15, 1.5, 0], [16, 14.5, 1.5], "#frame"),
        el([1, 1.5, 0.5], [15, 14.5, 1], "#glass", faces={"up": None, "down": None, "east": None, "west": None}),
    ])})
    b["bus_shelter_roof"] = ("facing", {"": model({"particle": "steel_dark", "frame": "steel_dark", "glass": "roof_glass", "fascia": "fascia"}, [
        el([0, 0, 1.5], [1, 2, 16], "#frame"),
        el([15, 0, 1.5], [16, 2, 16], "#frame"),
        el([1, 0.75, 1.5], [15, 1.25, 16], "#glass", faces={"north": None, "east": None, "west": None}),
        el([0, 0, 0], [16, 4, 1.5], "#frame", faces={"north": ("#fascia", [0, 4, 16, 8]), "south": ("#fascia", [0, 4, 16, 8])}),
    ])})
    b["bus_shelter_seat"] = ("facing", {"": model({"particle": "steel_dark", "frame": "steel_dark", "seat": "steel_perforated"}, [
        el([0, 7, 0.5], [16, 8, 7.5], "#seat"),
        el([1, 0, 6], [2.5, 7, 7.5], "#frame"),
        el([13.5, 0, 6], [15, 7, 7.5], "#frame"),
        el([1, 5.5, 0], [2.5, 7, 6], "#frame"),
        el([13.5, 5.5, 0], [15, 7, 6], "#frame"),
    ])})

    def curb(h_mid, h_top):
        return model({"particle": "curb_top", "top": "curb_top", "side": "concrete_light"}, [
            el([0, 0, 0], [16, 2, 0.75], "#side", faces={"up": ("#side", [0, 0, 16, 1])}),
            el([0, 0, 0.75], [16, h_mid, 1.5], "#side", faces={"up": ("#side", [0, 0, 16, 1])}),
            el([0, 0, 1.5], [16, h_top, 16], "#side", faces={"up": "#top"}),
        ])

    b["bus_curb"] = ("facing", {"": curb(5, 16)})
    b["bus_curb_low"] = ("facing", {"": curb(4, 8)})
    return b


# ---------------------------------------------------------------------------------------------------------------------
# Names, recipes
# ---------------------------------------------------------------------------------------------------------------------
NAMES = {
    "platform_paving_light": "Light Platform Paving",
    "platform_paving_dark": "Dark Platform Paving",
    "tactile_warning_paving": "Tactile Warning Paving",
    "platform_edge": "Platform Edge",
    "platform_edge_warning": "Warning Platform Edge",
    "platform_ramp_lower": "Platform Ramp (Lower)",
    "platform_ramp_upper": "Platform Ramp (Upper)",
    "station_name_sign": "Station Name Sign",
    "hanging_station_sign": "Hanging Station Name Sign",
    "platform_number_sign": "Platform Number Sign",
    "direction_sign": "Directional Sign",
    "information_case": "Information Case",
    "sign_pole": "Sign Pole",
    "steel_bench": "Steel Bench",
    "wooden_bench": "Wooden Bench",
    "waste_bin": "Waste Bin",
    "bollard": "Bollard",
    "platform_lamp": "Platform Lamp",
    "information_pillar": "Information Pillar",
    "steel_column_square": "Square Steel Column",
    "steel_column_round": "Round Steel Column",
    "structural_beam": "Steel Beam",
    "roof_support": "Roof Support",
    "glass_wall": "Framed Glass Wall",
    "glass_panel": "Glass Panel",
    "glass_barrier": "Glass Barrier",
    "canopy_flat": "Flat Canopy",
    "canopy_edge": "Canopy Edge",
    "canopy_slope_lower": "Sloped Canopy (Lower)",
    "canopy_slope_upper": "Sloped Canopy (Upper)",
    "canopy_wave_rise": "Wave Canopy Rise",
    "canopy_wave_crest": "Wave Canopy Crest",
    "canopy_skylight": "Canopy Skylight",
    "canopy_light": "Canopy Light",
    "catenary_mast": "Catenary Mast",
    "catenary_cantilever": "Catenary Cantilever",
    "catenary_gantry": "Catenary Gantry",
    "catenary_insulator": "Catenary Insulator",
    "bus_stop_sign": "Bus Stop Sign",
    "bus_timetable_case": "Bus Timetable Case",
    "bus_shelter_glass": "Shelter Glass",
    "bus_shelter_roof": "Bus Shelter Roof",
    "bus_shelter_seat": "Bus Shelter Seat",
    "bus_curb": "Bus Curb",
    "bus_curb_low": "Low Bus Curb",
}

EXTRA_LANG = {
    f"tooltip.{MOD}.sign_pole": "Joins signs above and below it",
    f"itemGroup.{MOD}.main": "ATA Architecture",
    f"itemGroup.{MOD}.wayfinding": "ATA Wayfinding",
    f"itemGroup.{MOD}.passenger_equipment": "ATA Passenger Equipment",
    f"itemGroup.{MOD}.bus_street": "ATA Bus / Street Transit",
    f"itemGroup.{MOD}.glass": "ATA Glass",
    f"screen.{MOD}.edit_sign": "Edit Sign Text",
    f"screen.{MOD}.line": "Line %s",
    f"tooltip.{MOD}.editable": "Right-click with an empty hand to edit",
    f"tooltip.{MOD}.joins": "Joins neighbours into one wide sign",
    f"tooltip.{MOD}.faces_you": "Faces you when placed",
    f"tooltip.{MOD}.points_away": "Edge points the way you look",
    f"tooltip.{MOD}.wall_mounted": "Sticks to the wall you aim at",
    f"tooltip.{MOD}.slope": "Low end points the way you look",
}


def item(name):
    return {"item": name if ":" in name else f"{MOD}:{name}"}


def recipes():
    r = {}

    def cut(result, ingredient, count=1):
        r[f"{result}_from_{ingredient.split(':')[-1]}_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": item(ingredient), "result": f"{MOD}:{result}", "count": count}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [item(i) if not i.startswith("#") else {"tag": i[1:]} for i in ingredients],
                     "result": {"item": f"{MOD}:{result}", "count": count}}

    def shaped(result, pattern, key, count=1):
        r[result] = {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern,
                     "key": {k: (item(v) if not v.startswith("#") else {"tag": v[1:]}) for k, v in key.items()},
                     "result": {"item": f"{MOD}:{result}", "count": count}}

    # Platforms: shaped from smooth stone, then refined in the stonecutter
    shaped("platform_paving_light", ["SS", "SS"], {"S": "minecraft:smooth_stone"}, 4)
    cut("platform_paving_light", "minecraft:smooth_stone")
    cut("platform_paving_dark", "minecraft:polished_deepslate")
    shaped("platform_paving_dark", ["SS", "SS"], {"S": "minecraft:polished_deepslate"}, 4)
    shapeless("tactile_warning_paving", [f"{MOD}:platform_paving_light"] * 4 + ["minecraft:yellow_dye"], 4)
    cut("platform_edge", f"{MOD}:platform_paving_light")
    shapeless("platform_edge_warning", [f"{MOD}:platform_edge"] * 4 + ["minecraft:yellow_dye"], 4)
    cut("platform_ramp_lower", f"{MOD}:platform_paving_light", 2)
    cut("platform_ramp_upper", f"{MOD}:platform_paving_light")
    cut("bus_curb", "minecraft:smooth_stone")
    cut("bus_curb_low", "minecraft:smooth_stone", 2)

    # Steel architecture: the stonecutter acts as the fabrication shop for iron ingots
    for result, count in (("sign_pole", 4), ("steel_column_square", 2), ("steel_column_round", 2), ("structural_beam", 2),
                          ("roof_support", 1), ("canopy_flat", 4), ("canopy_edge", 4), ("canopy_slope_lower", 4),
                          ("canopy_slope_upper", 4), ("canopy_wave_rise", 4), ("canopy_wave_crest", 4),
                          ("catenary_mast", 2), ("catenary_cantilever", 1), ("catenary_gantry", 2), ("bollard", 2),
                          ("bus_shelter_seat", 2)):
        cut(result, "minecraft:iron_ingot", count)

    # Glazing
    for result, count in (("glass_wall", 1), ("glass_panel", 2), ("glass_barrier", 2), ("canopy_skylight", 2),
                          ("bus_shelter_glass", 2), ("bus_shelter_roof", 2)):
        cut(result, "minecraft:glass", count)

    # Signage and furniture
    shapeless("station_name_sign", ["minecraft:iron_ingot", "minecraft:blue_dye", "minecraft:glowstone_dust"], 2)
    shapeless("hanging_station_sign", ["minecraft:iron_ingot", "minecraft:blue_dye", "minecraft:glowstone_dust", "minecraft:chain"], 2)
    shapeless("platform_number_sign", ["minecraft:iron_ingot", "minecraft:blue_dye", "minecraft:white_dye", "minecraft:chain"], 2)
    shapeless("direction_sign", ["minecraft:iron_ingot", "minecraft:gray_dye", "minecraft:glowstone_dust", "minecraft:chain"], 2)
    shapeless("information_case", ["minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:paper", "minecraft:glowstone_dust"])
    shaped("steel_bench", ["I  ", "III", "N N"], {"I": "minecraft:iron_ingot", "N": "minecraft:iron_nugget"}, 2)
    shaped("wooden_bench", ["I  ", "PPP", "N N"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "N": "minecraft:iron_nugget"}, 2)
    shaped("waste_bin", ["N N", "NGN", "NNN"], {"N": "minecraft:iron_nugget", "G": "minecraft:green_dye"}, 1)
    shapeless("platform_lamp", [f"{MOD}:sign_pole", "minecraft:glowstone"])
    shapeless("information_pillar", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:glowstone_dust", "minecraft:redstone"])
    shapeless("canopy_light", [f"{MOD}:canopy_flat", "minecraft:glowstone_dust"])
    shapeless("catenary_insulator", ["minecraft:brick", "minecraft:iron_nugget", "minecraft:iron_nugget"], 2)
    shapeless("bus_stop_sign", [f"{MOD}:sign_pole", "minecraft:iron_ingot", "minecraft:green_dye", "minecraft:white_dye"])
    shapeless("bus_timetable_case", [f"{MOD}:sign_pole", "minecraft:glass_pane", "minecraft:paper"])
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------------------------------------------------
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


# Sign pole joins the sign above / below it (Java: SignPoleBlock, tag pole_mounts). Under a street sign the pole moves
# under the sign's own off-centre post instead (align=<street sign facing>; model sign_pole_post, rotated like the sign).
# The extension is a little slimmer
# than the pole so its faces never share a plane with a 2 px sign panel (z 7..9), and it stops where every tagged sign
# still covers it: sign y 9 going up (all panels span y 7..11), sign y 7 going down.
POLE_VARIANTS = (("", False, False), ("_up", True, False), ("_down", False, True), ("_up_down", True, True))
POLE_MOUNTS = ("station_name_sign", "hanging_station_sign", "platform_number_sign", "direction_sign", "composition_board",
               "hanging_wayfinding_sign", "exit_sign", "pictogram_sign")


def sign_pole(up, down):
    els = [el([7, 0, 7], [9, 16, 9], "#pole", skip=(("up",) if up else ()) + (("down",) if down else ()))]
    side = {f: ("#pole", [7, 0, 9, 16]) for f in ("north", "south", "east", "west")}
    if up:
        els.append(el([7.25, 16, 7.25], [8.75, 25, 8.75], "#pole", faces=side | {"down": None}, cull=False))
    if down:
        els.append(el([7.25, -9, 7.25], [8.75, 0, 8.75], "#pole", faces=side | {"up": None}, cull=False))
    return model({"particle": "steel", "pole": "steel"}, els)


def blockstate(block_id, kind, variant_names):
    ref = lambda suffix: f"{MOD}:block/{block_id}{suffix}"
    if kind == "simple":
        return {"variants": {"": {"model": ref("")}}}
    if kind in ("facing", "sign_single"):
        return {"variants": {f"facing={f}": ({"model": ref("")} | ({"y": y} if y else {})) for f, y in FACING_Y.items()}}
    if kind == "pole":
        variants = {}
        for suffix, up, down in POLE_VARIANTS:
            for align in ("centre",) + tuple(FACING_Y):
                key = f"align={align},down={str(down).lower()},up={str(up).lower()}"
                variants[key] = {"model": ref(suffix)} if align == "centre" else {"model": ref("_post")} | ({"y": FACING_Y[align]} if FACING_Y[align] else {})
        return {"variants": variants}
    if kind == "axis":
        return {"variants": {"axis=x": {"model": ref("")}, "axis=z": {"model": ref(""), "y": 90}}}
    if kind == "sign":
        variants = {}
        for f, y in FACING_Y.items():
            for left in ("false", "true"):
                for right in ("false", "true"):
                    suffix = ("_l" if left == "true" else "") + ("_r" if right == "true" else "")
                    assert suffix in variant_names
                    variants[f"facing={f},left={left},right={right}"] = {"model": ref(suffix)} | ({"y": y} if y else {})
        return {"variants": variants}
    raise ValueError(kind)


def icon():
    img = Image.new("RGBA", (128, 128), SIGN_BLUE + (255,))
    d = ImageDraw.Draw(img)
    # wave canopy
    points = [(x, 52 - 18 * math.sin(math.pi * (x - 14) / 100) ** 2) for x in range(14, 115)]
    d.line(points, fill=WHITE + (255,), width=7)
    for x in (30, 64, 98):
        d.rectangle((x - 3, 50, x + 3, 100), fill=STEEL + (255,))
    d.rectangle((10, 100, 118, 108), fill=PAVING_LIGHT + (255,))
    d.rectangle((10, 108, 118, 112), fill=TACTILE + (255,))
    return img


EXTENSION_MODULES = ("assets_live", "assets_interactive", "assets_wayfinding", "assets_elevated", "assets_equipment", "assets_screens", "assets_signs14", "assets_glass", "assets_stations_nl_be", "assets_stations_de_it", "assets_metro")


def load_extensions():
    """Workstream modules may define: textures(), blocks(), names(), lang(), recipes(), write_extra(assets, data, write_json).
    They can import this module (``import generate_assets as g``) for the model/texture helpers."""
    import importlib
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    sys.modules.setdefault("generate_assets", sys.modules[__name__])
    return [importlib.import_module(name) for name in EXTENSION_MODULES]


def main():
    extensions = load_extensions()
    for sub in ("blockstates", "models", "textures", "lang"):
        shutil.rmtree(ASSETS / sub, ignore_errors=True)
    for sub in ("loot_tables", "recipes"):
        shutil.rmtree(DATA / MOD / sub, ignore_errors=True)

    textures = draw_textures()
    all_blocks = blocks()
    names = dict(NAMES)
    lang = dict(EXTRA_LANG)
    all_recipes = recipes()
    for ext in extensions:
        textures.update(getattr(ext, "textures", dict)())
        all_blocks.update(getattr(ext, "blocks", dict)())
        names.update(getattr(ext, "names", dict)())
        lang.update(getattr(ext, "lang", dict)())
        all_recipes.update(getattr(ext, "recipes", dict)())

    for name, img in textures.items():
        path = ASSETS / "textures" / "block" / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
    icon().save(ASSETS / "icon.png")

    assert set(all_blocks) == set(names), set(all_blocks) ^ set(names)
    for block_id, (kind, variants) in all_blocks.items():
        for suffix, m in variants.items():
            write_json(ASSETS / "models" / "block" / f"{block_id}{suffix}.json", m)
        write_json(ASSETS / "blockstates" / f"{block_id}.json", blockstate(block_id, kind, variants))
        write_json(ASSETS / "models" / "item" / f"{block_id}.json", {"parent": f"{MOD}:block/{block_id}"})
        write_json(DATA / MOD / "loot_tables" / "blocks" / f"{block_id}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{block_id}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })

    for name, recipe in all_recipes.items():
        write_json(DATA / MOD / "recipes" / f"{name}.json", recipe)

    write_json(DATA / MOD / "tags" / "blocks" / "pole_mounts.json", {"replace": False, "values": [f"{MOD}:{b}" for b in POLE_MOUNTS]})
    write_json(DATA / "minecraft" / "tags" / "blocks" / "mineable" / "pickaxe.json",
               {"replace": False, "values": [f"{MOD}:{b}" for b in all_blocks]})

    lang.update({f"block.{MOD}.{b}": n for b, n in names.items()})
    write_json(ASSETS / "lang" / "en_us.json", dict(sorted(lang.items())))
    for ext in extensions:
        if hasattr(ext, "write_extra"):
            ext.write_extra(ASSETS, DATA, write_json)
    print(f"Generated {len(all_blocks)} blocks, {len(textures)} textures, {len(all_recipes)} recipes")


if __name__ == "__main__":
    main()
