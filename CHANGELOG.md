# Changelog

## 1.4 (in progress on `release/1.3.1`)

- **Station suffixes (A5):** `/ata_suffix` adds "Station", "Airport", "Port", "Terminal" or custom text to an MTR station's name, per context (signs, displays, terminals, announcements). MTR's own name is unchanged.
- **Per-exit settings (A3):** the station information board's Exits view sets each exit's text, arrow and visibility independently, and can add manual exits.
- **Lift status panel (A8):** lift name, levels served and a status bar (in service, out of service, maintenance), set by hand in its editor.
- **Noise barriers (A12):** solid, glass and solid-with-glass panels in full and half height; stack them for taller walls.
- **Station equipment props (A10):** fare gate (gate, wide, end; place side by side for a bank), card reader (post, wall), booth window, CCTV camera (wall, pendant, dome). Decorative: no fare logic.
- **Concourse board:** column headings, Departures/Arrivals, station summary.
- **Platform screen doors for straight and curved edges:** fixed panel and always-open doorway (opens MTR train doors).
- **Drop-barrier and boarding-step platform edges:** the bars drop / the step extends while a train stands at the platform (client-side, from MTR's arrival data). `/aurelia_live edge` explains what an edge sees.
- **Calling-point times (A14):** PIDS/CIS "Calling at" can show minutes to each stop where MTR reports the same trip there (option **Times**, off by default). `/aurelia_live routes` shows the data.

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
