# Session log (1.3.1 → 1.4 run)

Source of truth for resuming. Branch `release/1.3.1`. Never tag, push or delete branches.

Test world: dev client on a copy of the release-check world, `run/client/saves/ATA Release Check` (the Prism "Noriega" instance is not touched). Datapack `tools/release_check/ata_test` copied into its `datapacks/`; scenes `ata_test:v131` etc.

| Package | Status | Commit | Verification | Skipped / needs human eye |
|---|---|---|---|---|
| 1. 1.3.1 fixes (B10, e-paper scaling, BOARD destination field, viaduct + z-fighting re-check, hanging rods ≤ 2) | done | 909407a | 197 tests, asset audit OK, z-fighting 0, in-game screenshots `screenshots/1.3.1/` | e-paper 1-wide readability at distance; z-fighting in motion; block outline still has rods on inner blocks |
| 2. A5 suffix, A3 per-exit, A14 calling times | done | 9363e36 | 221 tests, audit OK, z-fight 0; in game A5/A3/A14 + counters | station with MTR-defined exits not checked; suffix displays/announcements contexts unit-tested only |
| 3. A8 lift panel, A12 noise barriers, A10 props | done | bc72fa0 | 225 tests, audit OK, z-fight 0; in game all blocks + non-Latin names | shader pass skipped (dev client cannot load shader packs); lift status cycler not clicked live; looks need human eye |
| 4. 1.4 requests a-d | done | 91ed5ca | 233 tests, audit OK, z-fight 0; live: concourse arrivals+summary, screen doors, drop barrier up/down with a train, counters | step slide not captured; curved screens and departures headings need human eye |
| 5. A17 PSD text, A16 stand-back, A18 composition (manual) + package 4 review follow-ups | **staged, not committed** (review cut off by rate limit) | — | 237 tests, audit OK, z-fight 0; live: all three signs | follow-ups not re-measured live; stand-back text size needs human eye |

Automation note: keystrokes go to the frontmost app. `mc.sh` front() now refuses to type unless the client window is frontmost (two stray chat commands reached the Claude terminal once when the client had failed to start).

**Resume:** follow `docs/HANDOFF_LOOP.md` (queue item 1: review + commit package 5; version already bumped to 1.4.0, unstaged).
