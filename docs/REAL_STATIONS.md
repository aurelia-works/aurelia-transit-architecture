# Real stations

The generator can build a real station as it is, rather than a look "after" it. Its layout, dimensions, materials and operator come from published facts.
Photos are used only as reference for facts. Nothing is traced or copied from them.
Where a fact is unknown or does not fit the game, the plan builds the nearest honest subset, and this page says what that is.

Real operators, looks and logos are allowed (Jah, 2026-10-19).
A release page or store listing that shows them should say so.

Code: `RealStation` (the facts and the kind: a hall over the tracks or a train shed), `StationDescriptionParser.applyReal` and `applyShed` (what the words may change), `PlanBridges.realHall` (Utrecht's hall) and `PlanBridges.plateaus` (its ends), `PlanRoofs.naveAndAisles` (Hamburg's shed), `RealStationTest`, `HamburgHbfTest`.
`PassengerWalkTest` walks every preset from outside, so no lift or stair may lead nowhere and every platform is reached.

## Asking for one

Type the station's full name, for example `utrecht centraal`.
A station merely *named* that (`named Utrecht Centraal`) is an ordinary station with that name.

Words the real station leaves open still apply: length, platform width, track count, seed, facing and furniture.
Words that contradict it are dropped, and each drop gives a warning.
At Utrecht: terminus, hub, station building, mezzanine, train shed, side platforms and "no footbridge".
At Hamburg: terminus, hub, "no building", look words, a smaller building, mezzanine, any roof but the arched shed, side platforms and "no footbridge".

## Utrecht Centraal (pilot)

| Fact | Real | Built |
|---|---|---|
| Hall | One closed hall over all tracks (Benthem Crouwel, opened 2016) | The footbridge becomes the hall: a deck over all platforms, walls and roof around it |
| Platforms | 8 platforms, 16 platform tracks, 4 of them terminating (1–3 and 4a at the north end) | 8 through islands. The terminating tracks are not built. A track count gives islands (2 tracks each); an odd count falls back to plain platforms with a warning |
| Hall depth along the tracks | about 85 m | 128 blocks at 1.5/m (127 built, odd so there is a centre row) on platforms 175 or longer. Shorter platforms get a shallower hall (warned), down to 31 blocks; below platforms 78 long there is no hall |
| Roof | Steel, three waves across the tracks, highest about 18 m over the rails; the highest wave covers the trains, the two lower ones the tram and bus stations either side (Dezeen) | Three sine waves. The middle one spans the tracks, its feet over the outermost tracks, and peaks at y 27; the side ones cover the hall's ends beyond the tracks and rise 40 % as high. The eaves are at y 17. The bus and tram stations are not built, so the side waves are narrow (the ends are 11 columns inside the hall) |
| Walls | Glass façades hung from the roof | `utrecht_hall_glass_facade` from the deck to the roof on all four sides |
| Columns | White "tree" columns | A round steel shaft with the white tree crown, on the platform pier lines every 24 rows |
| Gate lines | OV-chipkaart gates at the hall's entrances (see the gate sources) | A gate line each side, where the streets meet the hall: 13 gates around the centre line and barriers to the walls |
| Ways down | Stairs, escalators and lifts to each island (counts and widths not in the sources) | A stair, an escalator and a lift to each island from the middle of the hall. Openings in the floor get barriers, and the flights under the hall get glass sides |
| Ends of the hall | The hall ends at the raised Stationsplein (east) and at a plateau with a wide stair down to the Jaarbeursplein (west); a gateless passage runs along the north side | At both ends a broad stair (15 wide) from the square up to an opening in the glass end wall, and a lift from a lobby off the square to the hall floor. The space under the floor at the ends is walled in, so nothing at track level is walkable beside the tracks. The gateless passage runs along the low wall. The raised Stationsplein itself and Hoog Catharijne are not built |
| Platform roofs outside the hall | Separate canopies, renewed from 2010, some with solar cells; shape and material not in the sources | Plain steel canopies on round columns, 24 rows or more past each end of the hall. Under the hall the floor is the roof: no canopy and no girders there, except in its two edge rows |
| Platform length | Longer than 160 m (for full-length intercity trains) | 240 by default (keeps the build to about 105,000 blocks); longer if asked |
| Operator, platforms | NS | NS operator signs, light paving with the tactile edge, escalators on (not another Dutch station's materials) |
| Inside the hall | Ticket machines, departure boards, signs | Ticket machines at both ends, name signs at the gates, wayfinding signs hung from the roof on chains |

Not built yet: the shops in the hall, the bus station and tram stops, Hoog Catharijne, and the departure boards hung in the hall.

### Sources

- Benthem Crouwel, project page: <https://benthemcrouwel.com/projects/utrecht-central-station/>
- Wikipedia: <https://en.wikipedia.org/wiki/Utrecht_Centraal_station>, <https://nl.wikipedia.org/wiki/Station_Utrecht_Centraal>
- Dezeen, 2017-01-13, "Undulating roof …": <https://www.dezeen.com/2017/01/13/undulating-roof-benthem-crouwels-bus-train-railway-station-utrecht-netherlands-architecture-infrastructure/>
- Octatube, façade engineering: <https://www.octatube.nl/project-item/projectitem/46-utrecht-centraal-engineering-gevels.html>
- ProRail capacity analysis 2017 (track layout): <https://www.prorail.nl/siteassets/homepage/samenwerken/vervoerders/documenten/2017-utrecht-spoor-5-capaciteitsanalyse.pdf>
- Gate lines: NOS <https://nos.nl/artikel/2169180-utrecht-centraal-alleen-toegankelijk-met-ov-chipkaart>, Treinreiziger <https://www.treinreiziger.nl/poortjes-utrecht-centraal-sluiten/>

### Changed after Jah's walk-through (2026-10-19)

The first pilot had a 12-wide street at track level beside each outer track, under the hall's floor, with a lift and a stair up from it. They led nowhere useful, which is not how the real station works. The platforms under the hall also had a grid of canopy girders just overhead, and Leidsche Rijn's materials. All three are gone.

## Hamburg Hauptbahnhof

Ask with `hamburg hauptbahnhof` or `hamburg hbf`.

| Fact | Real | Built |
|---|---|---|
| Train shed | Steel and glass, a 73 m middle span with a lower aisle each side; 150 m long, 114 m wide, up to 37 m high (German Wikipedia; English Wikipedia gives 206 × 135 m) | An arched shed over all tracks: the nave over the middle 73/114 of the width rises to y 56 (37 m), springing from the aisle vaults' crowns with a step between them; the aisles are low round vaults |
| Platforms | Five islands: S-Bahn tracks 3–4, long-distance and regional 5–8 and 11–14 | 5 through islands, 10 tracks. A track or island count builds those instead (islands only; side platforms are warned and left out) |
| Platform length | Longer than the shed | 225 (the shed's 150 m) by default; a length word applies |
| Bridges | The tracks lie in a cutting. The north Wandelhalle (shops on two floors, stairs and lifts) and the narrower Südsteg (escalators) cross at street level | The generator's footbridge with stairs, escalators and lifts to every island. The tracks stay at street level |
| Building | Neo-Renaissance, "fortification-like", stone reliefs; Reinhardt & Süßenguth, opened 1906. The stone is not named in the sources | The grand station building beside the tracks, in the generator's sandstone (a stand-in) |
| Clock towers | Two, 45 m, square, at the west and east entrance halls of the Wandelhalle | One, 9 wide (an eighth of its height; was 5, 3.3 m, a chimney), its pyramid's point at y 68 (45 m): stone storeys with string courses and three-wide windows, louvred belfry, one-block clocks, pyramid cap. The second tower needs a building on the far street (not planned) |
| Operator | DB | DB signs, German S-Bahn platform finish, escalators on |

Not built yet: the cutting (the streets are at track level), the second tower and the east entrance hall, the Wandelhalle's shops, the building and main bridge at the north end (they stand at the side), through tracks 9–10, the S-Bahn tunnel (tracks 1–2), the Steintorbrücke and platforms longer than the shed by default.

### Sources

- Wikipedia: <https://de.wikipedia.org/wiki/Hamburg_Hauptbahnhof>, <https://en.wikipedia.org/wiki/Hamburg_Hauptbahnhof>
- Structurae: <https://structurae.net/en/structures/hamburg-central-station>
- Entwicklungsstadt: <https://www.entwicklungsstadt.de/hauptbahnhof-hamburg-vom-flickenteppich-zum-knotenpunkt/>

## Adding a station

1. Add an entry to `RealStation` with its operator, islands, hall depth, height, number of waves and kind. Cite every number here.
2. If the station is not "one hall over island platforms, streets at both sides", it needs its own rule in the planner. Do not stretch Utrecht's.
3. Add its facts and its subset table to this page. Add a test for its parse result and for a plan with 0 violations that the audit can build.
