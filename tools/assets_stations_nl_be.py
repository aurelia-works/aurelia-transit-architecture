"""1.5 Dutch and Belgian station architecture: Utrecht Centraal and Leidsche Rijn, Amsterdam Centraal, Rotterdam Centraal,
Antwerpen-Centraal, plus colour-only Dutch-style signage (no operator logo or lettering).

Same extension interface as the other assets_*.py modules. All textures are 16x16 like the rest of the mod, original and
generated from fixed seeds. Models stay at six cuboids or fewer.
"""
import math

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
P = "nlbe_"

WHITE_STEEL = (236, 237, 233)
BRICK_RED = (146, 62, 50)
MORTAR = (176, 168, 152)
SANDSTONE = (214, 196, 160)
LIMESTONE = (216, 202, 172)
GOLD = (204, 164, 64)
IRON_GREEN = (38, 66, 54)
DARK_ROOF = (46, 51, 56)
ANTHRACITE = (54, 57, 61)
SIGN_YELLOW = (250, 200, 24)
SIGN_NAVY = (0, 40, 108)


def put(img, x, y, color, alpha=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.load()[x, y] = tuple(g.clamp(c) for c in color[:3]) + (alpha,)


def get(img, x, y):
    return img.load()[x, y][:3]


def edge_ao(img, light=7, dark=9):
    """Subtle ambient-occlusion look: top and left rim lighter, bottom and right rim darker."""
    for i in range(16):
        put(img, i, 0, g.shade(get(img, i, 0), light))
        put(img, 0, i, g.shade(get(img, 0, i), light // 2))
        put(img, i, 15, g.shade(get(img, i, 15), -dark))
        put(img, 15, i, g.shade(get(img, 15, i), -dark // 2))


def brick(base, name, rows=range(16)):
    """Running-bond brick: four courses of 3px brick plus 1px mortar, 8px bricks offset by half a brick per course."""
    img = g.noisy(MORTAR, 4, name + "-mortar")
    r = g.rng(name)
    tones = {}
    for y in rows:
        course, row = divmod(y, 4)
        if row == 3:
            continue
        shift = 4 if course % 2 else 0
        for x in range(16):
            if (x + shift) % 8 == 7:
                continue
            key = (course, (x + shift) // 8)
            if key not in tones:
                tones[key] = (r.randint(-14, 12), r.randint(-6, 6), r.randint(-6, 6))
            dr, dg, db = tones[key]
            c = (base[0] + dr, base[1] + dg + dr // 3, base[2] + db + dr // 3)
            c = g.shade(c, r.randint(-5, 5) + (7 if row == 0 else -5 if row == 2 else 0))
            put(img, x, y, c)
    # mortar sits slightly in shadow under each course
    for y in rows:
        if y % 4 == 3:
            for x in range(16):
                put(img, x, y, g.shade(get(img, x, y), -6))
    return img


def ashlar(base, name, amount=5, joints=((0, 8), (8, 16)), offset=8):
    """Smooth dressed stone: grain noise plus 1px joints between courses 8px high, offset every other course."""
    img = g.noisy(base, amount, name)
    r = g.rng(name + "-grain")
    for _ in range(26):
        x, y = r.randrange(16), r.randrange(16)
        put(img, x, y, g.shade(get(img, x, y), r.choice((-9, -6, 6, 9))))
        put(img, (x + 1) % 16, y, g.shade(get(img, (x + 1) % 16, y), r.choice((-5, 5))))
    for course in (0, 1):
        y = course * 8
        for x in range(16):
            put(img, x, y, g.shade(get(img, x, y), -22))
            put(img, x, y + 7, g.shade(get(img, x, y + 7), -8))
        jx = (offset * course + 8) % 16
        for yy in range(y, y + 8):
            put(img, jx, yy, g.shade(get(img, jx, yy), -20))
    return img


def gilt(name, base=GOLD):
    img = g.noisy(base, 5, name)
    r = g.rng(name + "-streak")
    for x in range(16):
        s = r.randint(-8, 8)
        for y in range(16):
            put(img, x, y, g.shade(get(img, x, y), s + (10 if (x + y) % 9 == 0 else 0)))
    return img


def textures():
    t = {}

    # ---- Utrecht Centraal: white undulating roof, slender white steel, glass, light floor
    wave_top = g.noisy(WHITE_STEEL, 2, P + "wave_top")
    wave_under = g.noisy(WHITE_STEEL, 2, P + "wave_under")
    for y in range(16):
        swell = 13 * math.sin(2 * math.pi * y / 16)
        for x in range(16):
            put(wave_top, x, y, g.shade(get(wave_top, x, y), swell))
            put(wave_under, x, y, g.shade(get(wave_under, x, y), -6 + swell * 0.5))
    for img in (wave_top, wave_under):
        for y in range(16):
            put(img, 0, y, g.shade(get(img, 0, y), -20))
            put(img, 8, y, g.shade(get(img, 8, y), -9))
    t[P + "wave_top"] = wave_top
    t[P + "wave_under"] = wave_under

    white = g.noisy(WHITE_STEEL, 2, P + "white_steel")
    for x in range(16):
        for y in range(16):
            put(white, x, y, g.shade(get(white, x, y), 7 - x * 1.0))
    t[P + "white_steel"] = white

    facade = g.glass(P + "hall_glass", tint=(176, 206, 214), alpha=58)
    for i in range(16):
        put(facade, i, 0, (232, 234, 232), 255)
        put(facade, i, 15, (216, 218, 216), 255)
        put(facade, 0, i, (232, 234, 232), 255)
        put(facade, 15, i, (216, 218, 216), 255)
        put(facade, i, 8, (226, 228, 226), 255)
    t[P + "hall_glass"] = facade

    tile = g.noisy((178, 180, 180), 3, P + "floor_tile")
    r = g.rng(P + "floor_tile-sheen")
    for y in range(16):
        for x in range(16):
            put(tile, x, y, g.shade(get(tile, x, y), 4 - (x + y) * 0.35 + (4 if (x - y) % 11 == 0 else 0)))
    for i in range(16):
        put(tile, i, 0, g.shade(get(tile, i, 0), -16))
        put(tile, 0, i, g.shade(get(tile, 0, i), -16))
    for _ in range(14):
        put(tile, r.randrange(1, 16), r.randrange(1, 16), (150, 152, 152))
    t[P + "floor_tile"] = tile

    # ---- Utrecht Leidsche Rijn: dark standing-seam roof over a light timber soffit, perforated cladding, concrete paver
    roof = g.noisy(DARK_ROOF, 2, P + "dark_roof")
    for x in range(0, 16, 4):
        for y in range(16):
            put(roof, x, y, g.shade(DARK_ROOF, 16))
            put(roof, x + 1, y, g.shade(DARK_ROOF, -8))
    t[P + "dark_roof"] = roof
    t[P + "dark_steel"] = g.noisy((40, 44, 48), 2, P + "dark_steel")

    soffit = g.noisy((206, 172, 124), 4, P + "soffit")
    r = g.rng(P + "soffit-grain")
    for x in range(16):
        tone = r.randint(-9, 9)
        for y in range(16):
            put(soffit, x, y, g.shade(get(soffit, x, y), tone + (r.randint(-4, 4) if y % 3 else 6)))
    for x in range(0, 16, 4):
        for y in range(16):
            put(soffit, x, y, g.shade(get(soffit, x, y), -34))
    t[P + "soffit"] = soffit

    perf = g.noisy(ANTHRACITE, 2, P + "perforated")
    for y in range(1, 16, 2):
        for x in range(1 + (y // 2) % 2, 16, 2):
            put(perf, x, y, (20, 22, 24))
            put(perf, x, y + 1, g.shade(ANTHRACITE, 9))
    for i in range(16):
        put(perf, i, 0, g.shade(ANTHRACITE, 12))
        put(perf, 0, i, g.shade(ANTHRACITE, 8))
        put(perf, 8, i, g.shade(ANTHRACITE, -10))
        put(perf, i, 15, g.shade(ANTHRACITE, -14))
    t[P + "perforated"] = perf

    paver = g.pavers((160, 160, 156), (112, 113, 110), P + "paver", size=8)
    t[P + "paver"] = paver

    # ---- Amsterdam Centraal: deep red brick, sandstone bands, cornice, arched window, cast iron, clock
    red = brick(BRICK_RED, P + "brick")
    t[P + "brick"] = red

    band = brick(BRICK_RED, P + "brick_band", rows=range(0, 5))
    band_stone = ashlar(SANDSTONE, P + "band_stone")
    for y in range(5, 11):
        for x in range(16):
            put(band, x, y, get(band_stone, x, y))
    for x in range(16):
        put(band, x, 5, g.shade(SANDSTONE, -26))
        put(band, x, 10, g.shade(SANDSTONE, -18))
        put(band, x, 6, g.shade(get(band, x, 6), 8))
    band_rest = brick(BRICK_RED, P + "brick_band", rows=range(11, 16))
    for y in range(11, 16):
        for x in range(16):
            put(band, x, y, get(band_rest, x, y))
    t[P + "brick_band_side"] = band

    t[P + "sandstone"] = ashlar(SANDSTONE, P + "sandstone")
    dentil = ashlar(SANDSTONE, P + "dentil")
    for y in range(16):
        for x in range(16):
            if 5 <= y <= 11 and x % 4 in (0, 1):
                put(dentil, x, y, g.shade(SANDSTONE, 12 if y < 8 else 2))
            elif 5 <= y <= 11:
                put(dentil, x, y, g.shade(SANDSTONE, -42))
    t[P + "dentil"] = dentil

    window = brick(BRICK_RED, P + "window_wall")
    inside = lambda x, y: (abs(x + 0.5 - 8) <= 3.5 and 8.5 <= y + 0.5 <= 14) or ((x + 0.5 - 8) ** 2 + (y + 0.5 - 8.5) ** 2 <= 12.25 and y + 0.5 < 8.5)
    r = g.rng(P + "window")
    for y in range(16):
        for x in range(16):
            if inside(x, y):
                muntin = x in (7, 8) or y in (6, 10)
                if muntin:
                    put(window, x, y, (28, 38, 34), 255)
                else:
                    put(window, x, y, (150, 186, 200) if (x + y) % 5 else (200, 224, 232), 92)
            elif any(inside(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                put(window, x, y, g.shade(SANDSTONE, r.randint(-6, 6)))
    for x in range(1, 15):
        put(window, x, 14, g.shade(SANDSTONE, 8 + r.randint(-4, 4)))
        put(window, x, 15, g.shade(SANDSTONE, -22 + r.randint(-4, 4)))
    t[P + "window"] = window

    iron = g.noisy(IRON_GREEN, 3, P + "cast_iron")
    t[P + "cast_iron"] = iron
    t[P + "shed_leg"] = shed_leg()
    t[P + "shed_arch"] = shed_arch()

    t[P + "gilt"] = gilt(P + "gilt")
    t[P + "clock_panel"] = clock_panel()

    # ---- Rotterdam Centraal: brushed stainless, warm timber slats, black stone
    steel = g.noisy((172, 178, 182), 2, P + "stainless")
    r = g.rng(P + "stainless-brush")
    for y in range(16):
        streak = r.randint(-8, 8)
        for x in range(16):
            put(steel, x, y, g.shade(get(steel, x, y), streak + (5 if (x * 3 + y) % 13 == 0 else 0) + 5 * math.sin(x / 5.0)))
    for x in range(16):
        put(steel, x, 7, g.shade(get(steel, x, 7), -22))
    t[P + "stainless"] = steel

    slats = g.noisy((190, 142, 88), 3, P + "slats")
    r = g.rng(P + "slats-grain")
    for x in range(16):
        slat, col = divmod(x, 4)
        tone = [-10, 6, -4, 10][slat]
        for y in range(16):
            c = g.shade((190, 142, 88), tone + r.randint(-5, 5) + (3 if col == 1 else -3 if col == 2 else 0) + 4 * math.sin(y / 2.6 + slat))
            put(slats, x, y, c)
    for y in range(16):
        for x in (3, 7, 11, 15):
            put(slats, x, y, (52, 36, 24))
    for kx, ky in ((5, 5), (13, 11)):
        put(slats, kx, ky, (106, 70, 40))
        put(slats, kx, ky + 1, (122, 84, 50))
    t[P + "slats"] = slats

    black = g.noisy((30, 31, 33), 2, P + "black_stone")
    r = g.rng(P + "black_stone-fleck")
    for _ in range(30):
        put(black, r.randrange(16), r.randrange(16), (r.randint(52, 76),) * 3)
    for y in range(16):
        for x in range(16):
            if x % 8 == 0 or y % 8 == 0:
                put(black, x, y, (16, 17, 18))
            elif x % 8 == 1 or y % 8 == 1:
                put(black, x, y, g.shade(get(black, x, y), 5))
    t[P + "black_stone"] = black

    # ---- Antwerpen-Centraal: ornate limestone, polished multi-coloured marble, marble floor, gilded trim
    t[P + "limestone"] = limestone()
    t[P + "marble_wall"] = marble_wall()
    t[P + "marble_floor"] = marble_floor()
    t[P + "gilt_trim"] = gilt_trim()

    # ---- Dutch-style signage: colours only (text is drawn by the sign renderer)
    t[P + "sign_yellow"] = g.noisy(SIGN_YELLOW, 2, P + "sign_yellow")
    t[P + "sign_frame"] = g.noisy(SIGN_NAVY, 2, P + "sign_frame")
    plate = g.noisy(SIGN_YELLOW, 2, P + "platform_sign")
    for i in range(4, 12):
        put(plate, i, 5, SIGN_NAVY)
        put(plate, i, 12, SIGN_NAVY)
    for i in range(5, 13):
        put(plate, 4, i, SIGN_NAVY)
        put(plate, 11, i, SIGN_NAVY)
    t[P + "platform_sign"] = plate

    band_tex = g.noisy(SIGN_YELLOW, 2, P + "column_band")
    for x in range(16):
        put(band_tex, x, 5, SIGN_NAVY)
        put(band_tex, x, 10, SIGN_NAVY)
        put(band_tex, x, 6, g.shade(SIGN_YELLOW, 14))
    t[P + "column_band"] = band_tex
    return t


def shed_leg():
    """Riveted lattice column: two chords, X bracing, rivet heads. Transparent between members."""
    img = g.new()
    r = g.rng(P + "shed_leg")
    for y in range(16):
        for x in (5, 6, 13, 14):
            put(img, x, y, g.shade(IRON_GREEN, 14 if x in (5, 13) else -8 + r.randint(-3, 3)))
    for y0 in (0, 8):
        for i in range(8):
            put(img, 7 + (i * 6) // 8, y0 + i, g.shade(IRON_GREEN, -4))
            put(img, 12 - (i * 6) // 8, y0 + i, g.shade(IRON_GREEN, -4))
        for x in range(7, 13):
            put(img, x, y0, g.shade(IRON_GREEN, 10))
    for y in (0, 4, 8, 12):
        for x in (5, 14):
            put(img, x, y, g.shade(IRON_GREEN, 30))
    return img


def shed_arch():
    """Quarter arch rising from the leg: two curved chords and radial bracing around centre (0, 16)."""
    img = g.new()
    r = g.rng(P + "shed_arch")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5, 16 - (y + 0.5))
            if 13 <= d < 15 or 5 <= d < 7:
                put(img, x, y, g.shade(IRON_GREEN, 12 - (d % 2) * 18 + r.randint(-3, 3)))
    for k in range(3):
        ang = math.radians(20 + k * 25)
        for s in range(70):
            rad = 7 + s * 6 / 70
            x, y = int(rad * math.cos(ang)), int(16 - rad * math.sin(ang))
            if 0 <= x < 16 and 0 <= y < 16 and 6.9 < math.hypot(x + 0.5, 16 - (y + 0.5)) < 13.1:
                put(img, x, y, g.shade(IRON_GREEN, -4))
    for ang in (8, 33, 58, 82):
        a = math.radians(ang)
        for rad in (6, 14):
            put(img, int(rad * math.cos(a)), int(15 - rad * math.sin(a)), g.shade(IRON_GREEN, 32))
    return img


def clock_panel():
    img = g.noisy((26, 50, 58), 2, P + "clock_panel")
    for i in range(16):
        for x, y, s in ((i, 0, 24), (0, i, 24), (i, 15, -10), (15, i, -10)):
            put(img, x, y, g.shade(GOLD, s))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 5.0:
                put(img, x, y, g.shade((240, 234, 216), -3 + (x + y) % 3))
            elif d <= 6.6:
                put(img, x, y, g.shade(GOLD, 22 if x + y < 15 else -14))
            elif d <= 7.2:
                put(img, x, y, (50, 36, 18))
    for i in range(12):
        a = math.radians(i * 30)
        put(img, int(8 + 4.1 * math.sin(a)), int(8 - 4.1 * math.cos(a)), (36, 30, 28))
    for s in range(1, 5):
        put(img, 8, 8 - s, (30, 28, 28))
        put(img, 8 + s * 4 // 5 + 1, 8 - s // 2, (30, 28, 28))
    put(img, 8, 8, GOLD)
    return img


def limestone():
    img = ashlar(LIMESTONE, P + "limestone", amount=4)
    # bevelled carved panel in each course
    for course in (0, 1):
        y0 = course * 8
        for x in range(3, 13):
            put(img, x, y0 + 2, g.shade(get(img, x, y0 + 2), 14))
            put(img, x, y0 + 5, g.shade(get(img, x, y0 + 5), -20))
        for y in range(y0 + 2, y0 + 6):
            put(img, 3, y, g.shade(get(img, 3, y), 14))
            put(img, 12, y, g.shade(get(img, 12, y), -20))
        put(img, 7 + course, y0 + 3, g.shade(LIMESTONE, -26))
        put(img, 8 + course, y0 + 4, g.shade(LIMESTONE, -26))
    return img


def marble_wall():
    """Polished warm cream marble with soft grey veining. Veins are continuous wrapped paths and the clouds use whole-number
    sine waves, so the tile is seamless."""
    tau = 2 * math.pi / 16
    r = g.rng(P + "marble_wall")
    cream = (233, 226, 211)
    vein = (132, 130, 130)
    img = g.new()
    for y in range(16):
        for x in range(16):
            cloud = 3.0 * math.sin(tau * (x + y) + 0.5) + 2.5 * math.sin(tau * (2 * x - y) + 2.0) + 2.0 * math.sin(tau * (x - 2 * y) + 4.0)
            put(img, x, y, g.shade(cream, cloud + r.randint(-1, 1)))

    def lay(path, strength):
        for (x, y) in path:  # soft halo first, then the core, so the vein reads as a blurred line
            for dy, w in ((-2, 0.1), (-1, 0.35), (1, 0.35), (2, 0.1), (0, 1.0)):
                base = get(img, x % 16, (y + dy) % 16)
                k = strength * w
                put(img, x % 16, (y + dy) % 16, tuple(int(base[c] + (vein[c] - base[c]) * k) for c in range(3)))

    main, branch = [], []
    prev = None
    for x in range(16):
        y = 3 + x + round(1.6 * math.sin(tau * x + 0.6) + 0.7 * math.sin(tau * 2 * x + 1.9))
        if prev is not None:
            lo, hi = sorted((prev, y))
            main += [(x, yy) for yy in range(lo + 1, hi)]
        main.append((x, y))
        prev = y
    prev = None
    for x in range(16):
        y = 13 - x + round(1.2 * math.sin(tau * x + 2.2))
        if prev is not None:
            lo, hi = sorted((prev, y))
            branch += [(x, yy) for yy in range(lo + 1, hi)]
        branch.append((x, y))
        prev = y
    lay(main, 0.5)
    lay(branch, 0.12)
    return img


def marble_floor():
    img = g.new()
    r = g.rng(P + "marble_floor")
    for y in range(16):
        for x in range(16):
            dark = ((x // 8) + (y // 8)) % 2
            base = (66, 70, 70) if dark else (226, 220, 206)
            c = g.shade(base, r.randint(-4, 4) + 5 * math.sin((x * 2 + y) / 3.5))
            put(img, x, y, c)
    for _ in range(5):
        x, y = r.randrange(16), r.randrange(16)
        for s in range(4):
            put(img, (x + s) % 16, (y + s // 2) % 16, g.shade(get(img, (x + s) % 16, (y + s // 2) % 16), 18))
    for i in range(16):
        for k in (0, 8):
            put(img, i, k, g.shade(get(img, i, k), -20))
            put(img, k, i, g.shade(get(img, k, i), -20))
    return img


def gilt_trim():
    """Rows 2..9 are the visible face: moulding lines, a leaf scroll and a bead-and-reel band."""
    img = gilt(P + "gilt_trim")
    for x in range(16):
        put(img, x, 2, g.shade(GOLD, 26))
        put(img, x, 3, g.shade(GOLD, -26))
        put(img, x, 8, g.shade(GOLD, -26))
        put(img, x, 9, g.shade(GOLD, -36))
    for x in range(0, 16, 4):
        put(img, x, 5, g.shade(GOLD, 24))
        put(img, x + 1, 4, g.shade(GOLD, 24))
        put(img, x + 2, 4, g.shade(GOLD, -22))
        put(img, x + 3, 5, g.shade(GOLD, -22))
        put(img, x + 1, 6, g.shade(GOLD, 44))
        put(img, x + 2, 6, g.shade(GOLD, 8))
        put(img, x + 1, 7, g.shade(GOLD, -8))
        put(img, x + 2, 7, g.shade(GOLD, -42))
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------------------------------
def cube_all(texture):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": g.tx(texture)}}


def stepped(profile, texs, step=4, thickness=2):
    """Four stepped strips following a z-profile, matching Shapes.profile(..., step 4) exactly."""
    els = []
    for z in range(0, 16, step):
        a, b = profile(z), profile(z + step)
        els.append(g.el([0, max(0, min(a, b)), z], [16, min(16, max(a, b) + thickness), z + step], "#side", faces={"up": "#top", "down": "#under"}))
    return g.model(texs, els)


def blocks():
    b = {}
    wave = {"particle": P + "white_steel", "top": P + "wave_top", "under": P + "wave_under", "side": P + "white_steel"}
    b["utrecht_wave_roof_panel"] = ("simple", {"": g.model(wave, [g.el([0, 0, 0], [16, 3, 16], "#side", faces={"up": "#top", "down": "#under"})])})
    b["utrecht_wave_roof_rise"] = ("facing", {"": stepped(g.WAVE_RISE, wave)})
    b["utrecht_wave_roof_edge"] = ("facing", {"": g.model(wave, [
        g.el([0, 0, 2], [16, 3, 16], "#side", faces={"up": "#top", "down": "#under", "north": None}),
        g.el([0, 0, 0], [16, 4, 2], "#side", faces={"up": "#top", "down": "#under"}),
    ])})
    col = {"particle": P + "white_steel", "side": P + "white_steel"}
    b["utrecht_tree_column_white"] = ("simple", {"": g.model(col, [
        g.el([6.5, 0, 6.5], [9.5, 8, 9.5], "#side"),
        g.el([5.5, 8, 5.5], [10.5, 11, 10.5], "#side"),
        g.el([3.5, 11, 3.5], [12.5, 14, 12.5], "#side"),
        g.el([1, 14, 1], [15, 16, 15], "#side"),
    ])})
    b["utrecht_hall_glass_facade"] = ("facing", {"": g.model({"particle": P + "white_steel", "glass": P + "hall_glass", "frame": P + "white_steel"}, [
        g.el([0, 0, 7.5], [16, 16, 8.5], "#glass", faces={"up": None, "down": None, "east": None, "west": None}),
        g.el([0, 0, 6.5], [1, 16, 9.5], "#frame"),
        g.el([15, 0, 6.5], [16, 16, 9.5], "#frame"),
        g.el([1, 14.5, 6.5], [15, 16, 9.5], "#frame", faces={"east": None, "west": None}),
    ])})
    b["utrecht_light_grey_floor_tile"] = ("simple", {"": cube_all(P + "floor_tile")})

    b["leidsche_rijn_timber_soffit_canopy"] = ("simple", {"": g.model({"particle": P + "soffit", "top": P + "dark_roof", "under": P + "soffit", "side": P + "dark_steel"}, [
        g.el([0, 0, 0], [16, 3, 16], "#side", faces={"up": "#top", "down": "#under"}),
    ])})
    b["leidsche_rijn_perforated_cladding"] = ("simple", {"": cube_all(P + "perforated")})
    b["leidsche_rijn_concrete_platform_paver"] = ("simple", {"": g.cube_bottom_top(P + "paver", "concrete_light", "concrete_light")})

    b["amsterdam_red_brick"] = ("simple", {"": cube_all(P + "brick")})
    b["amsterdam_brick_sandstone_band"] = ("simple", {"": cube_all(P + "brick_band_side")})
    st = {"particle": P + "sandstone", "side": P + "sandstone", "dentil": P + "dentil"}
    b["amsterdam_stone_cornice"] = ("facing", {"": g.model(st, [
        g.el([0, 0, 10], [16, 5, 16], "#side"),
        g.el([0, 5, 7], [16, 9, 16], "#side", faces={"north": "#dentil"}),
        g.el([0, 9, 3], [16, 12, 16], "#side"),
        g.el([0, 12, 1], [16, 16, 16], "#side"),
    ])})
    b["amsterdam_arched_window"] = ("facing", {"": g.model({"particle": P + "brick", "wall": P + "brick", "window": P + "window"}, [
        g.el([0, 0, 6], [16, 16, 10], "#wall", faces={"north": "#window", "south": "#window"}),
    ])})
    for kind in ("leg", "arch"):
        b[f"amsterdam_cast_iron_shed_{kind}"] = ("facing", {"": g.model({"particle": P + "cast_iron", "side": P + "cast_iron", "truss": P + f"shed_{kind}"}, [
            g.el([0, 0, 7], [16, 16, 9], "#side", faces={"north": "#truss", "south": "#truss"}),
        ])})
    b["amsterdam_clock_face_panel"] = ("facing", {"": g.model({"particle": P + "gilt", "side": P + "gilt", "face": P + "clock_panel"}, [
        g.el([1, 1, 13], [15, 15, 16], "#side", faces={"north": ("#face", [1, 1, 15, 15])}),
    ])})

    sp = {"particle": P + "stainless", "side": P + "stainless"}
    b["rotterdam_stainless_roof_panel"] = ("simple", {"": g.model(sp, [g.el([0, 0, 0], [16, 2, 16], "#side")])})
    b["rotterdam_stainless_roof_slope"] = ("facing", {"": stepped(g.SLOPE_LOWER, {**sp, "top": P + "stainless", "under": P + "stainless"})})
    b["rotterdam_timber_slat_ceiling"] = ("simple", {"": cube_all(P + "slats")})
    b["rotterdam_black_stone_floor"] = ("simple", {"": cube_all(P + "black_stone")})

    b["antwerp_ornate_limestone"] = ("simple", {"": cube_all(P + "limestone")})
    b["antwerp_polished_marble_wall"] = ("simple", {"": cube_all(P + "marble_wall")})
    b["antwerp_marble_floor_tile"] = ("simple", {"": cube_all(P + "marble_floor")})
    b["antwerp_iron_glass_vault"] = ("facing", {"": vault()})
    b["antwerp_gilded_ornament_trim"] = ("facing", {"": g.model({"particle": P + "gilt", "side": P + "gilt", "orn": P + "gilt_trim"}, [
        g.el([0, 4, 13], [16, 12, 16], "#side", faces={"north": ("#orn", [0, 2, 16, 10])}),
        g.el([0, 12, 12], [16, 14, 16], "#side"),
        g.el([0, 2, 12], [16, 4, 16], "#side"),
    ])})

    sign = {}
    for left in (False, True):
        for right in (False, True):
            sign[("_l" if left else "") + ("_r" if right else "")] = g.sign_variant("station_name_sign", left, right, face=P + "sign_yellow", frame=P + "sign_frame", compact=True)
    b["dutch_station_sign"] = ("sign", sign)
    b["dutch_platform_sign"] = ("sign_single", {"": g.platform_number_model(P + "sign_frame", P + "platform_sign")})
    b["dutch_column_band"] = ("simple", {"": g.model({"particle": P + "column_band", "side": P + "column_band"}, [
        g.el([3.5, 5, 3.5], [12.5, 11, 12.5], "#side"),
    ])})
    return b


def vault():
    """Low glazed barrel vault: two gable ribs and two tilted glass panes meeting at a ridge."""
    t = {"particle": "steel_dark", "iron": P + "cast_iron", "glass": "roof_glass"}
    els = []
    for z1, z2 in ((0, 1.5), (14.5, 16)):
        els.append(g.el([0, 0, z1], [16, 3.4, z2], "#iron"))
        els.append(g.el([4, 3.4, z1], [12, 6.9, z2], "#iron"))
    pane = {"up": "#glass", "down": "#glass", "north": None, "south": None, "east": None, "west": None}
    els.append(g.el([0, 6.25, 1.5], [8, 6.75, 14.5], "#glass", faces=pane, rot=("z", 22.5, [8, 6.5, 8])))
    els.append(g.el([8, 6.25, 1.5], [16, 6.75, 14.5], "#glass", faces=pane, rot=("z", -22.5, [8, 6.5, 8])))
    return g.model(t, els)


def names():
    return {
        "utrecht_wave_roof_panel": "Utrecht Wave Roof Panel",
        "utrecht_wave_roof_rise": "Utrecht Wave Roof Rise",
        "utrecht_wave_roof_edge": "Utrecht Wave Roof Edge",
        "utrecht_tree_column_white": "Utrecht Tree Column",
        "utrecht_hall_glass_facade": "Utrecht Hall Glass Facade",
        "utrecht_light_grey_floor_tile": "Utrecht Light Grey Floor Tile",
        "leidsche_rijn_timber_soffit_canopy": "Leidsche Rijn Timber Soffit Canopy",
        "leidsche_rijn_perforated_cladding": "Leidsche Rijn Perforated Cladding",
        "leidsche_rijn_concrete_platform_paver": "Leidsche Rijn Concrete Platform Paver",
        "amsterdam_red_brick": "Amsterdam Red Brick",
        "amsterdam_brick_sandstone_band": "Amsterdam Brick with Sandstone Band",
        "amsterdam_stone_cornice": "Amsterdam Stone Cornice",
        "amsterdam_arched_window": "Amsterdam Arched Window",
        "amsterdam_cast_iron_shed_leg": "Amsterdam Cast Iron Shed Leg",
        "amsterdam_cast_iron_shed_arch": "Amsterdam Cast Iron Shed Arch",
        "amsterdam_clock_face_panel": "Amsterdam Clock Face Panel",
        "rotterdam_stainless_roof_panel": "Rotterdam Stainless Roof Panel",
        "rotterdam_stainless_roof_slope": "Rotterdam Stainless Roof Slope",
        "rotterdam_timber_slat_ceiling": "Rotterdam Timber Slat Ceiling",
        "rotterdam_black_stone_floor": "Rotterdam Black Stone Floor",
        "antwerp_ornate_limestone": "Antwerp Ornate Limestone",
        "antwerp_polished_marble_wall": "Antwerp Polished Marble Wall",
        "antwerp_marble_floor_tile": "Antwerp Marble Floor Tile",
        "antwerp_iron_glass_vault": "Antwerp Iron and Glass Vault",
        "antwerp_gilded_ornament_trim": "Antwerp Gilded Ornament Trim",
        "dutch_station_sign": "Dutch Station Sign",
        "dutch_platform_sign": "Dutch Platform Sign",
        "dutch_column_band": "Dutch Column Band",
    }


def lang():
    return {f"itemGroup.{MOD}.stations": "ATA European Stations"}


def recipes():
    r = {}

    def cut(result, ingredient, count=1):
        r[f"{result}_from_{ingredient.split(':')[-1]}_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": g.item(ingredient), "result": f"{MOD}:{result}", "count": count}

    def shapeless(result, ingredients, count=2):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) if not i.startswith("#") else {"tag": i[1:]} for i in ingredients],
                     "result": {"item": f"{MOD}:{result}", "count": count}}

    for result in ("utrecht_wave_roof_panel", "utrecht_wave_roof_rise", "utrecht_wave_roof_edge", "rotterdam_stainless_roof_panel", "rotterdam_stainless_roof_slope"):
        cut(result, "minecraft:iron_ingot", 4)
    cut("utrecht_tree_column_white", "minecraft:iron_ingot", 2)
    cut("utrecht_hall_glass_facade", "minecraft:glass", 2)
    cut("utrecht_light_grey_floor_tile", "minecraft:light_gray_concrete", 2)
    shapeless("leidsche_rijn_timber_soffit_canopy", ["minecraft:iron_ingot", "#minecraft:planks"], 4)
    cut("leidsche_rijn_perforated_cladding", "minecraft:iron_ingot", 2)
    cut("leidsche_rijn_concrete_platform_paver", "minecraft:light_gray_concrete", 2)
    cut("amsterdam_red_brick", "minecraft:bricks")
    shapeless("amsterdam_brick_sandstone_band", [f"{MOD}:amsterdam_red_brick", "minecraft:smooth_sandstone"])
    cut("amsterdam_stone_cornice", "minecraft:smooth_sandstone", 2)
    shapeless("amsterdam_arched_window", [f"{MOD}:amsterdam_red_brick", "minecraft:glass_pane"])
    shapeless("amsterdam_cast_iron_shed_leg", ["minecraft:iron_ingot", "minecraft:green_dye"])
    shapeless("amsterdam_cast_iron_shed_arch", ["minecraft:iron_ingot", "minecraft:green_dye", "minecraft:iron_nugget"])
    shapeless("amsterdam_clock_face_panel", ["minecraft:clock", "minecraft:gold_nugget", "minecraft:blue_dye"], 1)
    cut("rotterdam_timber_slat_ceiling", "minecraft:oak_planks", 2)
    cut("rotterdam_black_stone_floor", "minecraft:polished_blackstone", 2)
    cut("antwerp_ornate_limestone", "minecraft:smooth_sandstone")
    cut("antwerp_polished_marble_wall", "minecraft:quartz_block")
    cut("antwerp_marble_floor_tile", "minecraft:quartz_block", 2)
    shapeless("antwerp_iron_glass_vault", ["minecraft:glass", "minecraft:iron_ingot"])
    shapeless("antwerp_gilded_ornament_trim", ["minecraft:gold_nugget", "minecraft:smooth_sandstone"])
    shapeless("dutch_station_sign", ["minecraft:iron_ingot", "minecraft:yellow_dye", "minecraft:blue_dye", "minecraft:glowstone_dust"])
    shapeless("dutch_platform_sign", ["minecraft:iron_ingot", "minecraft:yellow_dye", "minecraft:blue_dye", "minecraft:chain"])
    shapeless("dutch_column_band", ["minecraft:paper", "minecraft:yellow_dye", "minecraft:blue_dye"])
    return r
