"""1.5 car stop boards: the boards at the platform end that tell the driver where to stop a train of N cars.

Same extension interface as the other assets_*.py modules. Three generic, logo-free styles:
  uk_car_stop_marker  small white plate, big black number (1-12)
  german_stop_board   black board, yellow border, white number; 0 = the "H" (stop here) board
  dutch_stop_board    white board, black number; 0 = the red and white stop-here board

One 16x16 texture per style and number, drawn with a 4x6 pixel font (doubled, so strokes are two texels wide). Each block has a
freestanding model (plate on a thin post, 2 cuboids) and a wall model (1 cuboid); blockstates pick the number texture through
tiny child models, so the elements are written once per mount.
"""
from PIL import Image

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
MSG = f"message.{MOD}.car_stop."
MAX_CARS = 12

# 4x6 digits and H. Single digits are drawn doubled (8x12); two digits are drawn 5 wide and 12 tall, so the stems stay two texels thick.
FONT = {
    "0": (".##.", "#..#", "#..#", "#..#", "#..#", ".##."),
    "1": (".#..", "##..", ".#..", ".#..", ".#..", "###."),
    "2": (".##.", "#..#", "..#.", ".#..", "#...", "####"),
    "3": ("###.", "...#", ".##.", "...#", "#..#", ".##."),
    "4": ("..#.", ".##.", "#.#.", "####", "..#.", "..#."),
    "5": ("####", "#...", "###.", "...#", "#..#", ".##."),
    "6": (".##.", "#...", "###.", "#..#", "#..#", ".##."),
    "7": ("####", "...#", "..#.", ".#..", ".#..", ".#.."),
    "8": (".##.", "#..#", ".##.", "#..#", "#..#", ".##."),
    "9": (".##.", "#..#", "#..#", ".###", "...#", ".##."),
    "H": ("#..#", "#..#", "####", "#..#", "#..#", "#..#"),
}

UK_WHITE, UK_INK, UK_EDGE = (240, 240, 235), (22, 22, 24), (196, 196, 190)
NL_WHITE, NL_INK, NL_FRAME, NL_EDGE = (244, 244, 240), (22, 22, 24), (58, 60, 64), (200, 200, 196)
NL_RED, NL_RED_WHITE = (196, 30, 36), (244, 244, 240)
DE_BLACK, DE_YELLOW, DE_WHITE, DE_EDGE = (22, 22, 24), (232, 190, 30), (240, 240, 235), (30, 30, 32)

UK_ROWS = 14    # the UK plate is 8 x 7 px, so it shows the top 14 of the 16 texture rows


def fill(img, color):
    g.rect(img, 0, 0, 16, 16, color)


def frame(img, color):
    g.rect(img, 0, 0, 16, 1, color)
    g.rect(img, 0, 15, 16, 16, color)
    g.rect(img, 0, 0, 1, 16, color)
    g.rect(img, 15, 0, 16, 16, color)


def number(img, text, color, rows=16):
    """A centred number: one glyph doubled, or two glyphs 5 wide with two-texel strokes."""
    single = len(text) == 1
    step_x, cell_w = (2, 2) if single else (1, 2)
    glyph_w = 3 * step_x + cell_w
    total = glyph_w if single else 2 * glyph_w + 2
    x0, y0 = (16 - total) // 2, (rows - 12) // 2
    for i, ch in enumerate(text):
        gx = x0 + i * (glyph_w + 2)
        for r, line in enumerate(FONT[ch]):
            for c, bit in enumerate(line):
                if bit == "#":
                    g.rect(img, gx + c * step_x, y0 + r * 2, gx + c * step_x + cell_w, y0 + r * 2 + 2, color)


def uk(n):
    img = g.new()
    fill(img, UK_WHITE)
    number(img, str(n), UK_INK, UK_ROWS)
    return img


def german(n):
    img = g.new()
    fill(img, DE_BLACK)
    frame(img, DE_YELLOW)
    number(img, "H" if n == 0 else str(n), DE_WHITE)
    return img


def dutch(n):
    img = g.new()
    if n == 0:
        fill(img, NL_RED)
        frame(img, NL_RED_WHITE)
        g.rect(img, 3, 6, 13, 10, NL_RED_WHITE)
        return img
    fill(img, NL_WHITE)
    frame(img, NL_FRAME)
    number(img, str(n), NL_INK)
    return img


STYLES = {  # block id -> (texture prefix, painter, first number, edge colour, plate uv rows, post plate, wall plate)
    "uk_car_stop_marker": ("cs_uk", uk, 1, UK_EDGE, UK_ROWS, ([4, 6, 7], [12, 13, 9]), ([4, 5, 14.5], [12, 12, 16])),
    "german_stop_board": ("cs_de", german, 0, DE_EDGE, 16, ([4, 6, 7], [12, 14, 9]), ([4, 4, 14.5], [12, 12, 16])),
    "dutch_stop_board": ("cs_nl", dutch, 0, NL_EDGE, 16, ([4, 6, 7], [12, 14, 9]), ([4, 4, 14.5], [12, 12, 16])),
}
DEFAULT_CARS = 4
KIND = {"uk_car_stop_marker": "car_stop", "german_stop_board": "stop_board", "dutch_stop_board": "stop_board"}


def textures():
    t = {}
    for block, (prefix, paint, first, edge, _, _, _) in STYLES.items():
        t[f"{prefix}_edge"] = g.new(edge)
        for n in range(first, MAX_CARS + 1):
            t[f"{prefix}_{n}"] = paint(n)
    return t


def blocks():
    b = {}
    for block, (prefix, _, first, _, rows, post, wall) in STYLES.items():
        uv = [0, 0, 16, rows]
        face = {"north": ("#face", uv), "south": ("#face", uv)}
        textures_ = {"particle": f"{prefix}_edge", "edge": f"{prefix}_edge", "post": "steel", "face": f"{prefix}_{DEFAULT_CARS}"}
        post_model = g.model(textures_, [
            g.el(post[0], post[1], "#edge", faces=face),
            g.el([7.5, 0, 7.5], [8.5, post[0][1], 8.5], "#post", faces={"up": None}),
        ])
        wall_model = g.model(textures_, [
            g.el(wall[0], wall[1], "#edge", faces={"north": ("#face", uv), "south": None}),
        ])
        variants = {"": post_model, "_wall": wall_model}
        for n in range(first, MAX_CARS + 1):
            for mount, parent in (("post", ""), ("wall", "_wall")):
                variants[f"_{mount}_{n}"] = {"parent": f"{MOD}:block/{block}{parent}", "textures": {"face": g.tx(f"{prefix}_{n}")}}
        b[block] = (KIND[block], variants)
    return b


def names():
    return {"uk_car_stop_marker": "UK Car Stop Marker", "german_stop_board": "German Stop Board", "dutch_stop_board": "Dutch Stop Board"}


def lang():
    return {
        TIP + "car_stop_cycle": "Right-click to change the car number",
        MSG + "cars": "Train length: %s cars",
        MSG + "stop": "Stop here",
    }


def recipes():
    r = {}

    def shapeless(result, ingredients, count=2):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    shapeless("uk_car_stop_marker", ["minecraft:iron_ingot", "minecraft:white_dye", "minecraft:black_dye", "minecraft:iron_nugget"])
    shapeless("german_stop_board", ["minecraft:iron_ingot", "minecraft:black_dye", "minecraft:yellow_dye", "minecraft:white_dye"])
    shapeless("dutch_stop_board", ["minecraft:iron_ingot", "minecraft:white_dye", "minecraft:black_dye", "minecraft:red_dye"])
    return r
