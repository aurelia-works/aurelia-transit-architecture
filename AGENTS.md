# Aurelia Transit Architecture (ATA): agent notes

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
- Live-test recipes and the screenshot hook: `docs/LIVE_TESTING.md`. Station Studio is served by `StudioServer`.

## Where things are
- Station generator: `station/` (parser → spec → planner → validator/audit → build job). Design: `docs/STATION_GENERATOR.md`, Studio UI: `docs/STUDIO_DESIGN.md`.
- MTR hooks: `docs/MTR_INTEGRATION.md`.
- History, do not read unless asked: `docs/STATUS_2026-10-01.md`, `docs/RELEASE_CHECK_1.3.md`, `docs/DESIGN_1.4.md`, `docs/SESSION_LOG.md`, `docs/MEGAPROMPT_1.3.1_to_1.4.md`, `CHANGELOG.md` (append only).

## Hard rules
- No mixins and no MTR core changes. Where MTR lacks data, build the honest subset and say so.
- Potato-PC contract: no per-tick block-entity work, no new server polling, bounded caches.
- Generic designs only: no real operator branding or copyrighted looks.
- Every new pure-logic class gets a unit test. Keep blockstates, models, lang, loot and recipes complete.
- `main` = `release/1.6.0` until Jah approves 1.6.0 in game. Work on a branch; merge one branch at a time.
- Unit tests in `src/test/java`, game tests via `runGametest`. Never claim a visual works unless you saw it.
