"""1.4 station equipment assets: noise barriers, fare gates, card readers, ticket booth window, CCTV cameras, lift status panel.

Same extension interface as the other assets_*.py modules. Models face NORTH; blockstates (written in write_extra) rotate them
by facing. Blocks with kinds emit one model per kind; the item shows the first (default) kind. Lift status panels reuse one
model per status (the lamp texture differs). All art is original and generic: no operator branding, logos or text.
"""
import math

from PIL import Image, ImageDraw

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
MSG = f"message.{MOD}.style."
SCREEN = f"screen.{MOD}."
CARD = f"message.{MOD}.card."

BARRIER = ("solid", "solid_half", "glass", "glass_half", "solid_glass")
GATE = ("gate", "wide", "end")
READER = ("post", "wall")
CAMERA = ("wall", "pendant", "dome")
STATUS = ("in_service", "out_of_service", "maintenance")

KIND_NAMES = {
    "solid": "Solid", "solid_half": "Solid, half height", "glass": "Glass", "glass_half": "Glass, half height", "solid_glass": "Solid below, glass above",
    "gate": "Gate", "wide": "Wide gate", "end": "End cabinet", "post": "Post", "wall": "Wall", "pendant": "Pendant", "dome": "Dome",
}

READER_LIGHT = (196, 232, 238)
FLAT_Y = {"up": None, "down": None}


def px_set(img, x, y, colour):
    if 0 <= x < 16 and 0 <= y < 16:
        img.load()[x, y] = colour[:3] + (255,)


def textures():
    t = {}

    # Absorptive noise panel: grey-green perforated metal with vertical ribs.
    panel = g.noisy((104, 120, 112), 3, "noise_panel")
    px = panel.load()
    for x in range(16):
        amount = (12, 5, -8, -14)[x % 4]
        for y in range(16):
            px[x, y] = g.shade(px[x, y][:3], amount) + (255,)
    for y in range(2, 16, 4):
        for x in range(1, 16, 4):
            px[x, y] = g.shade((104, 120, 112), -34) + (255,)
    g.rect(panel, 0, 0, 16, 1, g.shade((104, 120, 112), -22))
    t["noise_panel"] = panel

    # Fare gate cabinet top: dark with a card-reader pad near the north end (the model's top face covers x 0..4, z 1..15).
    top = g.noisy((38, 42, 46), 2, "gate_top")
    g.rect(top, 0, 2, 4, 6, (92, 168, 178))
    g.rect(top, 1, 3, 3, 5, (22, 26, 30))
    px_set(top, 1, 3, READER_LIGHT)
    t["gate_top"] = top

    # Indicator end faces (model uv [0,0,4,14]): green arrow / red cross on a dark lens.
    go = g.brushed(g.STEEL, "gate_go")
    g.rect(go, 0, 1, 4, 7, (18, 24, 20))
    for x, y in ((1, 2), (2, 2), (1, 3), (2, 3), (0, 4), (1, 4), (2, 4), (3, 4), (1, 5), (2, 5)):
        px_set(go, x, y, (70, 214, 110))
    t["gate_indicator_go"] = go
    stop = g.brushed(g.STEEL, "gate_stop")
    g.rect(stop, 0, 1, 4, 7, (26, 18, 18))
    for i in range(4):
        px_set(stop, i, 2 + i, (226, 62, 56))
        px_set(stop, 3 - i, 2 + i, (226, 62, 56))
    t["gate_indicator_stop"] = stop

    # Contactless reader face: dark with concentric arcs and a dot (generic wave symbol).
    face = g.noisy((22, 27, 32), 2, "reader_face")
    cx, cy = 4.5, 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            angle = abs(math.degrees(math.atan2(dy, dx)))
            if d < 1.3:
                px_set(face, x, y, READER_LIGHT)
            elif angle < 50 and any(abs(d - r) < 0.6 for r in (3.4, 5.6, 7.8)):
                px_set(face, x, y, READER_LIGHT)
    g.rect(face, 0, 0, 16, 1, (70, 78, 86))
    g.rect(face, 0, 15, 16, 16, (70, 78, 86))
    g.rect(face, 0, 0, 1, 16, (70, 78, 86))
    g.rect(face, 15, 0, 16, 16, (70, 78, 86))
    t["reader_face"] = face

    # Booth window: neutral counter panel, shelf and a round speech grille (cut-out).
    t["booth_counter"] = g.noisy((176, 180, 182), 3, "booth_counter")
    g.rect(t["booth_counter"], 0, 0, 16, 1, (130, 134, 138))
    t["booth_shelf"] = g.brushed((92, 98, 102), "booth_shelf", vertical=False)
    grille = g.new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 7.5:
                hole = d < 6 and x % 2 == 0 and y % 2 == 0
                px_set(grille, x, y, (24, 27, 30) if hole else (g.shade(g.STEEL_DARK, 12 if d > 6 else 0)))
    t["booth_grille"] = grille

    # CCTV: off-white housing, dark lens face, smoked dome (opaque: these blocks render cut-out).
    t["cam_body"] = g.noisy((208, 211, 212), 2, "cam_body")
    lens = g.noisy((210, 213, 214), 2, "cam_lens")
    g.rect(lens, 1, 1, 15, 15, (28, 31, 36))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 4.5:
                px_set(lens, x, y, (18, 22, 40) if d > 2.2 else (46, 70, 120))
    px_set(lens, 7, 6, (190, 210, 235))
    t["cam_lens"] = lens
    smoked = g.noisy((50, 56, 62), 3, "cam_dome")
    for i in range(16):
        px_set(smoked, i, 15 - i, (88, 98, 108))
    t["cam_dome"] = smoked

    # Lift status panel: plain dark face with a thin border (region shown on the model is x 2..14, y 2..13), status lamps.
    lift = g.noisy((24, 28, 33), 2, "lift_face")
    for x in range(2, 14):
        px_set(lift, x, 2, (84, 92, 100))
        px_set(lift, x, 12, (84, 92, 100))
    for y in range(2, 13):
        px_set(lift, 2, y, (84, 92, 100))
        px_set(lift, 13, y, (84, 92, 100))
    t["lift_face"] = lift
    for status, colour in zip(STATUS, ((64, 220, 96), (232, 60, 52), (244, 176, 40))):
        lamp = g.noisy(colour, 4, f"lift_lamp_{status}")
        g.rect(lamp, 0, 0, 16, 2, g.shade(colour, 30))
        t[f"lift_lamp_{status}"] = lamp
    return t


# ---------------------------------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------------------------------
def noise_barrier(kind):
    t = {"particle": "noise_panel", "panel": "noise_panel", "frame": "steel_dark", "glass": "glass_clear"}
    height = 8 if kind.endswith("_half") else 16
    x0 = 1.5
    els = [g.el([0, 0, 0], [x0, 16 if kind == "solid_glass" else height, 2], "#frame")]
    glass = lambda y1, y2: g.el([x0, y1, 0.75], [16, y2, 1.25], "#glass", faces={"up": None, "down": None, "east": None, "west": None})
    rail = lambda y1, y2: g.el([x0, y1, 0], [16, y2, 2], "#frame")
    if kind in ("solid", "solid_half"):
        els += [g.el([x0, 0, 0], [16, height - 1, 2], "#panel"), rail(height - 1, height)]
    elif kind in ("glass", "glass_half"):
        els += [rail(0, 1.5), glass(1.5, height - 1.5), rail(height - 1.5, height)]
    else:
        els += [g.el([x0, 0, 0], [16, 7.5, 2], "#panel"), rail(7.5, 9), glass(9, 14.5), rail(14.5, 16)]
    return g.model(t, els)


def fare_gate(kind, open_=False):
    """Closed paddles reach into the passage; open ones are folded into the cabinet (not drawn)."""
    t = {"particle": "steel", "body": "steel", "top": "gate_top", "go": "gate_indicator_go", "stop": "gate_indicator_stop", "glass": "glass_clear"}
    faces = {"up": "#top"}
    if kind != "end":
        faces |= {"north": ("#go", [0, 0, 4, 14]), "south": ("#stop", [0, 0, 4, 14])}
    els = [g.el([0, 0, 1], [4, 14, 15], "#body", faces=faces)]
    if kind == "gate" and not open_:
        els.append(g.el([4, 5, 7], [9, 12, 9], "#glass", faces={"west": None}))
    elif kind == "wide" and not open_:
        els.append(g.el([4, 5, 7], [13, 12, 9], "#glass", faces={"west": None}))
    return g.model(t, els)


def card_reader(kind):
    t = {"particle": "steel_dark", "post": "steel", "head": "steel_dark", "face": "reader_face"}
    if kind == "post":
        return g.model(t, [
            g.el([6.5, 0, 6.5], [9.5, 11, 9.5], "#post", faces={"up": None}),
            g.el([5, 11, 5], [11, 16, 11], "#head", faces={"north": ("#face", [0, 1.5, 16, 14.5])}),
        ])
    return g.model(t, [g.el([5, 5, 12], [11, 12, 16], "#head", faces={"north": ("#face", [0, 0, 16, 16])})])


def booth_window():
    t = {"particle": "booth_counter", "counter": "booth_counter", "shelf": "booth_shelf", "frame": "steel_dark", "glass": "glass_clear", "grille": "booth_grille"}
    return g.model(t, [
        g.el([0, 0, 12], [16, 6, 16], "#counter"),
        g.el([0, 6, 8], [16, 7, 12], "#shelf"),
        g.el([0, 6, 12.5], [16, 8, 14.5], "#frame"),
        g.el([0, 14, 12.5], [16, 16, 14.5], "#frame"),
        g.el([0, 8, 12.5], [1, 14, 14.5], "#frame"),
        g.el([15, 8, 12.5], [16, 14, 14.5], "#frame"),
        g.el([1, 8, 13], [15, 14, 14], "#glass", faces={"up": None, "down": None, "east": None, "west": None}),
        g.el([6, 9, 12.6], [10, 13, 13], "#grille", faces={"north": "#grille", "up": None, "down": None, "east": None, "west": None, "south": None}),
    ])


def cctv_camera(kind):
    t = {"particle": "cam_body", "body": "cam_body", "lens": "cam_lens", "bracket": "steel_dark", "dome": "cam_dome"}
    if kind == "wall":
        return g.model(t, [
            g.el([7, 8, 12], [9, 10, 16], "#bracket"),
            g.el([5.5, 9, 4], [10.5, 13, 13], "#body", faces={"north": ("#lens", [0, 1.6, 16, 14.4])}),
        ])
    if kind == "pendant":
        return g.model(t, [
            g.el([7, 12, 7], [9, 16, 9], "#bracket"),
            g.el([5.5, 8, 3], [10.5, 12, 12], "#body", faces={"north": ("#lens", [0, 1.6, 16, 14.4])}),
        ])
    return g.model(t, [
        g.el([4, 15, 4], [12, 16, 12], "#bracket"),
        g.el([4, 13.5, 4], [12, 15, 12], "#dome"),
        g.el([5, 12, 5], [11, 13.5, 11], "#dome", faces={"up": None}),
        g.el([6, 11, 6], [10, 12, 10], "#dome", faces={"up": None}),
    ])


def lift_panel(status):
    t = {"particle": "steel_dark", "shell": "steel_dark", "face": "lift_face", "lamp": f"lift_lamp_{status}"}
    lamp_faces = {f: ("#lamp", [5, 5, 11, 11]) for f in ("north", "up", "down", "east", "west")} | {"south": None}
    return g.model(t, [
        g.el([2, 3, 13.5], [14, 14, 16], "#shell", faces={"north": "#face"}),
        g.el([11.5, 12, 13], [13, 13.5, 13.5], "#lamp", faces=lamp_faces),
    ])


def blocks():
    wd = lambda models: {"": next(iter(models.values()))} | models
    b = {}
    b["noise_barrier"] = ("simple", wd({f"_{k}": noise_barrier(k) for k in BARRIER}))
    b["fare_gate"] = ("simple", wd({f"_{k}": fare_gate(k) for k in GATE} | {f"_{k}_open": fare_gate(k, True) for k in GATE if k != "end"}))
    b["card_reader"] = ("simple", wd({f"_{k}": card_reader(k) for k in READER}))
    b["booth_window"] = ("facing", {"": booth_window()})
    b["cctv_camera"] = ("simple", wd({f"_{k}": cctv_camera(k) for k in CAMERA}))
    b["lift_status_panel"] = ("simple", wd({f"_{s}": lift_panel(s) for s in STATUS}))
    return b


def names():
    return {
        "noise_barrier": "Noise Barrier",
        "fare_gate": "Fare Gate",
        "card_reader": "Card Reader",
        "booth_window": "Booth Window",
        "cctv_camera": "CCTV Camera",
        "lift_status_panel": "Lift Status Panel",
    }


def lang():
    lg = {
        TIP + "noise_barrier": "Trackside noise wall; stack for height",
        TIP + "prop_only": "Decorative only",
        TIP + "fare_gate": "Walk through with a transit card to pay",
        TIP + "transit_card": "Right-click to see your balance",
        TIP + "transit_card.load": "Top up with /card load <emeralds>",
        f"item.{MOD}.transit_card": "Transit Card",
        CARD + "balance": "Transit card balance: $%s",
        CARD + "given": "You received a transit card.",
        CARD + "how_to_load": "Top up with /card load <emeralds>",
        CARD + "loaded": "Loaded $%s for %s emeralds. Balance: $%s",
        CARD + "not_enough": "You need %s emeralds but carry %s.",
        CARD + "unavailable": "Fares are unavailable with this MTR version",
        CARD + "need_card": "Carry a transit card (/card)",
        CARD + "welcome": "Welcome! You have a transit card; see /card",
        TIP + "lift_status": "Shows lift levels and status; right-click to edit",
        SCREEN + "lift_in_service": "In service",
        SCREEN + "lift_out_of_service": "Out of service",
        SCREEN + "lift_maintenance": "Maintenance",
        SCREEN + "lift_default_name": "Lift",
        SCREEN + "lift_status": "Status: %s",
    }
    for block, kinds in (("noise_barrier", BARRIER), ("fare_gate", GATE), ("card_reader", READER), ("cctv_camera", CAMERA)):
        for kind in kinds:
            lg[f"{MSG}{block}.{kind}"] = KIND_NAMES[kind]
    return lg


def recipes():
    r = {}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    iron, nugget, pane, redstone = "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:glass_pane", "minecraft:redstone"
    shapeless("noise_barrier", [iron, pane, "minecraft:stone"], 4)
    shapeless("fare_gate", [iron, iron, pane, redstone], 2)
    shapeless("card_reader", [iron, nugget, redstone], 2)
    shapeless("booth_window", [pane, pane, pane, iron], 2)
    shapeless("cctv_camera", [iron, nugget, pane, redstone], 2)
    shapeless("lift_status_panel", [iron, redstone, pane], 2)
    return r


def write_extra(assets, data, write_json):
    ref = lambda name: f"{MOD}:block/{name}"
    states = assets / "blockstates"
    for block, kinds in (("noise_barrier", BARRIER), ("card_reader", READER), ("cctv_camera", CAMERA)):
        write_json(states / f"{block}.json", {"variants": {
            f"facing={f},kind={k}": {"model": ref(f"{block}_{k}")} | ({"y": y} if y else {}) for k in kinds for f, y in g.FACING_Y.items()}})
    # Fare gate: paddles drawn while closed or waiting for MTR's answer, folded away while open.
    write_json(states / "fare_gate.json", {"variants": {
        f"facing={f},kind={k},open={o}": {"model": ref(f"fare_gate_{k}_open" if o == "open" and k != "end" else f"fare_gate_{k}")} | ({"y": y} if y else {})
        for k in GATE for o in ("closed", "pending", "open") for f, y in g.FACING_Y.items()}})

    # Transit card (an item, not a block): flat generated item model and its texture.
    card = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle((0, 3, 15, 12), radius=1, fill=(38, 92, 156, 255), outline=(22, 56, 98, 255))
    d.rectangle((1, 5, 14, 6), fill=(230, 236, 242, 255))
    d.rectangle((2, 8, 4, 10), fill=(214, 178, 72, 255))
    d.rectangle((7, 9, 13, 9), fill=(160, 190, 222, 255))
    path = assets / "textures" / "item" / "transit_card.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    card.save(path)
    write_json(assets / "models" / "item" / "transit_card.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/transit_card"}})
    variants = {}
    for f, y in g.FACING_Y.items():
        for left in ("false", "true"):
            for right in ("false", "true"):
                for s in STATUS:
                    variants[f"facing={f},left={left},right={right},status={s}"] = {"model": ref(f"lift_status_panel_{s}")} | ({"y": y} if y else {})
    write_json(states / "lift_status_panel.json", {"variants": variants})
