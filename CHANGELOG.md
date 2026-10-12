# Changelog

## 1.6.0

- **Station generator:** describe a station in plain English and it is planned and built from ATA blocks: 1 to 10 tracks, 16 to 300 blocks long, side or island platforms, Modern, Dutch, German S-Bahn or Metro style, a canopy, a hall roof or no roof, ramps and name signs. `/ata_station plan <description>` shows a particle outline; `/ata_station confirm` builds it in batches, with cancel and undo. Descriptions can be up to 1200 characters. Fully offline. Terrain is only changed after you approve it (`/ata_station confirm replace_terrain`). MTR rail nodes are placed at the platform ends for you to connect, and existing MTR rail nodes are never replaced or removed. Operators only. See docs/STATION_GENERATOR.md.
- **Station Wrench:** the generator without commands. Right-click the air to pick a city look (and one to mix in), station or terminus, tracks, platforms, length, roof, building and name, then *Place in front of me*, *Build it!*, *Undo* or *Clear*. A 3D preview beside the buttons updates as you change things (drag to turn, scroll to zoom, right-drag to move, R to reset). Eight presets and a typed description are on the Advanced page. Right-click a block to plan there, sneak + right-click to build, left-click to cancel. Chat summaries are short, with Build it, Cancel and Details buttons. The wrench never breaks blocks.
- **City looks:** Utrecht, Amsterdam, St Pancras, Antwerp, Frankfurt, Hamburg, Munich and Grand Central, plus words such as american, new york, british, leipzig, cologne and brussels. Looks can be mixed: "american station, utrecht and hamburg combined" gives a Grand Central building, Dutch platforms and a Hamburg arched shed.
- **Station buildings:** a ticket hall with gates, ticket office, MTR ticket machines, kiosk, clock and departure boards in 8 styles, in three sizes: compact, city (three storeys with wings) and grand (four storeys, end pavilions, a clock tower and hipped copper or slate roofs). Larger buildings get an upstairs waiting room. Big stations get a grand building unless you say `compact` or `city`.
- **Footbridges and lifts:** a footbridge with stairs down to every platform, enclosed in glass where it rises through a canopy. Platforms with room get a working MTR lift, set up in MTR when the build finishes, with doors and call buttons linked; undo removes it. Footbridge side platforms are 6 wide unless you give a width, so their lifts fit.
- **Termini and train sheds:** add "terminus" and the tracks end at buffer stops in front of a head building with a gate line, boards, clock, ticket office and machines; every platform is step-free from the concourse. A bare "terminus" is 8 tracks, 160 long ("large" 10 and 200, "city" 4 and 96, "small" stays small). Add "hub" for a bit of both: the middle tracks end at the head building and the outer ones run on through, on the wrench as a third type. Hubs of 4 to 6 tracks keep one through track a side; 8 and 10 tracks get two a side, so the outer islands have trains running through on both sides and a footbridge with lifts links them to the rest. Each platform has its own colour stripe at the gates and a big number hung over them, and footbridge platforms get a big number under the bridge. Train sheds come flat, arched, pointed or as several arches, with glass vaults, ribs and an end screen, framed in the new **station girder** block (four colours).
- **On the platforms:** departure screens hang from the canopy (back to back on wide islands so both tracks see one), with a help point and benches.
- **Station checks:** every plan is checked block by block before it is built: boarding edges, rail nodes, roof cover, headroom, step-free access to every platform, name signs, lighting and nothing left floating. Warnings are listed in the summary; a failed check stops the build.
- **Station Studio:** the generator in your browser, running on your own computer. Open it with the wrench screen's *Station Studio* button or `/ata_studio`. Pick a city from tiles showing its real blocks, then the type, size, roof, building and name, the same steps as the wrench; the big 3D preview uses the game's own models and textures (several views, cuts, click a block to name it), and the checks sit beside it. Presets and a 1000-character description are on an Advanced fold. Without WebGL in the browser it still plans and checks, and says how to turn graphics on. Its buttons plan, build, cancel and undo in your world. It only listens on 127.0.0.1, and only the page the game served can act in the game.
- **Guide book:** operators get a *Station Generator Guide* book the first time they join; `/ata_guide` gives another copy.
- **Platform slabs (8 blocks):** half-height slabs of the walkable platform surfaces: light and dark paving, tactile warning paving, the Leidsche Rijn paver, the S-Bahn slab and the Munich granite, Amsterdam terrazzo and Washington quarry tile metro floors. Train doors open beside them like the full blocks, and two make a full block. Three in a row craft six; the stonecutter makes two from one.
- **More realistic blocks:** about 80 textures repainted, still 16x16: bricks and clinker with a tone per brick, sandstone, formwork concrete, marble, terrazzo, riveted ironwork, standing-seam roofs, glazing bars, platform and guidance paving, chequer-plate stairs, timber slats and sign faces. The S-Bahn platform edges are a coping on a wall, the structural beam is an I-beam, and the square steel column has visible edges. No block ids, names or states changed.

## 1.5.0

- **Wording pass:** tooltips are short and plain (50 characters or fewer, no full stops), and block names name their city. No lang keys of existing blocks changed.
- **ATA Glass tab (22 blocks):** clear float, low-iron, grey, bronze and blue tinted, reflective, frosted, fritted and wired glass, each as a block and a pane, plus glass brick, curtain wall glass, structural glass fin and glass floor panel.
- **ATA European Stations tab:**
  - Netherlands and Belgium (28): Utrecht wave roof, tree column and hall glass; Leidsche Rijn timber soffit and cladding; Amsterdam red brick, stone cornice, cast iron shed and clock face; Rotterdam stainless roof and timber ceiling; Antwerp limestone, marble and glass vault.
  - Germany and Italy (27): Frankfurt sandstone and iron hall truss; Munich concrete and terrazzo; Hamburg clinker brick and hall roof; a small S-Bahn station; Roma travertine and ribbon window.
  - Dutch and German station signs and the Dutch platform sign use the existing editable sign: right-click to type text. The Dutch column band is plain.
- **ATA Metro tab (27):** tiles, ceilings, floors, columns and tunnel finishes inspired by the Munich, Frankfurt, Amsterdam, Rotterdam, Lisbon and Washington metros.
- **Car stop boards (3, Wayfinding tab):** UK car stop marker, German stop board (with an "H" board) and Dutch stop board (with a red and white stop-here board), numbers 1-12 for a train of that many cars. Right-click with an empty hand to change the number, sneak-right-click to go back. Freestanding on a thin post (faces you) or mounted on the wall you aim at; a sign pole joins a freestanding board. No block entity, no ticking.
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
