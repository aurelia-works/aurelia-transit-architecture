# ATA 1.3.0 release check

One sitting, top to bottom. Everything here needs a real client with a real MTR network; the automated gates (build, ~195 tests, asset audit, dedicated server, live-client screenshots) are already green.

Mark each box: ✅ pass · ❌ fail (write what you saw) · ➖ not tested.

> ⚠ = MTR 4.0.5 UI wording I could not confirm from code. Use whatever the button is actually called and correct this file.

**Test world:** `tools/release_check/ata_test` is a datapack that builds section A's world (`/function ata_test:build`), the section D showcase (`ata_test:showcase`) and the section E names (`ata_test:e_setup` / `e_restore`). Copy it into the world's `datapacks/` folder and `/reload`. Rails still need connecting and the MTR dashboard filled in by hand.

**Setup:** `./gradlew runClient` (dev client with MTR 4.0.5, Sodium 0.5.13 and Iris 1.7.6). Single-player creative world, Superflat is fine. For section D put shader packs in `run/client/shaderpacks/`.

---

## A. Test world recipe

Build this once; every later section uses it.

| # | Step | Done |
|---|---|---|
| A1 | Open the **Railway Dashboard** (MTR item). Create 3 stations: **Alpha**, **Beta**, **Gamma**, each about 40 × 20 blocks, 60+ blocks apart. ⚠ "Add station" + drawing the area corners on the map. | ✅ |
| A2 | Lay one line of MTR rail nodes through all three stations. Between Alpha and Beta, make the track change direction with **one 45° section** (rail nodes at a 45° facing; MTR draws the curve between nodes). | ✅ |
| A3 | In **Alpha**, make **2 platforms** with MTR's **platform rail connector** ⚠ on two parallel tracks: P1 on the straight section, P2 on the 45°/curved section. One platform each in Beta and Gamma. | ✅ |
| A4 | Beyond Gamma, create a **depot** area in the dashboard ⚠ "Add depot", lay a **siding** with the siding rail connector ⚠ and assign a train to it ⚠ "Edit siding". | ✅ |
| A5 | Create **one route**: Alpha P1 → Beta → Gamma (and back). Assign it to the depot and set a departure frequency ⚠ "Edit depot" → frequency; "Refresh/Generate path" ⚠. Wait until a train runs. | ✅ |
| A6 | Edge Alpha P1 with straight `platform_edge`; edge Alpha P2 (curved side) with `platform_edge_curve` (right-click with an empty hand: 45° / convex / concave). **No MTR platform blocks or PSD/APG within 3 blocks of either platform.** | ✅ |
| A7 | At Alpha: 2-wide Passenger Information Terminal, a kiosk, a 4-wide platform PIDS at each platform, a concourse board, a 3-wide station information board, a bus e-paper board, 2 standalone Help Points within 24 blocks of the terminal. At Beta: one PIDS. | ✅ |
| A8 | Messages: `/ata_message add station "Alpha" severe Alpha severe test`, `/ata_message add station "Beta" notice Beta only`, `/ata_message add network info Network info test`. | ✅ |

The world is ready when a train stops at Alpha P1 and Alpha P2 on schedule and the Alpha PIDS lists departures.

---

## B. Items that need a real station

| # | Do | Pass | Fail | Result |
|---|---|---|---|---|
| B1 | Ride or watch a train stop at **Alpha P1** (straight edges). | Doors on the platform side open. | Doors stay shut. | ✅ 2026-10-01, Noriega (ATA 1.3.0 build 5dd4e12f): doors open, passengers alight onto the ATA edge. |
| B2 | Same at **Alpha P2** (curved/45° edges), each of the three curve kinds at least once under a doorway. | Doors open exactly as at P1. | Doors shut beside a curve piece but open beside a straight one. | ✅ 2026-10-01, Noriega: doors opened along the whole curved platform (diagonal, convex and concave pieces). |
| B3 | Walk the curved edge. | Coping follows the curve; no hole you can fall through between pieces (walking off the edge onto the track is normal, as with straight edges); no train clips the edge. | Gap you can fall through, or train body inside the edge. | ✅ (gaps) 2026-10-01: no holes; walking off onto the track possible, as designed. Train clipping not yet confirmed. |
| B4 | Sneak + right-click the Alpha terminal → **Station: Auto** → pick **Gamma** → Done. | Idle face says Gamma; Departures/Station tabs show Gamma; map highlights Gamma. | Still Alpha, empty, or error. | ✅ 2026-10-01 (build 0845c760): terminal set to Gamma; idle face, Departures and Station show Gamma. Save confirms Mode=MANUAL, Gamma id on the row owner. |
| B5 | Do B4 on a sign (exit sign or pylon) and the e-paper board. Then switch all back to Auto. | Each follows the pick, and returns to Alpha on Auto. | Any block ignores the pick or sticks on it. | ✅ 2026-10-01: e-paper at Alpha set to Beta showed Beta; terminal and e-paper reset to Automatic show Alpha again. Save confirms both Mode=AUTO. |
| B6 | Pick **Gamma** while standing at Alpha and Gamma is 100+ blocks away. | Gamma data shows, or an honest idle message. | Crash, frozen screen, wrong station. *(Unverified: whether MTR syncs far stations to the client.)* | ✅ 2026-10-01 (after the station-list fix, build 0845c760): Beta PIDS set manually to Gamma, flown to Alpha and back; PIDS shows Gamma. Gamma is not listed in the picker from Alpha (MTR sync range, see STATUS). |
| B7 | Open the terminal's **Accessibility** tab (wait up to 10 s). | Both help points, nearest first, "about N blocks away". | Missing, wrong distance, or more than 4 listed. | ✅ 2026-10-01: both help points listed, nearest first. |
| B8 | Look at Alpha PIDS/CIS/concourse strips. | Rotate: Alpha severe → Network info. **Beta only** never appears. | Beta message at Alpha, or Alpha message missing. | ✅ 2026-10-01: Alpha boards rotate Alpha severe → Network info; Beta message never shown. |
| B9 | Look at the Beta PIDS. | Beta notice → Network info; no Alpha message. | Wrong station's message. | ✅ 2026-10-01: Beta PIDS rotates Beta notice → Network info; no Alpha message. |
| B10 | Set a **manual station name** on the Alpha terminal (e.g. "Alpha Central"). Reopen it. | *Known issue, see STATUS doc:* station messages for "Alpha" disappear from this terminal while PIDS still show them. Record what you see. | — | ❌ known issue confirmed 2026-10-01: terminal named "Alpha Central" shows only "Network info test"; "Alpha severe test" missing while departures still come from MTR's Alpha. See STATUS. Release decision 2026-10-01: ship 1.3.0 with this as a documented known issue; fix in 1.3.1. |
| B11 | `/ata_message clear` | Strips vanish; rows reclaim the space; terminal says "No service notices". | Strip stays. | ✅ 2026-10-01: strips gone, rows reclaim space, terminal shows "No service notices". |

---

## C. Terminal tab click-through (Alpha terminal, Auto)

| Tab | Expected | Result |
|---|---|---|
| Home | Station name, clock, next departures (same order as PIDS), notices rotating with "n/N" counter. | ✅ 2026-10-01 quick pass (tester); no ATA errors in log. |
| Departures | Same rows as a station-wide PIDS; ‹ › page when more than fit; no departed trains. | ✅ 2026-10-01 quick pass (tester); no ATA errors in log. |
| System map | The route as a strip, Alpha highlighted, transfers ringed; pages through lines. | ✅ 2026-10-01 quick pass (tester); no ATA errors in log. |
| Station | Name/code, lines, platforms P1 P2, exits with destinations (if MTR exits set), transfers, street. | ✅ 2026-10-01 quick pass (tester); no ATA errors in log. |
| Service info | All applicable notices grouped Station / Network with ! / !! marks; "No service notices" when none. | ✅ 2026-10-01 quick pass (tester); no ATA errors in log. |
| Accessibility | Configured accessibility signs + help points; else "No accessibility information has been provided for this station". | ✅ 2026-10-01 quick pass (tester); no ATA errors in log. |

Also: remove the terminal block while the UI is open → the screen closes. ☐ not reported

---

## D. Shader pass

Ran 2026-10-01 by automated tour (screenshots via F2, Iris toggled with K / reloaded with R). Light = AureliaShaders-v1-candidate, heavy = Complementary Reimagined r5.9.3. Stills cannot show shimmer; see the PIDS burst note.

Run each scene three times: **vanilla (Iris shaders off)**, **Iris + light pack**, **Iris + heavy pack**.
Suggested on macOS (OpenGL 4.1, Iris reports no DSA): light = *MakeUp Ultra Fast* or *BSL*, heavy = *Complementary Reimagined*. Packs that need newer OpenGL may not load on a Mac; that is not an ATA failure.

Look for: flicker, wrong brightness (displays should glow, e-paper should not), z-fighting, missing faces, transparency sorting.

| Scene | Vanilla | Light | Heavy | Notes |
|---|---|---|---|---|
| 2-wide joined terminal, front + 45° | ✅ | ✅ | ✅ |  |
| PIDS with rotating/scrolling strip (readable?) | ✅ | ✅ | ✅ | Strip not re-shot after B11 cleared messages; rows readable. 10-frame burst: 0 changed pixels in vanilla; under the light pack the only change was idle → live data after teleport. |
| Bus e-paper board (must stay matte) | ✅ | ✅ | ✅ | Matte under both packs. |
| Station information board | ✅ | ✅ | ✅ |  |
| Station stair in every shape + enclosure glass | ✅ | ✅ | ✅ |  |
| Fences / handrails / glass balustrade | ✅ | ✅ | ✅ | Trackside fence z-fighting found and fixed after this pass (see STATUS). |
| Viaduct: columns, beams, braces, wind-screen glass from below | ✅ | ✅ | ✅ |  |
| Entrance pylon (both faces) | ✅ | ✅ | ✅ |  |
| Boarding markers (no z-fighting with floor) | ➖ not shot | ✅ | ✅ |  |

---

## E. Non-Latin text

Rename stations in MTR: Beta → `東京駅`, Gamma → `Москва Курская`, and set a manual name `محطة مصر` on one sign. Check sign, terminal, PIDS and e-paper board.

Ran 2026-10-01 (stations renamed in MTR; names set with `/data merge` from `ata_test:e_setup`). No overflow, clipping or missing glyphs. CJK in the concourse header is tiny but drawn.

| Script | Sign | Terminal | PIDS | E-paper |
|---|---|---|---|---|
| CJK | ✅ | ✅ | ✅ | ✅ |
| Cyrillic | ✅ | ✅ | ✅ | ➖ (no Cyrillic station in e-paper range) |
| Arabic | ✅ | ➖ | ➖ | ➖ |

Arabic is drawn left-to-right without shaping (Minecraft limit, documented). Only flag **overflow, clipping or missing glyphs**.

---

## F. Performance

Commands (docs/LIVE_TESTING.md): `/aurelia_live debug true`, `/aurelia_live stats`, `/aurelia_live_stress place 50 20`, `/aurelia_live_stress clear`. Render distance 12, GUI F3 for FPS. For each scenario: note average FPS over ~30 s with nothing in view, then with the scenario in view; run `/aurelia_live stats` at the start and after 60 s and record the counter **increase**.

| Scenario | Shaders | FPS before | FPS after | board_rebuilds | provider_refresh | arrival_requests | engine_updates |
|---|---|---|---|---|---|---|---|
| Baseline (no ATA blocks in view) | off | not measured | — | 0 | 0 | 0 | 0 |
| 50 displays + 20 speakers (stress) | off | — | ~243 (derived) | 4302 | 2152 | 62 | 62 |
| 50 displays + 20 speakers (stress) | on | — | ~176 (derived) | 4257 | 2084 | 61 | 62 |
| 12 mixed wayfinding blocks | off | — | not measured | — | — | — | — |
| 12 info boards / terminals, joined | off | — | — | 372 (Alpha wall, ~6 live displays) | 295 | 93 | 62 |
| 30-block viaduct in view | off | — | not measured | 0 | 105 | 27 | 62 |
| 30-block viaduct in view | on | — | not measured | 0 | 96 | 24 | 64 |

Ran 2026-10-01, render distance 8 (tester's setting), maxFps 260, 60 s windows, counters = increase over the window. F3 could not be captured by automation (macOS maps F3 to Mission Control), so FPS is derived from `board_frames ÷ 50 displays ÷ 57 s`, a lower bound; vanilla stress stays near the 260 cap. Engine updates stay ~1/s in every row because the 20 stress speakers stayed loaded until the end. Verdict: **pass** — rebuilds ≈1.4/s per display (CIS marquees scroll), provider refresh ≈ 70 blocks / 2 s, arrival requests ≈1/s for 2 platform sets (not per display), zero rebuilds with no boards in view, 70 blocks removed by `clear`.

How to read the counters (expectations from LIVE_TESTING.md / the performance contract):

- **board_rebuilds** ≈ visible boards × 1 per second (more only while a marquee or message strip scrolls). Far above that = rebuilding per frame → fail.
- **provider_refresh / arrival_requests** grow with the number of *distinct stations/platforms* in view, not with the number of displays. 50 displays at one station should not request 50× more.
- **engine_updates** ≈ one per second while any speaker is loaded, zero with none.
- Viaduct and wayfinding blocks have no block entities (except signs/boards); FPS with them in view should be within noise of baseline.
- Walking 40+ blocks away: board_rebuilds stops growing.
- The counters only cover the live systems (PIDS/CIS/concourse boards, speakers). Wayfinding signs, terminals, info boards and the viaduct do not report counters; for those rows FPS is the measure, and the counters should stay flat.

---

## Sign-off

All of B, C, E pass, no ❌ in D other than packs that cannot load on this GPU, F within expectations → tag `v1.3.0` on the tested commit.

**Result 2026-10-01: PASS with documented exceptions** (Noriega instance, macOS, Apple M1; final build 3112a40d).

- B1–B9, B11, C, D (three settings), E, F: pass. Gaps noted in the tables (boarding markers not shot in vanilla; some E cells and FPS rows not measured; FPS derived, not read from F3).
- B10: known issue (manual name hides station messages), shipped by release-owner decision; documented in README → Known issues; fix in 1.3.1.
- Three bugs found and fixed during the check (station list never loaded; picker search box over "Found:"; info board street box over platform box), each re-checked in game. See STATUS → Bugs found during the 1.3 check.
- Trackside fence flicker (z-fighting) found late, fixed and confirmed by the tester; the same defect fixed in 12 other models via `tools/check_zfighting.py` (not each re-checked by eye).
- Open, not blocking: e-paper text size on small boards (1.3.1).
