import os, json
root = os.path.join(os.path.dirname(__file__), "ata_test")
fn = os.path.join(root, "data/ata_test/functions")
os.makedirs(fn, exist_ok=True)
json.dump({"pack": {"pack_format": 15, "description": "ATA 1.3 release-check test world (dev only)"}},
          open(os.path.join(root, "pack.mcmeta"), "w"), indent=2)
A = "aurelia_transit_architecture:"
Y = -60          # superflat: grass top at -61, so nodes/platforms sit at -60
L = []
def c(s): L.append(s)
def sb(x, y, z, b): c(f"setblock {x} {y} {z} {b}")
def fill(x1, y1, z1, x2, y2, z2, b): c(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {b}")
EW  = "mtr:rail[facing=true,is_45=false,is_22_5=false]"   # track along X
DIA = "mtr:rail[facing=false,is_45=true,is_22_5=false]"   # track NE-SW

# ---- rail nodes (connect them yourself; see README in chat)
for x in (0, 32, 64, 130, 162, 260, 292, 320, 380): sb(x, Y, 0, EW)
sb(36, Y, 14, DIA); sb(12, Y, 38, DIA)

# ---- straight platform: south side of track, edge 2 blocks from rail centre
def straight_platform(x1, x2, depth):
    fill(x1, Y, 3, x2, Y, 2 + depth, A + "platform_paving_light")
    fill(x1, Y, 3, x2, Y, 3, A + "tactile_warning_paving")
    fill(x1, Y, 2, x2, Y, 2, A + "platform_edge[facing=north]")
# Alpha P1
straight_platform(0, 32, 6)
fill(0, Y, 9, 32, Y + 5, 9, "minecraft:stone_bricks")
# Beta, Gamma
straight_platform(130, 162, 3)
fill(140, Y, 6, 143, Y + 4, 6, "minecraft:stone_bricks")
straight_platform(260, 292, 3)

# ---- Alpha P2: 45 deg platform beside track x+z=50 (Q2 (12,38) -> Q1 (36,14))
kinds = ["diagonal", "outer", "inner"]
for x in range(13, 38):
    for s in range(54, 61):
        sb(x, Y, s - x, A + "platform_paving_light")
for i, x in enumerate(range(13, 38)):
    k = "diagonal" if i % 4 else kinds[(i // 4) % 3]
    sb(x, Y, 53 - x, A + f"platform_edge_curve[facing=west,kind={k}]")
fill(22, Y, 35, 25, Y + 4, 35, "minecraft:stone_bricks")

# ---- displays (facing = side the viewer stands on)
fill(4, Y + 2, 8, 7, Y + 2, 8, A + "platform_pids[facing=north]")            # P1 PIDS, 4 wide
fill(22, Y + 2, 34, 25, Y + 2, 34, A + "platform_pids[facing=north]")        # P2 PIDS, 4 wide
fill(12, Y + 2, 8, 13, Y + 2, 8, A + "passenger_info_terminal[facing=north]")  # terminal, 2 wide
sb(10, Y + 2, 8, A + "help_point[facing=north]")
sb(15, Y + 2, 8, A + "help_point[facing=north]")
fill(17, Y + 2, 8, 19, Y + 2, 8, A + "station_info_board[facing=north]")     # 3 wide
fill(22, Y + 3, 8, 25, Y + 3, 8, A + "concourse_board[facing=north]")         # 4 wide
sb(28, Y + 2, 8, A + "bus_epaper_board[facing=north]")
sb(20, Y + 1, 6, A + "passenger_info_kiosk[facing=north]")
fill(140, Y + 2, 5, 143, Y + 2, 5, A + "platform_pids[facing=north]")        # Beta PIDS

# ---- messages (A8)
c('ata_message add station "Alpha" severe Alpha severe test')
c('ata_message add station "Beta" notice Beta only')
c('ata_message add network info Network info test')
c('tp @a 16 -59 6 0 0')
c('tellraw @a {"text":"ATA test world placed. Now connect the rails and use the Railway Dashboard.","color":"green"}')
open(os.path.join(fn, "place.mcfunction"), "w").write("\n".join(L) + "\n")

open(os.path.join(fn, "build.mcfunction"), "w").write("\n".join([
    "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set noon", "weather clear",
    "gamerule doMobSpawning false",
    "forceload add -16 -16 400 64",
    'tellraw @a {"text":"Loading area, placing in 3 s...","color":"yellow"}',
    "schedule function ata_test:place 60t",
]) + "\n")
print(len(L), "commands")
