"""1.5 check scene (function ata_test:v15_showcase), north of the 1.4 scenes. Run gen, copy ata_test into the world, /reload.

Every new block laid out by creative tab (rows run west to east, two blocks apart, read the order off the tab), a small glass
wall and pane run, and the angled pieces (Frankfurt arch truss and ridge skylight, Antwerp vault) in all four facings.
Block lists are read from the registries, so new blocks show up after re-running this script."""
import os
import re
A = "aurelia_transit_architecture:"; Y = -60; Z = -140; L = []
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


L.append(f"fill -2 {Y} {Z - 12} 80 {Y + 8} {Z + 34} minecraft:air")
L.append(f"fill -2 {Y - 1} {Z - 12} 80 {Y - 1} {Z + 34} minecraft:smooth_stone")

# ---- every block by tab: one row per tab, 4 blocks apart in z; signs are stood facing south, text set by hand
count = 0
for row, (title, registry) in enumerate(TABS):
    z = Z - 8 + row * 4
    blocks = ids(registry)
    for i, block in enumerate(blocks):
        x = i * 2
        state = "[facing=south]" if block in SIGN_TEXT else ""
        sb(x, Y, z, block + state)
        if block in SIGN_TEXT:
            L.append(f"data merge block {x} {Y} {z} {SIGN_TEXT[block]}")
        count += 1
    L.append(f'tellraw @a {{"text":"{title}: {len(blocks)} blocks, row z {z}, x 0..{(len(blocks) - 1) * 2}","color":"gray"}}')

# ---- glass: a 6 x 3 curtain wall, a 6 x 3 wall of clear glass, and a run of panes with a corner and a gap
gz = Z + 10
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

# ---- angled pieces in all four facings (look from outside each: the high or low edge must follow the facing)
az = Z + 20
for row, block in enumerate(("frankfurt_hall_truss_arch", "frankfurt_ridge_skylight", "antwerp_iron_glass_vault")):
    for i, facing in enumerate(("north", "east", "south", "west")):
        sb(i * 3, Y, az + row * 3, f"{block}[facing={facing}]")
# ---- car stop boards: a platform-end row (4, 6, 8 and 12 cars per style, then the stop-here board), freestanding on their post,
#      then the same numbers wall-mounted on a short wall behind. Boards face south; look at them from the south.
cz = Z + 30
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
L.append(f'tellraw @a {{"text":"1.5 scene placed: {count} blocks by tab (z {Z - 8}..{Z + 4}), glass z {gz}, angled pieces z {az}..{az + 6} (north, east, south, west), car stop boards z {cz}.","color":"green"}}')
L.append(f"tp @a 20 {Y + 1} {Z + 38} 180 10")
open(os.path.join(os.path.dirname(__file__), "ata_test/data/ata_test/functions/v15_showcase.mcfunction"), "w").write("\n".join(L) + "\n")
print(len(L), "commands,", count, "blocks")
