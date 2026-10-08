"""1.5 architectural glass: float, low-iron, tinted, reflective, frosted, fritted and wired glass with matching panes, glass
brick, curtain wall panel, structural fin and glass floor panel.

Same extension interface as the other assets_*.py modules. All textures are 16x16 like the rest of the mod; realism comes from a
faint tint, low alpha (vanilla glass is about 40-60 inside a fully opaque rim), a soft diagonal streak and a slightly darker 1 px
edge. Wired and fritted glass are float glass (same faint tint and alpha) with opaque wire/dots on top, so they render translucent.
Panes reuse the block texture for their faces and vanilla's pane templates (one cuboid per part) for geometry.
"""
import math

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."

# id -> (name, tooltip key). Each type below also gets "<id>_pane" unless it is glass_brick.
PANE_PARTS = ("post", "side", "side_alt", "noside", "noside_alt")


def mix(a, b, t):
    return tuple(g.clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighten(color, amount):
    return g.shade(color, amount)


def pane(name, tint, alpha, edge, edge_alpha, streak=13, noise=0, noise_alpha=0, sky=None, ring=True):
    """Glass pane texture. `sky` = (top colour, bottom colour) for a mirror-coated vertical gradient; `ring` draws the 1 px rim."""
    r = g.rng(name)
    img = g.new()
    px = img.load()
    for y in range(16):
        base = mix(sky[0], sky[1], 0.5 - 0.125 * math.cos(math.pi * y / 8)) if sky else tint  # cyclic, so stacked blocks have no seam
        for x in range(16):
            jitter = r.randint(-noise, noise) if noise else 0
            color = lighten(base, jitter)
            a = alpha + (r.randint(-noise_alpha, noise_alpha) if noise_alpha else 0)
            d = (x + y) % 16  # wraps, so the streak continues across neighbouring blocks
            # soft reflection streaks, lower-left to upper-right: one bright band with a faint companion
            if streak and d in (12, 13):
                color, a = lighten(color, streak), a + streak // 2
            elif streak and d in (14, 6):
                color, a = lighten(color, streak // 2), a + streak // 4
            px[x, y] = color + (g.clamp(a),)
    if ring:
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                top_left = x == 0 or y == 0
                px[x, y] = lighten(edge, 6 if top_left else -6) + (edge_alpha,)
    return img


def edge_texture(name, color, alpha, noise=2):
    """Pane edge strip (the 2 px column vanilla's pane templates sample) and slab sides."""
    return g.noisy(color, noise, name, alpha)


def frit(name):
    """Float glass with staggered ceramic dots: opaque white enamel with a soft shadow pixel to the right and below, so it reads as
    fired frit in the glass rather than a loose white pixel. The glass between the dots is faint float glass."""
    img = pane(name, (172, 212, 194), 44, (106, 158, 134), 120, noise=1, ring=False)
    px = img.load()
    dots = [(x, y) for y in range(16) for x in range(16) if (x % 4 == 1 and y % 4 == 1) or (x % 4 == 3 and y % 4 == 3)]
    for x, y in dots:
        px[x, y] = (246, 249, 247, 255) if (x % 4, y % 4) == (1, 1) else (236, 241, 239, 255)
    for x, y in dots:
        for dx, dy in ((1, 0), (0, 1)):  # wrapped, so tiles stay seamless
            sx, sy = (x + dx) % 16, (y + dy) % 16
            if px[sx, sy][3] < 255:
                px[sx, sy] = (104, 136, 124, 92)
    return img


def wired(name):
    """Float glass with a thin grey square wire mesh (opaque, so the wire stays crisp inside the faint glass)."""
    r = g.rng(name)
    img = pane(name, (172, 212, 194), 44, (106, 158, 134), 120, noise=1, ring=False)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if y % 8 == 3 or x % 8 == 3:
                tone = 136 + r.randint(-6, 6) + (10 if (y % 8 == 3 and x % 8 != 3) else 0)
                px[x, y] = (tone, tone + 5, tone + 7, 255)
    for y in range(16):  # soft shadow under the horizontal wires
        for x in range(16):
            if y % 8 == 4 and px[x, y][3] < 255:
                px[x, y] = (96, 124, 114, 84)
    return img


def glass_brick(name):
    """2x2 glass blocks in light mortar; wavy, thick-looking glass inside each."""
    r = g.rng(name)
    img = g.new()
    px = img.load()
    mortar = (196, 198, 192)
    tint = (160, 204, 194)
    for y in range(16):
        for x in range(16):
            if x % 8 == 0 or y % 8 == 0:
                px[x, y] = g.shade(mortar, r.randint(-5, 5)) + (255,)
                continue
            wave = math.sin(x * 0.95 + y * 0.45) + 0.7 * math.sin(y * 1.25 - x * 0.35)
            color = g.shade(tint, int(wave * 9) + r.randint(-2, 2))
            a = int(112 + wave * 20)
            if x % 8 == 1 or y % 8 == 1:
                color, a = g.shade(color, 16), a + 28
            elif x % 8 == 7 or y % 8 == 7:
                color, a = g.shade(color, -14), a + 22
            px[x, y] = color + (g.clamp(a),)
    return img


# (id, name, tooltip key, texture) per glazing type; panes are generated from the same entries.
TYPES = (
    ("clear_float_glass", "Clear Float Glass", "glass_float", dict(tint=(172, 212, 194), alpha=40, edge=(106, 158, 134), edge_alpha=120, noise=1, ring=False),
     dict(color=(100, 156, 130), alpha=150)),
    ("low_iron_glass", "Low-Iron Glass", "glass_low_iron", dict(tint=(226, 238, 240), alpha=28, edge=(186, 208, 214), edge_alpha=90, streak=9, ring=False),
     dict(color=(190, 212, 218), alpha=120)),
    ("grey_tinted_glass", "Grey Tinted Glass", "glass_solar", dict(tint=(88, 94, 100), alpha=92, edge=(54, 58, 62), edge_alpha=150, noise=1, ring=False),
     dict(color=(60, 64, 68), alpha=180)),
    ("bronze_tinted_glass", "Bronze Tinted Glass", "glass_solar", dict(tint=(156, 110, 70), alpha=88, edge=(104, 70, 42), edge_alpha=150, noise=1, ring=False),
     dict(color=(112, 78, 48), alpha=180)),
    ("blue_tinted_glass", "Blue Tinted Glass", "glass_solar", dict(tint=(74, 124, 176), alpha=84, edge=(44, 82, 126), edge_alpha=150, noise=1, ring=False),
     dict(color=(50, 90, 134), alpha=180)),
    ("reflective_glass", "Reflective Glass", "glass_reflective",
     dict(tint=(0, 0, 0), alpha=150, edge=(78, 94, 110), edge_alpha=225, streak=17, ring=False, sky=((164, 198, 226), (96, 118, 140))),
     dict(color=(84, 100, 116), alpha=230)),
    ("frosted_glass", "Frosted Glass", "glass_frosted",
     dict(tint=(226, 233, 236), alpha=176, edge=(188, 198, 202), edge_alpha=215, streak=0, noise=7, noise_alpha=10, ring=False),
     dict(color=(196, 206, 210), alpha=225)),
)
CUTOUT_TYPES = (
    ("fritted_glass", "Fritted Glass", "glass_fritted", dict(color=(100, 156, 130), alpha=150)),
    ("wired_glass", "Wired Glass", "glass_wired", dict(color=(100, 156, 130), alpha=150)),
)
BRICK = ("glass_brick", "Glass Brick", "glass_brick")


def textures():
    t = {}
    for ident, _, _, face, edge in TYPES:
        t[ident] = pane(ident, **face)
        t[f"{ident}_edge"] = edge_texture(f"{ident}_edge", edge["color"], edge["alpha"])
    for ident, _, _, edge in CUTOUT_TYPES:
        t[ident] = frit(ident) if ident == "fritted_glass" else wired(ident)
        t[f"{ident}_edge"] = edge_texture(f"{ident}_edge", edge["color"], edge["alpha"])
    t["glass_brick"] = glass_brick("glass_brick")

    # curtain wall: slim anodised aluminium mullions and transoms around nearly clear glass
    wall = pane("curtain_wall_glass", (176, 210, 202), 42, (172, 177, 181), 255, streak=11, noise=1)
    px = wall.load()
    for i in range(1, 15):
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            px[x, y] = (120, 130, 138, 70)
    t["curtain_wall_glass"] = wall
    t["curtain_wall_frame"] = g.brushed((172, 177, 181), "curtain_wall_frame")

    # structural fin: the model shows columns 2..13 only, so no rim is drawn
    t["structural_glass_fin"] = pane("structural_glass_fin", (172, 212, 196), 52, (106, 158, 134), 120, streak=13, ring=False)
    t["structural_glass_fin_edge"] = edge_texture("structural_glass_fin_edge", (100, 156, 130), 165)

    # glass floor: slightly firmer glass with a faint anti-slip dot grid; the side shows laminated layers (rows 8..15)
    top = pane("glass_floor_panel", (170, 208, 196), 74, (110, 160, 138), 150, streak=10, noise=1, ring=False)
    px = top.load()
    for y in range(2, 15, 4):
        for x in range(2, 15, 4):
            px[x, y] = (214, 232, 226, 128)
    t["glass_floor_panel"] = top
    side = edge_texture("glass_floor_panel_edge", (98, 152, 128), 132, 3)
    px = side.load()
    for y in (10, 13):
        for x in range(16):
            px[x, y] = (214, 230, 222, 175)
    t["glass_floor_panel_edge"] = side
    return t


def pane_models(ident):
    return {f"_{part}": {"parent": f"minecraft:block/template_glass_pane_{part}",
                         "textures": {"pane": g.tx(ident[:-5]), "edge": g.tx(ident[:-5] + "_edge")}}
            for part in PANE_PARTS}


def cube(texture):
    return {"": {"parent": "minecraft:block/cube_all", "textures": {"all": g.tx(texture)}}}


def blocks():
    b = {}
    for ident in [t[0] for t in TYPES] + [t[0] for t in CUTOUT_TYPES]:
        b[ident] = ("simple", cube(ident))
        b[f"{ident}_pane"] = ("simple", pane_models(f"{ident}_pane"))
    b["glass_brick"] = ("simple", cube("glass_brick"))
    b["curtain_wall_glass"] = ("facing", {"": g.model(
        {"particle": "curtain_wall_frame", "glass": "curtain_wall_glass", "frame": "curtain_wall_frame"},
        [g.el([0, 0, 7], [16, 16, 9], "#frame", faces={"north": "#glass", "south": "#glass"})])})
    b["structural_glass_fin"] = ("axis", {"": g.model(
        {"particle": "structural_glass_fin_edge", "glass": "structural_glass_fin", "edge": "structural_glass_fin_edge"},
        [g.el([2, 0, 7], [14, 16, 9], "#edge", faces={"north": "#glass", "south": "#glass"})])})
    b["glass_floor_panel"] = ("simple", {"": g.model(
        {"particle": "glass_floor_panel", "top": "glass_floor_panel", "edge": "glass_floor_panel_edge"},
        [g.el([0, 0, 0], [16, 8, 16], "#edge", faces={"up": "#top", "down": "#top"})])})
    return b


def names():
    n = {}
    for ident, name, *_ in TYPES + CUTOUT_TYPES:
        n[ident] = name
        n[f"{ident}_pane"] = f"{name} Pane"
    n.update({
        "glass_brick": "Glass Brick",
        "curtain_wall_glass": "Curtain Wall Glass",
        "structural_glass_fin": "Structural Glass Fin",
        "glass_floor_panel": "Glass Floor Panel",
    })
    return n


def lang():
    return {
        TIP + "glass_float": "Standard glass with a faint green tint",
        TIP + "glass_low_iron": "Ultra-clear glass with almost no tint",
        TIP + "glass_solar": "Tinted glass that cuts glare",
        TIP + "glass_reflective": "Mirror-coated glass for facades",
        TIP + "glass_frosted": "Etched glass you cannot see through",
        TIP + "glass_fritted": "Clear glass with a dot pattern",
        TIP + "glass_wired": "Glass with a safety wire mesh",
        TIP + "glass_brick": "Thick, wavy glass block",
        TIP + "curtain_wall_glass": "Glass panel with a slim metal frame",
        TIP + "structural_glass_fin": "Slim fin that braces glass walls",
        TIP + "glass_floor_panel": "Walkable glass floor",
    }


def recipes():
    r = {}

    def cut(result, count=1):
        r[f"{result}_from_glass_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": g.item("minecraft:glass"), "result": f"{MOD}:{result}", "count": count}

    def shapeless(result, ingredients, count):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    float_glass = f"{MOD}:clear_float_glass"
    for result in ("clear_float_glass", "frosted_glass", "fritted_glass", "wired_glass", "glass_brick", "curtain_wall_glass", "glass_floor_panel"):
        cut(result)
    cut("structural_glass_fin", 2)
    shapeless("low_iron_glass", ["minecraft:glass", "minecraft:quartz"], 2)
    shapeless("grey_tinted_glass", [float_glass] * 4 + ["minecraft:gray_dye"], 4)
    shapeless("bronze_tinted_glass", [float_glass] * 4 + ["minecraft:brown_dye"], 4)
    shapeless("blue_tinted_glass", [float_glass] * 4 + ["minecraft:blue_dye"], 4)
    shapeless("reflective_glass", [float_glass] * 4 + ["minecraft:iron_nugget"], 4)

    # Panes: six blocks make sixteen, as with vanilla glass panes
    for ident in [t[0] for t in TYPES] + [t[0] for t in CUTOUT_TYPES]:
        r[f"{ident}_pane"] = {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["GGG", "GGG"],
                              "key": {"G": g.item(f"{MOD}:{ident}")}, "result": {"item": f"{MOD}:{ident}_pane", "count": 16}}
    return r


def write_extra(assets, data, write_json):
    ref = lambda name: f"{MOD}:block/{name}"
    states = assets / "blockstates"
    for ident in [t[0] for t in TYPES] + [t[0] for t in CUTOUT_TYPES]:
        pane_id = f"{ident}_pane"
        # vanilla pane layout: post always, a side per connection, a stub per open side
        parts = [{"apply": {"model": ref(f"{pane_id}_post")}}]
        for direction, model, y in (("north", "side", 0), ("east", "side", 90), ("south", "side_alt", 0), ("west", "side_alt", 90)):
            parts.append({"when": {direction: "true"}, "apply": {"model": ref(f"{pane_id}_{model}")} | ({"y": y} if y else {})})
        for direction, model, y in (("north", "noside", 0), ("east", "noside_alt", 0), ("south", "noside_alt", 90), ("west", "noside", 270)):
            parts.append({"when": {direction: "false"}, "apply": {"model": ref(f"{pane_id}_{model}")} | ({"y": y} if y else {})})
        write_json(states / f"{pane_id}.json", {"multipart": parts})
        write_json(assets / "models" / "item" / f"{pane_id}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": g.tx(ident)}})

    # Full-cube glass is impermeable like vanilla glass; panes, partial pieces are not.
    write_json(data / "minecraft" / "tags" / "blocks" / "impermeable.json", {
        "replace": False,
        "values": [f"{MOD}:{t[0]}" for t in TYPES + CUTOUT_TYPES] + [f"{MOD}:glass_brick"]})
