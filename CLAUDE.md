# Aurelia Transit Architecture (ATA)

MTR 4.0.5 addon, Minecraft 1.20.1 Fabric, Java. Read this file, then `HANDOFF.md` (local, gitignored). Do not read anything else up front.

## Commands
- While iterating, run only the tests you touched:
  `./gradlew test --offline --tests 'com.aureliatransit.architecture.station.StationPlannerTest'`
- Full gate, **once**, before a merge or when asked:
  `./gradlew build test --offline && ./gradlew runGametest --offline`
  (`test` alone skips the game tests.) Add `python3 tools/verify_assets.py` and `python3 tools/check_zfighting.py` when assets changed.
- Never use `--rerun-tasks` unless a stale build is proven. The Gradle build cache is on.

## Live testing
- Jah play-tests in the dev client. Never launch `runClient` or `runServer`, and never kill Java, unless Jah asks. A hook blocks a second client.
- When asked, launch the client as `./gradlew runClient --offline --args="--username ATATester"`. ATATester is the op in `run/server/ops.json` and `tools/station_tour.py`'s player; without `--username` the client gets a random `PlayerNNN` with no op. Dev server port: 25599.
- `run/` exists only in this checkout. To test a branch from a worktree, symlink `run/server` and `run/client` from here into the worktree and launch there.
- Live-test recipes and the screenshot hook: `docs/LIVE_TESTING.md`. Station Studio is served by `StudioServer`.

## Where things are
- Station generator: `station/` (parser → spec → planner → validator/audit → build job). Design: `docs/STATION_GENERATOR.md`, Studio UI: `docs/STUDIO_DESIGN.md`.
- MTR hooks: `docs/MTR_INTEGRATION.md`.
- History, do not read unless asked: `docs/STATUS_2026-10-01.md`, `docs/RELEASE_CHECK_1.3.md`, `docs/DESIGN_1.4.md`, `docs/SESSION_LOG.md`, `docs/MEGAPROMPT_1.3.1_to_1.4.md`, `CHANGELOG.md` (append only).

## Hard rules
- No mixins and no MTR core changes. Where MTR lacks data, build the honest subset and say so.
- Potato-PC contract: no per-tick block-entity work, no new server polling, bounded caches.
- Real stations, operator looks and logos are allowed (Jah, 2026-10-19). Use them in the Studio/generator freely.
- Every new pure-logic class gets a unit test. Keep blockstates, models, lang, loot and recipes complete.
- `main` = `release/1.6.0` until Jah approves 1.6.0 in game. Work on a branch; merge one branch at a time.
- If docs and code disagree, follow the code and list each inconsistency in your final reply.
- Before saying "we do/don't have X", reopen the code and cite file:line.
- Use the names already in the code and docs. Don't invent terminology.
- Review feedback never expands a change beyond its goal. Verify each finding against the source before acting.
- Unit tests in `src/test/java`, game tests via `runGametest`. Never claim a visual works unless you saw it.


## Stuck points and follow-ups (Jah, 2026-10-21)
- When stuck or needing a decision, ask Jah with AskUserQuestion, and record the point here (replace stale lines, keep it short).

Jah 10-21: "Finish the stations, make them realistic, finish what needs to be finished, then do whatever you want": finish the list below, then release 1.6.0 (merge, gate, tag, GitHub release, Modrinth).

Open (realism pass, worktree `../ATA-planner-split`, branch `agent/real-stations`):
- Concourse and entrance redesign (approved 5-7 h): in progress. `ConcourseFit` + `PlanFitOut` (boards, shops, kiosks, seats on big upper decks; portal, canopy, name, pylon at street doors). Seen in Studio at Utrecht only.
- Real-station fidelity: facades are approximations; layouts simplified (Frankfurt real 24 tracks, preset capped at 10; Grand Central's tracks are on two levels below the street, the preset is at grade).
- Edge railings (every handrail, station fence, glass barrier on the drop side of its block, corner pieces): built (`Planning.edgeRailings`, `RailEdges`, `tools/edge_railings.py`), seen in Studio only; needs the game-test gate and an in-game look.
- CLINKER look has no preset since Hamburg Dammtor went (test exempts it): ask Jah whether to add a real clinker-brick station.
- Faster-check hook (probe after station edits): proposed, awaiting Jah's yes.
- 1.6.0 trailer: workspace `~/Movies/ATA-trailer` (`node render.mjs all` there; music `audio/score.py` + `audio/master.sh`). v2 = original synth score in the mood of Anthropic's launch music (not a remix: copyright), plain (no-shader) hero shots, new UNDO shot; v1 kept as `video/launch_v1.mp4`. v2 awaits Jah's listen. No PIDS, train or announcement shots.
- None of the overrun/buffer/PIDS-centring work seen in game yet.
- Fixed 10-21, committed on `agent/real-stations` (8864e75, 6681da2), seen in Studio only, not in game: Grand Central's side lobbies open onto the hall (ground arcade); stringers under low stair steps (no walking in under a flight); Jah's Utrecht terminus items: fence-post second row (Studio guessed corner=north_cw; `BlockModels` now defaults corner to none), ballast between neighbouring buffer stops, overrun strip all platform floor, MTR gate-bank ends are the line's glass fence (was a mid-cell ATA glass_barrier).

Jah 10-22 play-test (Utrecht, "10 track utrecht station, 200 long"): 1.6.0 NOT ready. Work on `agent/real-stations` in `../ATA-planner-split`, Studio/tests only, nothing seen in game:
- A (done, 3e0749a + follow-up): no MTR tactile map or name board, facade name is the new `station_lettering` block, operator fonts (Hind/Barlow, OFL) on NS/GVB/DB/S-Bahn signs (one-line revert in `SignStyle.font()`), pylon beside canopy facing street, kiosks flush, waiting-room glass gone, one seating layout, no stray benches. Ticket machine: both halves answer in a game test, not reproduced; ask Jah what he held and where he clicked.
- B (in progress): "utrecht station/central/cs" now builds the real Utrecht Centraal hall when the words have nothing against it (`RealStation.loosely`, parser `looseFits`); Studio card writes "utrecht style" and stays the look. Hall roof dark grey with silver rim, lit beams, skylight grid (new blocks `utrecht_hall_roof`, `utrecht_hall_beam`). Next: rounded silver platform canopies with a glass strip, slender columns, hall fit-out (glass shop pavilions, boards on poles).
- Studio quirk for C: the opening state rolls a random station and may pin roof/size; pinned settings keep typed "utrecht station" as the look.
- C (later): Studio UI simpler, less cramped.

Jah 10-24/25 realism fix: branch `agent/station-realism` (off `agent/real-stations` b8171ec) in worktree `../ATA-station-realism`, uncommitted, gate green 10-25 (821 unit, 913 game; studio_swaps 30/30, studio_locks pass). Pass 3 as before; 10-25: shed wall along the side building's line where the building does not stand (`PlanRoofs.buildingLineWall`; Hamburg, Amsterdam), studio_swaps flake fixed (test causes + `wordsLook` only when words name a look). Shed walls not seen in game yet. Next from Jah: a better real-life entrance concourse (scope to agree). Awaiting Jah's yes: Utrecht curved outer canopies, Grand Central below street, Hamburg cutting/second tower, big clock faces, Amsterdam wind dial.

Jah 10-25: 1.6.0 RELEASED (88abfc5, tag v1.6.0, GitHub + Modrinth iegLePqE). Open after release: 15 city cards Studio-only, PIDS arm not seen in game; helper worktrees `../ATA-cities-a`, `../ATA-cities-b` can go on Jah's OK.

Jah 10-26: 1.7.0 = airports + underground, in this order, each its own branch and session (~20-30 h agreed; airports likely more):
1. Build job streams chunks: built on `agent/chunk-streaming` (`../ATA-streaming`), not merged. Capped `ChunkHold` window (1024), release on end; survey and rebuild `ours()` run over ticks as site tasks (Jah 10-26: "all of it"). Airport scale tests (`ATA_SCALE_TESTS=1`) pass: footprint 40,608 chunks (was OOM), runway 306k ops. Not seen in game.
2. Underground: deep tube (Angel/Westminster) + cut-and-cover box (Canary Wharf/Vijzelgracht) beside `PlanUnderground` (shallow U-Bahn). Match `docs/research/*` and reference photos.
Underground and airports are built on the station generator pipeline (parser → spec → planner layers → validator/audit → build job), not beside it (Jah 10-26). How-to: skill `ata-generator` (`~/.agents/skills/ata-generator`).
3. Airports, all of: runway + taxiways, gates + MTR airplane route, terminal building, real airport presets. Default size (Jah 10-26): Velare-style semi-realistic, one ~1,700 m runway at 1.5:1 (~2,550 blocks); hubs only from presets or words like "2 runways". Spike `../ATA-airport-spike` (uncommitted) proves the MTR route (needs depot frequencies; shipped path = `Init.sendMessageC2S("update_data")` as in `MtrRails`).
Station Codex merged into main 10-26 (916d71e, fast-forward, 914/914 game tests), pushed.
