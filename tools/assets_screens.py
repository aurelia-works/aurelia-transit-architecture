"""1.4 platform screen doors and train-keyed platform edges: screen_door_panel, screen_door_doorway, drop_barrier_edge,
boarding_step_edge.

Same extension interface as the other assets_*.py modules. Models face NORTH (track side z = 0); blockstates (written in
write_extra) rotate them by facing. The panel/doorway geometry mirrors ScreenDoorGeometry.boxes (Java) and
ElevatedKinds.CurveKind.reach. The edge blocks have a "base" model (the placed block) and a "moving" model that the block
entity renderer translates (bars down up to 9 px, step north up to 4 px). All art is original and generic.
"""
import math

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
MSG = f"message.{MOD}.style."

KINDS = ("straight", "diagonal", "outer", "inner")
KIND_NAMES = {"straight": "Straight", "diagonal": "45° Diagonal", "outer": "Convex Curve", "inner": "Concave Curve"}
PARTS = ("base", "moving")
STRIPS = 8
HEADER = 13


def reach(kind, z):
    """Mirror of ElevatedKinds.CurveKind.reach."""
    if kind == "diagonal":
        return min(16.0, z)
    if kind == "outer":
        return math.sqrt(max(0.0, 256 - (16 - z) ** 2))
    return 16 - math.sqrt(max(0.0, 256 - z * z))


def boxes(kind, doorway):
    """Mirror of ScreenDoorGeometry.boxes: [x1, y1, z1, x2, y2, z2] in north-facing pixels."""
    if kind == "straight":
        if doorway:
            return [[0, 0, 0, 1.5, 16, 1.5], [14.5, 0, 0, 16, 16, 1.5], [1.5, HEADER, 0, 14.5, 16, 1.5]]
        return [[0, 0, 0, 16, 16, 1.5]]
    strips = []
    for i in range(STRIPS):
        z1 = i * 2
        x2 = round(reach(kind, z1 + 1) * 2) / 2
        if x2 <= 0:
            continue
        strips.append([max(0, x2 - 2.5), z1, x2, z1 + 2])
    out = []
    for n, s in enumerate(strips):
        post = n in (0, len(strips) - 1)
        out.append([s[0], 0 if (not doorway or post) else HEADER, s[1], s[2], 16, s[3]])
    return out


def px_set(img, x, y, colour):
    if 0 <= x < 16 and 0 <= y < 16:
        img.load()[x, y] = colour[:3] + (255,)


def textures():
    t = {}
    header = g.noisy((34, 40, 48), 2, "screen_header")
    g.rect(header, 0, 0, 16, 1, (70, 78, 88))
    g.rect(header, 0, 2, 16, 3, (24, 28, 34))
    t["screen_header"] = header

    lamp = g.noisy((34, 40, 48), 2, "screen_header_lamp")
    g.rect(lamp, 0, 0, 16, 1, (70, 78, 88))
    g.rect(lamp, 0, 2, 16, 3, (24, 28, 34))
    for x in (6, 7, 8, 9):
        px_set(lamp, x, 1, (62, 220, 104))
    for x in (7, 8):
        px_set(lamp, x, 0, (170, 255, 190))
    t["screen_header_lamp"] = lamp

    post = g.noisy((60, 64, 68), 3, "barrier_post")
    for y in range(2, 14, 4):
        g.rect(post, 6, y, 10, y + 2, (14, 16, 18))
    g.rect(post, 0, 0, 1, 16, (96, 102, 108))
    t["barrier_post"] = post

    stripes = g.noisy((240, 196, 40), 2, "barrier_stripes")
    px = stripes.load()
    for y in range(16):
        for x in range(16):
            if ((x + y) // 3) % 2:
                px[x, y] = (30, 30, 32, 255)
    t["barrier_stripes"] = stripes

    housing = g.noisy((22, 25, 29), 2, "step_housing")
    for x in range(0, 16, 2):
        g.rect(housing, x, 2, x + 1, 4, (48, 54, 60))
    t["step_housing"] = housing

    plate = g.noisy((150, 154, 158), 3, "step_plate")
    for y in range(2, 16, 2):
        for x in range(1 + (y // 2) % 2, 16, 3):
            px_set(plate, x, y, (110, 114, 118))
    g.rect(plate, 0, 0, 16, 1, (240, 196, 40))
    t["step_plate"] = plate
    t["step_edge"] = g.noisy((240, 196, 40), 3, "step_edge")
    return t


# ---------------------------------------------------------------------------------------------------------------------
def screen_panel(kind):
    t = {"particle": "steel_dark", "glass": "glass_clear", "header": "screen_header", "rail": "steel_dark"}
    els = []
    for b in boxes(kind, False):
        x1, _, z1, x2, _, z2 = b
        els.append(g.el([x1, 0, z1], [x2, 0.5, z2], "#rail"))
        els.append(g.el([x1, 0.5, z1], [x2, HEADER, z2], "#glass", faces={"up": None, "down": None}))
        els.append(g.el([x1, HEADER, z1], [x2, 16, z2], "#header", faces={"down": None}))
    return g.model(t, els)


def screen_doorway(kind):
    t = {"particle": "steel", "post": "steel", "header": "screen_header", "lamp": "screen_header_lamp"}
    els = []
    for b in boxes(kind, True):
        x1, y1, z1, x2, y2, z2 = b
        if y1 == 0 and kind != "straight":
            els.append(g.el([x1, y1, z1], [x2, y2, z2], "#post"))
        elif y1 == 0:
            els.append(g.el([x1, y1, z1], [x2, y2, z2], "#post"))
        else:
            faces = {"down": "#header"}
            if kind == "straight":
                faces |= {"north": "#lamp", "south": "#lamp"}
            els.append(g.el([x1, y1, z1], [x2, y2, z2], "#header", faces=faces))
    return g.model(t, els)


def drop_barrier(part):
    if part == "base":
        m = g.platform_edge("platform_edge_top")
        m["textures"]["post"] = g.tx("barrier_post")
        for x1, x2 in ((1, 2.5), (13.5, 15)):
            m["elements"].append(g.el([x1, 16, 1], [x2, 30, 2.5], "#post", faces={"down": None}))
        return m
    t = {"particle": "barrier_stripes", "bar": "barrier_stripes"}
    return g.model(t, [g.el([2.5, y, 1.25], [13.5, y + 1, 2.25], "#bar") for y in (20, 26)])


def boarding_step(part):
    if part == "base":
        t = {"particle": "platform_edge_top", "top": "platform_edge_top", "coping": "coping_side", "face": "concrete_dark",
             "side": "concrete_light", "housing": "step_housing"}
        return g.model(t, [
            g.el([0, 13.5, 0], [16, 16, 16], "#side", faces={"up": "#top", "north": "#coping", "down": None}),
            g.el([0, 12, 0], [1, 13.5, 16], "#side", faces={"north": "#coping", "down": "#face", "up": None}),
            g.el([15, 12, 0], [16, 13.5, 16], "#side", faces={"north": "#coping", "down": "#face", "up": None}),
            g.el([1, 12, 0.5], [15, 13.5, 16], "#side", faces={"north": "#housing", "down": "#face", "up": None, "east": None, "west": None}),
            g.el([0, 0, 2], [16, 12, 16], "#side", faces={"north": "#face", "up": None}),
        ])
    t = {"particle": "step_plate", "plate": "step_plate", "front": "step_edge"}
    return g.model(t, [g.el([1, 12.5, -0.5], [15, 13.5, 2], "#plate", faces={"north": "#front"})])


def blocks():
    wd = lambda models: {"": next(iter(models.values()))} | models
    b = {}
    b["screen_door_panel"] = ("simple", wd({f"_{k}": screen_panel(k) for k in KINDS}))
    b["screen_door_doorway"] = ("simple", wd({f"_{k}": screen_doorway(k) for k in KINDS}))
    b["drop_barrier_edge"] = ("simple", wd({f"_{p}": drop_barrier(p) for p in PARTS}))
    b["boarding_step_edge"] = ("simple", wd({f"_{p}": boarding_step(p) for p in PARTS}))
    return b


def names():
    return {
        "screen_door_panel": "Platform Screen Panel",
        "screen_door_doorway": "Platform Screen Doorway",
        "drop_barrier_edge": "Drop Barrier Platform Edge",
        "boarding_step_edge": "Boarding Step Platform Edge",
    }


def lang():
    lg = {
        TIP + "screen_door_panel": "Fixed platform screen door panel",
        TIP + "screen_door_doorway": "Open screen door gap; train doors open here",
        TIP + "drop_barrier_edge": "Barrier drops when a train stops",
        TIP + "boarding_step_edge": "Step slides out when a train stops",
    }
    for block in ("screen_door_panel", "screen_door_doorway"):
        for kind in KINDS:
            lg[f"{MSG}{block}.{kind}"] = KIND_NAMES[kind]
    return lg


def recipes():
    r = {}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    iron, pane, bars = "minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:iron_bars"
    edge = f"{MOD}:platform_edge"
    shapeless("screen_door_panel", [pane, pane, pane, iron], 4)
    shapeless("screen_door_doorway", [iron, iron, bars, "minecraft:lime_dye"], 2)
    shapeless("drop_barrier_edge", [edge, bars, bars, "minecraft:chain"])
    shapeless("boarding_step_edge", [edge, iron, "minecraft:iron_nugget", "minecraft:yellow_dye"])
    return r


def write_extra(assets, data, write_json):
    ref = lambda name: f"{MOD}:block/{name}"
    states = assets / "blockstates"
    for block, prop, values in (("screen_door_panel", "kind", KINDS), ("screen_door_doorway", "kind", KINDS),
                                ("drop_barrier_edge", "part", PARTS), ("boarding_step_edge", "part", PARTS)):
        write_json(states / f"{block}.json", {"variants": {
            f"facing={f},{prop}={v}": {"model": ref(f"{block}_{v}")} | ({"y": y} if y else {}) for v in values for f, y in g.FACING_Y.items()}})
