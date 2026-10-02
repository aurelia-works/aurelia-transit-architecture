"""1.3.1 check scene (function ata_test:v131), north of the showcase. Run gen, copy ata_test into the world, /reload.

Hanging row (two-support rule), viaduct crossbeam/brace joints in every column style, the z-fighting fix models,
and 1/2/3-wide e-paper boards on Alpha's wall (y -56, above the 1.3 boards)."""
import os
A = "aurelia_transit_architecture:"; Y = -60; Z = -60; L = []


def sb(x, y, z, b):
    L.append(f"setblock {x} {y} {z} {A}{b}")


def joined(x1, x2, y, z, block, sign):
    # facing=north. Signs: left = west (x-1); live displays: left = viewer's left = east (x+1).
    for x in range(x1, x2 + 1):
        west, east = x > x1, x < x2
        left, right = (west, east) if sign else (east, west)
        extra = "" if sign else ",up=false,down=false"
        sb(x, y, z, f"{block}[facing=north,left={str(left).lower()},right={str(right).lower()}{extra}]")


L.append(f"fill -2 {Y} {Z - 12} 50 {Y + 10} {Z + 12} minecraft:air")
L.append(f"fill -2 {Y - 1} {Z - 12} 50 {Y - 1} {Z + 12} minecraft:smooth_stone")
# hanging row under a ceiling (rods: one near each free end, max two per row)
L.append(f"fill 0 {Y + 6} {Z - 2} 46 {Y + 6} {Z + 2} minecraft:stone")
hy = Y + 5
for x1, x2 in ((1, 1), (3, 4), (6, 9)):
    joined(x1, x2, hy, Z, "hanging_wayfinding_sign", True)
for x1, x2 in ((12, 12), (14, 15), (17, 20)):
    joined(x1, x2, hy, Z, "hanging_platform_pids", False)
joined(23, 26, hy, Z, "hanging_platform_cis", False)
joined(29, 31, hy, Z, "exit_sign", True)
joined(34, 36, hy, Z, "hanging_station_sign", True)
joined(39, 41, hy, Z, "direction_sign", True)
sb(44, hy, Z, "pictogram_sign[facing=north]")
# viaduct joints: column, crossbeam on top, knee + diagonal braces, one bay per column style
for i, style in enumerate(("steel_heavy", "steel_narrow", "concrete", "concrete_narrow")):
    x = 2 + i * 11; z = Z + 8
    for y in range(Y, Y + 5):
        sb(x, y, z, f"viaduct_column[style={style}]")
    concrete = "true" if style.startswith("concrete") else "false"
    for bx in range(x, x + 6):
        sb(bx, Y + 5, z, f"viaduct_beam[axis=x,kind=crossbeam,concrete={concrete}]")
    sb(x + 1, Y + 4, z, "viaduct_brace[facing=east,kind=diagonal]")
    sb(x - 1, Y + 4, z, "viaduct_brace[facing=west,kind=knee]")
# z-fighting fixes, one of each (contact sheet)
zf = ["viaduct_beam[axis=x,kind=girder,concrete=false]", "viaduct_beam[axis=x,kind=girder,concrete=true]",
      "viaduct_beam[axis=x,kind=stringer,concrete=false]", "viaduct_beam[axis=x,kind=stringer,concrete=true]",
      "viaduct_beam[axis=x,kind=platform_support,concrete=false]", "viaduct_beam[axis=x,kind=platform_support,concrete=true]",
      "platform_windscreen[facing=north,kind=lower]", "platform_windscreen[facing=north,kind=upper]",
      "bus_stop_sign[facing=north]", "catenary_cantilever[facing=north]", "catenary_insulator",
      "station_fence[kind=trackside,east=true,west=false,north=false,south=false]"]
for i, b in enumerate(zf):
    sb(2 + i * 2, Y + 1, Z - 8, b)
    L.append(f"setblock {2 + i * 2} {Y} {Z - 8} minecraft:smooth_stone")
sb(2 + (len(zf) - 1) * 2 + 1, Y + 1, Z - 8, "station_fence[kind=trackside,east=false,west=true,north=false,south=false]")
# e-paper 1/2/3 wide on Alpha's wall (wall z 9, boards at z 8), above the 1.3 boards
for x1, x2 in ((1, 1), (3, 4), (6, 8)):
    joined(x1, x2, -56, 8, "bus_epaper_board", True)
L.append('tellraw @a {"text":"1.3.1 check scene placed (z -72..-48) and e-paper boards on Alpha wall.","color":"green"}')
L.append(f"tp @a 22 {Y + 1} {Z - 7} 0 0")
open(os.path.join(os.path.dirname(__file__), "ata_test/data/ata_test/functions/v131.mcfunction"), "w").write("\n".join(L) + "\n")
print(len(L))
