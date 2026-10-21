@AGENTS.md

## Stuck points and follow-ups (Jah, 2026-10-21)
- When stuck or needing a decision, ask Jah with AskUserQuestion, and record the point here (replace stale lines, keep it short).

Jah 10-21: "Finish the stations, make them realistic, finish what needs to be finished, then do whatever you want": finish the list below, then release 1.6.0 (merge, gate, tag, GitHub release, Modrinth).

Open (realism pass, worktree `../ATA-planner-split`, branch `agent/real-stations`):
- Concourse and entrance redesign (approved 5-7 h): in progress. `ConcourseFit` + `PlanFitOut` (boards, shops, kiosks, seats on big upper decks; portal, canopy, name, pylon at street doors). Seen in Studio at Utrecht only.
- Real-station fidelity: facades are approximations; layouts simplified (Frankfurt real 24 tracks, preset capped at 10; Grand Central's tracks are on two levels below the street, the preset is at grade).
- Edge railings (every handrail, station fence, glass barrier on the drop side of its block, corner pieces): built (`Planning.edgeRailings`, `RailEdges`, `tools/edge_railings.py`), seen in Studio only; needs the game-test gate and an in-game look.
- CLINKER look has no preset since Hamburg Dammtor went (test exempts it): ask Jah whether to add a real clinker-brick station.
- Faster-check hook (probe after station edits): proposed, awaiting Jah's yes.
- None of the overrun/buffer/PIDS-centring work seen in game yet.
- Fixed 10-21, committed on `agent/real-stations` (8864e75, 6681da2), seen in Studio only, not in game: Grand Central's side lobbies open onto the hall (ground arcade); stringers under low stair steps (no walking in under a flight); Jah's Utrecht terminus items: fence-post second row (Studio guessed corner=north_cw; `BlockModels` now defaults corner to none), ballast between neighbouring buffer stops, overrun strip all platform floor, MTR gate-bank ends are the line's glass fence (was a mid-cell ATA glass_barrier).
