# Station buildings: the redesign spec

Target: **1.5 blocks = 1 metre** (`StationGeometry.BLOCKS_PER_METRE`). The metres below are real ones; the code builds them at 1.5x. What MTR's trains fix (platform edge, track spacing, train clearance) stays as MTR needs it. Each building is a real station building first and a generator output second, so the
room list, the order of spaces and the dimensions come from how stations actually work, and only then from what is easy
to place.

Sources: Network Rail *Station Design Guidance* (NR/GN/CIV/100/02), *Station Capacity Planning* (NR/GN/CIV/100/03)
and *Station Facilities & Amenities* (NR/GN/CIV/200/03); Wikipedia *Train station* / *Interchange station*;
Human Transit, *How End-Stations Differ from Through-Stations*.

## The one fact that drives the whole design

- A **through station** is where passengers **wait on the platforms**. The building is a *ticket hall* they pass through:
  short dwell, flow, platform width carries the crowd.
- A **terminus** is where passengers **wait in the concourse** until their train is called. The concourse carries the
  crowd, so it is the big room, and the gateline belongs **in the concourse**, not at the platform edge.
- An **interchange / hub** is about **transfer**: more platforms, cross-platform interchange, other modes attached
  (bus, tram, taxi), and amenities.

## Sizes (metres = blocks)

| Element | Value |
| --- | --- |
| Storey height | 4 |
| Ticket hall clear height | 4–5 |
| Paid concourse height | 8–12 (double height, with a mezzanine gallery) |
| Platform canopy clear height | 4–5 |
| Train shed crown | 15–25 (grand terminuses) |
| Platform width | 10–14 (waiting on platforms at a through station: the wider end) |
| Facade bay | 6–8 |
| Platform = train + 5 m spare | (we build to the requested length) |

Concourse depth grows with the scale **and** the number of platforms, because the hall must hold the waiting crowd:
`depth = base(scale) + perPlatform × tracks`, base compact 18 / city 30 / grand 42.

## Through station (side building)

1. **Forecourt** — paved, drop-off, cycle parking, trees.
2. **Unpaid ticket hall** (12–20 deep): ticket office window, ticket machines, toilets, one or two retail units
   facing the hall, information.
3. **Gateline** across the hall: run-off either side; a wide gate in the middle.
4. **Paid concourse** (modest): straight to the vertical circulation.
5. **Vertical circulation**: stairs + lift (escalators at big stations) up to the **footbridge**; each platform gets
   **two access points** at bigger stations so alighting passengers spread.
6. **Platforms**: waiting is on the platform — shelters/seating, PIDs, help points, wide enough.
7. **Frontage**: a real street elevation (arcade, tall ground floor, storeys), not a box.

## Terminus (head building)

Street → trains, all at one level:

1. **Street frontage** — the hall's end wall, full height.
2. **Unpaid ticket hall**: ticket office window, machines, toilets, two retail units, information desk.
3. **Gateline across the hall** — one line of gates, wide gate centre, run-off both sides.
4. **Paid concourse** — the big room where people wait: benches, departure boards, clock, kiosk; **double height with a
   mezzanine gallery** (stairs up, the main board on the gallery).
5. **Platform ends** open off the paid concourse, level, every platform reachable without crossing a track.
6. **Own roof** over the concourse; the **train shed springs from behind the hall**.
7. No footbridge at a terminus (already true).

## Hub / interchange

All of the terminus, plus whichever apply:

- **Bus interchange**: bays/stands with a shelter, on the unpaid side.
- **Taxi rank / drop-off** loop at the forecourt.
- **Tram / light rail** stop on the surface alongside.
- **Retail and amenities**: shops, toilets, waiting rooms, left luggage, a customer-service point on the gateline.
- **Multi-level concourse**: an upper/lower concourse over or under the platforms (metro-style), with lifts/escalators
  and **multiple access points per platform**.

## Built so far (scale and Phase 1)

- Ground storey 6 clear (4 m), first floor / footbridge deck at y = 7 (about 4.7 m over the rails), upper storey 4 clear,
  extra storeys 5 floor to floor, canopies 6 clear, sheds raised to match, the height limit 96.
- Stairs climb a whole storey (7 steps), escalators 10 steps; a station building needs platforms 24 long (four bays), a hub 20.
- Terminus head: unpaid ticket hall (ticket office, machines, information pillar, toilets, a shop), one gate line with a gate
  bank centred on the entrance and glass barriers to the walls, paid concourse with the main board and clock over the
  platform ends, seating islands, a kiosk and a help point, and in a big hall a gallery down each side with a glass lift at
  its platform end and a stair at its street end.

## Phases

Status as of `feature/studio-generator` (2026-10-18). A phase is **complete** only when everything it lists is built, tested, documented and the
audit is clean; the table says what is, with the test that shows it. Nothing below is marked complete unless every listed item is.

| Phase | Status | What exists (evidence) | What is not done |
|---|---|---|---|
| 1. Terminus concourse | complete | ticket hall, one gate line across the hall, paid double-height concourse with a mezzanine (3f271f3; `ConcourseGeometryTest`, `StationPlannerTest`) | |
| 2. Through-station building | **partial** | ticket hall, gate line, ticket office (before); **two ways down to each wide platform** at city and grand through stations: the stairs-both-ways geometry is now the default there, `single stairs` keeps one (ca848a1; `TwoWaysDownTest`, `StairOptionsTest`, the existing escalator game test builds the geometry); a **second footbridge at the far end** of the platforms, stairs only, glazed at both ends, at canopy and open stations long enough to keep both bridges' stairs apart (`StationLayout.farBridge`; `TwoWaysDownTest.longBigThroughStationsGetASecondFootbridgeAtTheFarEnd`) ; the far bridge under **hall roofs** too, the shed's cross girder resting on piers on both bridge roofs (e0f66f0; `TwoWaysDownTest`); an **underpass** (`underpass`): a tunnel a storey under the tracks at the end away from the bridges with a walled stair well down from every platform wide enough, dug only with replace-terrain and given back by undo (924d4ea; `UnderpassTest`, game test `underpassIsDugBuiltAndUndone`) | no escalators by default; the underpass is stairs only (no lift) |
| 3. Hub | **partial** | (493e58a) a hub has a forecourt past its head building (18 rows, inside the footprint): a **taxi rank** (three marked bays, a bollard at the head of each) and a **bus interchange** (kerbed island, a walk-in waiting shelter of the bus kit, two stop signs, a timetable case, a marked carriageway) (`HubForecourtTest`; every state parses in Minecraft in the game test `schematicStatesAreMinecraftsAndFollowTheBuild`) ; a **retail row**: shop units under both concourse galleries with glazed fronts, fascia and counters (d12c924; `RetailRowTest`); a **tram stop** (`tram`) in place of the taxi rank: new Street Tram Track and Tram Stop Sign blocks, a kerbed platform, a shelter, two signs and an end bollard (c5a1df8; `TramStopTest`, the schematic game test); a **mezzanine** (`mezzanine`, `upper concourse`, `multi-level`) at through stations: the footbridge widened to a 9-wide concourse over the tracks, edge piers, benches, hanging departure screens (376cbbc; `MezzanineTest`) | the tram's MTR rail and stop are the player's to lay; the mezzanine is an upper level only (no lower concourse under the tracks); no taxi sign block exists, so the rank is marked by bays and bollards only |
| 4. Forecourt / site | complete | drop-off forecourt by the door closed by bollards, ten cells of cycle stands (a post and a rail each; there is no bike-stand block), trees in the planter, and leaves planned persistent so they cannot decay (84c4da0; `ParkingTest.theForecourtHasADropOffCycleStandsAndTrees`, the schematic game test) ; a **terminus forecourt** with `parking`: a way to the door, a drop-off lay-by behind a kerb with bollards, cycle stands and trees (29e4d4b; `TerminusForecourtTest`) | a car park is opt-in by word |
| 5. Platform realism | complete | width rules by class (halt 5, regional 10/11, bigger classes 11 wide platforms; `StationClassTest`), a walk-in **shelter** on every open platform of 24 or more blocks (side: wall, seat, roof; island: wall with a seat each side) (d31b716, rebuilt from the bus kit in 493e58a; `ShelterTest`), the two ways down of phase 2, the existing platform numbers ; an open one- or two-track description with no class gets a halt's 5-wide platforms, other descriptions keep the 10/11 defaults so saved ones plan as before (a8841ed; `StationClassTest`); **wayfinding** signs over every stair head in the footbridge and an exit sign at the building (958d976; `WayfindingSignsTest`, game test `footbridgeWayfindingSignsSayWhereTheyLead`); a **second access** on platforms 96 long, the far-end footbridge (d687b3c; `TwoWaysDownTest`) | |

Each phase: implemented with tests, docs and the changelog updated, and the audit clean.
