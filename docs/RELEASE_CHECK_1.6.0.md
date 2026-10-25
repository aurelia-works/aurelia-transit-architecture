# 1.6.0 release check (2026-10-25)

Branch `agent/concourse` (worktree `../ATA-concourse`, base `9f4a777`), uncommitted until Jah approves.

## Gate
- [x] `./gradlew build test --offline` on the final tree: green 15:09
- [x] `./gradlew runGametest --offline` on the final tree: all 914 passed 15:11
- [x] Sweep of 1,982 card and word plans (every city card at its defaults and the settings around them): 0 validator
      violations, 0 unbuildable; 1,433 take a real-station front.
- [x] Custom stations unchanged against the base (e.g. "6 track station, 160 long" = 17,275 blocks, in game too).

## Seen in game (dev client, 10-25 14:19-14:44, screenshots in `run/client/screenshots/`)
- [x] Utrecht card (4 track, 160): wave hall, lit roof, gate line, platforms under the hall.
- [x] Hamburg card (6 track, 160): clock tower, glass shed, building-line wall, escalators, footbridge.
- [x] Tokyo (8 track, 240, grand): red-brick front with dome, gate hall, footbridge, platforms.
- [x] Paris (8 track terminus, 128): stone front, concourse, gates, glass shed.
- [x] Berlin (6 track, 160): building, glass barrel shed, lifts, gates.
- [x] Custom "6 track station, 160 long": canopies, footbridge.
- [ ] The other 15 city cards (Studio only).
- [ ] PIDS arm on its mount.
- [ ] Walking a station as a passenger (only fixed camera views were taken).

## Known and accepted
- Track beds are grass: the build lays no rail or ballast without replace-terrain (same before this release).
- Station name signs carry the player's station name, not the real station's.
- Underground stations: deferred to 1.7.0.

## Release (each needs Jah's yes)
- [ ] Commit on `agent/concourse` (exclude `ZzHallProbeTest`, `ZzWaveProbeTest`)
- [ ] Merge into `main` / `release/1.6.0`
- [ ] Tag `v1.6.0`, GitHub release
- [ ] Modrinth upload (real station names and operator looks on the page: allowed, Jah 10-19)
