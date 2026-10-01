"""1.3 urban infrastructure assets: elevated viaduct and station family, railings, tactile junctions.

Same extension interface as the other assets_*.py modules. Every block here is ONE inventory item whose looks are
block states ("kind", "style", "concrete"), so this module emits many models but few blocks:

viaduct_column (4 styles), viaduct_beam (4 roles x steel/concrete x 2 axes), viaduct_brace (diagonal / knee),
station_stair, stair_enclosure (3), platform_windscreen (2), platform_fascia (3), station_fence (2), utility_run (2),
deck_light, handrail (3), tactile_junction (3).  All art is original and generic.
"""
import math

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
MSG = f"message.{MOD}.style."

# (block id, property name, values in enum order); the Java enums (ElevatedKinds) list the same values in the same order.
COLUMN = ("steel_heavy", "steel_narrow", "concrete", "concrete_narrow")
BEAM = ("crossbeam", "girder", "stringer", "platform_support")
BRACE = ("diagonal", "knee")
ENCLOSURE = ("clad", "windowed", "glazed")
WINDSCREEN = ("lower", "upper")
FASCIA = ("plain", "panelled", "ribbed")
FENCE = ("platform", "trackside")
UTILITY = ("tray", "conduit")
RAIL = ("handrail", "balustrade", "ramp_rail")
JUNCTION = ("turn", "tee", "cross")
CURVE = ("diagonal", "outer", "inner")

TACTILE = g.TACTILE
CLADDING = (150, 158, 164)


def textures():
    t = {}

    # Steel-pan stair tread: diamond grating.
    tread = g.noisy((74, 79, 84), 3, "stair_tread")
    px = tread.load()
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                px[x, y] = g.shade((74, 79, 84), 26) + (255,)
    g.rect(tread, 0, 0, 16, 1, g.shade(g.STEEL, -20))
    g.rect(tread, 0, 15, 16, 16, g.shade(g.STEEL, -34))
    t["stair_tread"] = tread

    # Corrugated cladding: vertical ribs.
    cladding = g.noisy(CLADDING, 3, "cladding")
    px = cladding.load()
    for x in range(16):
        shade_amount = (14, 6, -10, -16)[x % 4]
        for y in range(16):
            px[x, y] = g.shade(px[x, y][:3], shade_amount) + (255,)
    t["cladding"] = cladding

    # Ribbed concrete fascia.
    ribbed = g.noisy(g.CONCRETE_DARK, 4, "ribbed_concrete")
    px = ribbed.load()
    for x in range(0, 16, 4):
        for y in range(16):
            px[x, y] = g.shade(g.CONCRETE_DARK, 22) + (255,)
            px[x + 1, y] = g.shade(g.CONCRETE_DARK, -22) + (255,)
    t["ribbed_concrete"] = ribbed

    # Trackside mesh (cut-out): wire diamond lattice on a transparent background.
    mesh = g.new()
    px = mesh.load()
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                px[x, y] = g.shade(g.STEEL, -26) + (255,)
    t["fence_mesh"] = mesh

    # Cable bundles: dark sheath with coloured conductor stripes.
    cables = g.noisy((34, 36, 40), 3, "utility_cables")
    for x, colour in ((2, (190, 50, 44)), (6, (44, 90, 190)), (10, (220, 190, 50)), (13, (60, 160, 90))):
        g.rect(cables, x, 0, x + 2, 16, colour)
    t["utility_cables"] = cables

    # Tactile guidance junctions: bars along the path, warning dots where lines meet. North is the way the line leaves.
    for kind in JUNCTION:
        t[f"wf_guidance_{kind}"] = junction_texture(kind)
    return t


def junction_texture(kind):
    img = g.new()
    r = g.rng(f"junction_{kind}")
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = g.shade(TACTILE, r.randint(-5, 5)) + (255,)

    def bars_vertical(x1, x2, y1, y2):
        for x in range(x1, x2, 3):
            for y in range(y1, y2):
                px[x, y] = g.shade(TACTILE, 34) + (255,)
                if x + 1 < x2:
                    px[x + 1, y] = g.shade(TACTILE, -28) + (255,)

    def bars_horizontal(y1, y2, x1, x2):
        for y in range(y1, y2, 3):
            for x in range(x1, x2):
                px[x, y] = g.shade(TACTILE, 34) + (255,)
                if y + 1 < y2:
                    px[x, y + 1] = g.shade(TACTILE, -28) + (255,)

    if kind == "turn":
        bars_vertical(5, 11, 0, 7)       # line arrives from the south edge ... actually leaves north: south half only
        bars_horizontal(5, 11, 11, 16)   # ... and continues east
    elif kind == "tee":
        bars_vertical(5, 11, 0, 5)       # stem from the south
        bars_horizontal(5, 11, 0, 5)     # branch west
        bars_horizontal(5, 11, 11, 16)   # branch east
    else:
        bars_vertical(5, 11, 0, 5)
        bars_vertical(5, 11, 11, 16)
        bars_horizontal(5, 11, 0, 5)
        bars_horizontal(5, 11, 11, 16)
    # warning dots in the middle square where the lines meet
    for y in range(5, 11, 2):
        for x in range(5, 11, 2):
            px[x, y] = g.shade(TACTILE, 40) + (255,)
            px[x + 1, y + 1] = g.shade(TACTILE, -34) + (255,)
    return img


def mat(concrete):
    """(body, trim) texture names of a beam material."""
    return ("concrete_light", "concrete_dark") if concrete else ("steel_dark", "steel")


def column(style):
    if style == "steel_heavy":
        return g.model({"particle": "steel_dark", "flange": "steel", "web": "steel_dark"}, [
            g.el([3, 0, 3], [13, 16, 5], "#flange"),
            g.el([3, 0, 11], [13, 16, 13], "#flange"),
            g.el([6.5, 0, 5], [9.5, 16, 11], "#web"),
        ])
    if style == "steel_narrow":
        return g.model({"particle": "steel", "tube": "steel", "band": "steel_dark"}, [
            g.el([5, 0, 5], [11, 16, 11], "#tube"),
            g.el([4.5, 7, 4.5], [11.5, 9, 11.5], "#band"),
        ])
    if style == "concrete":
        return g.model({"particle": "concrete_light", "pier": "concrete_light", "foot": "concrete_dark"}, [
            g.el([2, 0, 2], [14, 16, 14], "#pier"),
            g.el([1.5, 0, 1.5], [14.5, 1.5, 14.5], "#foot"),
        ])
    return g.model({"particle": "concrete_light", "pier": "concrete_light", "foot": "concrete_dark"}, [
        g.el([4, 0, 4], [12, 16, 12], "#pier"),
        g.el([3.5, 0, 3.5], [12.5, 1.5, 12.5], "#foot"),
    ])


def beam(kind, concrete):
    body, trim = mat(concrete)
    t = {"particle": body, "body": body, "trim": trim}
    if kind == "crossbeam":
        return g.model(t, [
            g.el([0, 14, 3], [16, 16, 13], "#trim"),
            g.el([0, 2, 3], [16, 4, 13], "#trim"),
            g.el([0, 4, 6], [16, 14, 10], "#body"),
            g.el([7, 4, 4.5], [9, 14, 11.5], "#trim"),
        ])
    if kind == "girder":
        return g.model(t, [
            g.el([0, 6, 2], [16, 16, 14], "#body"),
            g.el([0, 6, 2], [16, 7.5, 14], "#trim"),
            g.el([0, 14.5, 2], [16, 16, 14], "#trim"),
        ])
    if kind == "stringer":
        return g.model(t, [
            g.el([0, 14, 4], [16, 16, 12], "#trim"),
            g.el([0, 10, 7], [16, 14, 9], "#body"),
            g.el([0, 10, 5], [16, 11.5, 11], "#trim"),
        ])
    return g.model(t, [
        g.el([0, 12, 0], [16, 16, 16], "#trim"),
        g.el([0, 5, 6], [16, 12, 10], "#body"),
        g.el([0, 5, 3], [16, 7, 13], "#trim"),
    ])


def brace(kind):
    t = {"particle": "steel", "bar": "steel", "plate": "steel_dark"}
    if kind == "diagonal":
        bar = g.el([6, -3.3, 6.5], [10, 19.3, 9.5], "#bar", cull=False)
        bar["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": -45}
        return g.model(t, [bar, g.el([5, 0, 14], [11, 2, 16], "#plate"), g.el([5, 14, 0], [11, 16, 2], "#plate")])
    strut = g.el([6, -2.5, 8.5], [10, 14.5, 11.5], "#bar", cull=False)
    strut["rotation"] = {"origin": [8, 6, 10], "axis": "x", "angle": -45}
    return g.model(t, [g.el([6, 12, 0], [10, 16, 16], "#bar"), strut, g.el([5, 0, 14], [11, 4, 16], "#plate")])


def enclosure(kind):
    both = {"north": "#clad", "south": "#clad"}
    if kind == "clad":
        return g.model({"particle": "cladding", "clad": "cladding", "frame": "steel_dark"}, [
            g.el([0, 0, 0], [16, 16, 2], "#frame", faces=both)])
    t = {"particle": "cladding", "clad": "cladding", "frame": "steel_dark", "glass": "shelter_glass"}
    if kind == "windowed":
        return g.model(t, [
            g.el([0, 0, 0], [16, 5, 2], "#frame", faces=both),
            g.el([0, 13, 0], [16, 16, 2], "#frame", faces=both),
            g.el([0, 5, 0], [2, 13, 2], "#frame", faces=both),
            g.el([14, 5, 0], [16, 13, 2], "#frame", faces=both),
            g.el([2, 5, 0.75], [14, 13, 1.25], "#glass", faces={"up": None, "down": None, "west": None, "east": None}),
        ])
    return g.model(t, [
        g.el([0, 0, 0.75], [16, 16, 1.25], "#glass", faces={"up": None, "down": None, "west": None, "east": None}),
        g.el([0, 0, 0], [16, 1.5, 2], "#frame"),
        g.el([0, 14.5, 0], [16, 16, 2], "#frame"),
        g.el([0, 1.5, 0], [1.5, 14.5, 2], "#frame"),
        g.el([14.5, 1.5, 0], [16, 14.5, 2], "#frame"),
        g.el([7.5, 1.5, 0], [8.5, 14.5, 2], "#frame"),
    ])


def windscreen(kind):
    t = {"particle": "steel", "glass": "glass_clear", "frame": "steel", "plate": "cladding"}
    flat = {"up": None, "down": None, "west": None, "east": None}
    if kind == "lower":
        return g.model(t, [
            g.el([0, 0, 0], [16, 4, 1.5], "#plate"),
            g.el([0, 4, 0.5], [16, 16, 1.0], "#glass", faces=flat),
            g.el([0, 4, 0], [1.2, 16, 1.5], "#frame"),
            g.el([14.8, 4, 0], [16, 16, 1.5], "#frame"),
            g.el([0, 3.5, 0], [16, 4.5, 1.5], "#frame"),
        ])
    return g.model(t, [
        g.el([0, 0, 0.5], [16, 13, 1.0], "#glass", faces=flat),
        g.el([0, 0, 0], [1.2, 13, 1.5], "#frame"),
        g.el([14.8, 0, 0], [16, 13, 1.5], "#frame"),
        g.el([0, 13, 0], [16, 14.5, 1.5], "#frame"),
        g.el([0, 15, 0], [16, 16, 1.5], "#frame"),
    ])


def fascia(kind):
    if kind == "plain":
        return g.model({"particle": "concrete_dark", "face": "concrete_dark", "side": "concrete_light"}, [
            g.el([0, 0, 0], [16, 16, 3], "#side", faces={"north": "#face"})])
    if kind == "panelled":
        return g.model({"particle": "concrete_dark", "face": "concrete_dark", "side": "concrete_light", "panel": "fascia"}, [
            g.el([0, 0, 1], [16, 16, 3], "#side", faces={"north": "#face"}),
            g.el([0.5, 0.5, 0], [15.5, 15.5, 1], "#side", faces={"north": "#panel"}),
        ])
    return g.model({"particle": "ribbed_concrete", "rib": "ribbed_concrete", "side": "concrete_light"}, [
        g.el([0, 0, 0], [16, 16, 3], "#side", faces={"north": "#rib"})])


def fence_post(kind):
    if kind == "platform":
        return g.model({"particle": "steel", "post": "steel", "cap": "steel_dark"}, [
            g.el([6.5, 0, 6.5], [9.5, 15, 9.5], "#post"), g.el([6, 15, 6], [10, 16, 10], "#cap")])
    return g.model({"particle": "steel", "post": "steel", "cap": "steel_dark"}, [
        g.el([7, 0, 7], [9, 16, 9], "#post"), g.el([6.5, 15, 6.5], [9.5, 16, 9.5], "#cap")])


def fence_side(kind):
    if kind == "platform":
        return g.model({"particle": "steel", "rail": "steel", "picket": "steel_dark"}, [
            g.el([7.25, 12, 0], [8.75, 14, 6.5], "#rail"), g.el([7.25, 6, 0], [8.75, 8, 6.5], "#rail"),
            g.el([7.5, 0, 1], [8.5, 12, 2], "#picket"), g.el([7.5, 0, 4], [8.5, 12, 5], "#picket")])
    flat = {"up": None, "down": None, "north": None, "south": None}
    return g.model({"particle": "steel", "rail": "steel", "mesh": "fence_mesh"}, [
        g.el([7.75, 1, 0], [8.25, 15, 7], "#mesh", faces=flat), g.el([7, 15, 0], [9, 16, 7], "#rail"), g.el([7, 0, 0], [9, 1, 7], "#rail")])


def rail_post():
    return g.model({"particle": "steel", "post": "steel"}, [g.el([7, 0, 7], [9, 15, 9], "#post"), g.el([6.5, 14.5, 6.5], [9.5, 15, 9.5], "#post")])


def rail_side(kind):
    t = {"particle": "steel", "rail": "steel", "glass": "glass_clear", "kerb": "concrete_light"}
    if kind == "handrail":
        return g.model(t, [g.el([7, 13, 0], [9, 15, 7], "#rail"), g.el([7.5, 7, 0], [8.5, 8.5, 7], "#rail")])
    if kind == "balustrade":
        return g.model(t, [
            g.el([7.25, 2, 0], [8.75, 13, 7], "#glass", faces={"up": None, "down": None, "north": None, "south": None}),
            g.el([6.5, 13, 0], [9.5, 15, 7], "#rail"), g.el([7, 0, 0], [9, 2, 7], "#rail")])
    return g.model(t, [
        g.el([6, 0, 0], [10, 2, 7], "#kerb"), g.el([7, 13, 0], [9, 15, 7], "#rail"), g.el([7.5, 6, 0], [8.5, 7.5, 7], "#rail")])


def utility(axis, kind):
    t = {"particle": "steel", "frame": "steel", "cables": "utility_cables", "pipe": "steel_dark", "clamp": "steel"}
    if axis == "x":
        if kind == "tray":
            return g.model(t, [
                g.el([0, 11, 4], [16, 12, 12], "#frame"), g.el([0, 12, 4], [16, 16, 5.5], "#frame"), g.el([0, 12, 10.5], [16, 16, 12], "#frame"),
                g.el([0, 12, 5.5], [16, 14.5, 10.5], "#cables"), g.el([7, 12, 5.5], [9, 15, 10.5], "#clamp")])
        return g.model(t, [
            g.el([0, 12.5, 4.5], [16, 15.5, 7.5], "#pipe"), g.el([0, 12.5, 8.5], [16, 15.5, 11.5], "#pipe"),
            g.el([7, 11, 4], [9, 16, 12], "#clamp")])
    if kind == "tray":
        return g.model(t, [
            g.el([4, 0, 4], [5.5, 16, 12], "#frame"), g.el([10.5, 0, 4], [12, 16, 12], "#frame"),
            g.el([5.5, 0, 5.5], [10.5, 16, 10.5], "#cables"), g.el([4, 7, 4], [12, 9, 12], "#clamp")])
    return g.model(t, [
        g.el([4.5, 0, 4.5], [7.5, 16, 7.5], "#pipe"), g.el([8.5, 0, 8.5], [11.5, 16, 11.5], "#pipe"),
        g.el([4, 7, 4], [12, 9, 12], "#clamp")])


def deck_light():
    return g.model({"particle": "steel_dark", "body": "steel_dark", "lamp": "lamp_diffuser"}, [
        g.el([1, 14, 6], [15, 16, 10], "#body"), g.el([2, 13, 6.5], [14, 14, 9.5], "#lamp"),
        g.el([0.5, 13.5, 6], [1.5, 16, 10], "#body"), g.el([14.5, 13.5, 6], [15.5, 16, 10], "#body")])


def curve_reach(kind, z):
    """Mirror of ElevatedKinds.CurveKind.reach: platform extent (px from x = 0) at depth z (0 = track side)."""
    if kind == "diagonal":
        return min(16.0, z)
    if kind == "outer":
        return math.sqrt(max(0.0, 256 - (16 - z) ** 2))
    return 16 - math.sqrt(max(0.0, 256 - z * z))


def platform_curve(kind):
    """Same section as the straight edge (paving 12-16 px, face 2 px back), drawn in 1 px strips along the edge line,
    with a granite coping strip on the edge."""
    t = {"particle": "platform_paving_light", "top": "platform_paving_light", "side": "concrete_light", "coping": "coping_side", "face": "concrete_dark"}
    els = []
    for z in range(16):
        reach = round(curve_reach(kind, z + 0.5) * 2) / 2
        if reach <= 0:
            continue
        coping = min(1.5, reach)
        if reach - coping > 0:
            els.append(g.el([0, 12, z], [reach - coping, 16, z + 1], "#side", faces={"up": "#top", "down": "#face", "north": None, "south": None}))
        els.append(g.el([reach - coping, 12, z], [reach, 16, z + 1], "#coping", faces={"north": None, "south": None}))
        if reach > 2:
            els.append(g.el([0, 0, z], [reach - 2, 12, z + 1], "#side", faces={"east": "#face", "north": None, "south": None, "up": None}))
    # close the open ends of the strip stack on the block faces
    return g.model(t, els)


def stair(kind):
    parent = {"": "stairs", "_inner": "inner_stairs", "_outer": "outer_stairs"}[kind]
    return {"parent": f"minecraft:block/{parent}", "textures": {
        "bottom": g.tx("steel_dark"), "top": g.tx("stair_tread"), "side": g.tx("steel_dark")}}


def with_default(first, models):
    """models: {suffix: model}; the unsuffixed model (used by the item) is a copy of the first."""
    out = {"": next(iter(models.values()))}
    out.update(models)
    return out


def blocks():
    b = {}
    b["viaduct_column"] = ("simple", with_default(None, {f"_{s}": column(s) for s in COLUMN}))
    b["viaduct_beam"] = ("simple", with_default(None, {f"_{k}{'_concrete' if c else ''}": beam(k, c) for k in BEAM for c in (False, True)}))
    b["viaduct_brace"] = ("simple", with_default(None, {f"_{k}": brace(k) for k in BRACE}))
    b["station_stair"] = ("simple", {"": stair(""), "_inner": stair("_inner"), "_outer": stair("_outer")})
    b["stair_enclosure"] = ("simple", with_default(None, {f"_{k}": enclosure(k) for k in ENCLOSURE}))
    b["platform_windscreen"] = ("simple", with_default(None, {f"_{k}": windscreen(k) for k in WINDSCREEN}))
    b["platform_fascia"] = ("simple", with_default(None, {f"_{k}": fascia(k) for k in FASCIA}))
    fence = {f"_post_{k}": fence_post(k) for k in FENCE} | {f"_side_{k}": fence_side(k) for k in FENCE}
    b["station_fence"] = ("simple", {"": fence_post("platform")} | fence)
    b["utility_run"] = ("simple", {"": utility("x", "tray")} | {f"_{a}_{k}": utility(a, k) for a in ("x", "y") for k in UTILITY})
    b["deck_light"] = ("axis", {"": deck_light()})
    rail = {"_post": rail_post()} | {f"_side_{k}": rail_side(k) for k in RAIL}
    b["handrail"] = ("simple", {"": rail_post()} | rail)
    b["tactile_junction"] = ("simple", {"": g.cube_bottom_top("wf_guidance_turn", "concrete_light", "concrete_light")}
                             | {f"_{k}": g.cube_bottom_top(f"wf_guidance_{k}", "concrete_light", "concrete_light") for k in JUNCTION})
    b["platform_edge_curve"] = ("simple", with_default(None, {f"_{k}": platform_curve(k) for k in CURVE}))
    return b


def names():
    return {
        "viaduct_column": "Viaduct Support Column",
        "viaduct_beam": "Viaduct Beam",
        "viaduct_brace": "Viaduct Brace",
        "station_stair": "Station Stair",
        "stair_enclosure": "Stair Enclosure Panel",
        "platform_windscreen": "Platform Wind Screen",
        "platform_fascia": "Platform Fascia",
        "station_fence": "Station Fence",
        "utility_run": "Utility Run",
        "deck_light": "Under-Deck Light",
        "handrail": "Handrail and Balustrade",
        "tactile_junction": "Tactile Guidance Junction",
        "platform_edge_curve": "Angled / Curved Platform Edge",
    }


def pretty(value):
    return value.replace("_", " ").capitalize()


def lang():
    lg = {
        TIP + "style_cycle": "Right-click with an empty hand to change the style",
        TIP + "style_cycle_beam": "Right-click with an empty hand to change the beam type; sneak + right-click switches steel / concrete",
        TIP + "railing_joins": "Joins neighbouring railings, fences and walls",
        TIP + "brace_facing": "Rises toward the direction you are facing",
        MSG + "viaduct_beam.steel": "Beam material: steel",
        MSG + "viaduct_beam.concrete": "Beam material: concrete",
    }
    for block, values in (("viaduct_column", COLUMN), ("viaduct_beam", BEAM), ("viaduct_brace", BRACE), ("stair_enclosure", ENCLOSURE),
                          ("platform_windscreen", WINDSCREEN), ("platform_fascia", FASCIA), ("station_fence", FENCE), ("utility_run", UTILITY),
                          ("handrail", RAIL), ("tactile_junction", JUNCTION), ("platform_edge_curve", CURVE)):
        for value in values:
            lg[f"{MSG}{block}.{value}"] = pretty(value)
    return lg


def recipes():
    r = {}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    iron, nugget, pane = "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:glass_pane"
    shapeless("viaduct_column", [iron, iron, "minecraft:stone"], 2)
    shapeless("viaduct_beam", [iron, iron, iron], 3)
    shapeless("viaduct_brace", [iron, nugget, nugget], 2)
    shapeless("station_stair", [iron, iron, nugget], 4)
    shapeless("stair_enclosure", [iron, pane, pane], 2)
    shapeless("platform_windscreen", [pane, pane, pane, nugget], 2)
    shapeless("platform_fascia", ["minecraft:stone", "minecraft:white_dye"], 4)
    shapeless("station_fence", [iron, nugget, nugget, nugget], 4)
    shapeless("utility_run", [iron, "minecraft:redstone", nugget], 4)
    shapeless("deck_light", [iron, "minecraft:glowstone_dust", pane], 2)
    shapeless("handrail", [iron, nugget, pane], 4)
    shapeless("tactile_junction", [f"{MOD}:tactile_guidance_paving", "minecraft:yellow_dye"], 1)
    shapeless("platform_edge_curve", [f"{MOD}:platform_edge"], 1)
    return r


def write_extra(assets, data, write_json):
    ref = lambda name: f"{MOD}:block/{name}"
    states = assets / "blockstates"

    def put(block_id, variants):
        write_json(states / f"{block_id}.json", {"variants": variants})

    def rotated(model, y=0, x=0, uvlock=False):
        out = {"model": model}
        if x:
            out["x"] = x
        if y:
            out["y"] = y
        if uvlock:
            out["uvlock"] = True
        return out

    put("viaduct_column", {f"style={s}": {"model": ref(f"viaduct_column_{s}")} for s in COLUMN})
    variants = {}
    for k in BEAM:
        for c in (False, True):
            model = ref(f"viaduct_beam_{k}{'_concrete' if c else ''}")
            variants[f"axis=x,kind={k},concrete={str(c).lower()}"] = {"model": model}
            variants[f"axis=z,kind={k},concrete={str(c).lower()}"] = {"model": model, "y": 90}
    put("viaduct_beam", variants)
    for block, kinds in (("viaduct_brace", BRACE), ("stair_enclosure", ENCLOSURE), ("platform_windscreen", WINDSCREEN), ("platform_fascia", FASCIA),
                         ("tactile_junction", JUNCTION), ("platform_edge_curve", CURVE)):
        put(block, {f"facing={f},kind={k}": {"model": ref(f"{block}_{k}")} | ({"y": y} if y else {}) for k in kinds for f, y in g.FACING_Y.items()})

    # utility run: x/z share the x model (z turned), y has its own
    variants = {}
    for k in UTILITY:
        variants[f"axis=x,kind={k}"] = {"model": ref(f"utility_run_x_{k}")}
        variants[f"axis=z,kind={k}"] = {"model": ref(f"utility_run_x_{k}"), "y": 90}
        variants[f"axis=y,kind={k}"] = {"model": ref(f"utility_run_y_{k}")}
    put("utility_run", variants)

    # connecting railings: a post per kind plus an arm toward each connected side
    def multipart(block_id, kinds, post_name, side_name):
        parts = []
        for k in kinds:
            parts.append({"when": {"kind": k}, "apply": {"model": ref(post_name(k))}})
            for direction, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                parts.append({"when": {"AND": [{"kind": k}, {direction: "true"}]}, "apply": {"model": ref(side_name(k))} | ({"y": y} if y else {})})
        write_json(states / f"{block_id}.json", {"multipart": parts})

    multipart("station_fence", FENCE, lambda k: f"station_fence_post_{k}", lambda k: f"station_fence_side_{k}")
    multipart("handrail", RAIL, lambda k: "handrail_post", lambda k: f"handrail_side_{k}")

    # station stair: vanilla stair state mapping
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y in rot.items():
        for half in ("bottom", "top"):
            x = 180 if half == "top" else 0
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = ref("station_stair" + ("" if shape == "straight" else "_inner" if shape.startswith("inner") else "_outer"))
                if half == "bottom":
                    yy = y if shape in ("straight", "inner_right", "outer_right") else (y - 90) % 360
                else:
                    yy = y if shape in ("straight", "inner_left", "outer_left") else (y + 90) % 360
                variants[f"facing={facing},half={half},shape={shape}"] = rotated(model, yy, x, uvlock=bool(yy or x))
    put("station_stair", variants)
