"""1.7 airport block assets: airfield ground lights, apron floodlight, check-in desk, baggage belt, departures board, windsock and
terminal terrazzo.

Standalone on purpose: it writes only these blocks' files (textures, block and item models, blockstates, loot tables, stonecutter
recipes) and adds their lang lines and pickaxe tag entries in place. It never runs generate_assets.main(), which wipes hand-made
assets. Run from the repo root: python3 tools/assets_airport.py. Models face NORTH; facing blockstates rotate them. All art is
original and generic.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_assets as g  # noqa: E402  (helpers only; its main() is never called)

MOD = g.MOD
ASSETS = g.ASSETS
DATA = g.DATA
TEX = ASSETS / "textures" / "block"

ASPHALT = (42, 44, 47)
TAXI_GREY = (112, 115, 118)
STEEL = g.STEEL
STEEL_DARK = g.STEEL_DARK


def lens(img, colour, rim=(150, 154, 158)):
    """A round flush lamp in the middle of a ground light: a steel rim and a bright lens."""
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 2.6:
                px[x, y] = colour + (255,)
            elif d <= 3.6:
                px[x, y] = rim + (255,)
    px[7, 7] = (255, 255, 255, 255)
    return img


def textures():
    t = {}
    t["runway_edge_light_top"] = lens(g.noisy(ASPHALT, 4, "rw_edge"), (255, 250, 232))
    t["runway_threshold_light_top"] = lens(g.noisy(ASPHALT, 4, "rw_thr"), (96, 236, 120))
    t["airfield_asphalt"] = g.noisy(ASPHALT, 4, "asphalt")
    t["taxiway_edge_light_top"] = lens(g.noisy(TAXI_GREY, 4, "tw_edge"), (70, 120, 255))
    t["airfield_concrete"] = g.noisy(TAXI_GREY, 4, "tw_concrete")

    housing = g.noisy((70, 74, 78), 3, "flood_housing")
    g.rect(housing, 0, 0, 16, 2, (52, 55, 58))
    t["floodlight_housing"] = housing
    lamp = g.new((255, 246, 214))
    for x in range(1, 16, 3):
        g.rect(lamp, x, 0, x + 1, 16, (236, 226, 196))
    t["floodlight_lens"] = lamp

    desk_front = g.noisy((206, 208, 210), 2, "desk_front")
    g.rect(desk_front, 0, 0, 16, 2, (44, 62, 92))  # the counter's top edge
    g.rect(desk_front, 0, 8, 16, 9, (150, 154, 158))
    t["check_in_desk_front"] = desk_front
    desk_top = g.noisy((70, 74, 80), 2, "desk_top")
    g.rect(desk_top, 0, 0, 16, 5, (196, 198, 200))  # the passengers' shelf
    t["check_in_desk_top"] = desk_top
    screen = g.new((24, 38, 66))
    g.rect(screen, 1, 1, 15, 3, (236, 196, 64))
    for y in (5, 8, 11):
        g.rect(screen, 2, y, 13, y + 1, (226, 230, 236))
    t["check_in_desk_screen"] = screen

    belt = g.new((30, 31, 33))
    for y in range(0, 16, 2):
        g.rect(belt, 2, y, 14, y + 1, (52, 54, 57))
    g.rect(belt, 0, 0, 2, 16, (168, 172, 176))
    g.rect(belt, 14, 0, 16, 16, (168, 172, 176))
    t["baggage_belt_top"] = belt
    side = g.noisy((168, 172, 176), 2, "belt_side")
    g.rect(side, 0, 0, 16, 2, (120, 124, 128))
    t["baggage_belt_side"] = side

    board = g.new((14, 16, 20))
    g.rect(board, 0, 0, 16, 1, (96, 100, 106))
    g.rect(board, 0, 15, 16, 16, (96, 100, 106))
    g.rect(board, 1, 2, 15, 4, (240, 190, 40))  # the header band
    for i, y in enumerate((6, 8, 10, 12)):
        g.rect(board, 1, y, 4, y + 1, (240, 190, 40))  # time
        g.rect(board, 5, y, 11, y + 1, (232, 234, 238))  # destination
        g.rect(board, 12, y, 15, y + 1, (96, 220, 120) if i % 2 == 0 else (240, 190, 40))  # gate or status
    t["departures_board_face"] = board

    sock = g.new()
    for x in range(16):
        stripe = (x // 4) % 2 == 0
        g.rect(sock, x, 0, x + 1, 16, (240, 110, 30) if stripe else (240, 240, 236))
    t["windsock_cloth"] = sock

    terrazzo = g.noisy((226, 224, 218), 3, "terrazzo")
    r = g.rng("terrazzo_chips")
    px = terrazzo.load()
    for _ in range(26):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = r.choice(((150, 152, 156), (186, 176, 160), (110, 114, 120), (204, 196, 182))) + (255,)
    t["terminal_terrazzo"] = terrazzo
    return t


def cube(top, side, bottom=None):
    return {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": g.tx(top), "side": g.tx(side), "bottom": g.tx(bottom or side)}}


def models():
    m = {}
    m["runway_edge_light"] = cube("runway_edge_light_top", "airfield_asphalt")
    m["runway_threshold_light"] = cube("runway_threshold_light_top", "airfield_asphalt")
    m["taxiway_edge_light"] = cube("taxiway_edge_light_top", "airfield_concrete")
    m["terminal_terrazzo"] = {"parent": "minecraft:block/cube_all", "textures": {"all": g.tx("terminal_terrazzo")}}
    m["apron_floodlight"] = g.model({"particle": "floodlight_housing", "housing": "floodlight_housing", "lens": "floodlight_lens"}, [
        g.el([7, 0, 7], [9, 8, 9], "#housing"),
        g.el([2, 8, 2], [14, 16, 14], "#housing", faces={"down": "#lens"}),
    ])
    m["check_in_desk"] = g.model({"particle": "check_in_desk_front", "front": "check_in_desk_front", "top": "check_in_desk_top",
                                  "steel": "steel", "screen": "check_in_desk_screen"}, [
        g.el([0, 0, 4], [16, 10, 16], "#front", faces={"up": "#top", "south": "#steel", "down": "#steel"}),
        g.el([7, 10, 13], [9, 14, 15], "#steel"),
        g.el([3, 14, 12], [13, 20, 14], "#steel", faces={"north": "#screen"}),
    ])
    m["baggage_belt"] = g.model({"particle": "baggage_belt_side", "top": "baggage_belt_top", "side": "baggage_belt_side"}, [
        g.el([0, 0, 0], [16, 7, 16], "#side", faces={"up": "#top"}),
    ])
    m["departures_board"] = g.model({"particle": "departures_board_face", "face": "departures_board_face", "steel": "steel_dark"}, [
        g.el([0, 2, 13], [16, 14, 16], "#steel", faces={"north": "#face"}),
    ])
    m["windsock"] = g.model({"particle": "windsock_cloth", "steel": "steel", "cloth": "windsock_cloth"}, [
        g.el([7, 0, 7], [9, 12, 9], "#steel"),
        g.el([5, 9, 2], [11, 15, 8], "#cloth"),
        g.el([6, 10, 0], [10, 14, 2], "#cloth"),
    ], ao=False)
    return m


FACING = ("check_in_desk", "departures_board", "windsock")
BLOCKS = ("runway_edge_light", "runway_threshold_light", "taxiway_edge_light", "apron_floodlight", "check_in_desk", "baggage_belt",
          "departures_board", "windsock", "terminal_terrazzo")
NAMES = {
    "runway_edge_light": "Runway Edge Light", "runway_threshold_light": "Runway Threshold Light", "taxiway_edge_light": "Taxiway Edge Light",
    "apron_floodlight": "Apron Floodlight", "check_in_desk": "Check-in Desk", "baggage_belt": "Baggage Belt",
    "departures_board": "Departures Board", "windsock": "Windsock", "terminal_terrazzo": "Terminal Terrazzo",
}
# stonecutter: (ingredient, count)
RECIPES = {
    "runway_edge_light": ("minecraft:glowstone", 4), "runway_threshold_light": ("minecraft:glowstone", 4), "taxiway_edge_light": ("minecraft:glowstone", 4),
    "apron_floodlight": ("minecraft:iron_ingot", 2), "check_in_desk": ("minecraft:iron_ingot", 2), "baggage_belt": ("minecraft:iron_ingot", 4),
    "departures_board": ("minecraft:iron_ingot", 2), "windsock": ("minecraft:iron_ingot", 2), "terminal_terrazzo": ("minecraft:smooth_stone", 1),
}
LANG = {f"block.{MOD}.{b}": n for b, n in NAMES.items()} | {
    f"itemGroup.{MOD}.airport": "Aurelia Transit: Airport",
    f"tooltip.{MOD}.airport_ground_light": "Set flush into the airfield; gives light",
}


def write_lang():
    path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(path.read_text(encoding="utf-8"))
    lang.update({k: v for k, v in LANG.items() if k not in lang})
    path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_tag():
    path = DATA / "minecraft" / "tags" / "blocks" / "mineable" / "pickaxe.json"
    tag = json.loads(path.read_text(encoding="utf-8"))
    for b in BLOCKS:
        if f"{MOD}:{b}" not in tag["values"]:
            tag["values"].append(f"{MOD}:{b}")
    path.write_text(json.dumps(tag, indent=2) + "\n", encoding="utf-8")


def main():
    for name, img in textures().items():
        img.save(TEX / f"{name}.png")
    for name, data in models().items():
        g.write_json(ASSETS / "models" / "block" / f"{name}.json", data)
    for b in BLOCKS:
        if b in FACING:
            state = {"variants": {f"facing={f}": {"model": f"{MOD}:block/{b}"} | ({"y": y} if y else {}) for f, y in g.FACING_Y.items()}}
        else:
            state = {"variants": {"": {"model": f"{MOD}:block/{b}"}}}
        g.write_json(ASSETS / "blockstates" / f"{b}.json", state)
        g.write_json(ASSETS / "models" / "item" / f"{b}.json", {"parent": f"{MOD}:block/{b}"})
        g.write_json(DATA / MOD / "loot_tables" / "blocks" / f"{b}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        ingredient, count = RECIPES[b]
        g.write_json(DATA / MOD / "recipes" / f"{b}_from_{ingredient.split(':')[1]}_stonecutting.json", {
            "type": "minecraft:stonecutting", "ingredient": {"item": ingredient}, "result": f"{MOD}:{b}", "count": count})
    write_lang()
    write_tag()
    print(f"airport assets: {len(BLOCKS)} blocks")


if __name__ == "__main__":
    main()
