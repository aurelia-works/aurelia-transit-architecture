"""Asset extension for workstream A (live transit systems). See load_extensions() in generate_assets.py.

Defines textures, models, names, lang, recipes and the extra files (sounds.json, multi-connection blockstates) of the
live PIDS/CIS/concourse displays and announcement speakers.

Display blocks join on all four sides (properties facing, left, right, up, down, all from the viewer's side), so each has
16 connection models; the generator's built-in block kinds cannot express that, so the models are registered as a
"facing" block (which writes every model file) and write_extra() then overwrites the blockstate JSON.
"""
import itertools

import generate_assets as g

MOD = g.MOD

DISPLAYS = {
    # id: (plate z range in px, double sided, top inset in px, English name)
    "platform_cis": ((12.5, 16.0), False, 0, "Platform CIS Board"),
    "hanging_platform_cis": ((6.5, 9.5), True, 3, "Hanging Platform CIS Board"),
    "platform_pids": ((12.5, 16.0), False, 0, "Platform PIDS"),
    "hanging_platform_pids": ((6.5, 9.5), True, 3, "Hanging Platform PIDS"),
    "concourse_board": ((11.5, 16.0), False, 0, "Concourse Departure Board"),
}
SPEAKERS = {
    "wall_speaker": "Wall Speaker",
    "ceiling_speaker": "Ceiling Speaker",
}
# Connection flags in suffix order; a display model variant exists for each combination.
FLAGS = ("l", "r", "u", "d")
CHIMES = {"info": "Station chime", "alert": "Safety chime", "delay": "Delay chime"}


def suffix(combo):
    return "".join(f"_{flag}" for flag, on in zip(FLAGS, combo) if on)


def textures():
    t = {}
    t["live_pixel"] = g.new((255, 255, 255, 255))

    screen = g.noisy((14, 17, 21), 2, "live_screen")
    px = screen.load()
    for i in range(16):
        x, y = i, 15 - i
        px[x, y] = g.shade(px[x, y][:3], 7) + (255,)
    g.rect(screen, 0, 0, 16, 1, (24, 28, 34))
    t["live_screen"] = screen

    t["live_body"] = g.noisy((48, 52, 57), 3, "live_body")

    body = g.noisy((52, 56, 61), 2, "speaker_body")
    t["speaker_body"] = body
    grille = g.noisy((34, 37, 41), 2, "speaker_grille")
    gp = grille.load()
    for y in range(1, 16, 2):
        for x in range(1 + (y // 2) % 2, 16, 2):
            gp[x, y] = (14, 15, 17, 255)
    g.rect(grille, 0, 0, 16, 1, (70, 74, 80))
    g.rect(grille, 0, 15, 16, 16, (22, 24, 27))
    t["speaker_grille"] = grille
    return t


def display_model(z0, z1, double, top_inset, combo):
    left, right, up, down = combo
    y1 = 16 - (0 if up else top_inset)
    zf = z0 - 0.5
    zb = z1 + (0.5 if double else 0)
    textures = {"particle": "live_body", "body": "live_body", "screen": "live_screen"}
    plate_faces = {"north": "#screen", "south": "#screen" if double else "#body"}
    els = [g.el([0, 0, z0], [16, y1, z1], "#body", faces=plate_faces)]
    # 1px bezel on every edge that is not joined to another display
    if not up:
        els.append(g.el([0, y1 - 1, zf], [16, y1, zb], "#body"))
    if not down:
        els.append(g.el([0, 0, zf], [16, 1, zb], "#body"))
    if not left:  # viewer's left is model +X
        els.append(g.el([15, 0, zf], [16, y1, zb], "#body"))
    if not right:
        els.append(g.el([0, 0, zf], [1, y1, zb], "#body"))
    # At most two hanger rods per screen: only the top row, one near each free end (viewer's left is model +X).
    if top_inset and not up:
        if not left:
            els.append(g.el([12, y1, 7.5], [13, 16, 8.5], "#body"))
        if not right:
            els.append(g.el([3, y1, 7.5], [4, 16, 8.5], "#body"))
    return g.model(textures, els)


def speaker_wall():
    return g.model({"particle": "speaker_body", "body": "speaker_body", "grille": "speaker_grille"}, [
        g.el([4, 4, 14], [12, 12, 16], "#body"),
        g.el([4.5, 4.5, 13], [11.5, 11.5, 14], "#body", faces={"north": "#grille"}),
    ])


def speaker_ceiling():
    return g.model({"particle": "speaker_body", "body": "speaker_body", "grille": "speaker_grille"}, [
        g.el([3, 14.5, 3], [13, 16, 13], "#body"),
        g.el([4, 14, 4], [12, 14.5, 12], "#body", faces={"down": "#grille"}),
    ])


def blocks():
    b = {}
    for block_id, ((z0, z1), double, inset, _name) in DISPLAYS.items():
        variants = {}
        for combo in itertools.product((False, True), repeat=4):
            variants[suffix(combo)] = display_model(z0, z1, double, inset, combo)
        b[block_id] = ("facing", variants)
    b["wall_speaker"] = ("facing", {"": speaker_wall()})
    b["ceiling_speaker"] = ("facing", {"": speaker_ceiling()})
    return b


def names():
    n = {block_id: spec[3] for block_id, spec in DISPLAYS.items()}
    n.update(SPEAKERS)
    return n


def lang():
    p = f"tooltip.{MOD}."
    s = f"screen.{MOD}."
    lg = {
        p + "live_config": "Right-click to choose the MTR station and style",
        p + "live_joins": "Displays with the same facing that touch join into one larger screen",
        p + "live_hanging": "Hangs from a ceiling; readable from both sides",
        p + "live_speaker": "Right-click to configure; plays station announcements",
        s + "live_display_title": "Passenger Information Display",
        s + "live_speaker_title": "Announcement Speaker",
        s + "live_mode_auto": "Station: Automatic",
        s + "live_mode_manual": "Station: Choose manually",
        s + "live_filter": "Search stations",
        s + "live_auto_none": "No MTR station found here",
        s + "live_auto_found": "Found: %s",
        s + "live_platforms_hint": "No platform ticked = all platforms",
        s + "live_style": "Style: %s",
        s + "live_rows": "Rows: %s",
        s + "live_clock": "Clock: %s",
        s + "live_calling_at": "Calling at: %s",
        s + "live_calling_times": "Times: %s",
        s + "live_calling_times.tip": "Show the arrival time at each calling point where MTR reports one (the same train further down the line). Adds those platforms to this display's arrivals request.",
        s + "live_page_seconds": "Page time: %s s",
        s + "live_radius": "Range: %s blocks",
        s + "live_volume": "Volume: %s%%",
        s + "live_category": "%s: %s",
        s + "live.style.european_amber": "European Amber",
        s + "live.style.european_modern": "European Modern",
        s + "live.style.dutch_modern": "Dutch Modern",
        s + "live.category.safety": "Safety",
        s + "live.category.delay": "Delays",
        s + "live.category.terminating": "Terminating",
        s + "live.category.approaching": "Approaching",
        s + "live.category.standing": "Standing",
    }
    for chime, label in CHIMES.items():
        lg[f"subtitles.{MOD}.live.chime.{chime}"] = label
    return lg


def recipes():
    r = {}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "building",
                     "ingredients": [g.item(i) for i in ingredients],
                     "result": {"item": f"{MOD}:{result}", "count": count}}

    shapeless("platform_cis", ["minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:redstone", "minecraft:glowstone_dust"], 2)
    shapeless("hanging_platform_cis", [f"{MOD}:platform_cis", "minecraft:chain"])
    shapeless("platform_pids", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:glass_pane", "minecraft:redstone", "minecraft:glowstone_dust"], 2)
    shapeless("hanging_platform_pids", [f"{MOD}:platform_pids", "minecraft:chain"])
    shapeless("concourse_board", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:glass_pane",
                                  "minecraft:glass_pane", "minecraft:redstone", "minecraft:glowstone_dust"])
    shapeless("wall_speaker", ["minecraft:iron_ingot", "minecraft:redstone", "minecraft:iron_nugget", "minecraft:iron_nugget"], 2)
    shapeless("ceiling_speaker", [f"{MOD}:wall_speaker", "minecraft:iron_nugget"])
    return r


def write_extra(assets, data, write_json):
    # Connection-aware blockstates for the joinable displays.
    for block_id in DISPLAYS:
        variants = {}
        for facing, y in g.FACING_Y.items():
            for combo in itertools.product((False, True), repeat=4):
                left, right, up, down = combo
                key = f"facing={facing},left={str(left).lower()},right={str(right).lower()},up={str(up).lower()},down={str(down).lower()}"
                entry = {"model": f"{MOD}:block/{block_id}{suffix(combo)}"}
                if y:
                    entry["y"] = y
                variants[key] = entry
        write_json(assets / "blockstates" / f"{block_id}.json", {"variants": variants})

    # Announcement chimes (original synthesised audio, see tools/make_live_sounds.py). Fragment sounds come from
    # resource-pack voice packs, which ship their own sounds.json.
    sounds = {}
    for chime in CHIMES:
        sounds[f"live.chime.{chime}"] = {
            "sounds": [{"name": f"{MOD}:live/chime_{chime}", "stream": False}],
            "subtitle": f"subtitles.{MOD}.live.chime.{chime}",
        }
    write_json(assets / "sounds.json", sounds)
