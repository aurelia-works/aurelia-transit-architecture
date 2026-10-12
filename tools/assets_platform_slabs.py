"""1.6 platform slabs: a half-height slab of every walkable platform surface, so a platform can sit level with a train floor.

Same extension interface as the other assets_*.py modules. Each slab uses its full block's textures and Minecraft's own slab
models (bottom, top; a double slab shows the full block), and carries the same MTR door marker in Java
(MtrPlatformContract.slab). Recipes follow vanilla: three full blocks in a row make six slabs, the stonecutter makes two from one.
A double slab drops two.
"""
import generate_assets as g

MOD = g.MOD

# slab id -> (full block id, English name, top texture, side and bottom texture)
SLABS = {
    "platform_paving_light_slab": ("platform_paving_light", "Light Platform Paving Slab", "platform_paving_light", "concrete_light"),
    "platform_paving_dark_slab": ("platform_paving_dark", "Dark Platform Paving Slab", "platform_paving_dark", "concrete_dark"),
    "tactile_warning_paving_slab": ("tactile_warning_paving", "Tactile Warning Paving Slab", "tactile_warning_paving", "concrete_light"),
    "leidsche_rijn_concrete_platform_paver_slab": ("leidsche_rijn_concrete_platform_paver", "Leidsche Rijn Concrete Platform Paver Slab",
                                                   "nlbe_paver", "concrete_light"),
    "sbahn_platform_half_slab": ("sbahn_platform_slab", "S-Bahn Platform Half Slab", "sbahn_platform_slab", "sbahn_platform_slab"),
    "metro_granite_floor_slab": ("metro_granite_floor", "Munich Metro Granite Floor Slab", "metro_granite_floor", "metro_concrete_plain"),
    "metro_terrazzo_floor_slab": ("metro_terrazzo_floor", "Amsterdam Metro Terrazzo Floor Slab", "metro_terrazzo_floor", "metro_concrete_plain"),
    "metro_quarry_floor_slab": ("metro_quarry_floor", "Washington Metro Quarry Tile Floor Slab", "metro_quarry_floor", "metro_concrete_plain"),
}


def blocks():
    b = {}
    for slab, (full, _, top, side) in SLABS.items():
        textures = {"bottom": f"{MOD}:block/{side}", "top": f"{MOD}:block/{top}", "side": f"{MOD}:block/{side}"}
        # the blockstate is rewritten for the slab's type property in write_extra
        b[slab] = ("simple", {
            "": {"parent": "minecraft:block/slab", "textures": textures},
            "_top": {"parent": "minecraft:block/slab_top", "textures": textures},
        })
    return b


def names():
    return {slab: name for slab, (_, name, _, _) in SLABS.items()}


def recipes():
    r = {}
    for slab, (full, _, _, _) in SLABS.items():
        r[slab] = {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                   "key": {"#": g.item(full)}, "result": {"item": f"{MOD}:{slab}", "count": 6}}
        r[f"{slab}_from_{full}_stonecutting"] = {"type": "minecraft:stonecutting", "ingredient": g.item(full),
                                                 "result": f"{MOD}:{slab}", "count": 2}
    return r


def write_extra(assets, data, write_json):
    for slab, (full, _, _, _) in SLABS.items():
        write_json(assets / "blockstates" / f"{slab}.json", {"variants": {
            "type=bottom": {"model": f"{MOD}:block/{slab}"},
            "type=top": {"model": f"{MOD}:block/{slab}_top"},
            "type=double": {"model": f"{MOD}:block/{full}"},
        }})
        name = f"{MOD}:{slab}"
        write_json(data / MOD / "loot_tables" / "blocks" / f"{slab}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{
                "type": "minecraft:item", "name": name,
                "functions": [
                    {"function": "minecraft:set_count", "count": 2, "add": False,
                     "conditions": [{"condition": "minecraft:block_state_property", "block": name, "properties": {"type": "double"}}]},
                    {"function": "minecraft:explosion_decay"},
                ],
            }]}],
        })
