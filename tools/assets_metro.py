"""1.5 metro (underground) station blocks: Western European and American finishes.

Same extension interface as the other assets_*.py modules. Everything here is a plain model (no block entities, no
ticking): full cubes, platform surfaces and edges, one wall-mounted vault springer and one cove light strip.
Munich glazed tiles and aluminium ceiling strips, Frankfurt ribbed concrete and ochre tile, Amsterdam pale concrete,
acoustic ceiling and terrazzo, Rotterdam steel and blue-grey tile, Lisbon-style blue-and-white tile, Washington-style
coffered vault and quarry tile, plus generic tunnel pieces. Colours and materials only: no operator marks or lettering.
"""
import math

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."

CONCRETE = (140, 140, 136)
BLUE = (30, 64, 140)
BLUE_MID = (84, 124, 196)
WHITE = (234, 234, 228)
GROUT = (196, 194, 188)

TILE_COLOURS = {
    "orange": (214, 112, 36),
    "blue": (40, 92, 170),
    "green": (48, 138, 82),
    "yellow": (228, 186, 42),
}

NAMES = {
    "metro_tile_orange": "Munich Metro Tile, Orange",
    "metro_tile_blue": "Munich Metro Tile, Blue",
    "metro_tile_green": "Munich Metro Tile, Green",
    "metro_tile_yellow": "Munich Metro Tile, Yellow",
    "metro_ceiling_strip": "Munich Metro Aluminium Ceiling",
    "metro_granite_floor": "Munich Metro Granite Floor",
    "metro_ribbed_concrete": "Frankfurt Metro Ribbed Concrete",
    "metro_ochre_tile": "Frankfurt Metro Ochre Tile",
    "metro_pale_concrete": "Amsterdam Metro Pale Concrete",
    "metro_acoustic_ceiling": "Amsterdam Metro Acoustic Ceiling",
    "metro_terrazzo_floor": "Amsterdam Metro Terrazzo Floor",
    "metro_steel_cladding": "Rotterdam Metro Steel Cladding",
    "metro_blue_grey_tile": "Rotterdam Metro Blue-Grey Tile",
    "metro_azulejo_rosette": "Lisbon Metro Tile, Rosette",
    "metro_azulejo_lattice": "Lisbon Metro Tile, Lattice",
    "metro_azulejo_wave": "Lisbon Metro Tile, Wave Band",
    "metro_coffer_ceiling": "Washington Metro Coffered Ceiling",
    "metro_vault_springer": "Washington Metro Vault Springer",
    "metro_quarry_floor": "Washington Metro Quarry Tile Floor",
    "metro_cove_light": "Washington Metro Cove Light",
    "metro_flashing_edge": "Washington Metro Granite Edge",
    "metro_cable_wall": "Metro Tunnel Wall with Cable Trays",
    "metro_tunnel_lining": "Metro Tunnel Lining",
    "metro_tactile_edge": "Metro Platform Edge with Tactile Line",
    "metro_recessed_light": "Metro Recessed Ceiling Light",
    "metro_column_tiled": "Metro Tiled Column",
    "metro_column_steel": "Metro Steel Column",
}


def put(img, x, y, color):
    img.load()[x % 16, y % 16] = tuple(color[:3]) + (255,)


def get(img, x, y):
    return img.load()[x % 16, y % 16][:3]


def tile_grid(base, tw, th, grout, name, jitter=7, gloss=True):
    """Glazed tiles with a 1 px grout line on the left and top of each tile, glaze highlight and shaded far edges."""
    img = g.noisy(base, 2, name)
    r = g.rng(name + "-tiles")
    tones = {}
    for y in range(16):
        for x in range(16):
            tone = tones.setdefault((y // th, x // tw), r.randint(-jitter, jitter))
            tx_, ty_ = x % tw, y % th
            if tx_ == 0 or ty_ == 0:
                put(img, x, y, g.shade(grout, r.randint(-4, 4) - (8 if ty_ == 0 and tx_ else 0)))
                continue
            c = g.shade(base, tone + r.randint(-2, 2))
            if gloss:
                if ty_ == 1 or tx_ == 1:
                    c = g.shade(c, 14)
                if tx_ == tw - 1 or ty_ == th - 1:
                    c = g.shade(c, -14)
                if tx_ == 2 and 2 <= ty_ <= th - 3:
                    c = g.shade(c, 9)
                if tx_ == 2 and ty_ == 2:
                    c = g.shade(c, 26)
            put(img, x, y, c)
    return img


def plain_concrete():
    img = g.noisy(CONCRETE, 4, "metro_concrete_plain")
    r = g.rng("metro_concrete_plain-pits")
    for _ in range(5):
        put(img, r.randint(0, 15), r.randint(0, 15), g.shade(CONCRETE, -24))
    for _ in range(4):
        put(img, r.randint(0, 15), r.randint(0, 15), g.shade(CONCRETE, 14))
    return img


def aluminium_ceiling():
    img = g.brushed((178, 182, 186), "metro_ceiling_strip", vertical=False)
    for y in range(16):
        k = y % 4
        for x in range(16):
            c = get(img, x, y)
            if k == 0:
                c = g.shade(c, -46)
            elif k == 1:
                c = g.shade(c, 20)
            elif k == 3:
                c = g.shade(c, -10)
            put(img, x, y, c)
    for y in range(16):  # panel joint
        put(img, 0, y, g.shade(get(img, 0, y), -30))
    return img


def granite_floor():
    img = g.noisy((150, 150, 147), 7, "metro_granite_floor")
    r = g.rng("metro_granite_floor-grain")
    for _ in range(26):
        x, y = r.randint(0, 15), r.randint(0, 15)
        put(img, x, y, g.shade(get(img, x, y), r.choice((-34, -22, 26, 38))))
    for _ in range(5):
        put(img, r.randint(0, 15), r.randint(0, 15), (92, 84, 82))
    for i in range(16):
        put(img, 0, i, g.shade(get(img, 0, i), -22))
        put(img, i, 0, g.shade(get(img, i, 0), -22))
        put(img, 1, i, g.shade(get(img, 1, i), 8))
        put(img, i, 1, g.shade(get(img, i, 1), 8))
    return img


def ribbed_concrete():
    base = (150, 146, 138)
    img = g.noisy(base, 3, "metro_ribbed_concrete")
    for x in range(16):
        amount = (16, 6, -18, -8)[x % 4]
        for y in range(16):
            put(img, x, y, g.shade(get(img, x, y), amount))
    for x in range(16):  # board-marked pour joint
        put(img, x, 8, g.shade(get(img, x, 8), -26))
    r = g.rng("ribbed-stain")
    for _ in range(9):
        put(img, r.randint(0, 15), r.randint(0, 15), g.shade(base, -22))
    return img


def concrete_panel():
    """Large pale panel: shadow joint on two edges and four form-tie holes."""
    base = (188, 188, 183)
    img = g.noisy(base, 3, "metro_pale_concrete")
    r = g.rng("metro_pale_concrete-bloom")
    for _ in range(14):
        x, y = r.randint(0, 15), r.randint(0, 15)
        put(img, x, y, g.shade(get(img, x, y), r.choice((-12, 9))))
    for i in range(16):
        put(img, 0, i, g.shade(base, -34))
        put(img, i, 0, g.shade(base, -34))
        put(img, 1, i, g.shade(base, 10))
        put(img, i, 1, g.shade(base, 10))
    for hx, hy in ((4, 5), (12, 5), (4, 13), (12, 13)):
        put(img, hx, hy, (96, 96, 94))
        put(img, hx + 1, hy + 1, (206, 206, 201))
        put(img, hx, hy + 1, (150, 150, 146))
    return img


def acoustic_ceiling():
    img = g.noisy((232, 232, 227), 2, "metro_acoustic_ceiling")
    for y in range(1, 16, 2):
        for x in range(1, 16, 2):
            put(img, x, y, (170, 172, 172) if (x + y) % 4 else (152, 154, 156))
    for i in range(16):
        put(img, 0, i, (196, 196, 192))
        put(img, i, 0, (196, 196, 192))
    return img


def terrazzo_floor():
    img = g.noisy((54, 56, 60), 4, "metro_terrazzo_floor")
    r = g.rng("metro_terrazzo_floor-chips")
    palette = ((196, 190, 178), (150, 132, 120), (222, 220, 212), (104, 112, 124), (170, 160, 150))
    for _ in range(30):
        x, y = r.randint(0, 15), r.randint(0, 15)
        c = r.choice(palette)
        put(img, x, y, c)
        if r.random() < 0.45:
            put(img, x + 1, y, g.shade(c, -26))
        if r.random() < 0.2:
            put(img, x, y + 1, g.shade(c, -14))
    for i in range(16):
        put(img, 0, i, g.shade(get(img, 0, i), -16))
        put(img, i, 0, g.shade(get(img, i, 0), -16))
    return img


def steel_cladding():
    img = g.brushed((186, 190, 192), "metro_steel_cladding", vertical=True)
    for y in range(16):
        for x in range(16):
            c = get(img, x, y)
            if x in (0, 8):
                c = g.shade(c, -42)
            elif x in (1, 9):
                c = g.shade(c, 24)
            elif x in (4, 5, 12, 13):
                c = g.shade(c, 11)  # soft reflection band
            put(img, x, y, c)
    return img


def azulejo(kind):
    """Blue-and-white tile: three original patterns on a 16 px repeat built of four 8 px tiles."""
    fill = [[False] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            u, v = x - 7.5, y - 7.5
            rad = math.hypot(u, v)
            ang = math.atan2(v, u)
            if kind == "rosette":
                petal = 4.2 < rad < 3.2 + 4.6 * abs(math.cos(2 * ang)) + 0.9
                fill[y][x] = rad < 2.3 or petal or (min(x, 15 - x) <= 1 and min(y, 15 - y) <= 1)
            elif kind == "lattice":
                d = abs((x % 8) - 3.5) + abs((y % 8) - 3.5)
                fill[y][x] = 1.8 < d < 3.7 or d < 0.8 or (x % 8 in (0, 7) and y % 8 in (0, 7))
            else:
                wave = 4 + 2.2 * math.sin((x + 1.5) * math.pi / 4)
                fill[y][x] = abs(y - wave) < 0.9 or abs(y - (15 - wave)) < 0.9 or (y in (7, 8) and x % 4 == 1)
    img = g.noisy(WHITE, 2, f"metro_azulejo_{kind}")
    r = g.rng(f"metro_azulejo_{kind}-glaze")
    for y in range(16):
        for x in range(16):
            if fill[y][x]:
                edge = any(not fill[(y + dy) % 16][(x + dx) % 16] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                put(img, x, y, g.shade(BLUE if edge else BLUE_MID, r.randint(-5, 5)))
            else:
                put(img, x, y, g.shade(WHITE, r.randint(-4, 3)))
    # glazed tile edges: light catches the top-left of each 8 px tile, the far edge falls into shadow
    for i in range(16):
        for x, y in ((i, 0), (i, 8), (0, i), (8, i)):
            put(img, x, y, g.shade(get(img, x, y), 38 if fill[y][x] else 14))
        for x, y in ((i, 7), (i, 15), (7, i), (15, i)):
            put(img, x, y, g.shade(get(img, x, y), -14))
    return img


def coffer_ceiling():
    """2x2 coffers: raised ribs, recessed panel lit from one side."""
    base = (164, 160, 152)
    img = g.noisy(base, 2, "metro_coffer_ceiling")
    for y in range(16):
        for x in range(16):
            lx, ly = x % 8, y % 8
            if lx < 2 or ly < 2:
                c = g.shade(base, 12 if (lx < 1 or ly < 1) else 4)
            else:
                c = g.shade(base, -20)
                if lx == 2 or ly == 2:
                    c = g.shade(base, -42)
                elif lx == 7 or ly == 7:
                    c = g.shade(base, -6)
            put(img, x, y, g.shade(c, (x * 7 + y * 3) % 5 - 2))
    return img


def quarry_floor():
    """Brown hexagonal quarry tile: nearest-centre cells on an offset lattice, grout between."""
    centres = [(0, 0), (8, 0), (4, 8), (12, 8)]
    r = g.rng("metro_quarry_floor")
    tones = [r.randint(-10, 10) for _ in centres]
    base = (128, 82, 56)
    img = g.new()
    for y in range(16):
        for x in range(16):
            ds = []
            for i, (cx, cy) in enumerate(centres):
                dx = min(abs(x - cx), 16 - abs(x - cx))
                dy = min(abs(y - cy), 16 - abs(y - cy))
                ds.append((max(dx * 0.866 + dy * 0.5, dy), i))
            ds.sort()
            (d1, i), (d2, _) = ds[0], ds[1]
            if d2 - d1 < 0.7:
                put(img, x, y, g.shade((104, 96, 88), r.randint(-4, 4)))
            else:
                c = g.shade(base, tones[i] + r.randint(-4, 4))
                if d2 - d1 < 1.7:
                    c = g.shade(c, 12)
                put(img, x, y, c)
    return img


def cove_lit():
    img = g.new()
    for y in range(16):
        for x in range(16):
            put(img, x, y, (255 - y // 2, 216 - y, 150 - y * 2))
    return img


def flashing_edge_top():
    """Brown granite with a rough outer band and a row of recessed amber lights."""
    base = (96, 80, 72)
    img = g.noisy(base, 6, "metro_flashing_edge")
    r = g.rng("metro_flashing_edge-grain")
    for _ in range(24):
        x, y = r.randint(0, 15), r.randint(0, 15)
        put(img, x, y, g.shade(get(img, x, y), r.choice((-26, 22, 30))))
    for y in range(0, 4):
        for x in range(16):
            if (x + y) % 2 == 0:
                put(img, x, y, g.shade(get(img, x, y), 14))
    for x in range(16):
        put(img, x, 1, (36, 32, 30))
        put(img, x, 2, (36, 32, 30))
    for x0 in (1, 5, 9, 13):
        for x in (x0, x0 + 1):
            put(img, x, 1, (255, 232, 160))
            put(img, x, 2, (240, 190, 96))
    return img


def cable_wall():
    img = g.noisy((98, 100, 100), 4, "metro_cable_wall")
    r = g.rng("metro_cable_wall-stain")
    for _ in range(10):
        put(img, r.randint(0, 15), r.randint(0, 15), (78, 80, 80))
    sheaths = ((24, 24, 26), (150, 38, 34), (30, 30, 34), (40, 70, 140), (24, 24, 26))
    for top in (2, 9):
        for x in range(16):
            put(img, x, top, (184, 188, 190))
            put(img, x, top + 4, (112, 116, 120))
            for y in range(top + 1, top + 4):
                put(img, x, y, sheaths[(x // 2 + top) % len(sheaths)] if y < top + 3 else (60, 62, 66))
        for x in (3, 11):
            for y in range(top + 5, top + 7):
                put(img, x, y, (150, 154, 158))
    return img


def tunnel_lining():
    img = g.noisy((88, 90, 92), 4, "metro_tunnel_lining")
    for y in range(16):
        for x in range(16):
            shift = 4 if y >= 8 else 0
            if y % 8 == 0:
                put(img, x, y, (52, 54, 56))
            elif y % 8 == 1:
                put(img, x, y, g.shade(get(img, x, y), 10))
            if (x + shift) % 8 == 0:
                put(img, x, y, (54, 56, 58))
    for bx, by in ((2, 4), (6, 4), (10, 12), (14, 12)):
        put(img, bx, by, (46, 48, 50))
        put(img, bx + 1, by, (118, 120, 122))
    for y in range(8, 16):
        put(img, 5, y, g.shade(get(img, 5, y), -10))
    return img


def tactile_edge_top():
    img = g.noisy((160, 158, 152), 5, "metro_tactile_edge_top")
    g.tactile_rows(img, 0, 5, "metro_tactile_edge_rows")
    for x in range(16):
        put(img, x, 5, (110, 100, 60))
    return img


def granite_side():
    img = g.noisy((150, 150, 147), 5, "metro_granite_side")
    g.rect(img, 0, 15, 16, 16, (110, 110, 108))
    return img


def recessed_light():
    img = g.noisy((176, 178, 178), 2, "metro_recessed_light")
    for y in range(2, 14):
        for x in range(2, 14):
            rim = x in (2, 13) or y in (2, 13)
            put(img, x, y, (92, 94, 96) if rim else (255, 252, 236 - abs(x - 8) - abs(y - 8)))
    return img


def textures():
    t = {}
    for name, colour in TILE_COLOURS.items():
        t[f"metro_tile_{name}"] = tile_grid(colour, 4, 8, GROUT, f"metro_tile_{name}")
    t["metro_concrete_plain"] = plain_concrete()
    t["metro_ceiling_strip"] = aluminium_ceiling()
    t["metro_granite_floor"] = granite_floor()
    t["metro_ribbed_concrete"] = ribbed_concrete()
    t["metro_ochre_tile"] = tile_grid((184, 132, 56), 4, 4, (120, 104, 84), "metro_ochre_tile", jitter=12, gloss=False)
    t["metro_pale_concrete"] = concrete_panel()
    t["metro_acoustic_ceiling"] = acoustic_ceiling()
    t["metro_terrazzo_floor"] = terrazzo_floor()
    t["metro_steel_cladding"] = steel_cladding()
    t["metro_blue_grey_tile"] = tile_grid((88, 112, 130), 8, 8, (168, 172, 174), "metro_blue_grey_tile", jitter=6)
    for kind in ("rosette", "lattice", "wave"):
        t[f"metro_azulejo_{kind}"] = azulejo(kind)
    t["metro_coffer_ceiling"] = coffer_ceiling()
    t["metro_quarry_floor"] = quarry_floor()
    t["metro_cove_lit"] = cove_lit()
    t["metro_flashing_edge_top"] = flashing_edge_top()
    t["metro_cable_wall"] = cable_wall()
    t["metro_tunnel_lining"] = tunnel_lining()
    t["metro_tactile_edge_top"] = tactile_edge_top()
    t["metro_granite_side"] = granite_side()
    t["metro_edge_face"] = g.noisy((104, 104, 102), 3, "metro_edge_face")
    t["metro_recessed_light"] = recessed_light()
    t["metro_tile_cream"] = tile_grid((226, 222, 208), 4, 4, (176, 174, 166), "metro_tile_cream", jitter=4)
    return t


# ---- models ---------------------------------------------------------------------------------------------------------

def cube_all(tex):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": g.tx(tex)}}


def cube(top, side, bottom):
    return g.cube_bottom_top(top, side, bottom)


def floor(top):
    return cube(top, "metro_concrete_plain", "metro_concrete_plain")


def ceiling(bottom):
    return cube("metro_concrete_plain", "metro_concrete_plain", bottom)


def edge(top, side, face):
    return g.model({"particle": side, "top": top, "side": side, "face": face}, [
        g.el([0, 12, 0], [16, 16, 16], "#side", faces={"up": "#top", "north": "#side", "down": "#face"}),
        g.el([0, 0, 2], [16, 12, 16], "#face", faces={"north": "#face", "up": None}),
    ])


def springer():
    """Concave spandrel: full height at the wall (south), thinning to nothing at the open side."""
    els = []
    for z1, z2 in ((2, 5), (5, 8), (8, 11), (11, 14), (14, 16)):
        mid = (z1 + z2) / 2
        y1 = math.sqrt(max(0.0, 256 - mid * mid)) if z2 < 16 else 5.5
        els.append(g.el([0, round(y1 * 2) / 2, z1], [16, 16, z2], "#c"))
    return g.model({"particle": "metro_coffer_ceiling", "c": "metro_coffer_ceiling"}, els)


def cove():
    return g.model({"particle": "metro_steel_cladding", "metal": "metro_steel_cladding", "lit": "metro_cove_lit"}, [
        g.el([0, 8, 13], [16, 16, 16], "#metal"),
        g.el([0, 8, 9], [16, 10, 13], "#metal"),
        g.el([0, 10, 10], [16, 12, 13], "#lit", faces={"down": None, "south": None}),
    ])


def column(tex):
    return g.model({"particle": tex, "c": tex}, [g.el([3, 0, 3], [13, 16, 13], "#c", cull=False)])


def blocks():
    b = {}
    for name in TILE_COLOURS:
        b[f"metro_tile_{name}"] = ("simple", {"": cube_all(f"metro_tile_{name}")})
    b["metro_ceiling_strip"] = ("simple", {"": ceiling("metro_ceiling_strip")})
    b["metro_granite_floor"] = ("simple", {"": floor("metro_granite_floor")})
    b["metro_ribbed_concrete"] = ("simple", {"": cube_all("metro_ribbed_concrete")})
    b["metro_ochre_tile"] = ("simple", {"": cube_all("metro_ochre_tile")})
    b["metro_pale_concrete"] = ("simple", {"": cube_all("metro_pale_concrete")})
    b["metro_acoustic_ceiling"] = ("simple", {"": ceiling("metro_acoustic_ceiling")})
    b["metro_terrazzo_floor"] = ("simple", {"": floor("metro_terrazzo_floor")})
    b["metro_steel_cladding"] = ("simple", {"": cube_all("metro_steel_cladding")})
    b["metro_blue_grey_tile"] = ("simple", {"": cube_all("metro_blue_grey_tile")})
    for kind in ("rosette", "lattice", "wave"):
        b[f"metro_azulejo_{kind}"] = ("simple", {"": cube_all(f"metro_azulejo_{kind}")})
    b["metro_coffer_ceiling"] = ("simple", {"": ceiling("metro_coffer_ceiling")})
    b["metro_vault_springer"] = ("facing", {"": springer()})
    b["metro_quarry_floor"] = ("simple", {"": floor("metro_quarry_floor")})
    b["metro_cove_light"] = ("facing", {"": cove()})
    b["metro_flashing_edge"] = ("facing", {"": edge("metro_flashing_edge_top", "metro_granite_side", "metro_edge_face")})
    b["metro_cable_wall"] = ("simple", {"": cube("metro_concrete_plain", "metro_cable_wall", "metro_concrete_plain")})
    b["metro_tunnel_lining"] = ("simple", {"": cube_all("metro_tunnel_lining")})
    b["metro_tactile_edge"] = ("facing", {"": edge("metro_tactile_edge_top", "metro_granite_side", "metro_edge_face")})
    b["metro_recessed_light"] = ("simple", {"": cube("metro_concrete_plain", "metro_concrete_plain", "metro_recessed_light")})
    b["metro_column_tiled"] = ("simple", {"": column("metro_tile_cream")})
    b["metro_column_steel"] = ("simple", {"": column("metro_steel_cladding")})
    return b


def names():
    return dict(NAMES)


def lang():
    return {f"itemGroup.{MOD}.metro": "ATA Metro"}


def recipes():
    r = {}

    def cut(result, ingredient, count=1):
        r[f"{result}_from_{ingredient.split(':')[-1]}_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": g.item(ingredient), "result": f"{MOD}:{result}", "count": count}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    for name in TILE_COLOURS:
        shapeless(f"metro_tile_{name}", ["minecraft:terracotta", "minecraft:terracotta", f"minecraft:{name}_dye"], 4)
    shapeless("metro_ochre_tile", ["minecraft:terracotta", "minecraft:terracotta", "minecraft:yellow_dye", "minecraft:brown_dye"], 4)
    shapeless("metro_blue_grey_tile", ["minecraft:terracotta", "minecraft:terracotta", "minecraft:blue_dye", "minecraft:gray_dye"], 4)
    for kind in ("rosette", "lattice", "wave"):
        shapeless(f"metro_azulejo_{kind}", ["minecraft:terracotta", "minecraft:terracotta", "minecraft:white_dye", "minecraft:blue_dye"], 4)
    for result in ("metro_ribbed_concrete", "metro_pale_concrete", "metro_cable_wall", "metro_tunnel_lining",
                   "metro_coffer_ceiling", "metro_vault_springer"):
        cut(result, "minecraft:smooth_stone", 2)
    cut("metro_granite_floor", "minecraft:polished_granite", 2)
    cut("metro_terrazzo_floor", "minecraft:polished_deepslate", 2)
    cut("metro_quarry_floor", "minecraft:terracotta", 2)
    cut("metro_acoustic_ceiling", "minecraft:smooth_quartz", 2)
    cut("metro_column_tiled", "minecraft:smooth_quartz", 2)
    for result in ("metro_ceiling_strip", "metro_steel_cladding", "metro_column_steel"):
        cut(result, "minecraft:iron_ingot", 2)
    cut("metro_tactile_edge", "minecraft:polished_granite", 2)
    cut("metro_flashing_edge", "minecraft:polished_granite", 2)
    shapeless("metro_cove_light", ["minecraft:iron_ingot", "minecraft:glowstone_dust"], 2)
    shapeless("metro_recessed_light", ["minecraft:smooth_stone", "minecraft:glowstone_dust"], 2)
    return r
