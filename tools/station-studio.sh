#!/bin/sh
# Station Studio outside the game: compiles the planner, starts the studio on 127.0.0.1 and opens it in the browser.
# The studio takes the first free port from 25680, so the game's own studio and this one can run side by side.
cd "$(dirname "$0")/.." || exit 1
./gradlew compileJava processResources --offline -q || exit 1
LOG=$(mktemp)
java -cp build/classes/java/main:build/resources/main tools/StationStudio.java > "$LOG" 2>&1 &
STUDIO=$!
TAIL=
trap 'kill $STUDIO $TAIL 2>/dev/null; rm -f "$LOG"' INT TERM EXIT
URL=
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
	URL=$(sed -n 's/^Station Studio: \(http[^ ]*\).*/\1/p' "$LOG")
	[ -n "$URL" ] && break
	kill -0 $STUDIO 2>/dev/null || break
	sleep 0.5
done
cat "$LOG"
[ -n "$URL" ] || exit 1
open "$URL" 2>/dev/null || xdg-open "$URL" 2>/dev/null
tail -n 0 -f "$LOG" &
TAIL=$!
wait $STUDIO
