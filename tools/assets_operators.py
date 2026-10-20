"""Operator station name signs: the operator's mark painted at the left end of a row, the station name beside it.

Same extension interface as the other assets_*.py modules. Each sign is the ordinary joined name sign; only the block that
starts a row (no neighbour on its left) shows the mark, the rest of a row is the plain coloured panel. Textures are 64x64 (the
model's UVs are in sixteenths, so they map unchanged). The marks come from tools/logos/*.png, rendered from the .svg files there.
"""
from pathlib import Path

from PIL import Image

import generate_assets as g

MOD = g.MOD
P = "operator_"
LOGOS = Path(__file__).resolve().parent / "logos"
SIZE = 64
# model pixels of the face the mark occupies at the start of a row (Java: StationBlocksOperators.MARK)
MARK_UNITS = 10

# (block id, face colour, mark colour or None for its own, crop of the mark's width as a fraction, English name)
OPERATORS = (
    ("ns", (0, 48, 130), (255, 255, 255), 1.0, "NS Station Sign"),
    ("db", (236, 236, 236), None, 1.0, "DB Station Sign"),
    ("gvb", (11, 99, 181), (255, 255, 255), 0.45, "GVB Station Sign"),
    # on white, so the roundel's blue bar stays visible; the name in the bar's blue
    ("underground", (236, 236, 236), None, 1.0, "Underground Station Sign"),
    ("national_rail", (236, 236, 236), None, 1.0, "National Rail Station Sign"),
    ("sbahn", (236, 236, 236), None, 1.0, "S-Bahn Station Sign"),
    ("sbb", (45, 50, 125), None, 1.0, "SBB Station Sign"),
    ("jr", (236, 236, 236), None, 1.0, "JR Station Sign"),
)


def mark(name, colour, crop):
    img = Image.open(LOGOS / f"{name}.png").convert("RGBA")
    if crop < 1.0:
        img = img.crop((0, 0, int(img.width * crop), img.height))
    img = img.crop(img.getbbox())
    if colour is not None:
        solid = Image.new("RGBA", img.size, tuple(colour) + (255,))
        solid.putalpha(img.split()[3])
        img = solid
    return img


def face(name, background, colour, crop, with_mark):
    img = Image.new("RGBA", (SIZE, SIZE), tuple(background) + (255,))
    if with_mark:
        # the mark sits clear of the sign's 1-unit end cap (so it is not cut off), centred in the first MARK_UNITS units
        box = SIZE * 6 // 16
        logo = mark(name, colour, crop)
        logo.thumbnail((box, box), Image.LANCZOS)
        centre = int(SIZE * (1 + (MARK_UNITS - 1) / 2) / 16)
        img.alpha_composite(logo, (centre - logo.width // 2, 17 - logo.height // 2))
    return img


def textures():
    t = {}
    for name, background, colour, crop, _ in OPERATORS:
        t[P + name + "_logo"] = face(name, background, colour, crop, True)
        t[P + name + "_plain"] = face(name, background, colour, crop, False)
    return t


def sign_model(name, left, right):
    """The joined name sign. A sign's left and right are in its own frame (TextSignBlock.localRight = facing turned clockwise), so
    for a viewer at its front the row's left end is its LAST block (no neighbour on its right) and for a viewer behind it the
    row's left end is its FIRST block (no neighbour on its left). Each face carries the mark on the block at its viewer's left."""
    front = P + name + ("_plain" if right else "_logo")
    model = g.sign_variant("station_name_sign", left, right, face=front, frame="steel", compact=True)
    back = P + name + ("_plain" if left else "_logo")
    model["textures"]["back"] = "aurelia_transit_architecture:block/" + back
    for element in model["elements"]:
        south = element["faces"].get("south")
        if south and south.get("texture") == "#face":
            south["texture"] = "#back"
    return model


def blocks():
    b = {}
    for name, *_ in OPERATORS:
        variants = {}
        for left in (False, True):
            for right in (False, True):
                variants[("_l" if left else "") + ("_r" if right else "")] = sign_model(name, left, right)
        b[f"{name}_station_sign"] = ("sign", variants)
    return b


def names():
    return {f"{name}_station_sign": label for name, *_, label in OPERATORS}


def lang():
    return {}


def recipes():
    r = {}
    for name, background, *_ in OPERATORS:
        dye = {"ns": "minecraft:blue_dye", "db": "minecraft:white_dye", "gvb": "minecraft:light_blue_dye",
               "underground": "minecraft:lapis_lazuli", "national_rail": "minecraft:red_dye", "sbahn": "minecraft:green_dye",
               "sbb": "minecraft:redstone", "jr": "minecraft:lime_dye"}[name]
        r[f"{name}_station_sign"] = {"type": "minecraft:crafting_shapeless", "category": "building",
                                     "ingredients": [g.item("minecraft:iron_ingot"), g.item(dye), g.item("minecraft:paper"), g.item("minecraft:glowstone_dust")],
                                     "result": {"item": f"{MOD}:{name}_station_sign", "count": 2}}
    return r
