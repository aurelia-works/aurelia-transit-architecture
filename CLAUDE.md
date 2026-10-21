@AGENTS.md

## Stuck points and follow-ups (Jah, 2026-10-21)
- When stuck or needing a decision, ask Jah with AskUserQuestion, and record the point here (replace stale lines, keep it short).

Open (realism pass, worktree `../ATA-planner-split`, branch `agent/real-stations`):
- Concourse and entrance redesign (approved 5-7 h): in progress. `ConcourseFit` + `PlanFitOut` (boards, shops, kiosks, seats on big upper decks; portal, canopy, name, pylon at street doors). Seen in Studio at Utrecht only.
- Grand Central head: the outer lift lobbies (u1..4 and mirror) are walled off from the hall by a solid wall; only a 1-wide slot past the lift shaft leads in. Fix with the concourse work.
- Real-station fidelity: facades are approximations; layouts simplified (Frankfurt real 24 tracks, preset capped at 10; Grand Central's tracks are on two levels below the street, the preset is at grade).
- Edge railings (every handrail, station fence, glass barrier on the drop side of its block, corner pieces): built (`Planning.edgeRailings`, `RailEdges`, `tools/edge_railings.py`), seen in Studio only; needs the game-test gate and an in-game look.
- CLINKER look has no preset since Hamburg Dammtor went (test exempts it): ask Jah whether to add a real clinker-brick station.
- Low headroom: floor cells under the low end of footbridge stair flights at Utrecht, Amsterdam, Hamburg Hbf.
- Faster-check hook (probe after station edits): proposed, awaiting Jah's yes.
- None of the overrun/buffer/PIDS-centring work seen in game yet.
- Jah 10-21 at "Utrecht terminus, 10 tracks, 200 long, seed tmptnb" (Studio top view `$JOB/tmp/realism/renders/v17/top.png`): (1) a second row of fence posts one block behind every edged station fence (blockstate and Studio multipart look right, cause not found yet; next: dump `/api/plan` model parts for station_fence `edge_south=true`); (2) no block between the two buffer stops at u17 v206, the gap between tracks shows through; (3) the platforms beside the overrun (v201..206) mix concourse tile inside with granite edges; make them one platform floor; (4) a stray glass block just in front of the MTR fare gates (where not given).
