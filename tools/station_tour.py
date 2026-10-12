#!/usr/bin/env python3
"""Live station checks on the dev server, driven by RCON and the dev client's screenshot hook (docs/LIVE_TESTING.md).

Needs the dev server (`./gradlew runServer --args=nogui`) with RCON enabled in run/server/server.properties, and the dev
client (`./gradlew runClient`) joined as PLAYER. Pillow makes the contact sheets. Plans come from the plan export:

    java -cp build/classes/java/main tools/StationPlanExport.java build/station-plans.json

Commands:
    rcon CMD...                 run server commands
    build KEY X Z [DIR]         plan the export's KEY at (X, -59, Z) with tracks running DIR (south, west, north, east; default
                                south) and build it; prints the footprint
    tour KEY X Z [DIR]          shoot every view of KEY, built with tracks running DIR and its footprint from (X, -59, Z) as
                                the build printed it, and make a sheet
    shot NAME [OPTIONS]         one screenshot from the current view, e.g. "nohud fov=70", "open=wrench", "use"
    look X Y Z TX TY TZ         put the camera at X Y Z (feet) looking at TX TY TZ

Shots land in run/client/screenshots/, sheets in build/tour/.
"""
import json
import math
import os
import socket
import struct
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAYER = os.environ.get("ATA_PLAYER", "ATATester")
EYE = 1.62
REQUESTS = os.path.join(ROOT, "run/client/screenshot-requests")
SHOTS = os.path.join(ROOT, "run/client/screenshots")
CLIENT_LOG = os.path.join(ROOT, "run/client/logs/latest.log")
PLANS = os.path.join(ROOT, "build/station-plans.json")
OUT = os.path.join(ROOT, "build/tour")


def server_properties():
    props = {}
    with open(os.path.join(ROOT, "run/server/server.properties")) as f:
        for line in f:
            if "=" in line and not line.startswith("#"):
                key, value = line.rstrip("\n").split("=", 1)
                props[key] = value
    return props


def rcon(*commands):
    props = server_properties()
    if props.get("enable-rcon") != "true":
        sys.exit("RCON is off: set enable-rcon=true and rcon.password in run/server/server.properties, then restart the server")

    def packet(request, kind, body):
        data = body.encode() + b"\x00\x00"
        return struct.pack("<iii", len(data) + 8, request, kind) + data

    def receive(sock):
        size = struct.unpack("<i", sock.recv(4))[0]
        data = b""
        while len(data) < size:
            data += sock.recv(size - len(data))
        return struct.unpack("<i", data[:4])[0], data[8:-2].decode(errors="replace")

    out = []
    with socket.create_connection(("127.0.0.1", int(props.get("rcon.port", 25575))), timeout=30) as sock:
        sock.sendall(packet(1, 3, props["rcon.password"]))
        if receive(sock)[0] == -1:
            sys.exit("RCON authentication failed")
        for command in commands:
            sock.sendall(packet(2, 2, command))
            out.append(receive(sock)[1])
    return out


def look(x, y, z, tx, ty, tz):
    dx, dy, dz = tx - x, ty - (y + EYE), tz - z
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    rcon(f"tp {PLAYER} {x:g} {y:g} {z:g} {yaw:.1f} {pitch:.1f}")


def shot(name, options=""):
    """Asks the client for a screenshot and waits for it; returns its path or None."""
    path = os.path.join(SHOTS, name + ".png")
    if os.path.exists(path):
        os.replace(path, os.path.join(SHOTS, f"{name}-{int(time.time())}.png"))
    os.makedirs(REQUESTS, exist_ok=True)
    with open(os.path.join(REQUESTS, name), "w") as f:
        f.write(options)
    for _ in range(60):
        if os.path.exists(path) and os.path.getsize(path) > 0:
            time.sleep(0.5)
            return path
        time.sleep(0.5)
    return None


def sheet(out, files):
    from PIL import Image, ImageDraw
    width, height, cols = 700, 394, 2
    rows = (len(files) + cols - 1) // cols
    canvas = Image.new("RGB", (width * cols, height * rows), "white")
    for i, file in enumerate(files):
        image = Image.open(file).convert("RGB").resize((width, height))
        draw = ImageDraw.Draw(image)
        draw.rectangle([0, 0, 200, 22], fill="black")
        draw.text((6, 5), os.path.basename(file), fill="white")
        canvas.paste(image, ((i % cols) * width, (i // cols) * height))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    canvas.save(out)


def plan(key):
    with open(PLANS) as f:
        plans = {p["key"]: p for p in json.load(f)}
    if key not in plans:
        sys.exit(f"no plan {key} in {PLANS}; plans: {', '.join(plans)}")
    return plans[key]


def chat_since(lines_before):
    with open(CLIENT_LOG, errors="ignore") as f:
        lines = f.read().splitlines()[lines_before:]
    return [line.split("[CHAT] ", 1)[1] for line in lines if "[CHAT] " in line]


def log_length():
    with open(CLIENT_LOG, errors="ignore") as f:
        return len(f.read().splitlines())


DIRECTIONS = {"south": ((0, 1), 0), "west": ((-1, 0), 90), "north": ((0, -1), 180), "east": ((1, 0), -90)}


def build(key, x, z, direction="south"):
    description = plan(key)["description"]
    rcon(f"gamemode creative {PLAYER}", f"tp {PLAYER} {x} -59 {z} {DIRECTIONS[direction][1]} 0")
    time.sleep(5)  # let the chunks load before the site survey
    mark = log_length()
    rcon(f"execute as {PLAYER} at @s run ata_station plan {description}")
    time.sleep(1.5)
    # the plan's chat summary is short; the footprint is in the full one
    rcon(f"execute as {PLAYER} at @s run ata_station details")
    time.sleep(1)
    for line in chat_since(mark):
        if line.startswith("Footprint") or line.startswith("Cannot build") or line.startswith("Site check"):
            print(line)
    mark = log_length()
    rcon(f"execute as {PLAYER} at @s run ata_station confirm")
    for _ in range(90):
        time.sleep(1)
        done = [line for line in chat_since(mark) if "Station built" in line or "site changed" in line or "cannot be built" in line]
        if done:
            print(done[-1])
            return
    print("no build result after 90 s")


def views(key, fx, fz, direction="south"):
    """Camera views (name, camera feet, target) at the plan's own features. Local v runs along the tracks (DIR), u across
    them (DIR turned a quarter clockwise seen from above), from the footprint corner (fx, fz) the build printed."""
    p = plan(key)
    roles = p["roles"]
    blocks = [(b[0], b[1], b[2], roles[b[3]]) for b in p["blocks"]]
    vmin = min(b[2] for b in blocks)
    (vx, vz), _ = DIRECTIONS[direction]
    ux, uz = -vz, vx
    world = lambda u, y, v: (fx + u * ux + (v - vmin) * vx + 0.5, -59 + y, fz + u * uz + (v - vmin) * vz + 0.5)
    umax = max(b[0] for b in blocks)
    vmax = max(b[2] for b in blocks)
    found = []

    def view(name, cam, target):
        found.append((name, world(*cam), world(*target)))

    def middle(cells):
        us = sorted(c[0] for c in cells)
        vs = sorted(c[2] for c in cells)
        return us[len(us) // 2], vs[len(vs) // 2]

    view("over", (-12, 22, -14), (umax / 2, 3, vmax / 3))
    floors = [b for b in blocks if b[3] == "FLOOR" and b[1] == 0]
    terminus = any(b[3] == "BUFFER_STOP" for b in blocks)
    if floors:
        u, v = middle(floors)
        view("hall", (u, 1, min(b[2] for b in floors) + 1), (u, 2, v + 4))
    upper = [b for b in blocks if b[3] == "FLOOR" and b[1] == 5]
    if upper:
        u, v = middle(upper)
        view("upstairs", (u, 6, min(b[2] for b in upper) + 1), (u, 7, v + 3))
    deck = [b for b in blocks if b[3] == "BRIDGE_DECK"]
    if deck:
        u, v = middle(deck)
        view("bridge", (max(b[0] for b in deck) - 1, 6, v), (u - 6, 7, v))
    building_u = max(b[0] for b in floors) if floors else -1
    stairs = [b for b in blocks if b[3] == "STAIR" and b[1] <= 2 and b[0] > building_u]
    if stairs:
        s = min(stairs, key=lambda b: (b[1], b[2]))
        view("stair", (s[0], 1, s[2] + (8 if any(b[2] < s[2] for b in stairs) else -8)), (s[0], 3.5, s[2]))
    platform = sorted({b[0] for b in blocks if b[3] == "PLATFORM_SURFACE"})
    if platform:
        u = platform[len(platform) // 2]
        view("platform", (u, 1, 6 if floors else vmax - 6), (u, 2, vmax / 2))
    gates = [b for b in blocks if b[3] == "GATE" and b[1] == 1]
    if gates and not terminus:
        gu, gv = gates[0][0], sorted(b[2] for b in gates)[len(gates) // 2]
        view("gates", (gu + 3, 1, gv + 5), (gu, 2, gv))
    if terminus:
        vb = next(b[2] for b in blocks if b[3] == "BUFFER_STOP")
        u = platform[len(platform) // 2]
        view("gates", (u, 1, vb - 9), (u, 2.5, vb + 3))
        view("street", (umax / 2, 3, vmax + 14), (umax / 2, 4, vmax - 4))
        view("junction", (-6, 14, vb - 14), (umax / 2, 6, vb))
    return found


def tour(key, x0, zfoot, direction="south"):
    rcon(f"gamemode spectator {PLAYER}", "weather clear 1000000", "time set noon")
    files = []
    for name, cam, target in views(key, x0, zfoot, direction):
        look(*cam, *target)
        time.sleep(2.5)  # chunk sections around a new camera position need a moment to mesh
        path = shot(f"{key}_{name}", "nohud fov=70")
        if path:
            files.append(path)
        else:
            print("no screenshot for", name)
    out = os.path.join(OUT, key + ".png")
    sheet(out, files)
    print(out, len(files), "views")


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        return
    command, args = argv[1], argv[2:]
    if command == "rcon":
        for command_text, out in zip(args, rcon(*args)):
            print(f"> {command_text}\n{out}")
    elif command == "build":
        build(args[0], int(args[1]), int(args[2]), *args[3:4])
    elif command == "tour":
        tour(args[0], int(args[1]), int(args[2]), *args[3:4])
    elif command == "shot":
        print(shot(args[0], " ".join(args[1:])) or "no screenshot")
    elif command == "look":
        look(*map(float, args[:6]))
    else:
        sys.exit(f"unknown command {command}; see --help")


if __name__ == "__main__":
    main(sys.argv)
