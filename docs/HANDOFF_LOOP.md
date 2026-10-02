# Handoff: finish ATA 1.4 with /loop and Sonnet subagents

Paste everything below the line into a new Claude Code session (Opus) in this repo, prefixed with `/loop ` (no interval: the session paces itself).

---

You are taking over as lead engineer on Aurelia Transit Architecture (ATA), an MTR 4.0.5 addon (Minecraft 1.20.1 Fabric), on branch `release/1.3.1`. Each /loop iteration does **one** item from the queue below, verifies it, records it, and schedules the next iteration. You (Opus) own architecture, MTR integration, performance and acceptance. Delegate bounded work to **Sonnet subagents** (`model: "sonnet"`): independent diff reviews, unit tests for a finished pure class, asset/model generation in a single new `tools/assets_*.py` module, read-only audits, doc edits. Each brief must be self-contained: the goal, the files it may touch, the files it must not touch, the constraints, and what to return. Parallel agents never edit the same file. Read their diffs and run the gates yourself before accepting.

## Resume point (state at handoff, 2026-10-01)

- Read first: `docs/SESSION_LOG.md` (source of truth), `docs/STATUS_2026-10-01.md`, `docs/DESIGN_1.4.md`, `docs/RELEASE_CHECK_1.3.md` (sections 1.3.1 and 1.4 packages 2-5), `docs/MTR_INTEGRATION.md`, `CHANGELOG.md`. Do not re-read code you do not touch.
- Commits so far: `909407a` 1.3.1 (tag candidate v1.3.1), `9363e36` package 2, `bc72fa0` package 3, `91ed5ca` package 4.
- **Package 5 is staged but NOT committed**: PSD text panel (A17), stand-back sign (A16), composition board (A18), and follow-ups to the package 4 review. Unstaged on top of it: the version bump to `1.4.0` (gradle.properties, CHANGELOG, README), `docs/HANDOFF_LOOP.md` and `tools/release_check/mc_automation.sh`. Its independent review was cut off by a rate limit and **has not run**.
- Last verified gates: 237 tests, `python3 tools/verify_assets.py` → Asset audit: OK, `python3 tools/check_zfighting.py` → 0 flagged, `./gradlew build` OK (jar `aurelia-transit-architecture-1.4.0+mc1.20.1-fabric.jar`).

## Queue (one item per iteration, in order)

1. **Package 5 review and commit.** Spawn a Sonnet reviewer on `git diff --cached` plus the unstaged files. Focus: `TrainEdgeRenderer`'s static per-platform `TimedLruCache` (the lambda captures the first caller's pos/stationId; stale platform when a block's platform changes), README block counts (96) vs `register("` calls, composition_board rods (at most two per joined row), TextLayout planes vs model faces, lang keys, docs vs code. Fix real bugs, run all gates, `git add -A` (never `docs/MEGAPROMPT_1.3.1_to_1.4.md`), commit "1.4 package 5 ...", and fill in the commit hashes in SESSION_LOG.
2. **Live re-checks still open** (dev client, see "In game" below), one sitting:
   - The boarding step slides toward the track while a train stands (Alpha P1, x 16-19, ~160 s headway; `/aurelia_live edge` shows the next arrival).
   - Re-measure the counters with 8 edges after the per-platform cache (expect provider_refresh well below +785 per 60 s).
   - Concourse editor: the Departures/Summary row fits; Done is not off-screen at GUI scale 2 and at a 854x480 window.
   - Lift panel Status cycler clicked in the editor.
   - Exits view on a station with MTR-defined exits.
   - Departures-mode column headings.
   Record each in RELEASE_CHECK as ✅/❌/➖ with screenshots in `screenshots/1.4/`, then commit.
3. **Shader pass in the dev client**, only if it is small: add Iris's preprocessor library (`org.anarres:jcpp`, the version Iris 1.7.6 bundles) as a `modLocalRuntime`/`localRuntime` dependency so shader packs load in `./gradlew runClient`. Never add it to the published jar. Then shoot the new families under the light pack (copy `AureliaShaders-v1-candidate.zip` from the Prism instance's `shaderpacks/`; read-only, do not modify the Prism instance). If it is not small, leave it as a "Request raised".
4. **Readability items marked "needs human eye"**: list them in a short checklist at the top of RELEASE_CHECK's 1.4 section for the release owner. Do not change visuals without a failing check.
5. **Final report**, then stop the loop (`ScheduleWakeup` with `stop: true`): what shipped (hashes), how it was verified, what was skipped or needs the owner's eyes, and the exact commands: `./gradlew clean build`; `git tag v1.3.1 909407a`; `git tag v1.4.0 <final commit>`. **Do not run the tag commands.**

## Hard constraints (unchanged)

- No mixins, no MTR core changes. Where MTR lacks data, build the honest subset, document the gap, never fake live data.
- Potato-PC contract (triage §11): no per-tick block-entity work, no new server polling, bounded caches (512/512/128, 30 s), static first, fail visibly with an idle message.
- Generic design only, with no real operator branding. New ideas go as one line under STATUS "Requests raised" and are not built.
- Commit locally after each item. Never tag, push, publish, delete branches or force anything.
- Do not claim something works unless you ran it. Skipped checks are reported as skipped; unseen visuals are "needs human eye".
- Every new pure-logic class is unit-tested. Keep blockstates, models, lang, loot and recipes complete (the audit enforces this).

## In game (macOS automation, read carefully)

- Use the dev client on the **copy** of the world: `./gradlew runClient --args='--quickPlaySingleplayer "ATA Release Check"'` (world at `run/client/saves/ATA Release Check`, datapack `ata_test` with functions `v131`, `v14_props`, `v14_platform`, `v14_signs`, `v14_names`; regenerate them with `tools/release_check/gen_v131.py` / `gen_v14.py` and copy `tools/release_check/ata_test` into the world's `datapacks/`, then `/reload`).
- `source tools/release_check/mc_automation.sh` gives `startmc`, `quitmc`, `mc "<command>"`, `key <code>`, `rclick`, `lclick x y`, `cap out.png`. **Keystrokes go to the frontmost app.** Its final `front()` refuses to type unless the Minecraft window is frontmost. Keep that guard: once, two chat commands were typed into the Claude terminal while the client had failed to start. If `pid` is empty, stop and check `/tmp/ata_client.log`.
- `keystroke` cannot type non-ASCII. Put CJK/Cyrillic/Arabic text in an mcfunction instead.
- A macOS "claude.exe would like to access your Desktop folder" dialog may be on screen. That is the owner's decision; never click it.
- Shader packs crash the dev client until item 3 is done (`ClassNotFoundException: org.anarres.cpp.PreprocessorListener`).

## Each iteration

1. Read SESSION_LOG and pick the first open item.
2. Do it (delegate where the rules above allow).
3. Run the gates: `./gradlew test`, the asset audit, `check_zfighting.py`.
4. Update SESSION_LOG (status, commit, verification, skipped items) and commit.
5. Schedule the next wakeup (delaySeconds 60-120 while work remains; `noop: false` after progress).

If the session limit hits, SESSION_LOG must already say exactly where to resume. Stop early only when the next item needs the owner (say which decision), or after item 5.
