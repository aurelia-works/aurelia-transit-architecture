"""1.5 check scene (function ata_test:v15_showcase), north of the 1.4 scenes. Run gen, copy ata_test into the world, /reload.

Every new block laid out by creative tab (rows run west to east, two blocks apart, read the order off the tab), a small glass
wall and pane run, and the angled pieces (Frankfurt arch truss and ridge skylight, Antwerp vault) in all four facings.
Block lists are read from the registries, so new blocks show up after re-running this script."""
import os
import re
A = "aurelia_transit_architecture:"; Y = -60; Z = -140; L = []
# layout constants (tools/release_check/tour.py reads these, so the screenshot tour stays in sync with the scene)
ROW_Z0, ROW_PITCH, BLOCK_PITCH = Z - 8, 4, 2   # tab rows: row r at z = ROW_Z0 + r * ROW_PITCH, block i at x = i * BLOCK_PITCH
GZ = Z + 10      # glass: curtain wall x 0..5, clear wall x 8..13, pane run x 16..25 (+ corner x 25), fin 28, floor 30, brick 32
AZ = Z + 20      # angled pieces: ANGLED rows, 3 apart, facings north east south west at x 0 3 6 9; the wave strip is at x 14..21 of the last row
ANGLED = ("frankfurt_hall_truss_arch", "frankfurt_ridge_skylight", "antwerp_iron_glass_vault", "utrecht_wave_roof_rise",
          "rotterdam_stainless_roof_slope", "utrecht_wave_roof_edge")
CZ = Z + 40      # car stop boards (freestanding at CZ, wall-mounted at CZ - 1), styles 12 apart in x
SZ = Z + 44      # regional station signs, joined three blocks wide, then a one-block sign of each for comparison
REG = os.path.join(os.path.dirname(__file__), "../../src/main/java/com/aureliatransit/architecture/registry")
TABS = (  # (title, registry file) in creative tab order
    ("ATA Glass", "GlassBlocks"),
    ("ATA European Stations: Netherlands and Belgium", "StationBlocksNlBe"),
    ("ATA European Stations: Germany and Italy", "StationBlocksDeIt"),
    ("ATA Metro", "MetroBlocks"),
)
SIGN_TEXT = {  # editable signs: text set through the block entity (left/right stay false)
    "dutch_station_sign": '{Sign:{Primary:"Utrecht Centraal",Secondary:"Spoor 5-12",Platform:"5"}}',
    "dutch_platform_sign": '{Sign:{Primary:"Spoor",Platform:"7"}}',
    "german_station_sign": '{Sign:{Primary:"Frankfurt Hbf",Secondary:"Gleis 1-9",Platform:"3"}}',
}


def sb(x, y, z, b, nbt=""):
    L.append(f"setblock {x} {y} {z} {A}{b}{nbt}")


def ids(registry):
    text = open(os.path.join(REG, registry + ".java")).read()
    return re.findall(r'register\("([a-z_0-9]+)"', text)


def main():
    global count
    L.append(f"fill -2 {Y} {Z - 12} 80 {Y + 8} {Z + 48} minecraft:air")
    L.append(f"fill -2 {Y - 1} {Z - 12} 80 {Y - 1} {Z + 48} minecraft:smooth_stone")

    # ---- every block by tab: one row per tab, 4 blocks apart in z; signs are stood facing south, text set by hand
    count = 0
    for row, (title, registry) in enumerate(TABS):
        z = ROW_Z0 + row * ROW_PITCH
        blocks = ids(registry)
        for i, block in enumerate(blocks):
            x = i * BLOCK_PITCH
            state = "[facing=south]" if block in SIGN_TEXT else ""
            sb(x, Y, z, block + state)
            if block in SIGN_TEXT:
                L.append(f"data merge block {x} {Y} {z} {SIGN_TEXT[block]}")
            count += 1
        L.append(f'tellraw @a {{"text":"{title}: {len(blocks)} blocks, row z {z}, x 0..{(len(blocks) - 1) * 2}","color":"gray"}}')

    # ---- glass: a 6 x 3 curtain wall, a 6 x 3 wall of clear glass, and a run of panes with a corner and a gap
    gz = GZ
    for dx in range(6):
        for dy in range(3):
            sb(dx, Y + dy, gz, "curtain_wall_glass[facing=south]")
            sb(8 + dx, Y + dy, gz, "clear_float_glass")
    for dx in range(10):
        sb(16 + dx, Y, gz, "clear_float_glass_pane")
        sb(16 + dx, Y + 1, gz, "bronze_tinted_glass_pane" if dx % 2 else "grey_tinted_glass_pane")
    for dz in range(1, 5):
        sb(25, Y, gz + dz, "clear_float_glass_pane")
    sb(28, Y, gz, "structural_glass_fin")
    sb(30, Y, gz, "glass_floor_panel")
    sb(32, Y, gz, "glass_brick")
    # 1.5 polish: a hall facade wall, a wired and a fritted wall (3 x 3 each, with a pane run of both above the pane run)
    for dx in range(6):
        for dy in range(3):
            sb(36 + dx, Y + dy, gz, "utrecht_hall_glass_facade[facing=south]")
    for dx in range(3):
        for dy in range(3):
            sb(44 + dx, Y + dy, gz, "wired_glass")
            sb(48 + dx, Y + dy, gz, "fritted_glass")
    for dx in range(10):
        sb(16 + dx, Y + 2, gz, "wired_glass_pane" if dx % 2 else "fritted_glass_pane")

    # ---- angled pieces in all four facings (look from outside each: the high or low edge must follow the facing)
    az = AZ
    for row, block in enumerate(ANGLED):
        for i, facing in enumerate(("north", "east", "south", "west")):
            sb(i * 3, Y, az + row * 3, f"{block}[facing={facing}]")
    for dx in range(8):  # a strip of wave roof panels: the wave should run on from panel to panel
        sb(14 + dx, Y, az + 15, "utrecht_wave_roof_panel")
    # ---- car stop boards: a platform-end row (4, 6, 8 and 12 cars per style, then the stop-here board), freestanding on their post,
    #      then the same numbers wall-mounted on a short wall behind. Boards face south; look at them from the south.
    cz = CZ
    L.append(f"fill -2 {Y} {cz - 2} 40 {Y + 3} {cz - 2} minecraft:stone_bricks")
    for row, (block, first) in enumerate((("uk_car_stop_marker", 4), ("german_stop_board", 0), ("dutch_stop_board", 0))):
        numbers = [4, 6, 8, 12] + ([0] if first == 0 else [])
        for i, cars in enumerate(numbers):
            x = row * 12 + i * 2
            sb(x, Y, cz, f"{block}[cars={cars},facing=south,wall=false]")
            sb(x, Y + 1, cz - 1, f"{block}[cars={cars},facing=south,wall=true]")
        count += len(numbers) * 2
    # a sign pole under a freestanding board: the pole joins it (it does not join a wall-mounted board)
    sb(40, Y, cz, "sign_pole")
    sb(40, Y + 1, cz, "uk_car_stop_marker[cars=8,facing=south,wall=false]")
    # ---- station signs: the name does not fit on one block (same as the original station_name_sign), so each is three blocks wide
    #      and joined; the text goes on every block of the row (only the leftmost draws it). A one-block sign of each kind follows.
    sz = SZ
    SIGNS = (("dutch_station_sign", 0, 3, SIGN_TEXT["dutch_station_sign"]), ("german_station_sign", 4, 3, SIGN_TEXT["german_station_sign"]),
             ("station_name_sign", 8, 3, '{Sign:{Primary:"Utrecht Centraal",Secondary:"Spoor 5-12",Platform:"5"}}'),
             ("dutch_station_sign", 12, 1, SIGN_TEXT["dutch_station_sign"]), ("station_name_sign", 14, 1, '{Sign:{Primary:"Utrecht Centraal",Secondary:"Spoor 5-12",Platform:"5"}}'))
    for block, x0, width, nbt in SIGNS:
        for dx in range(width):
            sb(x0 + dx, Y, sz, f"{block}[facing=south]")
            L.append(f"data merge block {x0 + dx} {Y} {sz} {nbt}")
    L.append(f'tellraw @a {{"text":"1.5 scene placed: {count} blocks by tab (z {Z - 8}..{Z + 4}), glass z {gz}, angled pieces z {az}..{az + 15} (north, east, south, west), car stop boards z {cz}, signs z {sz}.","color":"green"}}')
    L.append(f"tp @a 20 {Y + 1} {Z + 52} 180 10")
    open(os.path.join(os.path.dirname(__file__), "ata_test/data/ata_test/functions/v15_showcase.mcfunction"), "w").write("\n".join(L) + "\n")
    print(len(L), "commands,", count, "blocks")


if __name__ == "__main__":
    main()
