#!/usr/bin/env python3
"""Writes the in-game screenshot tour into the ATA Release Check test world's datapack (dev only, never in the release jar).

    python3 tools/release_check/tour.py [--views views.json] [--scene ata_test:v15_showcase] [--world <save dir>]
    python3 tools/release_check/tour.py --clean          remove everything this script wrote

What it writes under <world>/datapacks/ata_test/data/:
  minecraft/tags/functions/load.json   -> ata_test:tour_setup   gamerules (no day/night, no weather), time 6000, clear weather, then
                                           the scene function, scheduled a moment later so the force-loaded chunks are ready
  minecraft/tags/functions/tick.json   -> ata_test:tour_tick    starts the tour for a player who does not carry this run's tag
  ata_test/functions/tour_tick, tour_start, tour_v01 ... tour_vNN, tour_end

The run tag is unique per run (a timestamp), so tags left on the player by earlier runs never block a new tour. tour_start puts the
player in spectator mode, moves them to the first view and schedules view 1 for 200 ticks later; every view teleports the camera,
announces itself with `say ATA_TOUR_VIEW <n> <name>` (so tools/release_check/shoot.sh can line each screenshot up with its view by
reading latest.log instead of trusting the clock), and schedules the next view 100 ticks later. tour_end puts everything back
(creative mode, day/night and weather cycles, force-loaded chunks), saves the world and stops the integrated server.

A view is {"name", "x", "y", "z", "yaw", "pitch"}: the camera EYE is at x, y, z (the script subtracts the 1.62 eye height).
The default views frame closeups for FOV 70 (about 3.6 blocks back, 2.2 up, pitch 30) and read their coordinates from the layout
constants in gen_v15.py, so they follow the scene when it changes. build/tour/views.json receives the list that was written.
"""
import argparse
import json
import shutil
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
import gen_v15 as scene  # noqa: E402  (constants only: its generator runs under __main__)

WORLD = ROOT / "run" / "client" / "saves" / "ATA Release Check"
EYE = 1.62
NORTH = 180  # yaw that looks towards -z; every board and sign in the scene faces south, so we look at them from the south
STEP = 100   # ticks between views
FIRST = 200  # ticks from joining to the first view


def view(name, x, z, back=3.6, up=2.2, pitch=30, yaw=NORTH):
    """Camera `back` blocks south of (x, z) (block centre) and `up` blocks above the floor, looking north and down by `pitch`."""
    return {"name": name, "x": round(x + 0.5, 2), "y": round(scene.Y + up, 2), "z": round(z + 0.5 + back, 2), "yaw": yaw, "pitch": pitch}


def default_views():
    v = []
    tags = ("glass", "nl_be", "de_it", "metro")
    for row, (title, registry) in enumerate(scene.TABS):
        n = len(scene.ids(registry))
        last = (n - 1) * scene.BLOCK_PITCH
        z = scene.ROW_Z0 + row * scene.ROW_PITCH
        for k, cx in enumerate(range(4, last + 4, 8)):
            v.append(view(f"row_{tags[row]}_{k + 1:02d}", cx, z))
    gz = scene.GZ
    v.append(view("glass_curtain_and_clear_walls", 7, gz + 1, back=6.5, up=3.0, pitch=15))
    v.append(view("glass_pane_run", 21, gz + 1, back=4.6, up=2.8, pitch=25))
    v.append(view("glass_hall_facade_wall", 39, gz + 1, back=6.5, up=3.0, pitch=15))
    v.append(view("glass_wired_and_fritted_walls", 47, gz + 1, back=6.5, up=3.0, pitch=15))
    # the car stop wall (CZ - 2) stands south of these pieces, so they are viewed from the NORTH (yaw 0, camera at z - back) with the wall as backdrop; rows are 3 apart in z (row r at AZ + 3r), the four facings at x 0, 3, 6, 9; the wall of car stops is far south of them
    for r, block in enumerate(scene.ANGLED):
        v.append(view(f"angled_{block}", 4, scene.AZ + 3 * r, back=-6.0, up=3.2, pitch=25, yaw=0))
    sz = scene.AZ + 15  # strip row: waves x 14..21, arch chain x 24..26, gilded trim x 29..32
    v.append(view("wave_roof_panel_strip", 17, sz, back=-5.5, up=3.0, pitch=25, yaw=0))
    v.append(view("wave_roof_panel_close", 15, sz, back=-3.0, up=1.8, pitch=25, yaw=0))
    v.append(view("wave_roof_rise_close", 1, scene.AZ + 9, back=-3.0, up=1.8, pitch=25, yaw=0))
    v.append(view("wave_roof_edge_close", 1, sz, back=-3.0, up=1.8, pitch=25, yaw=0))
    v.append(view("stainless_roof_slope_close", 1, scene.AZ + 12, back=-3.0, up=1.8, pitch=25, yaw=0))
    v.append(view("arch_chain", 25, sz, back=-4.0, up=2.2, pitch=25, yaw=0))
    v.append(view("gilded_trim_row", 30, sz, back=-3.5, up=2.0, pitch=25, yaw=0))
    for name, cx in (("uk", 4), ("german", 16), ("dutch", 28)):
        v.append(view(f"car_stops_{name}", cx, scene.CZ, back=4.6, up=2.8, pitch=22))
    for name, cx in (("dutch_station_sign", 1.5), ("german_station_sign", 5.5), ("original_station_name_sign", 9.5), ("single_block_signs", 13.5)):
        v.append(view(name, cx - 0.5, scene.SZ, back=3.4, up=2.0, pitch=25))
    return v


def mc(view_):
    return f"{view_['x']} {round(view_['y'] - EYE, 2)} {view_['z']} {view_['yaw']} {view_['pitch']}"


def write(path, lines):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n")


def tour_files(views, scene_fn, run_tag):
    f = {"tour_setup": [
        "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set 6000", "weather clear",
        f"forceload add -4 {scene.Z - 12} 84 {scene.Z + 52}",
        f"schedule function {scene_fn} 40t replace",
    ]}
    f["tour_tick"] = [f"execute as @a[tag=!{run_tag},limit=1] run function ata_test:tour_start"]
    f["tour_start"] = [f"tag @s add {run_tag}", "gamemode spectator @s", f"tp @s {mc(views[0])}",
                       f"schedule function ata_test:tour_v01 {FIRST}t replace", "say ATA_TOUR_START"]
    for i, v in enumerate(views, 1):
        nxt = f"tour_v{i + 1:02d}" if i < len(views) else "tour_end"
        f[f"tour_v{i:02d}"] = [f"tp @a {mc(v)}", f"say ATA_TOUR_VIEW {i} {v['name']}", f"schedule function ata_test:{nxt} {STEP}t replace"]
    f["tour_end"] = ["say ATA_TOUR_DONE", "gamemode creative @a", "gamerule doDaylightCycle true", "gamerule doWeatherCycle true",
                     "forceload remove all", "save-all flush", "stop"]
    return f


def clean(world):
    data = world / "datapacks" / "ata_test" / "data"
    for tag in ("load", "tick"):
        (data / "minecraft" / "tags" / "functions" / f"{tag}.json").unlink(missing_ok=True)
    for d in (data / "minecraft" / "tags" / "functions", data / "minecraft" / "tags", data / "minecraft"):
        if d.is_dir() and not any(d.iterdir()):
            d.rmdir()
    for fn in (data / "ata_test" / "functions").glob("tour_*.mcfunction"):
        fn.unlink()


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--views", help="JSON file: a list of {name, x, y, z, yaw, pitch} (camera eye position); default: closeups of the whole scene")
    ap.add_argument("--scene", default="ata_test:v15_showcase", help="function that builds the scene")
    ap.add_argument("--world", default=str(WORLD), help="save directory of the test world")
    ap.add_argument("--clean", action="store_true", help="remove the tour tags and functions from the world and stop")
    args = ap.parse_args()
    world = Path(args.world)
    if args.clean:
        clean(world)
        print("tour removed from", world)
        return
    views = json.loads(Path(args.views).read_text()) if args.views else default_views()
    names = [v["name"] for v in views]
    assert len(set(names)) == len(names), "view names must be unique"
    data = world / "datapacks" / "ata_test" / "data"
    if not data.parent.is_dir():  # first run: the pack is copied from the repo
        shutil.copytree(HERE / "ata_test", data.parent)
    clean(world)
    run_tag = "ata_tour_" + time.strftime("%Y%m%d%H%M%S")
    for name, lines in tour_files(views, args.scene, run_tag).items():
        write(data / "ata_test" / "functions" / f"{name}.mcfunction", lines)
    for tag, fn in (("load", "tour_setup"), ("tick", "tour_tick")):
        write(data / "minecraft" / "tags" / "functions" / f"{tag}.json", [json.dumps({"values": [f"ata_test:{fn}"]})])
    out = ROOT / "build" / "tour"
    out.mkdir(parents=True, exist_ok=True)
    (out / "views.json").write_text(json.dumps(views, indent=1))
    print(f"{len(views)} views, run tag {run_tag}, first view {FIRST} ticks after joining, then every {STEP} ticks "
          f"(about {(FIRST + STEP * len(views)) // 20} s)")


if __name__ == "__main__":
    main()
