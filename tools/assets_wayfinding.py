"""1.2 wayfinding / accessibility / street presentation assets (owned by the presentation workstream, subagent B).

Same extension interface as assets_live.py: textures(), blocks(), names(), lang(), recipes(), write_extra(...).

Blocks: entrance_pylon (two blocks tall), wall_wayfinding_sign, hanging_wayfinding_sign, exit_sign, street_sign,
pictogram_sign, tactile_guidance_paving, help_point, boarding_marker (3 marker variants), bus_epaper_board.
Generic modern American transit look (charcoal panels, white text, green exits); all art is drawn here, no operator marks.
"""
from PIL import ImageDraw

import generate_assets as g

MOD = g.MOD
KEY = f"screen.{MOD}."
TIP = f"tooltip.{MOD}."

EXIT_GREEN = (30, 107, 69)
PAPER = (205, 208, 195)
MARKER_BASE = (66, 70, 74)
MARKER_YELLOW = (236, 190, 40)
MARKER_BLUE = (29, 90, 160)
HELP_BLUE = (29, 78, 140)
WHITE = g.WHITE

PICTOGRAMS = ("none", "accessible_route", "elevator", "escalator", "stairs", "exit", "entrance", "transfer", "bus", "train", "trolley",
              "help_point", "information", "tickets")


def glyph(img, rows, x0, y0, color):
    """Draws a small pixel map ('#' = pixel) with its top-left corner at (x0, y0)."""
    px = img.load()
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "#" and 0 <= x0 + dx < 16 and 0 <= y0 + dy < 16:
                px[x0 + dx, y0 + dy] = color[:3] + (255,)


def border(img, color):
    g.rect(img, 0, 0, 16, 1, color)
    g.rect(img, 0, 15, 16, 16, color)
    g.rect(img, 0, 0, 1, 16, color)
    g.rect(img, 15, 0, 16, 16, color)


def arrow_up(img, color):
    glyph(img, [
        "...##...",
        "..####..",
        ".######.",
        "########",
        "...##...",
        "...##...",
        "...##...",
        "...##...",
        "...##...",
    ], 4, 3, color)


def textures():
    t = {}
    t["wf_exit_green"] = g.noisy(EXIT_GREEN, 2, "wf_exit_green")
    t["wf_epaper"] = g.noisy(PAPER, 2, "wf_epaper")

    # Tactile guidance paving: raised bars along the facing (north-south) axis.
    guidance = g.new()
    g.rect(guidance, 0, 0, 16, 16, g.TACTILE)
    r = g.rng("wf_guidance")
    px = guidance.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = g.shade(g.TACTILE, r.randint(-5, 5)) + (255,)
    for x0 in (1, 5, 9, 13):
        for y in range(16):
            px[x0, y] = g.shade(g.TACTILE, 34) + (255,)
            px[x0 + 1, y] = g.shade(g.TACTILE, 14) + (255,)
            if x0 + 2 < 16:
                px[x0 + 2, y] = g.shade(g.TACTILE, -30) + (255,)
    t["wf_guidance_paving"] = guidance

    # Boarding markers (thin floor plates, arrows point north = the way the player looked when placing).
    door = g.noisy(MARKER_BASE, 3, "wf_marker_door")
    border(door, WHITE)
    arrow_up(door, MARKER_YELLOW)
    g.rect(door, 2, 13, 14, 14, MARKER_YELLOW)
    t["wf_marker_door"] = door

    accessible = g.noisy(MARKER_BLUE, 3, "wf_marker_accessible")
    border(accessible, WHITE)
    d = ImageDraw.Draw(accessible)
    white = WHITE + (255,)
    d.ellipse((7, 2, 9, 4), fill=white)
    d.line((8, 5, 8, 9), fill=white)
    d.line((8, 7, 11, 7), fill=white)
    d.line((8, 9, 11, 9), fill=white)
    d.line((11, 9, 12, 12), fill=white)
    d.arc((3, 7, 10, 14), 0, 360, fill=white)
    t["wf_marker_accessible"] = accessible

    wait = g.noisy(MARKER_BASE, 3, "wf_marker_wait")
    border(wait, MARKER_YELLOW)
    g.rect(wait, 0, 10, 16, 12, MARKER_YELLOW)
    glyph(wait, ["#.#", ".#.", "#.#"], 3, 4, MARKER_YELLOW)
    glyph(wait, ["#.#", ".#.", "#.#"], 10, 4, MARKER_YELLOW)
    t["wf_marker_wait"] = wait

    # Help point: blue unit face with a white question mark.
    help_face = g.noisy(HELP_BLUE, 2, "wf_help_face")
    border(help_face, g.shade(HELP_BLUE, 26))
    glyph(help_face, [
        ".#####.",
        "##...##",
        ".....##",
        "....##.",
        "...##..",
        "...##..",
        ".......",
        "...##..",
        "...##..",
    ], 5, 3, WHITE)
    t["wf_help_face"] = help_face

    # Terminal screen: dark glass with faint scanlines and a teal top edge.
    screen = g.noisy((16, 19, 24), 2, "wf_terminal_screen")
    for y in range(1, 16, 2):
        g.rect(screen, 0, y, 16, y + 1, (20, 24, 30))
    g.rect(screen, 0, 0, 16, 1, (79, 184, 176))
    border(screen, (40, 46, 54))
    g.rect(screen, 0, 0, 16, 1, (79, 184, 176))
    t["wf_terminal_screen"] = screen
    return t


# ---------------------------------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------------------------------
def joined(build):
    """The four left/right connection variants of a joinable sign (suffixes match generate_assets.blockstate)."""
    out = {}
    for left in (False, True):
        for right in (False, True):
            out[("_l" if left else "") + ("_r" if right else "")] = build(left, right)
    return out


def wall_sign(left, right):
    els = [
        g.el([0, 3, 13], [16, 13, 16], "#frame", faces={"north": "#face"}),
        g.el([0, 12.5, 12.75], [16, 13, 16], "#frame"),
        g.el([0, 3, 12.75], [16, 3.5, 16], "#frame"),
    ]
    if not left:
        els.append(g.el([0, 3, 12.75], [0.75, 13, 16], "#frame"))
    if not right:
        els.append(g.el([15.25, 3, 12.75], [16, 13, 16], "#frame"))
    return g.model({"particle": "steel", "frame": "steel", "face": "sign_charcoal"}, els)


def hanging_sign(face, y1, y2):
    def build(left, right):
        both = {"north": "#face", "south": "#face"}
        els = [
            g.el([0, y1, 7], [16, y2, 9], "#frame", faces=both),
            g.el([0, y2 - 0.5, 6.75], [16, y2, 9.25], "#frame"),
            g.el([0, y1, 6.75], [16, y1 + 0.5, 9.25], "#frame"),
            g.el([3, y2, 7.5], [5, 16, 8.5], "#frame"),
            g.el([11, y2, 7.5], [13, 16, 8.5], "#frame"),
        ]
        if not left:
            els.append(g.el([0, y1, 6.75], [0.75, y2, 9.25], "#frame"))
        if not right:
            els.append(g.el([15.25, y1, 6.75], [16, y2, 9.25], "#frame"))
        return g.model({"particle": "steel", "frame": "steel", "face": face}, els)
    return build


def street_sign():
    both = {"north": "#face", "south": "#face"}
    return g.model({"particle": "steel", "frame": "steel", "face": "sign_blue"}, [
        g.el([1, 0, 7], [3, 16, 9], "#frame"),
        g.el([3, 7, 7.25], [16, 13, 8.75], "#frame", faces=both),
        g.el([3, 12.5, 7], [16, 13, 9], "#frame"),
        g.el([3, 7, 7], [16, 7.5, 9], "#frame"),
        g.el([15.25, 7, 7], [16, 13, 9], "#frame"),
        g.el([3, 11, 7.5], [4, 12, 8.5], "#frame"),
    ])


def pictogram_sign():
    both = {"north": "#face", "south": "#face"}
    return g.model({"particle": "steel", "frame": "steel", "face": "sign_blue"}, [
        g.el([3, 2, 7], [13, 12, 9], "#frame", faces=both),
        g.el([3, 11.5, 6.75], [13, 12, 9.25], "#frame"),
        g.el([3, 2, 6.75], [13, 2.5, 9.25], "#frame"),
        g.el([3, 2, 6.75], [3.5, 12, 9.25], "#frame"),
        g.el([12.5, 2, 6.75], [13, 12, 9.25], "#frame"),
        g.el([7.5, 12, 7.5], [8.5, 16, 8.5], "#frame"),
    ])


def pylon(upper):
    t = {"particle": "steel", "trim": "steel", "body": "steel_dark", "face": "sign_charcoal"}
    both = {"north": "#face", "south": "#face"}
    if upper:
        return g.model(t, [
            g.el([3, 0, 6], [13, 14.5, 10], "#body", faces=both),
            g.el([2, 14.5, 5.5], [14, 16, 10.5], "#trim"),
        ])
    return g.model(t, [
        g.el([2, 0, 5.5], [14, 1.5, 10.5], "#trim"),
        g.el([3, 1.5, 6], [13, 16, 10], "#body", faces=both),
    ])


def help_point():
    return g.model({"particle": "steel_dark", "body": "steel_dark", "face": "wf_help_face", "light": "lamp_diffuser"}, [
        g.el([4, 3, 12], [12, 14, 16], "#body", faces={"north": "#face"}),
        g.el([5, 14, 13], [11, 15, 15], "#body", faces={"up": "#light"}),
    ])


def marker(texture):
    return g.model({"particle": texture, "top": texture}, [g.el([0, 0, 0], [16, 0.5, 16], None, faces={"up": "#top"})])


def epaper(left, right):
    els = [
        g.el([0, 0, 13], [16, 16, 16], "#frame", faces={"north": "#paper"}),
        g.el([0, 15.4, 12.75], [16, 16, 16], "#frame"),
        g.el([0, 0, 12.75], [16, 0.6, 16], "#frame"),
    ]
    if not left:
        els.append(g.el([0, 0, 12.75], [0.6, 16, 16], "#frame"))
    if not right:
        els.append(g.el([15.4, 0, 12.75], [16, 16, 16], "#frame"))
    return g.model({"particle": "steel_dark", "frame": "steel_dark", "paper": "wf_epaper"}, els)


def terminal(left, right):
    x1 = 0 if left else 0.75
    x2 = 16 if right else 15.25
    # The screen texture has a lighter 1 px rim on every edge. A north face is mirrored (u = 16 - x), so a screen that
    # runs to a block edge would put that rim exactly on the join and draw a visible seam between joined terminals.
    # Sampling only the interior columns (1..15) keeps the surface continuous across any number of blocks.
    u1 = max(1.0, 16 - x2)
    u2 = min(15.0, 16 - x1)
    return g.model({"particle": "steel_dark", "body": "steel_dark", "screen": "wf_terminal_screen"}, [
        g.el([0, 1, 12], [16, 15, 16], "#body"),
        g.el([x1, 2, 11.9], [x2, 14, 12], None, faces={"north": ("#screen", [u1, 2, u2, 14])}),
    ])


def kiosk():
    return g.model({"particle": "steel_dark", "body": "steel_dark", "trim": "steel", "screen": "wf_terminal_screen"}, [
        g.el([2, 0, 5], [14, 2, 12], "#trim"),
        g.el([3, 2, 6], [13, 16, 11], "#body"),
        g.el([3.5, 6, 5.9], [12.5, 14, 6], None, faces={"north": "#screen"}),
        g.el([5, 3, 5.8], [11, 4.5, 6], "#trim", faces={"north": "#trim"}),
    ])


def blocks():
    b = {}
    b["passenger_info_terminal"] = ("sign", joined(terminal))
    b["passenger_info_kiosk"] = ("facing", {"": kiosk()})
    b["entrance_pylon"] = ("facing", {"": pylon(False), "_upper": pylon(True)})
    b["wall_wayfinding_sign"] = ("sign", joined(wall_sign))
    b["hanging_wayfinding_sign"] = ("sign", joined(hanging_sign("sign_charcoal", 4, 12)))
    b["exit_sign"] = ("sign", joined(hanging_sign("wf_exit_green", 6, 12)))
    b["street_sign"] = ("sign_single", {"": street_sign()})
    b["pictogram_sign"] = ("sign_single", {"": pictogram_sign()})
    b["tactile_guidance_paving"] = ("facing", {"": g.cube_bottom_top("wf_guidance_paving", "concrete_light", "concrete_light")})
    b["help_point"] = ("facing", {"": help_point()})
    b["boarding_marker"] = ("facing", {"": marker("wf_marker_door"), "_accessible": marker("wf_marker_accessible"), "_wait": marker("wf_marker_wait")})
    b["bus_epaper_board"] = ("sign", joined(epaper))
    return b


def names():
    return {
        "entrance_pylon": "Station Entrance Pylon",
        "wall_wayfinding_sign": "Wall Wayfinding Sign",
        "hanging_wayfinding_sign": "Hanging Wayfinding Sign",
        "exit_sign": "Exit Sign",
        "street_sign": "Street Sign",
        "pictogram_sign": "Pictogram Sign",
        "tactile_guidance_paving": "Tactile Guidance Paving",
        "help_point": "Help Point",
        "boarding_marker": "Boarding Marker",
        "bus_epaper_board": "Bus E-Paper Board",
        "passenger_info_terminal": "Passenger Information Terminal",
        "passenger_info_kiosk": "Passenger Information Kiosk",
    }


def lang():
    lg = {
        TIP + "wf_configure": "Right-click with an empty hand to set station, lines and text",
        TIP + "wf_tall": "Two blocks tall; needs a free block above",
        TIP + "wf_guidance": "Guidance lines run the way you are looking; counts as platform surface for MTR doors",
        TIP + "wf_marker": "Right-click to switch between door, accessible and wait marker",
        TIP + "wf_help": "Right-click for a reminder of where to get help",
        TIP + "wf_epaper": "Low-refresh arrivals from the nearest station; updates every 15-30 seconds",
        f"message.{MOD}.help_point": "Help point: ask station staff or use the nearest information desk",
        f"message.{MOD}.boarding_marker.door": "Marker: train door position",
        f"message.{MOD}.boarding_marker.accessible": "Marker: accessible boarding position",
        f"message.{MOD}.boarding_marker.wait": "Marker: wait behind the line",
        KEY + "live_alignment": "Align content: %s",
        KEY + "live_alignment.top": "Top",
        KEY + "live_alignment.center": "Centre",
        KEY + "wf_edit": "Edit Wayfinding Sign",
        KEY + "wf_station": "Station name (manual)",
        KEY + "wf_stop": "Stop name (manual)",
        KEY + "wf_secondary": "Second language",
        KEY + "wf_code": "Station code",
        KEY + "wf_stop_code": "Stop no.",
        KEY + "wf_exit": "Exit",
        KEY + "wf_platform": "Platform",
        KEY + "wf_street": "Street / landmark / connection",
        KEY + "wf_transfers": "Transfer note",
        KEY + "wf_destination": "Direction / destination",
        KEY + "wf_caption": "Caption",
        KEY + "wf_exit_text": "Leads to",
        KEY + "wf_service_label": "Custom service label",
        KEY + "wf_badge": "Line",
        KEY + "wf_lines_auto": "Lines: Auto (MTR)",
        KEY + "wf_lines_manual": "Lines: Manual",
        KEY + "wf_layout": "Languages: %s",
        KEY + "wf_layout.single": "First only",
        KEY + "wf_layout.side_by_side": "Side by side",
        KEY + "wf_layout.stacked": "Stacked",
        KEY + "wf_service": "Service: %s",
        KEY + "wf_service.none": "none",
        KEY + "wf_service.local": "Local",
        KEY + "wf_service.express": "Express",
        KEY + "wf_service.limited": "Limited",
        KEY + "wf_service.custom": "Custom",
        KEY + "wf_symbol": "Symbol: %s",
        KEY + "wf_station_source": "Station: %s",
        KEY + "wf_station_source.auto": "Auto (nearest MTR station)",
        KEY + "wf_station_source.manual": "Manual: %s",
        TIP + "wf_terminal": "Right-click to use: departures, system map, station, service and accessibility information",
        TIP + "wf_terminal_edit": "Sneak + right-click with an empty hand to edit name, code, lines and notes",
        KEY + "term_no_station": "No station linked",
        KEY + "term_no_notices": "No service notices",
        KEY + "term_no_accessibility": "No accessibility information has been provided for this station",
        KEY + "term_no_platforms": "No platform information",
        KEY + "term_no_exits": "No exit information",
        KEY + "term_no_departures": "No departures currently available",
        KEY + "term_exit": "Exit %s",
        KEY + "term_lines": "Lines: %s",
        KEY + "term_platforms": "Platforms: %s",
        KEY + "term_transfers": "Transfers: %s",
        KEY + "term_street": "Street / landmark: %s",
        KEY + "term_scope.station": "Station",
        KEY + "term_scope.network": "Network",
        KEY + "term_scope.display": "This display",
        KEY + "term_touch": "Touch for information",
        KEY + "term_passenger_info": "Passenger information",
        KEY + "board_departures": "Departures",
        KEY + "board_departures_at": "Departures - %s",
        KEY + "board_terminates": "Terminates here",
        KEY + "board_delayed_one": "Delayed by %s minute",
        KEY + "board_delayed_many": "Delayed by %s minutes",
        KEY + "board_configure": "Right-click to configure",
        KEY + "board_welcome": "Welcome to %s",
        KEY + "epaper_no_stop": "No stop linked",
        f"note.{MOD}.help_point_distance": "about %s blocks away",
        KEY + "term_title": "Passenger Information",
        KEY + "term_next": "Next departures",
        KEY + "term_no_map": "No system map available",
    }
    for tab, title in (("home", "Home"), ("departures", "Departures"), ("map", "System map"), ("station", "Station"),
                       ("service", "Service info"), ("accessibility", "Accessibility")):
        lg[KEY + "term_tab." + tab] = title
    for name in PICTOGRAMS:
        lg[KEY + "wf_pictogram." + name] = "none" if name == "none" else name.replace("_", " ").capitalize()
    return lg


def recipes():
    r = {}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients],
                     "result": {"item": f"{MOD}:{result}", "count": count}}

    shapeless("entrance_pylon", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:blue_dye", "minecraft:glowstone_dust", "minecraft:glass_pane"])
    shapeless("wall_wayfinding_sign", ["minecraft:iron_ingot", "minecraft:gray_dye", "minecraft:glowstone_dust"], 2)
    shapeless("hanging_wayfinding_sign", [f"{MOD}:wall_wayfinding_sign", "minecraft:chain"])
    shapeless("exit_sign", ["minecraft:iron_ingot", "minecraft:green_dye", "minecraft:glowstone_dust", "minecraft:chain"], 2)
    shapeless("street_sign", [f"{MOD}:sign_pole", "minecraft:blue_dye", "minecraft:iron_ingot"])
    shapeless("pictogram_sign", ["minecraft:iron_ingot", "minecraft:blue_dye", "minecraft:white_dye"], 2)
    shapeless("tactile_guidance_paving", [f"{MOD}:tactile_warning_paving", "minecraft:iron_nugget"])
    shapeless("help_point", ["minecraft:iron_ingot", "minecraft:blue_dye", "minecraft:redstone", "minecraft:glowstone_dust", "minecraft:glass_pane"])
    shapeless("boarding_marker", ["minecraft:iron_nugget", "minecraft:yellow_dye", "minecraft:black_dye"], 4)
    shapeless("passenger_info_terminal", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:redstone", "minecraft:glowstone_dust"])
    shapeless("passenger_info_kiosk", [f"{MOD}:passenger_info_terminal", "minecraft:iron_ingot"])
    shapeless("bus_epaper_board", ["minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:paper", "minecraft:redstone"])
    return r


def write_extra(assets, data, write_json):
    ref = lambda suffix, block_id: f"{MOD}:block/{block_id}{suffix}"

    # Entrance pylon: lower/upper half models, rotated per facing.
    variants = {}
    for facing, y in g.FACING_Y.items():
        for half, suffix in (("lower", ""), ("upper", "_upper")):
            variants[f"facing={facing},half={half}"] = {"model": ref(suffix, "entrance_pylon")} | ({"y": y} if y else {})
    write_json(assets / "blockstates" / "entrance_pylon.json", {"variants": variants})
    # Only the lower half drops the item (the upper half is removed with it), like a vanilla door.
    write_json(data / MOD / "loot_tables" / "blocks" / "entrance_pylon.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{MOD}:entrance_pylon",
                                "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:entrance_pylon", "properties": {"half": "lower"}}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })

    # Entrance pylon inventory/hand model: both halves stacked and scaled so the whole two-block item is visible
    # (the block model alone is only the lower half, which showed as the bottom half of the pylon in the inventory).
    lower, upper = pylon(False), pylon(True)
    elements = list(lower["elements"])
    for element in upper["elements"]:
        shifted = dict(element)
        shifted["from"] = [element["from"][0], element["from"][1] + 16, element["from"][2]]
        shifted["to"] = [element["to"][0], element["to"][1] + 16, element["to"][2]]
        elements.append(shifted)
    scale = 0.34

    def display(rotation, translation, base_scale):
        k = base_scale * 0.55
        return {"rotation": rotation, "translation": [translation[0], translation[1] - 8 * k, translation[2]], "scale": [k, k, k]}

    item = {"parent": "block/block", "textures": {**lower["textures"], **upper["textures"]}, "elements": elements, "display": {
        "gui": display([30, 225, 0], [0, 0, 0], 0.625),
        "ground": display([0, 0, 0], [0, 3, 0], 0.25),
        "fixed": display([0, 0, 0], [0, 0, 0], 0.5),
        "thirdperson_righthand": display([75, 45, 0], [0, 2.5, 0], 0.375),
        "firstperson_righthand": display([0, 45, 0], [0, 0, 0], 0.4),
        "firstperson_lefthand": display([0, 225, 0], [0, 0, 0], 0.4),
    }}
    write_json(assets / "models" / "item" / "entrance_pylon.json", item)

    # Boarding marker: type is a block state.
    variants = {}
    for facing, y in g.FACING_Y.items():
        for marker_type, suffix in (("door", ""), ("accessible", "_accessible"), ("wait", "_wait")):
            variants[f"facing={facing},marker={marker_type}"] = {"model": ref(suffix, "boarding_marker")} | ({"y": y} if y else {})
    write_json(assets / "blockstates" / "boarding_marker.json", {"variants": variants})
