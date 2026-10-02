# Session log (1.3.1 → 1.4 run)

Source of truth for resuming. Branch `release/1.3.1`. Never tag, push or delete branches.

Test world: dev client on a copy of the release-check world, `run/client/saves/ATA Release Check` (the Prism "Noriega" instance is not touched). Datapack `tools/release_check/ata_test` copied into its `datapacks/`; scenes `ata_test:v131` etc.

| Package | Status | Commit | Verification | Skipped / needs human eye |
|---|---|---|---|---|
| 1. 1.3.1 fixes (B10, e-paper scaling, BOARD destination field, viaduct + z-fighting re-check, hanging rods ≤ 2) | done | 909407a | 197 tests, asset audit OK, z-fighting 0, in-game screenshots `screenshots/1.3.1/` | e-paper 1-wide readability at distance; z-fighting in motion; block outline still has rods on inner blocks |
| 2. A5 suffix, A3 per-exit, A14 calling times | done | (see git log, "1.4 package 2") | 221 tests, audit OK, z-fight 0; in game A5/A3/A14 + counters | station with MTR-defined exits not checked; suffix displays/announcements contexts unit-tested only |
