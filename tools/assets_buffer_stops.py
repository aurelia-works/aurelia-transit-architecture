"""Buffer stops (Jah, 2026-10-21: our own, after real ones, nothing taken from other mods): one block per track cell across
the track's end, joining its neighbours into one buffer (part = single, left, middle, right; left and right as seen by the
driver, so the left end is the east cell of a north-facing buffer).

- buffer_stop: a modern friction buffer. A beam striped red and white at buffer height, a round sprung head in front of
  each outer cell, posts braced back to a sled clamped on the rails, a red lamp on top in the middle.
- heritage_buffer_stop: the older rail-built kind. A timber baulk painted red on bent-rail uprights raked back to the rail,
  steel striking plates where the train's buffers meet it, a red lamp on top in the middle.

Models are authored front north (the side the train comes from), within the six-cuboid budget.
"""
import generate_assets as g

MOD = g.MOD

RED = (190, 34, 30)
WHITE = (234, 234, 228)
FRAME = (42, 44, 48)
HEAD_STEEL = (88, 92, 98)
RAIL = (96, 84, 74)
TIMBER_RED = (146, 42, 32)


def put(img, x, y, color, alpha=255):
    img.load()[x % 16, y % 16] = tuple(color[:3]) + (alpha,)


def beam():
    """Diagonal red and white bands four pixels wide, with a dark edge row top and bottom."""
    img = g.new()
    r = g.rng("buffer_beam")
    for y in range(16):
        for x in range(16):
            c = RED if ((x + y) // 4) % 2 == 0 else WHITE
            if y in (0, 15):
                c = g.shade(c, -50)
            put(img, x, y, g.shade(c, r.randint(-5, 5)))
    return img


def head():
    """A buffer head's face: a round steel plate, worn bright in the middle where buffers strike, on the dark frame."""
    img = g.noisy(FRAME, 4, "buffer_head")
    r = g.rng("buffer_head-plate")
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 7.6:
                c = HEAD_STEEL if d > 6.3 else g.shade(HEAD_STEEL, 28 - int(d * 3))
                put(img, x, y, g.shade(c, r.randint(-4, 4)))
    return img


def frame():
    """Painted black steel with faint wear."""
    img = g.noisy(FRAME, 5, "buffer_frame")
    r = g.rng("buffer_frame-wear")
    for _ in range(6):
        put(img, r.randrange(16), r.randrange(16), g.shade(FRAME, 22))
    return img


def lamp():
    """A red lens, brightest in the middle."""
    img = g.new()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            put(img, x, y, g.shade((214, 30, 24), max(-60, 40 - int(d * 9))))
    return img


def rail():
    """Old rail steel: rusty brown-grey, the running head (top rows) polished."""
    img = g.noisy(RAIL, 7, "buffer_rail")
    r = g.rng("buffer_rail-head")
    for x in range(16):
        for y in (0, 1):
            put(img, x, y, g.shade((150, 146, 140), r.randint(-6, 6)))
    return img


def timber():
    """A baulk painted red over its grain: streaks along it and a darker joint now and then."""
    img = g.new()
    r = g.rng("buffer_timber")
    for y in range(16):
        streak = r.randint(-10, 6)
        for x in range(16):
            c = g.shade(TIMBER_RED, streak + r.randint(-4, 4))
            if x == 11 and y > 3:
                c = g.shade(c, -26)
            put(img, x, y, c)
    return img


def textures():
    return {"buffer_beam": beam(), "buffer_head": head(), "buffer_frame": frame(), "buffer_lamp": lamp(), "buffer_rail": rail(),
            "buffer_timber": timber()}


def mirror(els):
    """The same elements reflected across the block's middle (x -> 16 - x); a tilted chord keeps its tilt about X."""
    out = []
    for e in els:
        m = dict(e)
        m["from"] = [round(16 - e["to"][0], 4), e["from"][1], e["from"][2]]
        m["to"] = [round(16 - e["from"][0], 4), e["to"][1], e["to"][2]]
        faces = dict(e["faces"])
        if "east" in faces or "west" in faces:
            east, west = faces.pop("east", None), faces.pop("west", None)
            if west is not None:
                faces["east"] = dict(west, cullface="east") if "cullface" in west else west
            if east is not None:
                faces["west"] = dict(east, cullface="west") if "cullface" in east else east
        m["faces"] = faces
        if "rotation" in e:
            rot = dict(e["rotation"])
            rot["origin"] = [round(16 - rot["origin"][0], 4)] + rot["origin"][1:]
            m["rotation"] = rot
        out.append(m)
    return out


def strut(x0, x1, tex):
    """A brace raked back at 45 degrees from the beam's back to the rail."""
    return g.chord((7, 9.5), (15.5, 1), 2.5, span=(x0, x1), tex=tex, inset=(0, 1.2))


MODERN_TEX = {"particle": "buffer_frame", "beam": "buffer_beam", "head": "buffer_head", "frame": "buffer_frame", "lamp": "buffer_lamp",
              "steel": "steel"}
HERITAGE_TEX = {"particle": "buffer_timber", "timber": "buffer_timber", "rail": "buffer_rail", "plate": "buffer_head", "lamp": "buffer_lamp",
                "frame": "buffer_frame"}


def modern():
    left = [
        g.el([0, 7, 4], [15, 12, 7], "#beam"),
        g.el([3, 7, 1], [8, 12, 2], "#steel", faces={"north": "#head"}),
        g.el([4.5, 8.5, 2], [6.5, 10.5, 4], "#steel"),
        g.el([4, 0, 7], [7, 12, 9], "#frame"),
        strut(4, 7, "#frame"),
        g.el([3, 0, 2], [8, 1.5, 15], "#frame"),
    ]
    middle = [
        g.el([0, 7, 4], [16, 12, 7], "#beam"),
        g.el([6, 12, 4.5], [10, 15, 7.5], "#frame"),
        g.el([6.5, 12.5, 4], [9.5, 14.5, 4.5], "#lamp", shade_=False),
        g.el([0, 1, 13], [16, 2.5, 14.5], "#frame"),
    ]
    single = [
        g.el([1, 7, 4], [15, 12, 7], "#beam"),
        g.el([1.5, 7.5, 2], [5.5, 11.5, 4], "#steel", faces={"north": "#head"}),
        g.el([10.5, 7.5, 2], [14.5, 11.5, 4], "#steel", faces={"north": "#head"}),
        g.el([3, 0, 2], [13, 1.5, 15], "#frame"),
        strut(6, 10, "#frame"),
        g.el([6, 12, 4.5], [10, 15, 7.5], "#frame", faces={"north": "#lamp"}),
    ]
    return variants(MODERN_TEX, left, middle, single)


def heritage():
    left = [
        g.el([0, 6, 3], [15, 11, 8], "#timber"),
        g.el([3, 7, 2.5], [8, 10, 3], "#plate"),
        g.el([4.5, 0, 8], [6.5, 12, 10], "#rail"),
        g.chord((9, 9), (16, 2), 2, span=(4.5, 6.5), tex="#rail", inset=(0, 1)),
        g.el([4, 0, 2], [7, 1, 16], "#rail"),
    ]
    middle = [
        g.el([0, 6, 3], [16, 11, 8], "#timber"),
        g.el([7, 11, 5], [9, 13.5, 7], "#frame"),
        g.el([7.25, 11.5, 4.5], [8.75, 13, 5], "#lamp", shade_=False),
    ]
    single = [
        g.el([1, 6, 3], [15, 11, 8], "#timber"),
        g.el([3, 0, 8], [5, 12, 10], "#rail"),
        g.el([11, 0, 8], [13, 12, 10], "#rail"),
        g.chord((9, 9), (16, 2), 2, span=(3, 5), tex="#rail", inset=(0, 1)),
        g.chord((9, 9), (16, 2), 2, span=(11, 13), tex="#rail", inset=(0, 1)),
        g.el([7, 11, 5], [9, 13.5, 7], "#frame", faces={"north": "#lamp"}),
    ]
    return variants(HERITAGE_TEX, left, middle, single)


def variants(tex, left, middle, single):
    return {"": g.model(tex, single), "_left": g.model(tex, left), "_middle": g.model(tex, middle), "_right": g.model(tex, mirror(left))}


NAMES = {"buffer_stop": "Buffer Stop", "heritage_buffer_stop": "Heritage Buffer Stop"}


def blocks():
    return {"buffer_stop": ("buffer_stop", modern()), "heritage_buffer_stop": ("buffer_stop", heritage())}


def names():
    return dict(NAMES)


def recipes():
    def shaped(result, pattern, key, count):
        return {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern,
                "key": {k: g.item(v) for k, v in key.items()}, "result": {"item": f"{MOD}:{result}", "count": count}}

    return {
        "buffer_stop": shaped("buffer_stop", ["RWR", "I I", "III"], {"R": "minecraft:red_dye", "W": "minecraft:white_dye", "I": "minecraft:iron_ingot"}, 3),
        "heritage_buffer_stop": shaped("heritage_buffer_stop", ["RLR", "I I", "I I"], {"R": "minecraft:red_dye", "L": "minecraft:oak_log",
                                                                                     "I": "minecraft:iron_ingot"}, 3),
    }
