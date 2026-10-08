#!/usr/bin/env bash
# In-game screenshot tour: launches the dev client, lets a datapack fly the camera through the 1.5 scene and captures the
# Minecraft window (only that window, never the desktop) once per view. Needs no Accessibility permission and sends no keystrokes.
#
#   tools/release_check/shoot.sh [--views views.json] [--keep]
#
#   --views <json>  custom views instead of the defaults (see tour.py)
#   --keep          leave the client running and the tour datapack in place when finished (default: quit and clean up)
#
# Output: build/tour/<view>.png for every view, build/tour/sheet.png (labelled contact sheet), build/tour/views.json, build/tour/client.log
# Steps: back up run/client/options.txt and set fov 70 + hidden GUI (restored on exit, even on Ctrl-C) -> stop any running dev client ->
# gen_v15 + tour.py + rsync of the ata_test pack into the save -> runClient -> wait for "joined the game" in a fresh latest.log ->
# find the window id with CoreGraphics (JXA) -> for each view wait for its "ATA_TOUR_VIEW n" line in the log, give the camera
# 2.5 s to settle and screencapture the window -> quit -> remove the tour tags and functions from the save.
set -u
cd "$(dirname "$0")/../.."
ROOT=$PWD
PY=${PYTHON:-python3}
SAVE="$ROOT/run/client/saves/ATA Release Check"
OPTIONS="$ROOT/run/client/options.txt"
LOG="$ROOT/run/client/logs/latest.log"
OUT="$ROOT/build/tour"
TITLEBAR=28        # points of window title bar cropped from every capture
SETTLE=2.5         # seconds between a view starting and its capture (a view lasts 5 s)
KEEP=0
VIEWS_ARGS=()
while [ $# -gt 0 ]; do
	case "$1" in
		--views) VIEWS_ARGS=(--views "$2"); shift 2 ;;
		--keep) KEEP=1; shift ;;
		*) echo "unknown argument $1" >&2; exit 2 ;;
	esac
done

mkdir -p "$OUT"
rm -f "$OUT"/*.png
BACKUP="$OUT/options.txt.bak"
cp "$OPTIONS" "$BACKUP"

client_pids() { pgrep -f 'net.fabricmc.devlaunchinjector.Main|KnotClient'; }

cleanup() {
	trap - EXIT INT TERM
	if [ "$KEEP" = 0 ]; then
		for p in $(client_pids); do kill "$p" 2>/dev/null; done
		for _ in $(seq 1 20); do [ -z "$(client_pids)" ] && break; sleep 1; done
		for p in $(client_pids); do kill -9 "$p" 2>/dev/null; done
		rm -f "$SAVE/session.lock"
		"$PY" tools/release_check/tour.py --clean
	fi
	cp "$BACKUP" "$OPTIONS" && echo "options.txt restored"
}
trap cleanup EXIT INT TERM

# 1. options: FOV 70 (the slider value 0.0), no GUI and no chat, so the HUD and the tour's own `say` lines do not cover the scene
set_option() { # key value
	if grep -q "^$1:" "$OPTIONS"; then sed -i '' "s/^$1:.*/$1:$2/" "$OPTIONS"; else echo "$1:$2" >>"$OPTIONS"; fi
}
set_option fov 0.0
set_option hideGui true
set_option chatVisibility 2

# 2. stop any running dev client
for p in $(client_pids); do kill "$p" 2>/dev/null; done
for _ in $(seq 1 20); do [ -z "$(client_pids)" ] && break; sleep 1; done
for p in $(client_pids); do kill -9 "$p" 2>/dev/null; done
rm -f "$SAVE/session.lock"

# 3. scene, tour, datapack
"$PY" tools/release_check/gen_v15.py || exit 1
"$PY" tools/release_check/tour.py "${VIEWS_ARGS[@]+"${VIEWS_ARGS[@]}"}" || exit 1
mkdir -p "$SAVE/datapacks/ata_test"
rsync -a "$ROOT/tools/release_check/ata_test/" "$SAVE/datapacks/ata_test/"
NVIEWS=$("$PY" -c "import json;print(len(json.load(open('$OUT/views.json'))))")

# 4. launch
LAUNCHED=$(date +%s)
touch "$OUT/.launched"
(./gradlew runClient --args='--quickPlaySingleplayer "ATA Release Check" --width 1600 --height 900' >"$OUT/client.log" 2>&1 &)

# 5. wait for a fresh latest.log that has the join line
echo "waiting for the client to join the world ..."
joined=0
for _ in $(seq 1 240); do
	if [ "$LOG" -nt "$OUT/.launched" ] && grep -q "joined the game" "$LOG" 2>/dev/null; then joined=1; break; fi
	sleep 2
done
[ "$joined" = 1 ] || { echo "client never joined the world (see $OUT/client.log)"; exit 1; }
echo "joined after $(( $(date +%s) - LAUNCHED )) s"

# 6. window id of the Minecraft window (CoreGraphics window list; no Accessibility permission needed). Prints "<id> <width> <height>".
window_info() {
	osascript -l JavaScript - "$(client_pids | head -1)" <<'JXA'
ObjC.import('CoreGraphics');
function run(argv) {
	var pid = parseInt(argv[0]);
	var list = ObjC.deepUnwrap(ObjC.castRefToObject($.CGWindowListCopyWindowInfo($.kCGWindowListOptionOnScreenOnly | $.kCGWindowListExcludeDesktopElements, 0)));
	var best = null, bestArea = 0;
	for (var i = 0; i < list.length; i++) {
		var w = list[i], name = w.kCGWindowName || '', b = w.kCGWindowBounds;
		if (w.kCGWindowLayer !== 0) continue;
		var byName = name.indexOf('Minecraft') === 0;
		var byPid = w.kCGWindowOwnerPID === pid && b.Width > 400;
		if (!byName && !byPid) continue;
		var area = b.Width * b.Height * (byName ? 2 : 1);
		if (area > bestArea) { best = w; bestArea = area; }
	}
	return best ? best.kCGWindowNumber + ' ' + best.kCGWindowBounds.Width + ' ' + best.kCGWindowBounds.Height : '';
}
JXA
}
info=""
for _ in $(seq 1 30); do info=$(window_info 2>/dev/null); [ -n "$info" ] && break; sleep 2; done
[ -n "$info" ] || { echo "no Minecraft window found"; exit 1; }
read -r WID WWIDTH WHEIGHT <<<"$info"
echo "window $WID (${WWIDTH}x${WHEIGHT} points)"

# 7./8. one capture per view, each lined up with the view's own log line
names=($("$PY" -c "import json;print(' '.join(v['name'] for v in json.load(open('$OUT/views.json'))))"))
for i in $(seq 1 "$NVIEWS"); do
	name=${names[$((i - 1))]}
	waited=0
	until grep -q "ATA_TOUR_VIEW $i $name" "$LOG" 2>/dev/null; do
		sleep 0.5
		waited=$((waited + 1))
		[ "$waited" -gt 600 ] && { echo "view $i ($name) never started"; exit 1; }
	done
	sleep "$SETTLE"
	file="$OUT/$name.png"
	screencapture -x -o -l "$WID" "$file"
	"$PY" - "$file" "$TITLEBAR" "$WWIDTH" <<'PIL'
import sys
from PIL import Image
path, bar, points = sys.argv[1], float(sys.argv[2]), float(sys.argv[3])
img = Image.open(path)
scale = img.width / points  # 2 on a Retina display
img.crop((0, round(bar * scale), img.width, img.height)).convert("RGB").save(path)
PIL
	printf '[%d/%d] %s\n' "$i" "$NVIEWS" "$name"
done

# contact sheet
"$PY" - "$OUT" <<'PIL'
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw
out = Path(sys.argv[1])
names = [v["name"] for v in json.loads((out / "views.json").read_text())]
shots = [(n, Image.open(out / f"{n}.png")) for n in names if (out / f"{n}.png").exists()]
cols, tw = 4, 560
th = round(tw * shots[0][1].height / shots[0][1].width)
rows = (len(shots) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw, rows * (th + 18)), (30, 32, 36))
draw = ImageDraw.Draw(sheet)
for k, (n, im) in enumerate(shots):
    x, y = (k % cols) * tw, (k // cols) * (th + 18)
    sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y + 18))
    draw.text((x + 4, y + 3), f"{k + 1:02d} {n}", fill=(235, 235, 235))
sheet.save(out / "sheet.png")
print("contact sheet:", out / "sheet.png")
PIL

# 9. the tour ends itself (tour_end), then the trap quits the client and removes the tour from the save
if [ "$KEEP" = 0 ]; then
	grep -q "ATA_TOUR_DONE" "$LOG" || sleep 6
fi
echo "done: $OUT"
