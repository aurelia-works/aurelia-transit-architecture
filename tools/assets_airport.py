"""1.7 airport block assets: airfield ground lights, apron floodlight, check-in desk, baggage belt, departures board, windsock and
terminal terrazzo; and the terminal's security and gate blocks (Null, 10-26): walk-through metal detector, X-ray bag scanner,
inspection table, queue posts with belts, self check-in kiosk, bag drop, baggage carousel (straight and corner), gate desk, luggage
trolley and linked airport seats.

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

    casing = g.noisy((214, 216, 218), 2, "casing")
    g.rect(casing, 0, 0, 16, 1, (176, 180, 184))
    g.rect(casing, 0, 15, 16, 16, (176, 180, 184))
    t["security_casing"] = casing
    lamp_strip = g.noisy((214, 216, 218), 2, "arch_lamp")
    g.rect(lamp_strip, 2, 6, 14, 10, (40, 44, 48))
    for x in range(3, 13, 3):
        g.rect(lamp_strip, x, 7, x + 2, 9, (90, 220, 110))  # the go/stop lights over the arch
    t["security_arch_lamps"] = lamp_strip
    curtain = g.new((26, 28, 30))
    for x in range(0, 16, 2):
        g.rect(curtain, x, 0, x + 1, 16, (44, 47, 50))  # the lead strips hanging in the tunnel mouth
    g.rect(curtain, 0, 0, 16, 2, (214, 216, 218))
    t["xray_scanner_curtain"] = curtain
    xside = g.noisy((214, 216, 218), 2, "xray_side")
    g.rect(xside, 3, 4, 13, 7, (60, 120, 200))  # a blue band along the casing
    t["xray_scanner_side"] = xside

    strap = g.new((34, 62, 140))
    g.rect(strap, 0, 0, 16, 1, (24, 44, 104))
    g.rect(strap, 0, 15, 16, 16, (24, 44, 104))
    t["queue_belt"] = strap

    kiosk_screen = g.new((20, 64, 140))
    g.rect(kiosk_screen, 1, 1, 15, 3, (236, 240, 244))
    for y in (5, 8, 11):
        g.rect(kiosk_screen, 3, y, 13, y + 2, (90, 170, 240))  # big touch buttons
    g.rect(kiosk_screen, 0, 0, 16, 0, (14, 16, 20))
    t["kiosk_screen"] = kiosk_screen
    kiosk_body = g.noisy((232, 233, 235), 2, "kiosk_body")
    g.rect(kiosk_body, 4, 12, 12, 13, (40, 44, 48))  # the boarding pass slot
    t["kiosk_body"] = kiosk_body

    plates = g.new((58, 60, 64))
    for y in range(0, 16, 4):
        g.rect(plates, 0, y, 16, y + 1, (36, 38, 41))  # overlapping slat edges
    t["carousel_plates"] = plates

    gate_front = g.noisy((34, 48, 84), 2, "gate_front")
    g.rect(gate_front, 0, 0, 16, 2, (196, 198, 200))
    g.rect(gate_front, 2, 6, 14, 8, (232, 234, 238))
    t["gate_desk_front"] = gate_front

    seat = g.noisy((52, 64, 86), 2, "airport_seat")
    for x in range(1, 16, 3):
        for y in range(1, 16, 3):
            seat.load()[x, y] = (38, 46, 62, 255)  # perforations
    t["airport_seat"] = seat
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

    # Null's security and gate blocks (10-26). Fronts face NORTH: the passenger's side, or the way through.
    m["security_arch"] = g.model({"particle": "security_casing", "c": "security_casing", "lamps": "security_arch_lamps"}, [
        g.el([0, 0, 5], [3, 30, 11], "#c"),
        g.el([13, 0, 5], [16, 30, 11], "#c"),
        g.el([0, 30, 5], [16, 32, 11], "#c", faces={"north": "#lamps", "south": "#lamps"}),
        g.el([3, 0, 4], [13, 0.25, 12], "#c"),
    ])
    m["xray_scanner"] = g.model({"particle": "security_casing", "c": "security_casing", "side": "xray_scanner_side",
                                 "belt": "baggage_belt_top", "curtain": "xray_scanner_curtain"}, [
        g.el([0, 0, 0], [16, 5, 16], "#c", faces={"up": "#belt"}),
        g.el([0, 5, 0], [2, 14, 16], "#side"),
        g.el([14, 5, 0], [16, 14, 16], "#side"),
        g.el([0, 14, 0], [16, 16, 16], "#c", faces={"north": "#curtain", "south": "#curtain"}),
    ])
    m["security_desk"] = g.model({"particle": "steel", "steel": "steel", "dark": "steel_dark"}, [
        g.el([0, 13, 1], [16, 14.5, 15], "#steel"),
        g.el([1, 0, 3], [15, 13, 13], "#dark"),
    ])
    m["queue_post"] = g.model({"particle": "steel", "steel": "steel", "dark": "steel_dark"}, [
        g.el([5, 0, 5], [11, 1, 11], "#dark"),
        g.el([7, 1, 7], [9, 14, 9], "#steel"),
        g.el([6.5, 14, 6.5], [9.5, 15.5, 9.5], "#steel"),
    ])
    m["queue_post_belt"] = g.model({"particle": "queue_belt", "belt": "queue_belt"}, [
        g.el([7.75, 11.5, 0], [8.25, 14, 7], "#belt"),
    ], ao=False)
    m["self_check_in_kiosk"] = g.model({"particle": "kiosk_body", "body": "kiosk_body", "screen": "kiosk_screen", "dark": "steel_dark"}, [
        g.el([3, 0, 4], [13, 1, 12], "#dark"),
        g.el([5, 1, 6], [11, 12, 11], "#body"),
        g.el([2, 12, 4], [14, 24, 12], "#body", faces={"north": "#screen"}),
    ])
    m["bag_drop"] = g.model({"particle": "kiosk_body", "body": "kiosk_body", "screen": "kiosk_screen", "belt": "baggage_belt_top",
                             "side": "baggage_belt_side"}, [
        g.el([0, 0, 0], [16, 7, 11], "#side", faces={"up": "#belt"}),
        g.el([4, 0, 11], [12, 18, 15], "#body"),
        g.el([3, 18, 10], [13, 24, 15], "#body", faces={"north": "#screen"}),
    ])
    m["baggage_carousel"] = g.model({"particle": "steel", "steel": "steel", "plates": "carousel_plates"}, [
        g.el([0, 0, 0], [16, 5, 16], "#steel", faces={"up": "#plates"}),
        g.el([0, 5, 0], [16, 7, 1], "#steel"),
    ])
    m["baggage_carousel_corner"] = g.model({"particle": "steel", "steel": "steel", "plates": "carousel_plates"}, [
        g.el([0, 0, 0], [16, 5, 16], "#steel", faces={"up": "#plates"}),
        g.el([0, 5, 0], [16, 7, 1], "#steel"),
        g.el([0, 5, 1], [1, 7, 16], "#steel"),
    ])
    m["gate_desk"] = g.model({"particle": "gate_desk_front", "front": "gate_desk_front", "top": "check_in_desk_top", "steel": "steel",
                              "screen": "check_in_desk_screen"}, [
        g.el([1, 0, 4], [15, 15, 14], "#front", faces={"up": "#top", "south": "#steel"}),
        g.el([4, 15, 11], [12, 21, 13], "#steel", faces={"south": "#screen"}),
    ])
    m["luggage_trolley"] = g.model({"particle": "steel", "steel": "steel", "dark": "steel_dark"}, [
        g.el([3, 1, 1], [13, 2.5, 15], "#steel"),
        g.el([3, 2.5, 13], [13, 9, 14], "#steel"),
        g.el([3, 9, 1.75], [4, 14, 2.25], "#steel"),
        g.el([12, 9, 1.75], [13, 14, 2.25], "#steel"),
        g.el([2, 14, 1.5], [14, 15, 2.5], "#dark"),
        g.el([4, 0, 2], [12, 1, 14], "#dark"),
    ])
    m["airport_seat_seat"] = g.model({"particle": "airport_seat", "seat": "airport_seat", "steel": "steel"}, [
        g.el([1, 6.5, 3.5], [15, 8, 11], "#seat"),
        g.el([1, 8, 10.5], [15, 15, 12], "#seat"),
        g.el([0, 4.5, 6], [16, 5.5, 9], "#steel"),
        g.el([0, 6.5, 4], [1, 11, 10], "#steel"),
    ])
    m["airport_seat_end_left"] = g.model({"particle": "steel", "steel": "steel"}, [
        g.el([1.5, 0, 6.5], [3, 4.5, 8.5], "#steel"),
        g.el([1, 0, 3], [3.5, 0.5, 12], "#steel"),
    ])
    m["airport_seat_end_right"] = g.model({"particle": "steel", "steel": "steel"}, [
        g.el([13, 0, 6.5], [14.5, 4.5, 8.5], "#steel"),
        g.el([12.5, 0, 3], [15, 0.5, 12], "#steel"),
        g.el([15, 6.5, 4], [16, 11, 10], "#steel"),
    ])
    return m


FACING = ("check_in_desk", "departures_board", "windsock", "security_arch", "xray_scanner", "self_check_in_kiosk", "bag_drop",
          "baggage_carousel", "baggage_carousel_corner", "gate_desk", "luggage_trolley")
# blockstates written by hand below: the queue post connects like a fence, the seats join into a row like the benches
QUEUE_POST = "queue_post"
SEAT = "airport_seat"
BLOCKS = ("runway_edge_light", "runway_threshold_light", "taxiway_edge_light", "apron_floodlight", "check_in_desk", "baggage_belt",
          "departures_board", "windsock", "terminal_terrazzo", "security_arch", "xray_scanner", "security_desk", QUEUE_POST,
          "self_check_in_kiosk", "bag_drop", "baggage_carousel", "baggage_carousel_corner", "gate_desk", "luggage_trolley", SEAT)
NAMES = {
    "runway_edge_light": "Runway Edge Light", "runway_threshold_light": "Runway Threshold Light", "taxiway_edge_light": "Taxiway Edge Light",
    "apron_floodlight": "Apron Floodlight", "check_in_desk": "Check-in Desk", "baggage_belt": "Baggage Belt",
    "departures_board": "Departures Board", "windsock": "Windsock", "terminal_terrazzo": "Terminal Terrazzo",
    "security_arch": "Walk-through Metal Detector", "xray_scanner": "X-ray Bag Scanner", "security_desk": "Security Inspection Table",
    "queue_post": "Queue Post", "self_check_in_kiosk": "Self Check-in Kiosk", "bag_drop": "Self Bag Drop",
    "baggage_carousel": "Baggage Carousel", "baggage_carousel_corner": "Baggage Carousel Corner", "gate_desk": "Gate Desk",
    "luggage_trolley": "Luggage Trolley", "airport_seat": "Airport Seating",
}
# stonecutter: (ingredient, count)
RECIPES = {
    "runway_edge_light": ("minecraft:glowstone", 4), "runway_threshold_light": ("minecraft:glowstone", 4), "taxiway_edge_light": ("minecraft:glowstone", 4),
    "apron_floodlight": ("minecraft:iron_ingot", 2), "check_in_desk": ("minecraft:iron_ingot", 2), "baggage_belt": ("minecraft:iron_ingot", 4),
    "departures_board": ("minecraft:iron_ingot", 2), "windsock": ("minecraft:iron_ingot", 2), "terminal_terrazzo": ("minecraft:smooth_stone", 1),
    "security_arch": ("minecraft:iron_ingot", 1), "xray_scanner": ("minecraft:iron_ingot", 1), "security_desk": ("minecraft:iron_ingot", 2),
    "queue_post": ("minecraft:iron_ingot", 4), "self_check_in_kiosk": ("minecraft:iron_ingot", 2), "bag_drop": ("minecraft:iron_ingot", 2),
    "baggage_carousel": ("minecraft:iron_ingot", 4), "baggage_carousel_corner": ("minecraft:iron_ingot", 4), "gate_desk": ("minecraft:iron_ingot", 2),
    "luggage_trolley": ("minecraft:iron_ingot", 2), "airport_seat": ("minecraft:iron_ingot", 2),
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
        if b == QUEUE_POST:
            state = {"multipart": [{"apply": {"model": f"{MOD}:block/queue_post"}}] + [
                {"when": {f: "true"}, "apply": {"model": f"{MOD}:block/queue_post_belt", "uvlock": True} | ({"y": y} if y else {})}
                for f, y in g.FACING_Y.items()]}
        elif b == SEAT:
            state = {"multipart": [part for f, y in g.FACING_Y.items() for part in (
                {"when": {"facing": f}, "apply": {"model": f"{MOD}:block/airport_seat_seat"} | ({"y": y} if y else {})},
                {"when": {"facing": f, "left": "false"}, "apply": {"model": f"{MOD}:block/airport_seat_end_left"} | ({"y": y} if y else {})},
                {"when": {"facing": f, "right": "false"}, "apply": {"model": f"{MOD}:block/airport_seat_end_right"} | ({"y": y} if y else {})})]}
        elif b in FACING:
            state = {"variants": {f"facing={f}": {"model": f"{MOD}:block/{b}"} | ({"y": y} if y else {}) for f, y in g.FACING_Y.items()}}
        else:
            state = {"variants": {"": {"model": f"{MOD}:block/{b}"}}}
        g.write_json(ASSETS / "blockstates" / f"{b}.json", state)
        item = {QUEUE_POST: "queue_post", SEAT: "airport_seat_seat"}.get(b, b)
        g.write_json(ASSETS / "models" / "item" / f"{b}.json", {"parent": f"{MOD}:block/{item}"})
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
