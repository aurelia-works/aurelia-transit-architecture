# Review rules (ATA)

Start every review with one line: `N issues` or `No issues.`

Every finding cites `path:line` and says what goes wrong, with the input or world state that triggers it. A finding without a file and line is dropped.

## Real failures here
- A planner change that moves a station other than the one intended: `StationSweepTest` above 0 violations or 0 unbuildable, or a preset that plans differently without a reason.
- A plan that fails `LayoutValidator` or `StationAudit` (floating blocks, unreachable platforms, falls, blocked access).
- World code that breaks undo, rebuild or chunk release: blocks or MTR data left after undo, chunks held after a job ends.
- Per-tick work in a block entity, new server polling, unbounded caches (the potato-PC contract).
- Mixins or MTR core changes.
- A new block or item missing its blockstate, model, lang, loot or recipe; `verify_assets.py` or `check_zfighting.py` failing.
- A test edited or weakened so it passes.
- A real-station preset that does not match its reference (layout, roof, entrances), or docs that state behaviour the code lacks.

## Ignore
- Style, naming and formatting that match the surrounding code.
- MTR gson log noise such as `unknown string mtr:train_cargo_*`.
- `~` budget notes in `verify_assets.py` output (pre-existing 1.5 notes).
- Suggestions that expand the change beyond its goal (list them as follow-ups, not findings).
- Performance guesses without a measured number.
