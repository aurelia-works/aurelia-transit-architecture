#!/bin/sh
# Station Studio outside the game: compiles the planner, starts the studio on 127.0.0.1 and opens it in the browser.
cd "$(dirname "$0")/.." || exit 1
./gradlew compileJava processResources --offline -q || exit 1
java -cp build/classes/java/main:build/resources/main tools/StationStudio.java &
STUDIO=$!
trap 'kill $STUDIO 2>/dev/null' INT TERM EXIT
sleep 2
open "http://127.0.0.1:25680/" 2>/dev/null || xdg-open "http://127.0.0.1:25680/" 2>/dev/null
wait $STUDIO
