"""Station structure blocks for the generator's train sheds: the connecting station girder (four colours).

Same extension interface as the other assets_*.py modules. The girder's blockstate is a multipart: the centre node always,
an I-section arm toward each joined side, a stiffener when it continues up or down, and, when nothing is joined, both arms
along its axis. Ambient occlusion is off so a run of girders shades as one piece instead of block-by-block bands.
"""
import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}."
MSG = f"message.{MOD}.style."

GIRDER = {
    "steel": (62, 67, 72),
    "white": (226, 228, 226),
    "blue": (104, 150, 186),
    "green": (66, 96, 82),
}
SIDES = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def girder_texture(kind):
    base = GIRDER[kind]
    img = g.brushed(base, f"girder_{kind}", vertical=False)
    px = img.load()
    # flange edge lines and a rivet row along each flange
    for x in range(16):
        px[x, 0] = g.shade(base, -16) + (255,)
        px[x, 15] = g.shade(base, -16) + (255,)
    for x in range(1, 16, 4):
        for y in (2, 13):
            px[x, y] = g.shade(base, 26) + (255,)
            px[x, y + 1] = g.shade(base, -18) + (255,)
    return img


def textures():
    return {f"girder_{k}": girder_texture(k) for k in GIRDER}


def node(kind):
    return g.model({"particle": f"girder_{kind}", "s": f"girder_{kind}"}, [
        g.el([6, 2, 6], [10, 14, 10], "#s"),
        g.el([4, 0, 4], [12, 2, 12], "#s"),
        g.el([4, 14, 4], [12, 16, 12], "#s"),
    ], ao=False)


def arm(kind):
    """The arm toward north (z = 0); the blockstate turns it for the other sides."""
    return g.model({"particle": f"girder_{kind}", "s": f"girder_{kind}"}, [
        g.el([6, 2, 0], [10, 14, 6], "#s", skip=("south",)),
        g.el([4, 0, 0], [12, 2, 6], "#s", skip=("south",)),
        g.el([4, 14, 0], [12, 16, 6], "#s", skip=("south",)),
    ], ao=False)


def vertical(kind):
    return g.model({"particle": f"girder_{kind}", "s": f"girder_{kind}"}, [g.el([4, 0, 7], [12, 16, 9], "#s")], ao=False)


def blocks():
    models = {"": node("steel")}
    for k in GIRDER:
        models[f"_node_{k}"] = node(k)
        models[f"_arm_{k}"] = arm(k)
        models[f"_vertical_{k}"] = vertical(k)
    return {"station_girder": ("simple", models)}


def names():
    return {"station_girder": "Station Roof Girder"}


def lang():
    out = {TIP + "girder_joins": "Joins girders on every side, and walls"}
    out |= {MSG + f"station_girder.{k}": f"Girder: {k}" for k in GIRDER}
    return out


def recipes():
    return {"station_girder_from_iron_ingot_stonecutting": {
        "type": "minecraft:stonecutting", "ingredient": {"item": "minecraft:iron_ingot"},
        "result": f"{MOD}:station_girder", "count": 2}}


def write_extra(assets, data, write_json):
    ref = lambda suffix: f"{MOD}:block/station_girder{suffix}"
    alone = {"north": "false", "east": "false", "south": "false", "west": "false", "up": "false", "down": "false"}
    parts = []
    for k in GIRDER:
        parts.append({"when": {"kind": k}, "apply": {"model": ref(f"_node_{k}")}})
        for side, y in SIDES:
            axis = "z" if side in ("north", "south") else "x"
            parts.append({"when": {"OR": [{"kind": k, side: "true"}, {"kind": k, "axis": axis} | alone]},
                          "apply": {"model": ref(f"_arm_{k}")} | ({"y": y} if y else {})})
        parts.append({"when": {"OR": [{"kind": k, "up": "true"}, {"kind": k, "down": "true"}]}, "apply": {"model": ref(f"_vertical_{k}")}})
    write_json(assets / "blockstates" / "station_girder.json", {"multipart": parts})
    # inventory: a straight run along x
    item = node("steel")
    a = arm("steel")
    for side in (90, 270):
        for e in a["elements"]:
            f, t = e["from"], e["to"]
            # turn the north arm to east (90) or west (270) around the block centre
            if side == 90:
                nf, nt = [16 - t[2], f[1], f[0]], [16 - f[2], t[1], t[0]]
            else:
                nf, nt = [f[2], f[1], 16 - t[0]], [t[2], t[1], 16 - f[0]]
            item["elements"].append(g.el(nf, nt, "#s"))
    item["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}}
    write_json(assets / "models" / "item" / "station_girder.json", item)
