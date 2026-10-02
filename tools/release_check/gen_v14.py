"""1.4 check scenes (function ata_test:v14_props), north of the 1.3.1 scene. Run gen, copy ata_test into the world, /reload.

Package 3: noise barriers in every kind (and stacked), fare gate bank, card readers, booth window, CCTV, lift panels."""
import os
A = "aurelia_transit_architecture:"; Y = -60; Z = -95; L = []


def sb(x, y, z, b):
    L.append(f"setblock {x} {y} {z} {A}{b}")


L.append(f"fill -2 {Y} {Z - 10} 50 {Y + 8} {Z + 12} minecraft:air")
L.append(f"fill -2 {Y - 1} {Z - 10} 50 {Y - 1} {Z + 12} minecraft:smooth_stone")
# noise barriers: each kind 3 wide, then a 3-high stack of solid and of glass
for i, kind in enumerate(("solid", "solid_half", "glass", "glass_half", "solid_glass")):
    for dx in range(3):
        sb(2 + i * 4 + dx, Y, Z, f"noise_barrier[facing=north,kind={kind}]")
for dy in range(3):
    for dx in range(3):
        sb(24 + dx, Y + dy, Z, "noise_barrier[facing=north,kind=solid]")
        sb(28 + dx, Y + dy, Z, f"noise_barrier[facing=north,kind={'solid' if dy == 0 else 'glass'}]")
# fare gate bank: end, gate, gate, wide, end (facing south: front toward a player standing south)
for i, kind in enumerate(("end", "gate", "gate", "wide", "end")):
    sb(2 + i, Y, Z + 6, f"fare_gate[facing=south,kind={kind}]")
# card readers on a post and on a wall
sb(9, Y, Z + 6, "card_reader[facing=south,kind=post]")
L.append(f"setblock 11 {Y} {Z + 5} minecraft:stone_bricks")
L.append(f"setblock 11 {Y + 1} {Z + 5} minecraft:stone_bricks")
sb(11, Y + 1, Z + 6, "card_reader[facing=south,kind=wall]")
# booth window in a wall, CCTV on wall / pendant / dome under a ceiling, lift panels in each status
L.append(f"fill 14 {Y} {Z + 5} 30 {Y + 3} {Z + 5} minecraft:stone_bricks")
L.append(f"fill 14 {Y + 4} {Z + 5} 30 {Y + 4} {Z + 8} minecraft:stone_bricks")
for dx in range(2):
    sb(15 + dx, Y + 1, Z + 6, "booth_window[facing=south]")
sb(19, Y + 2, Z + 6, "cctv_camera[facing=south,kind=wall]")
sb(21, Y + 3, Z + 7, "cctv_camera[facing=south,kind=pendant]")
sb(23, Y + 3, Z + 7, "cctv_camera[facing=south,kind=dome]")
for i, status in enumerate(("in_service", "out_of_service", "maintenance")):
    sb(25 + i * 2, Y + 1, Z + 6, f"lift_status_panel[facing=south,status={status},left=false,right=false]")
    L.append(f'data merge block {25 + i * 2} {Y + 1} {Z + 6} {{Sign:{{Primary:"Lift {"ABC"[i]}",Secondary:"Street - Platforms"}}}}')
L.append('tellraw @a {"text":"1.4 props scene placed (z -105..-83).","color":"green"}')
L.append(f"tp @a 16 {Y + 1} {Z + 11} 180 10")
open(os.path.join(os.path.dirname(__file__), "ata_test/data/ata_test/functions/v14_props.mcfunction"), "w").write("\n".join(L) + "\n")
print(len(L))
