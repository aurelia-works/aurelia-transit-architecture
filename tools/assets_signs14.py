"""1.4 hand-typed signs: PSD text panel, stand back sign, train composition board.

Same extension interface as the other assets_*.py modules. Models face NORTH and use the generator's "sign" kind (facing + left/right
joins); the faces are plain, text is drawn by the renderer. All art is original and generic: no operator branding.
"""
import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
SCREEN = f"screen.{MOD}."
FRAME = "steel"


def textures():
    t = {}
    face = g.noisy((22, 25, 30), 1, "psd_panel_face")
    g.rect(face, 0, 0, 16, 1, (30, 34, 40))
    t["psd_panel_face"] = face

    warn = g.noisy((250, 200, 20), 2, "warning_yellow")
    for i in range(16):
        for j in (0, 1, 15):
            for a, b in ((i, j), (j, i)):
                if 0 <= a < 16 and 0 <= b < 16:
                    warn.load()[a, b] = (24, 24, 24, 255)
    t["warning_yellow"] = warn

    t["composition_face"] = g.noisy((40, 52, 66), 2, "composition_face")
    return t


def psd_panel(left, right):
    els = [
        g.el([0, 4.5, 13], [16, 11.5, 16], "#frame", faces={"north": "#face", "west": None, "east": None, "up": None, "down": None}),
        g.el([0, 11.5, 12.75], [16, 12, 16], "#frame"),
        g.el([0, 4, 12.75], [16, 4.5, 16], "#frame"),
    ]
    if not left:
        els.append(g.el([0, 4.5, 12.75], [0.75, 11.5, 16], "#frame"))
    if not right:
        els.append(g.el([15.25, 4.5, 12.75], [16, 11.5, 16], "#frame"))
    return g.model({"particle": FRAME, "frame": FRAME, "face": "psd_panel_face"}, els)


def stand_back():
    return g.model({"particle": FRAME, "frame": FRAME, "face": "warning_yellow"}, [
        g.el([1, 2, 14], [15, 14, 16], "#frame", faces={"north": "#face"}),
    ])


def composition(left, right):
    els = [
        g.el([0, 3.5, 6.5], [16, 10.5, 9.5], "#frame", faces={"north": "#face", "south": "#face"}),
        g.el([0, 10.5, 6.25], [16, 11, 9.75], "#frame"),
        g.el([0, 3, 6.25], [16, 3.5, 9.75], "#frame"),
    ]
    if not left:
        els += [g.el([0, 3.5, 6.25], [0.75, 10.5, 9.75], "#frame"), g.el([3, 11, 7.5], [5, 16, 8.5], "#frame")]
    if not right:
        els += [g.el([15.25, 3.5, 6.25], [16, 10.5, 9.75], "#frame"), g.el([11, 11, 7.5], [13, 16, 8.5], "#frame")]
    return g.model({"particle": FRAME, "frame": FRAME, "face": "composition_face"}, els)


def joined(make):
    return {("_l" if left else "") + ("_r" if right else ""): make(left, right) for left in (False, True) for right in (False, True)}


def blocks():
    return {
        "psd_text_panel": ("sign", joined(psd_panel)),
        "stand_back_sign": ("sign", joined(lambda left, right: stand_back())),
        "composition_board": ("sign", joined(composition)),
    }


def names():
    return {"psd_text_panel": "PSD Text Panel", "stand_back_sign": "Stand Back Sign", "composition_board": "Train Composition Board"}


def lang():
    return {
        TIP + "psd_text_panel": "Text typed by hand for platform screen doors; MTR's own PSD text is not reachable",
        TIP + "stand_back_sign": "Warning for non-stopping trains, set by hand (MTR does not report through trains). Empty text reads \"Stand back\"",
        TIP + "composition_board": "Cars typed by hand left to right (\"1+ 2 3 | 4 5!\": + first class, ! accessible, | unit gap); second line: sector letters",
        SCREEN + "warning_default_primary": "Stand back",
        SCREEN + "warning_default_secondary": "Non-stopping trains",
    }


def recipes():
    r = {}

    def shapeless(result, ingredients, count=2):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients], "result": {"item": f"{MOD}:{result}", "count": count}}

    shapeless("psd_text_panel", ["minecraft:iron_ingot", "minecraft:black_dye", "minecraft:glowstone_dust"])
    shapeless("stand_back_sign", ["minecraft:iron_ingot", "minecraft:yellow_dye", "minecraft:black_dye"])
    shapeless("composition_board", ["minecraft:iron_ingot", "minecraft:gray_dye", "minecraft:glowstone_dust", "minecraft:chain"])
    return r
