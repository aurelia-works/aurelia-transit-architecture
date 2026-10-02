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

## 2026-10-02: tester bugs (Indus), not committed

| Report | Fix | Verification | Needs |
|---|---|---|---|
| "Fare Gate doesn't work" (expected MTR-style gates with a balance on a card) | `fare_gate` charges through MTR `TicketSystem.passThrough` (entry+exit, MTR decides), card required anywhere in inventory; `transit_card` item given once on first join (command tag); `/card`, `/card load <emeralds>` at MTR machine rates (`CardTopUp`, `CardTopUpTest`); `MtrFareContract` falls back to the old prop if MTR changes | 241 tests, audit OK, z-fight 0, dedicated server boots ("MTR fares: enabled") | in-game walk-through: open/close, refused (low balance, outside station), entry then exit fare, wide gate |
| "Block attached to a pole should extend" | `SignPoleBlock` up/down: pole continues into a `pole_mounts` sign above or below; under a street sign (own off-centre post) the pole moves under that post (`align`), down the whole stack | audit, z-fight 0; Noriega 12:31: street sign one continuous post, pictogram joined, exit sign hung from pole | exit sign under a pole shows its two rods plus the pole |
| "Make kiosk 2 blocks tall" | `passenger_info_kiosk` uses the two-half `EntrancePylonBlock`, screen in the upper half | audit | look in game; old single kiosks need re-placing |

In-game test, Noriega instance (2026-10-02; only MTR, Fabric API and ATA 1.4.0 enabled, other mods renamed `.disabled`; old ATA 1.3.0 jar and a world copy in `instances/Noriega/ata-test-backup-2026-10-02`). Scene: datapack function `ata_test:v14_fares` (inside Alpha, x 52-64, z 47-54).
- Bug found and fixed: the gate never opened. `onEntityCollision` gets a mutable `BlockPos` that the caller moves on; MTR answers later, so the callback updated the block above the gate (air). Now copied with `toImmutable()`.
- Bug found and fixed: the 2 s close timer shut the gate on a player standing in it, and the next touch was charged as an exit. Now the gate stays open while a player is in its block and closes once the player is clear of the paddles.
- Verified after the fixes: first-join card + welcome; `/card`; `/card load 3` = $32; entry ("Entered Alpha", balance unchanged, MTR charges at exit); exit with a 3 s stop in the gate charged once ($100 -> $98) and let through; walking in from the back is blocked; kiosk two blocks tall showing Alpha.
- Leftover: a first copy of the scene at x 42-54, z -83..-76 (outside Alpha) is still in the Noriega world; the backup copy predates both.

