# Changelog

## 1.5.0 (unreleased)

- **Wording pass:** tooltips are short and plain (50 characters or fewer, no full stops), and block names name their city. No lang keys of existing blocks changed.
- **ATA Glass tab (22 blocks):** clear float, low-iron, grey, bronze and blue tinted, reflective, frosted, fritted and wired glass, each as a block and a pane, plus glass brick, curtain wall glass, structural glass fin and glass floor panel.
- **ATA European Stations tab:**
  - Netherlands and Belgium (28): Utrecht wave roof, tree column and hall glass; Leidsche Rijn timber soffit and cladding; Amsterdam red brick, stone cornice, cast iron shed and clock face; Rotterdam stainless roof and timber ceiling; Antwerp limestone, marble and glass vault.
  - Germany and Italy (27): Frankfurt sandstone and iron hall truss; Munich concrete and terrazzo; Hamburg clinker brick and hall roof; a small S-Bahn station; Roma travertine and ribbon window.
  - Dutch and German station signs and the Dutch platform sign use the existing editable sign: right-click to type text. The Dutch column band is plain.
- **ATA Metro tab (27):** tiles, ceilings, floors, columns and tunnel finishes inspired by the Munich, Frankfurt, Amsterdam, Rotterdam, Lisbon and Washington metros.
- **No operator logos:** colours and shapes only, no operator wordmarks or symbols.
- **Lightweight:** no new ticking, at most six cuboids per model. Only the three editable signs have block entities, the same non-ticking kind as the existing signs.

## 1.4.0

- **Station suffixes (A5):** `/ata_suffix` adds "Station", "Airport", "Port", "Terminal" or custom text to an MTR station's name, per context (signs, displays, terminals, announcements). MTR's own name is unchanged.
- **Per-exit settings (A3):** the station information board's Exits view sets each exit's text, arrow and visibility independently, and can add manual exits.
- **Lift status panel (A8):** lift name, levels served and a status bar (in service, out of service, maintenance), set by hand in its editor.
- **Noise barriers (A12):** solid, glass and solid-with-glass panels in full and half height; stack them for taller walls.
- **Station equipment props (A10):** fare gate (gate, wide, end; place side by side for a bank), card reader (post, wall), booth window, CCTV camera (wall, pendant, dome).
- **Working fare gates and transit card (tester request):** the fare gate charges MTR's own fares (same balance, zones and records as MTR's ticket machines and barriers): walk in from the green arrow carrying a **transit card**, MTR picks entry or exit, and the paddles open for one passage. Every player gets a card on first join; `/card` shows the balance (and replaces a lost card), `/card load <emeralds>` tops up at MTR's ticket-machine rates. Works inside MTR station areas; collision 1.5 blocks high.
- **Passenger information kiosk is two blocks tall** (tester request), screen at eye height. Kiosks placed before 1.4 need to be placed again.
- **Sign pole joins its sign** (tester request): a pole directly under a pole-mounted sign, or above a hanging one, continues into it (`pole_mounts` tag).
- **Concourse board:** column headings, Departures/Arrivals, station summary.
- **Platform screen doors for straight and curved edges:** fixed panel and always-open doorway (opens MTR train doors).
- **Drop-barrier and boarding-step platform edges:** the bars drop / the step extends while a train stands at the platform (client-side, from MTR's arrival data). `/aurelia_live edge` explains what an edge sees.
- **PSD text panel (A17), stand-back sign (A16), train composition board (A18):** typed by hand. MTR does not expose PSD text, through trains or car layout to addons.
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
