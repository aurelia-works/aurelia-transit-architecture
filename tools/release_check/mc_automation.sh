# macOS automation for the dev client (./gradlew runClient). source this file. Later definitions override earlier ones;
# the final front()/key()/mc() refuse to send keys unless the Minecraft window is frontmost.

# helpers: mc "<command without slash>" [sleep] ; key <code> ; cap out.png
LOG="/Users/Boon/Downloads/Aurelia Transit Architecture/run/client/logs/latest.log"
pid() { pgrep -f 'net.fabricmc.devlaunchinjector.Main|KnotClient' | head -1; }
front() { osascript -e "tell application \"System Events\" to set frontmost of (first process whose unix id is $(pid)) to true"; sleep 0.6; }
mc() { front; osascript -e 'tell application "System Events"' -e 'keystroke "t"' -e 'delay 0.4' -e "keystroke \"/$1\"" -e 'delay 0.2' -e 'key code 36' -e 'end tell'; sleep "${2:-1}"; }
key() { front; osascript -e "tell application \"System Events\" to key code $1"; sleep "${2:-0.5}"; }
cap() { front; B=$(osascript -e "tell application \"System Events\" to tell (first process whose unix id is $(pid)) to get {position, size} of window 1" | tr -d ' '); IFS=, read X Y W H <<< "$B"; screencapture -x -R$X,$((Y+28)),$W,$((H-28)) "$1"; }
rclick() { front; osascript -l JavaScript -e 'ObjC.import("CoreGraphics"); var p=$.CGPointMake(427,270); var d=$.CGEventCreateMouseEvent(null,$.kCGEventRightMouseDown,p,$.kCGMouseButtonRight); $.CGEventPost($.kCGHIDEventTap,d); delay(0.08); var u=$.CGEventCreateMouseEvent(null,$.kCGEventRightMouseUp,p,$.kCGMouseButtonRight); $.CGEventPost($.kCGHIDEventTap,u);' >/dev/null; sleep "${1:-1}"; }
lclick() { osascript -l JavaScript -e "ObjC.import('CoreGraphics'); var p=\$.CGPointMake($1,$2); var m=\$.CGEventCreateMouseEvent(null,\$.kCGEventMouseMoved,p,0); \$.CGEventPost(\$.kCGHIDEventTap,m); delay(0.1); var d=\$.CGEventCreateMouseEvent(null,\$.kCGEventLeftMouseDown,p,0); \$.CGEventPost(\$.kCGHIDEventTap,d); delay(0.08); var u=\$.CGEventCreateMouseEvent(null,\$.kCGEventLeftMouseUp,p,0); \$.CGEventPost(\$.kCGHIDEventTap,u);" >/dev/null; sleep "${3:-0.6}"; }
quitmc() { front; osascript -e 'tell application "System Events" to keystroke "q" using command down'; for i in $(seq 1 40); do [ -z "$(pid)" ] && break; sleep 1; done; }
waitmc() { sleep 20; for i in $(seq 1 60); do [ -n "$(pid)" ] && osascript -e "tell application \"System Events\" to tell (first process whose unix id is $(pid)) to get position of window 1" >/dev/null 2>&1 && grep -q "joined the game" "$LOG" 2>/dev/null && break; sleep 3; done; sleep 8; osascript -e "tell application \"System Events\" to tell (first process whose unix id is $(pid)) to set position of window 1 to {0, 30}" -e "tell application \"System Events\" to tell (first process whose unix id is $(pid)) to set size of window 1 to {1440, 900}"; sleep 2; }
# mcq: like mc but safe for quotes (pastes the command through the clipboard)
mcq() { front; printf '/%s' "$1" | pbcopy; osascript -e 'tell application "System Events"' -e 'keystroke "t"' -e 'delay 0.4' -e 'keystroke "v" using command down' -e 'delay 0.2' -e 'key code 36' -e 'end tell'; sleep "${2:-1}"; }
quitmc() { front; osascript -e 'tell application "System Events" to keystroke "q" using command down'; for i in $(seq 1 40); do [ -z "$(pid)" ] && break; sleep 1; done; for p in $(pgrep -f devlaunchinjector); do kill $p; done; sleep 3; rm -f "/Users/Boon/Downloads/Aurelia Transit Architecture/run/client/saves/ATA Release Check/session.lock"; }
startmc() { cd "/Users/Boon/Downloads/Aurelia Transit Architecture" && (./gradlew runClient --args='--quickPlaySingleplayer "ATA Release Check"' > /tmp/ata_client.log 2>&1 &); waitmc; }
# keystrokes posted straight to the client process (System Events keystrokes stopped arriving)
pk() { osascript -l JavaScript -e "ObjC.import('CoreGraphics'); var d=\$.CGEventCreateKeyboardEvent(null,$1,true); if ($2) \$.CGEventSetFlags(d,$2); \$.CGEventPostToPid($(pid),d); delay(0.04); var u=\$.CGEventCreateKeyboardEvent(null,$1,false); if ($2) \$.CGEventSetFlags(u,$2); \$.CGEventPostToPid($(pid),u);" >/dev/null; }
key() { pk $1 0; sleep "${2:-0.5}"; }
mc() { printf '/%s' "$1" | pbcopy; pk 17 0; sleep 0.5; pk 9 1048576; sleep 0.3; pk 36 0; sleep "${2:-1}"; }
mcq() { mc "$@"; }
# System Events again, with a title-bar click to give the window key focus; command passed as argv (quotes safe)
front() { osascript -e "tell application \"System Events\" to set frontmost of (first process whose unix id is $(pid)) to true"; sleep 0.3; lclick 700 40 0.4; }
key() { front; osascript -e "tell application \"System Events\" to key code $1"; sleep "${2:-0.5}"; }
mc() { front; osascript -e 'on run argv' -e 'tell application "System Events"' -e 'keystroke "t"' -e 'delay 0.4' -e 'keystroke ("/" & item 1 of argv)' -e 'delay 0.2' -e 'key code 36' -e 'end tell' -e 'end run' "$1"; sleep "${2:-1}"; }
mcq() { mc "$@"; }
# guard: never send keys unless the Minecraft client exists and is frontmost
front() { local p=$(pid); [ -z "$p" ] && { echo "NO CLIENT" >&2; return 1; }; osascript -e "tell application \"System Events\" to set frontmost of (first process whose unix id is $p) to true" || return 1; sleep 0.3; lclick 700 40 0.4; local fp=$(osascript -e 'tell application "System Events" to get unix id of first process whose frontmost is true'); [ "$fp" = "$p" ] || { echo "CLIENT NOT FRONT" >&2; return 1; }; }
key() { front || return 1; osascript -e "tell application \"System Events\" to key code $1"; sleep "${2:-0.5}"; }
mc() { front || return 1; osascript -e 'on run argv' -e 'tell application "System Events"' -e 'keystroke "t"' -e 'delay 0.4' -e 'keystroke ("/" & item 1 of argv)' -e 'delay 0.2' -e 'key code 36' -e 'end tell' -e 'end run' "$1"; sleep "${2:-1}"; }
rclick() { front || return 1; osascript -l JavaScript -e 'ObjC.import("CoreGraphics"); var p=$.CGPointMake(720,480); var d=$.CGEventCreateMouseEvent(null,$.kCGEventRightMouseDown,p,$.kCGMouseButtonRight); $.CGEventPost($.kCGHIDEventTap,d); delay(0.08); var u=$.CGEventCreateMouseEvent(null,$.kCGEventRightMouseUp,p,$.kCGMouseButtonRight); $.CGEventPost($.kCGHIDEventTap,u);' >/dev/null; sleep "${1:-1}"; }
