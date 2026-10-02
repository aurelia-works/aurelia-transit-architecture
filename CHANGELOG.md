# Changelog

## 1.3.1

Fixes for the 1.3.0 release check. No new blocks.

- **Station messages with a manual station name (B10):** terminals and station information boards look station messages up by the MTR station they resolve, even when a manual name is shown; the manual name is the fallback only when no MTR station resolves. PIDS already did this. (`ServiceMessages.messageStation`, `ResolvedWayfinding.mtrStationName`)
- **E-paper bus board text scales with width:** full size from three blocks, scaled down to 0.375 on a one-block board; smaller text never shows fewer rows. (`EPaperLayout.textFactor`)
- **Hanging rods, at most two per row:** hanging wayfinding signs, exit signs, hanging station name signs, direction signs and hanging PIDS/CIS draw one rod near each free end and none on inner blocks.
- **Station information board editor:** the destination field gets its own row instead of being enabled but hidden behind the street field.
- **Viaduct joints:** crossbeam fills its block height and sits on the column; knee and diagonal braces reach the column faces (all four column styles).
- **Z-fighting:** trackside fence and 12 other models no longer have coplanar faces with different textures (`tools/check_zfighting.py` reports 0).

## 1.3.0

Urban infrastructure: elevated-transit kit, curved platform edges, station information board, joined terminals, multiple service messages. See `docs/RELEASE_CHECK_1.3.md` and `docs/STATUS_2026-10-01.md`.
