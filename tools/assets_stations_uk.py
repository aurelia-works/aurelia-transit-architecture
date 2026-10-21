"""British suburban stations (Enviro's play-test, 2026-10-20): the materials of three real stations studied in
docs/research: Teddington (1863, LSWR: yellow London stock brick with red brick dressings, timber canopies with a sawtooth
valance), Surbiton (1937, art deco: white render, steel windows, cantilevered concrete slab canopies) and Coventry (1962,
modernist: blue engineering brick, glazed booking hall, flat canopies with deep fascias and hardwood undersides), plus the
tarmac platforms and white-and-yellow coping every British platform has.

Same extension interface as the other assets_*.py modules. Textures are 16x16 like the rest of the mod.
"""
import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."

STOCK = (196, 170, 112)       # London stock: yellow-buff, sooted in places
RED_DRESSING = (156, 64, 46)  # rubbed red brick round windows and in bands
ENGINEERING = (62, 68, 86)    # Staffordshire blue, slightly glossy
RENDER = (234, 232, 222)      # 1930s white render
COPING = (184, 182, 174)      # precast coping slab
TARMAC = (70, 70, 72)
CANOPY_GREEN = (52, 86, 62)   # Southern Railway green
CANOPY_CREAM = (226, 214, 176)
HARDWOOD = (138, 88, 54)
FASCIA_DARK = (46, 50, 56)


def put(img, x, y, color, alpha=255):
    img.load()[x % 16, y % 16] = tuple(color[:3]) + (alpha,)


def get(img, x, y):
    return img.load()[x % 16, y % 16]


def brick(palette, mortar, name, soot=0.0, flecks=0.0):
    """Running bond: 3 px courses over 1 px mortar, bricks 7 px; each brick its own colour, some sooted or flecked."""
    img = g.new()
    r = g.rng(name)
    for row in range(4):
        y0 = row * 4
        offset = 0 if row % 2 == 0 else 4
        for sx in range(-offset, 16, 8):
            base = r.choice(palette)
            if r.random() < soot:
                base = g.shade(base, -34)
            for y in range(y0 + 1, y0 + 4):
                for x in range(sx + 1, sx + 8):
                    c = g.shade(base, r.randint(-6, 6))
                    if y == y0 + 1:
                        c = g.shade(c, 7)
                    if r.random() < flecks:
                        c = g.shade(c, -40)
                    put(img, x, y, c)
        for x in range(16):
            put(img, x, y0, g.shade(mortar, r.randint(-4, 4)))
        for sx in range(-offset, 16, 8):
            for y in range(y0 + 1, y0 + 4):
                put(img, sx, y, g.shade(mortar, r.randint(-4, 4)))
    return img


def coping(name, tactile):
    """A platform's edge slab seen from above: the white-painted nosing on the track side (north), the yellow line behind it,
    and on the tactile one the buff blister paving behind that (offset rows of round studs)."""
    img = g.noisy(COPING, 5, name)
    r = g.rng(name + "-line")
    for x in range(16):
        for y in (0, 1):
            put(img, x, y, g.shade((238, 238, 232), r.randint(-3, 3)))
        put(img, x, 2, g.shade(COPING, -18))
        for y in (5, 6):
            put(img, x, y, g.shade((232, 196, 40), r.randint(-6, 6)))
    if tactile:
        for y in range(8, 16):
            for x in range(16):
                put(img, x, y, g.shade((206, 184, 140), r.randint(-4, 4)))
        for y in (9, 12, 15):
            for x in range(1 if y == 12 else 0, 16, 3):
                put(img, x, y, g.shade((226, 206, 160), 6))
                put(img, x, y - 1, g.shade((226, 206, 160), 14))
    return img


def textures():
    t = {}
    t["uk_yellow_stock_brick"] = brick([STOCK, g.shade(STOCK, -12), g.shade(STOCK, 8), (182, 150, 98)], (214, 206, 186),
                                       "uk_stock", soot=0.12, flecks=0.06)
    t["uk_red_brick_dressing"] = brick([RED_DRESSING, g.shade(RED_DRESSING, 10), g.shade(RED_DRESSING, -8)], (206, 196, 178), "uk_red")
    blue = brick([ENGINEERING, g.shade(ENGINEERING, -8), g.shade(ENGINEERING, 10), (74, 70, 92)], (150, 150, 146), "uk_blue")
    for x in range(16):  # the glaze catches the light along each course
        for row in range(4):
            put(blue, x, row * 4 + 1, g.shade(get(blue, x, row * 4 + 1), 12))
    t["uk_blue_engineering_brick"] = blue

    render = g.noisy(RENDER, 3, "uk_render")
    r = g.rng("uk_render_patch")
    for _ in range(6):
        x, y = r.randint(0, 15), r.randint(0, 15)
        put(render, x, y, g.shade(RENDER, -9))
    for x in range(16):  # a faint scored joint every block, as 1930s render was lined out
        put(render, x, 15, g.shade(RENDER, -12))
    t["uk_white_render"] = render

    # steel (Crittall) windows: thin black glazing bars in small panes, horizontal emphasis
    win = g.new()
    for y in range(16):
        for x in range(16):
            frame = x in (0, 15) or y in (0, 15) or y in (5, 10) or x == 8
            put(win, x, y, (34, 36, 38) if frame else (168, 196, 204), 255 if frame else 120)
    t["uk_steel_window"] = win

    t["uk_platform_tarmac"] = g.noisy(TARMAC, 6, "uk_tarmac")
    r = g.rng("uk_tarmac_stones")
    for _ in range(22):
        put(t["uk_platform_tarmac"], r.randint(0, 15), r.randint(0, 15), g.shade(TARMAC, r.choice((-14, 18, 26))))
    t["uk_platform_coping_top"] = coping("uk_coping", False)
    t["uk_platform_coping_tactile_top"] = coping("uk_coping_t", True)
    t["uk_platform_coping_side"] = g.noisy(g.shade(COPING, -10), 4, "uk_coping_side")

    # canopy boards: painted tongue-and-groove planks (Teddington), board-marked white concrete (Surbiton), hardwood (Coventry)
    planks = g.noisy(CANOPY_CREAM, 3, "uk_planks")
    for x in range(0, 16, 4):
        for y in range(16):
            put(planks, x, y, g.shade(CANOPY_CREAM, -26))
    t["uk_timber_canopy_under"] = planks
    t["uk_canopy_lead"] = g.noisy((96, 100, 104), 4, "uk_lead")
    t["uk_canopy_green"] = g.noisy(CANOPY_GREEN, 3, "uk_green")
    slab = g.noisy(g.shade(RENDER, -6), 3, "uk_slab")
    for x in range(16):
        put(slab, x, 7, g.shade(RENDER, -16))
    t["uk_concrete_canopy"] = slab
    wood = g.noisy(HARDWOOD, 4, "uk_hardwood")
    for y in range(0, 16, 2):
        for x in range(16):
            if g.rng(f"uk_grain{y}").random() < 0.5:
                put(wood, x, y, g.shade(get(wood, x, y), -12))
    t["uk_hardwood_under"] = wood
    t["uk_fascia_dark"] = g.noisy(FASCIA_DARK, 3, "uk_fascia")
    return t


def cube_all(tex):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": g.tx(tex)}}


def coping_slab(top):
    return g.model({"particle": "uk_platform_coping_side", "top": top, "side": "uk_platform_coping_side"}, [
        g.el([0, 0, 0], [16, 16, 16], "#side", faces={"up": "#top"}),
    ])


def canopy_plate(top, under, thickness=2):
    """A canopy roof plate at the bottom of its cell, like the steel canopy_flat."""
    return g.model({"particle": under, "top": top, "under": under, "side": under}, [
        g.el([0, 0, 0], [16, thickness, 16], "#side", faces={"up": "#top", "down": "#under"}),
    ])


def valance():
    """Teddington's canopy edge: the roof plate, then a painted board hanging off the front (north) with a row of teeth
    below it, four to a block."""
    tex = {"particle": "uk_canopy_green", "top": "uk_canopy_lead", "under": "uk_timber_canopy_under", "paint": "uk_canopy_green"}
    els = [g.el([0, 0, 1.5], [16, 2, 16], "#paint", faces={"up": "#top", "down": "#under"}),
           g.el([0, -2, 0], [16, 3, 1.5], "#paint")]
    for i in range(4):  # within the six-element budget: one narrow tooth per quarter
        els.append(g.el([i * 4 + 1, -5, 0], [i * 4 + 3, -2, 1.5], "#paint"))
    return g.model(tex, els)


def deep_fascia(under):
    """Coventry's and Surbiton's canopy edge: the roof plate behind a deep plain fascia (north), 8 px from the plate down."""
    tex = {"particle": "uk_fascia_dark", "top": "uk_fascia_dark", "under": under, "fascia": "uk_fascia_dark"}
    return g.model(tex, [
        g.el([0, 0, 2], [16, 2, 16], "#fascia", faces={"down": "#under"}),
        g.el([0, -6, 0], [16, 4, 2], "#fascia"),
    ])


BLOCKS = {
    "uk_yellow_stock_brick": "Yellow London Stock Brick",
    "uk_red_brick_dressing": "Red Brick Dressing",
    "uk_blue_engineering_brick": "Blue Engineering Brick",
    "uk_white_render": "White Art Deco Render",
    "uk_steel_window": "Steel Frame Window",
    "uk_platform_tarmac": "British Platform Tarmac",
    "uk_platform_coping": "British Platform Coping",
    "uk_platform_coping_tactile": "British Platform Coping with Tactile Paving",
    "uk_timber_canopy": "Victorian Timber Canopy",
    "uk_canopy_valance": "Victorian Canopy Valance",
    "uk_concrete_canopy": "Art Deco Concrete Canopy",
    "uk_concrete_canopy_edge": "Art Deco Concrete Canopy Edge",
    "uk_hardwood_canopy": "Modernist Hardwood Canopy",
    "uk_hardwood_canopy_fascia": "Modernist Canopy Fascia",
}


def blocks():
    b = {}
    for bid in ("uk_yellow_stock_brick", "uk_red_brick_dressing", "uk_blue_engineering_brick", "uk_white_render", "uk_steel_window",
                "uk_platform_tarmac"):
        b[bid] = ("simple", {"": cube_all(bid)})
    b["uk_platform_coping"] = ("facing", {"": coping_slab("uk_platform_coping_top")})
    b["uk_platform_coping_tactile"] = ("facing", {"": coping_slab("uk_platform_coping_tactile_top")})
    b["uk_timber_canopy"] = ("simple", {"": canopy_plate("uk_canopy_lead", "uk_timber_canopy_under")})
    b["uk_canopy_valance"] = ("facing", {"": valance()})
    b["uk_concrete_canopy"] = ("simple", {"": canopy_plate("uk_concrete_canopy", "uk_concrete_canopy", 4)})
    b["uk_concrete_canopy_edge"] = ("facing", {"": g.model({"particle": "uk_concrete_canopy", "c": "uk_concrete_canopy"}, [
        g.el([0, 0, 2], [16, 4, 16], "#c"), g.el([0, -2, 0], [16, 5, 2], "#c")])})
    b["uk_hardwood_canopy"] = ("simple", {"": canopy_plate("uk_canopy_lead", "uk_hardwood_under")})
    b["uk_hardwood_canopy_fascia"] = ("facing", {"": deep_fascia("uk_hardwood_under")})
    return b


def names():
    return dict(BLOCKS)


def recipes():
    r = {}

    def cut(result, ingredient, count=1):
        r[f"{result}_from_{ingredient.split(':')[-1]}_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": g.item(ingredient), "result": f"{MOD}:{result}", "count": count}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    for result in ("uk_yellow_stock_brick", "uk_red_brick_dressing", "uk_blue_engineering_brick"):
        cut(result, "minecraft:bricks")
    cut("uk_white_render", "minecraft:white_concrete")
    cut("uk_concrete_canopy", "minecraft:white_concrete", 2)
    cut("uk_concrete_canopy_edge", "minecraft:white_concrete", 2)
    cut("uk_platform_tarmac", "minecraft:smooth_basalt")
    cut("uk_platform_coping", "minecraft:smooth_stone")
    shapeless("uk_platform_coping_tactile", [f"{MOD}:uk_platform_coping", "minecraft:yellow_dye"])
    shapeless("uk_steel_window", ["minecraft:glass", "minecraft:iron_nugget"], 2)
    shapeless("uk_timber_canopy", ["minecraft:iron_ingot", "minecraft:oak_slab"], 4)
    shapeless("uk_canopy_valance", [f"{MOD}:uk_timber_canopy", "minecraft:green_dye"])
    shapeless("uk_hardwood_canopy", ["minecraft:iron_ingot", "minecraft:dark_oak_slab"], 4)
    shapeless("uk_hardwood_canopy_fascia", [f"{MOD}:uk_hardwood_canopy", "minecraft:black_dye"])
    return r
