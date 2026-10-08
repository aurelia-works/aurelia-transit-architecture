"""1.5 European stations, German and Italian set: Frankfurt, Munich and Hamburg main stations, a small S-Bahn station
and Roma Termini.

Same extension interface as the other assets_*.py modules. Textures are 16x16 like the rest of the mod. All art is original
and generic: no operator logos or lettering (the S-Bahn sign only borrows the colours white and black).
"""
import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."

SANDSTONE = (192, 146, 116)       # Frankfurt: warm red-beige Main sandstone
CLINKER = (86, 54, 48)            # Hamburg: dark, partly burnt Backstein
SBAHN_BRICK = (148, 66, 50)       # small station: red clinker
CONCRETE = (152, 152, 148)
TRAVERTINE = (222, 208, 178)
PAINT_GREEN = (92, 110, 100)      # Frankfurt hall ironwork, grey-green
PAINT_BLUE = (74, 84, 94)         # Hamburg hall ironwork, dark blue-grey


def px(img):
    return img.load()


def put(img, x, y, color, alpha=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.load()[x, y] = tuple(color[:3]) + (alpha,)


def get(img, x, y):
    return img.load()[x % 16, y % 16][:3]


def sandstone_grain(img, r, strength=5):
    """Faint horizontal bedding lines, as in sedimentary stone."""
    for y in range(16):
        if r.random() < 0.35:
            d = r.randint(-strength, strength)
            for x in range(16):
                put(img, x, y, g.shade(get(img, x, y), d))


def ashlar(base, joint, name, course=4, chamfer=False, rough=3):
    """Dressed stone courses with staggered vertical joints, a tone per stone and an optional bevelled (rusticated) face."""
    img = g.noisy(base, rough, name)
    r = g.rng(name + "-courses")
    for row in range(16 // course):
        y0 = row * course
        offset = 0 if row % 2 == 0 else 4
        for start in (offset, offset + 8):
            tone = r.randint(-9, 9)
            for y in range(y0, y0 + course):
                for dx in range(8):
                    x = (start + dx) % 16
                    c = g.shade(get(img, x, y), tone)
                    if chamfer and y == y0 + 1:
                        c = g.shade(c, 12)
                    elif chamfer and y == y0 + course - 1:
                        c = g.shade(c, -9)
                    if y == y0 or dx == 0:
                        c = g.shade(joint, r.randint(-3, 3))
                    put(img, x, y, c)
    return img


def clinker(palette, mortar, name):
    """Running-bond brick, 3 px courses with a 1 px mortar line, bricks 7 px; each brick its own colour."""
    img = g.new()
    r = g.rng(name)
    for row in range(4):
        y0 = row * 4
        offset = 0 if row % 2 == 0 else 4
        for sx in range(-offset, 16, 8):
            base = r.choice(palette)
            for y in range(y0 + 1, y0 + 4):
                for x in range(sx + 1, sx + 8):
                    c = g.shade(base, r.randint(-7, 7))
                    if y == y0 + 1:
                        c = g.shade(c, 8)
                    if r.random() < 0.05:
                        c = g.shade(c, -22)
                    put(img, x % 16, y, c)
        for x in range(16):
            put(img, x, y0, g.shade(mortar, r.randint(-5, 5)))
        for sx in range(-offset, 16, 8):
            for y in range(y0 + 1, y0 + 4):
                put(img, sx % 16, y, g.shade(mortar, r.randint(-5, 5)))
    return img


def concrete_base(color, name, amount=4):
    img = g.noisy(color, amount, name)
    r = g.rng(name + "-cloud")
    for _ in range(5):
        cx, cy = r.randint(0, 15), r.randint(0, 15)
        d = r.randint(-6, 6)
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                if abs(dx) + abs(dy) <= 4:
                    put(img, (cx + dx) % 16, (cy + dy) % 16, g.shade(get(img, cx + dx, cy + dy), d))
    return img


def textures():
    t = {}

    # ---- Frankfurt: sandstone ----------------------------------------------------------------------------------
    stone = ashlar(SANDSTONE, g.shade(SANDSTONE, -42), "frankfurt_ashlar")
    sandstone_grain(stone, g.rng("frankfurt_grain"), 6)
    t["frankfurt_sandstone_ashlar"] = stone

    rustic = ashlar(g.shade(SANDSTONE, -6), g.shade(SANDSTONE, -62), "frankfurt_rusticated", course=8, chamfer=True, rough=9)
    r = g.rng("frankfurt_rustic_pits")
    for _ in range(18):
        put(rustic, r.randint(0, 15), r.randint(0, 15), g.shade(SANDSTONE, -34 - r.randint(0, 14)))
    t["frankfurt_sandstone_rusticated"] = rustic

    top = g.noisy(g.shade(SANDSTONE, 6), 4, "frankfurt_top")
    sandstone_grain(top, g.rng("frankfurt_top_grain"), 4)
    t["frankfurt_sandstone_top"] = top

    # ---- Munich: concrete and terrazzo ---------------------------------------------------------------------------
    exposed = concrete_base(CONCRETE, "munich_exposed")
    r = g.rng("munich_pores")
    for _ in range(9):  # air holes
        put(exposed, r.randint(0, 15), r.randint(0, 15), g.shade(CONCRETE, -38))
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):  # form-tie holes
        put(exposed, x, y, g.shade(CONCRETE, -52))
        put(exposed, x + 1, y, g.shade(CONCRETE, -30))
        put(exposed, x, y + 1, g.shade(CONCRETE, -30))
    for x in range(16):  # faint panel joint
        put(exposed, x, 15, g.shade(get(exposed, x, 15), -16))
    for y in range(16):
        put(exposed, 15, y, g.shade(get(exposed, 15, y), -14))
    t["munich_exposed_concrete"] = exposed

    boards = g.noisy(CONCRETE, 3, "munich_boards")
    r = g.rng("munich_boardgrain")
    for row in range(4):  # 4 px boards with timber grain imprinted on the concrete
        tone = r.randint(-7, 7)
        for y in range(row * 4, row * 4 + 4):
            for x in range(16):
                c = g.shade(get(boards, x, y), tone)
                if y == row * 4:
                    c = g.shade(c, -22)
                elif r.random() < 0.18:
                    c = g.shade(c, r.choice((-9, 9)))
                put(boards, x, y, c)
        put(boards, r.randint(0, 15), row * 4 + 2, g.shade(CONCRETE, -26))
    t["munich_board_formed_concrete"] = boards

    ter = g.noisy((206, 200, 188), 3, "munich_terrazzo")
    r = g.rng("munich_chips")
    chips = [(250, 248, 242), (118, 118, 120), (176, 132, 92), (150, 70, 62), (232, 224, 205), (90, 98, 96)]
    for _ in range(46):
        x, y = r.randint(0, 15), r.randint(0, 15)
        c = r.choice(chips)
        put(ter, x, y, g.shade(c, r.randint(-8, 8)))
        if r.random() < 0.4:
            put(ter, x + 1, y, g.shade(c, -14))
    for i in range(16):  # brass divider strips at the tile edge
        put(ter, i, 0, (176, 150, 88))
        put(ter, 0, i, (176, 150, 88))
    t["munich_terrazzo_floor"] = ter

    deck = g.brushed((176, 180, 182), "munich_deck", vertical=False)
    t["munich_roof_deck"] = deck

    # ---- Hamburg: clinker, stone trim ----------------------------------------------------------------------------
    hh = [(86, 54, 48), (74, 46, 44), (102, 62, 50), (62, 48, 54), (94, 58, 44), (70, 56, 60)]
    t["hamburg_clinker_brick"] = clinker(hh, (146, 140, 130), "hamburg_clinker")
    trim_side = clinker(hh, (146, 140, 130), "hamburg_clinker")
    stone = g.noisy((190, 178, 150), 3, "hamburg_trim")
    for y in range(16):
        for x in range(16):
            if 6 <= y <= 9 or y == 0:
                c = get(stone, x, y)
                if y == 6 or y == 0:
                    c = g.shade(c, 10)
                if y == 9:
                    c = g.shade(c, -24)
                put(trim_side, x, y, c)
    t["hamburg_clinker_stone_trim"] = trim_side
    t["hamburg_stone_trim"] = ashlar((190, 178, 150), (140, 128, 104), "hamburg_trim_block", course=8, rough=4)

    # ---- S-Bahn: clinker, platform --------------------------------------------------------------------------------
    sb = [(148, 66, 50), (134, 58, 46), (160, 76, 56), (120, 56, 50), (144, 70, 44)]
    t["sbahn_clinker_brick"] = clinker(sb, (176, 168, 156), "sbahn_clinker")

    slab = concrete_base((142, 144, 144), "sbahn_slab")
    for x in range(16):
        put(slab, x, 0, g.shade(get(slab, x, 0), -22))
    for y in range(16):
        put(slab, 0, y, g.shade(get(slab, 0, y), -22))
    t["sbahn_platform_slab"] = slab

    def edge(tactile):
        img = concrete_base((142, 144, 144), "sbahn_edge_t" if tactile else "sbahn_edge")
        for y in range(0, 2):  # white safety line along the edge (north side)
            for x in range(16):
                put(img, x, y, g.shade((232, 232, 226), g.rng(f"edge{x}{y}").randint(-4, 4)))
        if tactile:
            for y in range(3, 9):
                for x in range(16):
                    c = (206, 206, 200)
                    if x % 3 == 0:
                        c = g.shade(c, 18)
                    elif x % 3 == 2:
                        c = g.shade(c, -30)
                    put(img, x, y, c)
        return img

    t["sbahn_platform_edge_top"] = edge(False)
    t["sbahn_platform_edge_tactile_top"] = edge(True)

    sign = g.noisy((238, 238, 234), 2, "sbahn_sign")
    for i in range(16):
        for a, b in ((i, 0), (i, 15), (0, i), (15, i)):
            put(sign, a, b, (92, 94, 98))
    for y in range(5, 11):  # text field, left empty
        for x in range(2, 14):
            put(sign, x, y, (24, 24, 26))
    t["sbahn_sign_face"] = sign

    t["sbahn_lamp_head"] = g.noisy((255, 244, 214), 5, "sbahn_lamp_head")

    # ---- Roma: travertine, polished floor, concrete -----------------------------------------------------------------
    trav = g.noisy(TRAVERTINE, 3, "roma_travertine")
    r = g.rng("roma_bands")
    for y in range(16):
        if y % 4 == 1 or r.random() < 0.2:
            d = r.randint(-9, 7)
            for x in range(16):
                put(trav, x, y, g.shade(get(trav, x, y), d))
    for _ in range(14):  # horizontal pores with a lit lower lip
        x, y = r.randint(0, 14), r.randint(1, 14)
        w = r.choice((1, 2, 2, 3))
        for i in range(w):
            put(trav, x + i, y, g.shade(TRAVERTINE, -52))
            put(trav, x + i, y + 1, g.shade(TRAVERTINE, 12))
    for x in range(16):  # slab joints: 16x8 panels
        put(trav, x, 7, g.shade(TRAVERTINE, -34))
    for y in range(16):
        put(trav, 0, y, g.shade(TRAVERTINE, -30))
    t["roma_travertine"] = trav

    floor = g.noisy((196, 184, 164), 2, "roma_floor")
    r = g.rng("roma_veins")
    for _ in range(3):  # soft veining
        x, y = r.randint(0, 15), r.randint(0, 15)
        for i in range(r.randint(5, 9)):
            put(floor, (x + i) % 16, (y + i // 2) % 16, g.shade((196, 184, 164), -20))
    for i in range(16):  # polished sheen on a diagonal
        put(floor, i, (15 - i), g.shade(get(floor, i, 15 - i), 16))
        put(floor, (i + 1) % 16, 15 - i, g.shade(get(floor, i + 1, 15 - i), 9))
    for i in range(16):
        put(floor, i, 0, (150, 138, 118))
        put(floor, 0, i, (150, 138, 118))
    t["roma_polished_floor"] = floor

    t["roma_concrete"] = concrete_base((208, 205, 196), "roma_concrete", 3)

    # ---- Iron train-shed trusses (cutout) -------------------------------------------------------------------------------
    t["frankfurt_truss"] = truss("frankfurt_truss", PAINT_GREEN, "cross")
    t["hamburg_truss"] = truss("hamburg_truss", PAINT_BLUE, "warren")
    t["frankfurt_truss_leg"] = truss_leg("frankfurt_truss_leg", PAINT_GREEN)
    t["frankfurt_truss_paint"] = rusted(g.noisy(PAINT_GREEN, 4, "frankfurt_paint"), PAINT_GREEN)
    t["hamburg_truss_paint"] = rusted(g.noisy(PAINT_BLUE, 4, "hamburg_paint"), PAINT_BLUE)

    # ---- Glazing ---------------------------------------------------------------------------------------------------------
    sky = g.glass("frankfurt_skylight", tint=(158, 184, 190), alpha=112)
    t["frankfurt_skylight_glass"] = sky
    roof = g.glass("hamburg_roof", tint=(150, 172, 180), alpha=116)
    for y in range(16):  # glazing bars every 8 px
        put(roof, 0, y, g.shade(PAINT_BLUE, -6))
        put(roof, 8, y, g.shade(PAINT_BLUE, -6))
    t["hamburg_roof_glass"] = roof
    return t


def rusted(img, color):
    """Paint wear: slightly lighter wear at random edges, a few darker rust-brown flecks."""
    r = g.rng("wear" + str(color))
    for _ in range(6):
        put(img, r.randint(0, 15), r.randint(0, 15), (112, 82, 62))
    return img


def truss(name, color, pattern):
    """Span truss seen from the side: riveted chords top and bottom (3 px), lattice between (cutout)."""
    img = g.new()
    r = g.rng(name)
    for y in list(range(0, 3)) + list(range(13, 16)):
        for x in range(16):
            c = g.shade(color, r.randint(-5, 5))
            if y in (0, 13):
                c = g.shade(c, 14)
            if y in (2, 15):
                c = g.shade(c, -16)
            put(img, x, y, c)
    for x in range(1, 16, 3):  # rivet heads on the chord flanges
        put(img, x, 1, g.shade(color, 36))
        put(img, x, 14, g.shade(color, 36))
    lines = []

    def seg(x1, y1, x2, y2):
        n = max(abs(x2 - x1), abs(y2 - y1))
        for k in range(n + 1):
            lines.append((x1 + round((x2 - x1) * k / n), y1 + round((y2 - y1) * k / n)))

    if pattern == "cross":
        for x0 in (0, 8):
            seg(x0, 3, x0 + 7, 12)
            seg(x0, 12, x0 + 7, 3)
        verticals = (0, 7, 8, 15)
    else:
        seg(0, 3, 4, 12)
        seg(4, 12, 8, 3)
        seg(8, 3, 12, 12)
        seg(12, 12, 15, 3)
        verticals = (0, 15)
    for x, y in lines:
        put(img, x, y, g.shade(color, r.randint(-8, 6)))
    for x in verticals:
        for y in range(3, 13):
            put(img, x, y, g.shade(color, r.randint(-6, 4)))
    for x, y in ((0, 4), (0, 11), (7, 7), (8, 7), (15, 4), (15, 11), (4, 5), (12, 5)):  # gusset rivets
        if img.load()[x, y][3]:
            put(img, x, y, g.shade(color, 38))
    return img


def truss_leg(name, color):
    """Vertical truss leg: two riveted chords (x 3-4 and 11-12) with lattice panels between, symmetric left-right."""
    img = g.new()
    r = g.rng(name)
    for x in (3, 4, 11, 12):
        for y in range(16):
            c = g.shade(color, r.randint(-5, 5))
            if x in (3, 11):
                c = g.shade(c, 14)
            else:
                c = g.shade(c, -16)
            put(img, x, y, c)
    for y in range(1, 16, 3):
        put(img, 3, y, g.shade(color, 38))
        put(img, 12, y, g.shade(color, 38))
    for panel in (0, 8):  # X bracing between the chords, 5..10
        for i in range(8):
            x1 = 5 + i * 5 // 8
            x2 = 10 - i * 5 // 8
            put(img, x1, panel + i, g.shade(color, r.randint(-8, 6)))
            put(img, x2, panel + i, g.shade(color, r.randint(-8, 6)))
    for y in (0, 8):
        for x in range(5, 11):
            put(img, x, y, g.shade(color, -4))
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------------------------------
def cube_all(tex):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": g.tx(tex)}}


def truss_span(tex, paint, rot=None, along_z=False):
    """Chord boxes (side faces use the flange rows of the lattice texture) and a 1 px lattice plane."""
    if along_z:
        lo, hi, mid = [6.5, 0, 0], [9.5, 16, 16], ([7.5, 0, 0], [8.5, 16, 16])
        sides = ("east", "west")
    else:
        lo, hi, mid = [0, 0, 6.5], [16, 16, 9.5], ([0, 0, 7.5], [16, 16, 8.5])
        sides = ("north", "south")
    flat = {f: None for f in ("north", "south", "east", "west", "up", "down")}
    chord_faces = {f: "#paint" for f in flat} | {s: "#truss" for s in sides}
    els = []
    for y1, y2 in ((13, 16), (0, 3)):
        a, b = list(lo), list(hi)
        a[1], b[1] = y1, y2
        els.append(g.el(a, b, "#paint", faces=chord_faces, rot=rot))
    web_faces = flat | {s: "#truss" for s in sides}
    els.append(g.el(mid[0], mid[1], "#truss", faces=web_faces, rot=rot))
    return g.model({"particle": paint, "truss": tex, "paint": paint}, els)


def truss_leg_model(tex, paint):
    flat = {f: None for f in ("north", "south", "east", "west", "up", "down")}
    chord = lambda x1, x2: g.el([x1, 0, 6.5], [x2, 16, 9.5], "#paint", faces={f: "#paint" for f in flat} | {"north": "#truss", "south": "#truss"})
    web = g.el([5, 0, 7.5], [11, 16, 8.5], "#truss", faces=flat | {"north": "#truss", "south": "#truss"})
    return g.model({"particle": paint, "truss": tex, "paint": paint}, [chord(3, 5), chord(11, 13), web])


def cornice():
    side = "frankfurt_sandstone_ashlar"
    return g.model({"particle": side, "side": side, "top": "frankfurt_sandstone_top"}, [
        g.el([0, 0, 3], [16, 8, 16], "#side", faces={"up": None}),
        g.el([0, 8, 1.5], [16, 12, 16], "#side", faces={"up": None}),
        g.el([0, 12, 0], [16, 16, 16], "#side", faces={"up": "#top", "down": "#top"}),
    ])


def ridge_skylight():
    tex = {"particle": "steel_dark", "frame": "steel_dark", "glass": "frankfurt_skylight_glass"}
    no_sides = {"north": None, "south": None}
    return g.model(tex, [
        g.el([0, 0, 0], [1.5, 2.7, 16], "#frame"),
        g.el([14.5, 0, 0], [16, 2.7, 16], "#frame"),
        g.el([0.12, 2.65, 0], [8.12, 3.15, 16], "#glass", faces=no_sides, rot=("z", 22.5, [4, 2.9, 8])),
        g.el([7.88, 2.65, 0], [15.88, 3.15, 16], "#glass", faces=no_sides, rot=("z", -22.5, [12, 2.9, 8])),
        g.el([6.8, 5.4, 0], [9.2, 6.4, 16], "#frame"),
    ])


def roof_deck():
    tex = {"particle": "munich_roof_deck", "deck": "munich_roof_deck"}
    els = [g.el([0, 12, 0], [16, 13.5, 16], "#deck")]
    for x in (1, 5, 9, 13):
        els.append(g.el([x, 13.5, 0], [x + 2, 16, 16], "#deck", faces={"up": ("#deck", [0, 0, 16, 2])}))
    return g.model(tex, els)


def hamburg_glass_roof():
    tex = {"particle": "steel_dark", "glass": "hamburg_roof_glass", "frame": "steel_dark"}
    return g.model(tex, [
        g.el([0, 12.5, 0], [1, 14.5, 16], "#frame"),
        g.el([15, 12.5, 0], [16, 14.5, 16], "#frame"),
        g.el([1, 13.5, 0], [15, 14, 16], "#glass", faces={"north": None, "south": None, "east": None, "west": None}),
    ])


def edge_slab(top):
    return g.model({"particle": "sbahn_platform_slab", "top": top, "side": "sbahn_platform_slab"}, [
        g.el([0, 0, 0], [16, 16, 16], "#side", faces={"up": "#top"}),
    ])


def shelter():
    tex = {"particle": "steel_dark", "frame": "steel_dark", "glass": "glass_clear"}
    return g.model(tex, [
        g.el([0, 14.5, 0], [16, 16, 16], "#frame"),
        g.el([0, 0, 13], [1.5, 14.5, 15.5], "#frame"),
        g.el([14.5, 0, 13], [16, 14.5, 15.5], "#frame"),
        g.el([1.5, 0.5, 14], [14.5, 14.5, 14.75], "#glass", faces={"up": None, "down": None, "east": None, "west": None}),
    ])


def station_sign():
    tex = {"particle": "steel_dark", "frame": "steel_dark", "face": "sbahn_sign_face"}
    return g.model(tex, [
        g.el([0, 7, 7], [16, 13, 9], "#frame", faces={"north": "#face", "south": "#face"}),
        g.el([1, 0, 7.5], [2.5, 7, 8.5], "#frame"),
        g.el([13.5, 0, 7.5], [15, 7, 8.5], "#frame"),
    ])


def lamp():
    tex = {"particle": "steel_dark", "pole": "steel_dark", "lamp": "sbahn_lamp_head"}
    return g.model(tex, [
        g.el([7.25, 0, 7.25], [8.75, 13, 8.75], "#pole"),
        g.el([5.5, 13, 5.5], [10.5, 14, 10.5], "#pole"),
        g.el([6, 14, 6], [10, 16, 10], "#lamp"),
    ])


def ribbon_window():
    tex = {"particle": "roma_concrete", "concrete": "roma_concrete", "glass": "glass_clear", "frame": "steel"}
    return g.model(tex, [
        g.el([0, 0, 6], [16, 3, 10], "#concrete"),
        g.el([0, 13, 6], [16, 16, 10], "#concrete"),
        g.el([0, 3, 7.5], [16, 13, 8.5], "#glass", faces={"up": None, "down": None, "east": None, "west": None}),
        g.el([0, 3, 7], [0.75, 13, 9], "#frame", faces={"west": None}),
        g.el([15.25, 3, 7], [16, 13, 9], "#frame", faces={"east": None}),
    ])


def canopy_edge():
    """The cantilevered 'dinosaur' fascia: a flat roof slab whose front beam hangs down in a wave, drawn as four steps."""
    tex = {"particle": "roma_concrete", "c": "roma_concrete"}
    els = [g.el([0, 14, 2], [16, 16, 16], "#c")]
    for i, low in enumerate((8, 5, 7, 10)):
        els.append(g.el([i * 4, low, 0], [i * 4 + 4, 16, 2], "#c"))
    return g.model(tex, els)


def blocks():
    b = {}
    for bid, tex in (("frankfurt_sandstone_ashlar", "frankfurt_sandstone_ashlar"), ("frankfurt_sandstone_rusticated", "frankfurt_sandstone_rusticated"),
                     ("munich_exposed_concrete", "munich_exposed_concrete"), ("munich_board_formed_concrete", "munich_board_formed_concrete"),
                     ("munich_terrazzo_floor", "munich_terrazzo_floor"), ("hamburg_clinker_brick", "hamburg_clinker_brick"),
                     ("hamburg_stone_trim", "hamburg_stone_trim"), ("sbahn_clinker_brick", "sbahn_clinker_brick"),
                     ("sbahn_platform_slab", "sbahn_platform_slab"), ("roma_travertine", "roma_travertine"),
                     ("roma_polished_floor", "roma_polished_floor")):
        b[bid] = ("simple", {"": cube_all(tex)})
    b["hamburg_clinker_stone_trim"] = ("simple", {"": {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": g.tx("hamburg_stone_trim"), "side": g.tx("hamburg_clinker_stone_trim"), "bottom": g.tx("hamburg_clinker_brick")}}})
    b["sbahn_platform_edge"] = ("facing", {"": edge_slab("sbahn_platform_edge_top")})
    b["sbahn_platform_edge_tactile"] = ("facing", {"": edge_slab("sbahn_platform_edge_tactile_top")})

    b["frankfurt_sandstone_cornice"] = ("facing", {"": cornice()})
    b["frankfurt_hall_truss"] = ("axis", {"": truss_span("frankfurt_truss", "frankfurt_truss_paint")})
    b["frankfurt_hall_truss_leg"] = ("simple", {"": truss_leg_model("frankfurt_truss_leg", "frankfurt_truss_paint")})
    b["frankfurt_hall_truss_arch"] = ("facing", {"": truss_span("frankfurt_truss", "frankfurt_truss_paint", rot=("x", 22.5, [8, 8, 8]), along_z=True)})
    b["frankfurt_ridge_skylight"] = ("facing", {"": ridge_skylight()})
    b["munich_ribbed_roof_deck"] = ("simple", {"": roof_deck()})
    b["hamburg_hall_truss"] = ("axis", {"": truss_span("hamburg_truss", "hamburg_truss_paint")})
    b["hamburg_glass_roof"] = ("simple", {"": hamburg_glass_roof()})
    b["sbahn_shelter"] = ("facing", {"": shelter()})
    b["sbahn_station_sign"] = ("facing", {"": station_sign()})
    b["sbahn_platform_light"] = ("simple", {"": lamp()})
    b["roma_ribbon_window"] = ("facing", {"": ribbon_window()})
    b["roma_canopy_edge"] = ("facing", {"": canopy_edge()})
    return b


def names():
    return {
        "frankfurt_sandstone_ashlar": "Sandstone Ashlar (Frankfurt)",
        "frankfurt_sandstone_rusticated": "Rusticated Sandstone (Frankfurt)",
        "frankfurt_sandstone_cornice": "Sandstone Cornice (Frankfurt)",
        "frankfurt_hall_truss": "Iron Hall Truss (Frankfurt)",
        "frankfurt_hall_truss_leg": "Iron Hall Truss Leg (Frankfurt)",
        "frankfurt_hall_truss_arch": "Iron Hall Truss Arch (Frankfurt)",
        "frankfurt_ridge_skylight": "Ridge Skylight (Frankfurt)",
        "munich_exposed_concrete": "Exposed Concrete (Munich)",
        "munich_board_formed_concrete": "Board-Formed Concrete (Munich)",
        "munich_terrazzo_floor": "Terrazzo Floor (Munich)",
        "munich_ribbed_roof_deck": "Ribbed Steel Roof Deck (Munich)",
        "hamburg_clinker_brick": "Clinker Brick (Hamburg)",
        "hamburg_clinker_stone_trim": "Clinker Brick with Stone Trim (Hamburg)",
        "hamburg_stone_trim": "Stone Trim (Hamburg)",
        "hamburg_hall_truss": "Iron Hall Truss (Hamburg)",
        "hamburg_glass_roof": "Glass Hall Roof (Hamburg)",
        "sbahn_clinker_brick": "Red Clinker Brick (S-Bahn)",
        "sbahn_platform_slab": "Platform Slab (S-Bahn)",
        "sbahn_platform_edge": "Platform Edge with White Line (S-Bahn)",
        "sbahn_platform_edge_tactile": "Platform Edge with Tactile Strip (S-Bahn)",
        "sbahn_shelter": "Platform Shelter (S-Bahn)",
        "sbahn_station_sign": "Station Name Sign, Blank (S-Bahn)",
        "sbahn_platform_light": "Platform Light (S-Bahn)",
        "roma_travertine": "Travertine Cladding (Roma)",
        "roma_polished_floor": "Polished Stone Floor (Roma)",
        "roma_ribbon_window": "Ribbon Window (Roma)",
        "roma_canopy_edge": "Wavy Canopy Edge (Roma)",
    }


def lang():
    return {
        f"itemGroup.{MOD}.stations": "ATA European Stations",
        TIP + "rises_ahead": "Rises toward the way you are looking",
    }


def recipes():
    r = {}

    def cut(result, ingredient, count=1):
        r[f"{result}_from_{ingredient.split(':')[-1]}_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": g.item(ingredient), "result": f"{MOD}:{result}", "count": count}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    for result in ("frankfurt_sandstone_ashlar", "frankfurt_sandstone_rusticated", "frankfurt_sandstone_cornice"):
        cut(result, "minecraft:sandstone")
    for result in ("frankfurt_hall_truss", "frankfurt_hall_truss_leg", "frankfurt_hall_truss_arch", "hamburg_hall_truss",
                   "munich_ribbed_roof_deck"):
        cut(result, "minecraft:iron_ingot", 2)
    for result in ("frankfurt_ridge_skylight", "hamburg_glass_roof"):
        cut(result, "minecraft:glass", 2)
    for result in ("munich_exposed_concrete", "munich_board_formed_concrete"):
        cut(result, "minecraft:light_gray_concrete")
    cut("munich_terrazzo_floor", "minecraft:polished_diorite")
    for result in ("hamburg_clinker_brick", "hamburg_clinker_stone_trim", "sbahn_clinker_brick"):
        cut(result, "minecraft:bricks")
    cut("hamburg_stone_trim", "minecraft:smooth_sandstone")
    cut("sbahn_platform_slab", "minecraft:smooth_stone")
    cut("sbahn_platform_edge", f"{MOD}:sbahn_platform_slab")
    shapeless("sbahn_platform_edge_tactile", [f"{MOD}:sbahn_platform_edge", "minecraft:white_dye"])
    shapeless("sbahn_shelter", ["minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:glass_pane"], 2)
    shapeless("sbahn_station_sign", ["minecraft:iron_ingot", "minecraft:white_dye", "minecraft:black_dye"], 2)
    shapeless("sbahn_platform_light", [f"{MOD}:sign_pole", "minecraft:glowstone_dust"])
    cut("roma_travertine", "minecraft:calcite")
    cut("roma_polished_floor", "minecraft:smooth_quartz")
    shapeless("roma_ribbon_window", ["minecraft:glass", "minecraft:white_concrete", "minecraft:iron_nugget"], 2)
    cut("roma_canopy_edge", "minecraft:white_concrete")
    return r
