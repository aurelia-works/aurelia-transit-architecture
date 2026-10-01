# ATA 1.3.0 release check

One sitting, top to bottom. Everything here needs a real client with a real MTR network; the automated gates (build, ~195 tests, asset audit, dedicated server, live-client screenshots) are already green.

Mark each box: ✅ pass · ❌ fail (write what you saw) · ➖ not tested.

> ⚠ = MTR 4.0.5 UI wording I could not confirm from code. Use whatever the button is actually called and correct this file.

**Setup:** `./gradlew runClient` (dev client with MTR 4.0.5, Sodium 0.5.13 and Iris 1.7.6). Single-player creative world, Superflat is fine. For section D put shader packs in `run/client/shaderpacks/`.

---

## A. Test world recipe

Build this once; every later section uses it.

| # | Step | Done |
|---|---|---|
| A1 | Open the **Railway Dashboard** (MTR item). Create 3 stations: **Alpha**, **Beta**, **Gamma**, each about 40 × 20 blocks, 60+ blocks apart. ⚠ "Add station" + drawing the area corners on the map. | ☐ |
| A2 | Lay one line of MTR rail nodes through all three stations. Between Alpha and Beta, make the track change direction with **one 45° section** (rail nodes at a 45° facing; MTR draws the curve between nodes). | ☐ |
| A3 | In **Alpha**, make **2 platforms** with MTR's **platform rail connector** ⚠ on two parallel tracks: P1 on the straight section, P2 on the 45°/curved section. One platform each in Beta and Gamma. | ☐ |
| A4 | Beyond Gamma, create a **depot** area in the dashboard ⚠ "Add depot", lay a **siding** with the siding rail connector ⚠ and assign a train to it ⚠ "Edit siding". | ☐ |
| A5 | Create **one route**: Alpha P1 → Beta → Gamma (and back). Assign it to the depot and set a departure frequency ⚠ "Edit depot" → frequency; "Refresh/Generate path" ⚠. Wait until a train runs. | ☐ |
| A6 | Edge Alpha P1 with straight `platform_edge`; edge Alpha P2 (curved side) with `platform_edge_curve` (right-click with an empty hand: 45° / convex / concave). **No MTR platform blocks or PSD/APG within 3 blocks of either platform.** | ☐ |
| A7 | At Alpha: 2-wide Passenger Information Terminal, a kiosk, a 4-wide platform PIDS at each platform, a concourse board, a 3-wide station information board, a bus e-paper board, 2 standalone Help Points within 24 blocks of the terminal. At Beta: one PIDS. | ☐ |
| A8 | Messages: `/ata_message add station "Alpha" severe Alpha severe test`, `/ata_message add station "Beta" notice Beta only`, `/ata_message add network info Network info test`. | ☐ |

The world is ready when a train stops at Alpha P1 and Alpha P2 on schedule and the Alpha PIDS lists departures.

---

## B. Items that need a real station

| # | Do | Pass | Fail | Result |
|---|---|---|---|---|
| B1 | Ride or watch a train stop at **Alpha P1** (straight edges). | Doors on the platform side open. | Doors stay shut. | ☐ |
| B2 | Same at **Alpha P2** (curved/45° edges), each of the three curve kinds at least once under a doorway. | Doors open exactly as at P1. | Doors shut beside a curve piece but open beside a straight one. | ☐ |
| B3 | Walk the curved edge. | Coping follows the curve; you can't step past it; no train clips the edge. | Gap you can fall through, or train body inside the edge. | ☐ |
| B4 | Sneak + right-click the Alpha terminal → **Station: Auto** → pick **Gamma** → Done. | Idle face says Gamma; Departures/Station tabs show Gamma; map highlights Gamma. | Still Alpha, empty, or error. | ☐ |
| B5 | Do B4 on a sign (exit sign or pylon) and the e-paper board. Then switch all back to Auto. | Each follows the pick, and returns to Alpha on Auto. | Any block ignores the pick or sticks on it. | ☐ |
| B6 | Pick **Gamma** while standing at Alpha and Gamma is 100+ blocks away. | Gamma data shows, or an honest idle message. | Crash, frozen screen, wrong station. *(Unverified: whether MTR syncs far stations to the client.)* | ☐ |
| B7 | Open the terminal's **Accessibility** tab (wait up to 10 s). | Both help points, nearest first, "about N blocks away". | Missing, wrong distance, or more than 4 listed. | ☐ |
| B8 | Look at Alpha PIDS/CIS/concourse strips. | Rotate: Alpha severe → Network info. **Beta only** never appears. | Beta message at Alpha, or Alpha message missing. | ☐ |
| B9 | Look at the Beta PIDS. | Beta notice → Network info; no Alpha message. | Wrong station's message. | ☐ |
| B10 | Set a **manual station name** on the Alpha terminal (e.g. "Alpha Central"). Reopen it. | *Known issue, see STATUS doc:* station messages for "Alpha" disappear from this terminal while PIDS still show them. Record what you see. | — | ☐ |
| B11 | `/ata_message clear` | Strips vanish; rows reclaim the space; terminal says "No service notices". | Strip stays. | ☐ |

---

## C. Terminal tab click-through (Alpha terminal, Auto)

| Tab | Expected | Result |
|---|---|---|
| Home | Station name, clock, next departures (same order as PIDS), notices rotating with "n/N" counter. | ☐ |
| Departures | Same rows as a station-wide PIDS; ‹ › page when more than fit; no departed trains. | ☐ |
| System map | The route as a strip, Alpha highlighted, transfers ringed; pages through lines. | ☐ |
| Station | Name/code, lines, platforms P1 P2, exits with destinations (if MTR exits set), transfers, street. | ☐ |
| Service info | All applicable notices grouped Station / Network with ! / !! marks; "No service notices" when none. | ☐ |
| Accessibility | Configured accessibility signs + help points; else "No accessibility information has been provided for this station". | ☐ |

Also: remove the terminal block while the UI is open → the screen closes.

---

## D. Shader pass

Run each scene three times: **vanilla (Iris shaders off)**, **Iris + light pack**, **Iris + heavy pack**.
Suggested on macOS (OpenGL 4.1, Iris reports no DSA): light = *MakeUp Ultra Fast* or *BSL*, heavy = *Complementary Reimagined*. Packs that need newer OpenGL may not load on a Mac; that is not an ATA failure.

Look for: flicker, wrong brightness (displays should glow, e-paper should not), z-fighting, missing faces, transparency sorting.

| Scene | Vanilla | Light | Heavy | Notes |
|---|---|---|---|---|
| 2-wide joined terminal, front + 45° | ☐ | ☐ | ☐ | |
| PIDS with rotating/scrolling strip (readable?) | ☐ | ☐ | ☐ | |
| Bus e-paper board (must stay matte) | ☐ | ☐ | ☐ | |
| Station information board | ☐ | ☐ | ☐ | |
| Station stair in every shape + enclosure glass | ☐ | ☐ | ☐ | |
| Fences / handrails / glass balustrade | ☐ | ☐ | ☐ | |
| Viaduct: columns, beams, braces, wind-screen glass from below | ☐ | ☐ | ☐ | |
| Entrance pylon (both faces) | ☐ | ☐ | ☐ | |
| Boarding markers (no z-fighting with floor) | ☐ | ☐ | ☐ | |

---

## E. Non-Latin text

Rename stations in MTR: Beta → `東京駅`, Gamma → `Москва Курская`, and set a manual name `محطة مصر` on one sign. Check sign, terminal, PIDS and e-paper board.

| Script | Sign | Terminal | PIDS | E-paper |
|---|---|---|---|---|
| CJK | ☐ | ☐ | ☐ | ☐ |
| Cyrillic | ☐ | ☐ | ☐ | ☐ |
| Arabic | ☐ | ☐ | ☐ | ☐ |

Arabic is drawn left-to-right without shaping (Minecraft limit, documented). Only flag **overflow, clipping or missing glyphs**.

---

## F. Performance

Commands (docs/LIVE_TESTING.md): `/aurelia_live debug true`, `/aurelia_live stats`, `/aurelia_live_stress place 50 20`, `/aurelia_live_stress clear`. Render distance 12, GUI F3 for FPS. For each scenario: note average FPS over ~30 s with nothing in view, then with the scenario in view; run `/aurelia_live stats` at the start and after 60 s and record the counter **increase**.

| Scenario | Shaders | FPS before | FPS after | board_rebuilds | provider_refresh | arrival_requests | engine_updates |
|---|---|---|---|---|---|---|---|
| Baseline (no ATA blocks in view) | off | | | | | | |
| 50 displays + 20 speakers (stress) | off | | | | | | |
| 50 displays + 20 speakers (stress) | on | | | | | | |
| 12 mixed wayfinding blocks | off | | | | | | |
| 12 info boards / terminals, joined | off | | | | | | |
| 30-block viaduct in view | off | | | | | | |
| 30-block viaduct in view | on | | | | | | |

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
