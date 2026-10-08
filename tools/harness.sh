#!/usr/bin/env bash
# One command for everything that can be checked without a human playing the game.
#
#   tools/harness.sh
#
# Steps, in order (every step runs even if an earlier one fails, then a summary is printed):
#   1. generate_assets.py, then check that it changed nothing (a git diff of the generated resources before and after must match,
#      so stale checked-in assets are caught whether or not the tree is clean)
#   2. verify_assets.py          registry vs blockstates, models, textures, loot, lang, element budget
#   3. check_zfighting.py        coplanar overlapping faces
#   4. ./gradlew test            unit tests
#   5. ./gradlew runGametest     headless Fabric GameTests on a dedicated server (src/gametest, never in the release jar)
#   6. render_preview.py --changed   offline model previews of the blocks that changed, written to build/previews/
#
# Set GRADLE_ARGS to pass extra flags to Gradle, for example GRADLE_ARGS=--offline.
# Exit code: 0 when every step passed, 1 otherwise.
set -u
cd "$(dirname "$0")/.."

PY=${PYTHON:-python3}
GRADLE_ARGS=${GRADLE_ARGS:-}
RESOURCES=src/main/resources
LOG_DIR=build/harness
mkdir -p "$LOG_DIR"

names=()
results=()
seconds=()

# run <name> <command...>: runs the command with its output in build/harness/<n>.log, shown only when it fails
run() {
	local name=$1
	shift
	local n=$((${#names[@]} + 1))
	local log="$LOG_DIR/$n.log"
	printf '[%d/6] %s ... ' "$n" "$name"
	local start=$SECONDS
	if "$@" >"$log" 2>&1; then
		results+=("PASS")
		echo "pass ($((SECONDS - start))s)"
	else
		results+=("FAIL")
		echo "FAIL ($((SECONDS - start))s)"
		echo "----- last lines of $log -----"
		tail -n 25 "$log"
		echo "-----"
	fi
	names+=("$name")
	seconds+=("$((SECONDS - start))")
}

snapshot() {
	{
		git status --porcelain -- "$RESOURCES"
		git diff -- "$RESOURCES"
	}
}

generate_and_compare() {
	local before after
	before=$(snapshot) || return 1
	"$PY" tools/generate_assets.py || return 1
	after=$(snapshot) || return 1
	if [ "$before" != "$after" ]; then
		echo "Generated assets are stale: generate_assets.py changed files that are checked in or in the working tree."
		echo "Run python3 tools/generate_assets.py and commit the result. Changed:"
		diff <(echo "$before") <(echo "$after") | head -n 20
		return 1
	fi
	git diff --exit-code --quiet -- "$RESOURCES" >/dev/null 2>&1 || echo "note: the working tree has uncommitted resource changes (they match what the generator produces)"
}

run "generate_assets.py (assets up to date)" generate_and_compare
run "verify_assets.py" "$PY" tools/verify_assets.py
run "check_zfighting.py" "$PY" tools/check_zfighting.py
# shellcheck disable=SC2086
run "gradlew test" ./gradlew $GRADLE_ARGS test
# shellcheck disable=SC2086
run "gradlew runGametest" ./gradlew $GRADLE_ARGS runGametest
run "render_preview.py --changed" "$PY" tools/render_preview.py --changed

echo
echo "================ harness summary ================"
failed=0
for i in "${!names[@]}"; do
	printf '  %-4s %-40s %ss\n' "${results[$i]}" "${names[$i]}" "${seconds[$i]}"
	[ "${results[$i]}" = "PASS" ] || failed=1
done
if grep -h "required tests passed" "$LOG_DIR/5.log" 2>/dev/null; then :; fi
echo "Logs: $LOG_DIR/   Previews: build/previews/"
if [ "$failed" = 0 ]; then
	echo "ALL PASSED"
else
	echo "FAILED"
fi
exit "$failed"
