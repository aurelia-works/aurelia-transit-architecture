#!/bin/sh
# Fast checks run by ~/.agents/hooks/verify-stop.sh before an agent finishes: compile and asset checks for the kinds of
# files that changed. Tests stay the agent's job (CLAUDE.md "Verification") and CI's.
CHANGED=$( (git diff HEAD --name-only; git ls-files -o --exclude-standard) 2>/dev/null)
has() { printf '%s\n' "$CHANGED" | grep -q "$1"; }
run() { OUT=$("$@" 2>&1) || { printf '%s\n' "$OUT" | grep -v '^Note:' | tail -30; exit 1; }; }
has '\.java$' && run ./gradlew compileJava compileTestJava compileGametestJava --offline -q
if has '^src/main/resources/assets/\|^src/main/resources/data/'; then
	run python3 tools/verify_assets.py
	run python3 tools/check_zfighting.py
fi
has 'studio/.*\.js$' && run node --check src/main/resources/studio/studio.js
exit 0
