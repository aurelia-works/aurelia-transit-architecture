You are the lead engineer on Aurelia Transit Architecture (ATA), an addon for Minecraft Transit Railway (MTR 4.0.5, 1.20.1 Fabric). ATA 1.3.0 is a conditional pass. Your job: finish the mod. Ship 1.3.1, then build every remaining ATA item from the triage doc and every 1.4 request in the STATUS doc, in the package order below. ATU (Aurelia Transit Utilities) is out of scope entirely. Leave the repo clean, committed and verified at every package boundary so the run can stop anywhere and resume.

# Read first (do not summarise back to me)
1. docs/STATUS_2026-10-01.md - shipped, partly shipped, not shipped, known issues, 1.3.1 work, 1.4 requests.
2. docs/RELEASE_CHECK_1.3.md - how releases are verified here.
3. /Users/Boon/Downloads/Aurelia_Transit_Feature_Triage_2026-09-30.md - sections 4, 10 and 11.
Then read docs/MTR_INTEGRATION.md, docs/LIVE_TESTING.md, docs/URBAN_INFRASTRUCTURE.md and the code each package touches. Explore before you edit.
If docs/SESSION_LOG.md exists, resume from it instead of restarting.

# Hard constraints
- ATA stays an MTR addon: no mixins, no MTR core changes. Where MTR does not expose what a feature needs, build the honest subset that works with MTR as it is (manual or static configuration), document the gap, and never fake live data.
- Potato-PC contract (triage section 11): no per-tick block entity work, no new server polling, bounded caches with eviction (copy the 512/512/128, 30 s pattern), static-first displays, fail visibly with an idle message.
- Generic design logic only: no real operator branding, logos or station copies.
- Any idea beyond this list goes as one line under "Requests raised" in STATUS and is not built.
- Work on branch `release/1.3.1`; commit locally after each package (what changed, what was verified). Never tag, push, publish or delete branches.
- Do not claim something works unless you ran it. Skipped checks are reported as skipped; unseen visuals are marked "needs human eye".

# Packages (finish each completely before the next)
1. 1.3.1 fixes
 a. B10: station-scoped messages looked up by MTR station name when the block resolves an MTR station, manual name only as fallback (STATUS "Known issue found in review"); unit tests; remove from README known issues.
 b. E-paper text scales with panel width; check 1-, 2-, 3-wide by eye.
 c. Dead destination field on the BOARD edit panel.
 d. Confirm the viaduct crossbeam/brace fix in game; re-check the 12 z-fighting models via contact sheet.
 e. CHANGELOG, README, STATUS, RELEASE_CHECK updated for 1.3.1.
2. Small triage items: A5 station suffix with per-context display; A3 independent per-exit configuration on multi-exit signs; A14 calling-point timestamps (only where MTR supplies times, else none).
3. Partly shipped families: A8 lift status panel (static in/out-of-service state set in its edit screen; do not read MTR lift state); A12 noise barriers (several heights, glass and solid); A10 fare-gate bank, card reader, booth window, CCTV housing (props only, no fare logic).
4. 1.4 requests (each needs a short entry in docs/DESIGN_1.4.md first, then code):
 a. Concourse board: "Platform" column heading, departures/arrivals choice, station summary (platform count, your arriving platform).
 b. Curved platform screen doors following the 45 degree, convex and concave edges, compatible with MTR's door check as it is.
 c. Platform-integrated drop-down screen door: edge block whose barrier lowers into the block when a train is in.
 d. Automatic boarding step / gap filler keyed to the arriving train.
 For b-d the door-open contract in docs/MTR_INTEGRATION.md is the constraint. If an arriving-train visual cannot meet the performance contract (client-side only, no per-tick block entity work), implement the static or blockstate-driven version, say so in the design note, and move on.
5. Blocked items, built as far as honest: A17 platform-screen-door text panel with manually edited text; A16 static "stand back, non-stopping trains" sign set by hand; A18 manual train-composition/coach board. Record in STATUS what real automation still needs from MTR.

# How to work
- Per package: short plan, implement, run the full test suite, asset audit and tools/check_zfighting.py, commit. Unit-test every new pure-logic class. Keep blockstates, models, lang, loot tables and recipes complete.
- Match the surrounding code: naming, comment density, the layout/resolver/renderer split, the tools/assets_*.py generation approach.
- Every new display or sign family: run the docs/LIVE_TESTING.md stress scenario and record counters in RELEASE_CHECK; test CJK, Cyrillic and Arabic names (tools/release_check/ata_test helpers) and the light shader pack.
- Verify visuals yourself with `./gradlew runClient` and screenshots. Ask me only when a decision is genuinely mine.

# Subagents (Sonnet only; you stay Opus and own architecture, integration and acceptance)
Spawn Sonnet subagents in parallel for bounded independent work: model and texture generation per block family, unit tests for a finished class, read-only audits (lang/loot/recipe/blockstate completeness, contact sheets), doc updates, and an independent diff review per package before you commit. Each brief is self-contained: goal, files it may touch, files it must not touch, the constraints above, what to return. Parallel agents never edit the same file. Read their diffs and run the tests yourself before accepting. Never delegate data model, MTR integration or performance decisions.

# Pacing
Do not spend time that can be avoided: parallelise, do not re-read files you already have, no rework without a failing check. Keep docs/SESSION_LOG.md (package, status, commit, verification, skipped items) updated after every package; it is the source of truth and the resume point if the session limit hits.

# Finishing
Stop when Package 5 is done or the next item is blocked on me. Final message only: what shipped (commit hashes), how it was verified, what was skipped or needs my eyes, and the exact commands to build and tag.

# Standing instruction about turn endings
A message with no tool call ends your turn and stops the work. Do not end turns with a summary that announces the next step instead of taking it, an offer to continue unless I prefer otherwise, a list of decisions that do not actually block you, or because a milestone feels like a good place to report. Put status notes and recommendations in the same message as your next tool call and carry on with whatever does not depend on me. The only stops I want are when nothing can move without me, or the final report. This does not override confirmation for risky or destructive actions (tags, pushes, deletes, force operations).
