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

1. **Terminus concourse** — sizing (scale + platforms), ticket hall, gateline across the hall, paid double-height
   concourse with a mezzanine.
2. **Through-station building** — proper ticket hall, paid concourse, vertical circulation with two access points per
   platform at bigger stations.
3. **Hub** — bus interchange, taxi/drop-off, tram, retail, multi-level concourse.
4. **Forecourt / site** — drop-off, cycle parking, landscaping (extends the car park work).
5. **Platform realism** — width rules by station type, waiting/shelter, multiple accesses, signage.

Each phase: implemented with tests, docs and the changelog updated, and the audit clean.
