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

## Deep stations: Angel, Canary Wharf, Vijzelgracht, Westminster

Ask with the preset buttons, or with `2 track deep tube station, 40 blocks deep, 120 long, no ramps, tfl signs, named Angel`, `2 track cut and cover box station, 24 m deep, 200 long, tfl signs, named Canary Wharf`, or `2 track cut and cover box station, island platform, 26 m deep, 188 long, gvb signs, named Vijzelgracht`.
The operator is only the name sign (the operator's sign block and text style); the signs are not the London Underground roundel set (no LU-only blocks were added).

| Fact | Real | Built |
|---|---|---|
| Angel: type and depth | Bored tube, 27 m below street (`docs/research/angel.md`) | Deep tube, 40 blocks (the research figure; 27 m at 1.5 blocks a metre would be 41) |
| Angel: platforms | Two side platforms in two tunnels, 2 tracks; platform length not in the sources | Two side platforms in one tube; 120 long (a guess, the real length is unknown) |
| Angel: access | Escalators only, one long flight to a ticket hall at Angel Square | One escalator shaft to a surface ticket hall; no lift ("no ramps") |
| Angel: operator | TfL | `tfl signs`: the existing Underground name sign with its roundel mark on the row end (see the note below) |
| Canary Wharf: type and depth | Cut-and-cover pit, 24 m deep (Jubilee line, Foster, 1999) | Cut-and-cover box, 36 blocks deep |
| Canary Wharf: length | 265 m long | 200 blocks (265 m is 397 blocks, over the 300-block limit) |
| Canary Wharf: platforms | Two platforms in one box, screen doors; the sources do not say island or side | Two side platforms in one box |
| Canary Wharf: access | 20 escalators and 3 lifts for 2 platforms | Lifts (a box always has them), escalator banks from the generator; the 20 are not matched |
| Vijzelgracht: type and depth | Cut-and-cover, 26 m deep (Amsterdam Metro line 52, Benthem Crouwel) | Cut-and-cover box, 39 blocks deep |
| Vijzelgracht: platform | One island platform, 125 m by 9 m | Island platform, 188 long (125 m is 188 blocks; the lift landing's lamp used to float there, fixed in PlanDeep) |
| Vijzelgracht: access | Lifts and escalators; south escalators about 47 m | Lifts, and the generator's escalator banks; the 47 m run is not matched |
| Vijzelgracht: operator | GVB | `gvb signs`: the GVB name sign |
| Westminster: type and depth | Deep Jubilee platforms in a 39 m station box, the platforms one above the other (`docs/research/westminster.md`) | Stacked deep tube, 58 blocks deep (`2 track stacked deep tube station, 58 blocks deep, 100 long, step-free, tfl signs, named Westminster`) |
| Westminster: platforms | Two platform tunnels, one above the other; length unknown | Two bores one over the other, 12 blocks apart in height (`StationGeometry.STACK_RISE`): track over track, platform over platform, 7 wide, 100 long (a guess) |
| Westminster: access | 17 escalators and 5 lifts | The tube's shaft to the lower platform's end, a bank of two escalators in its own inclined lane beside the bores up to a passage onto the upper platform, one three-stop lift at the platforms' end (lower platform, upper platform, street) |
| Westminster: operator | TfL | `tfl signs` from the preset only; no London-only blocks |

Not built: the Angel's two tunnels and the 1990s wide southbound platform, Canary Wharf's glass canopies and the matched escalator count, the Vijzelgracht artwork over its escalators, and Westminster's sub-surface District/Circle platforms, its Portcullis House and clock tower, and the real escalator and lift counts. The planner now takes a platform level per track and platform band (`TrackLayout.Track.level`, `PlatformBand.level`, default 0), so every other station plans exactly as before.
Fixed: a 188-block island box failed the ground check because a lamp on a lift landing kept its head after the landing's pole was cleared; PlanDeep now clears the landing to the lamp head. Vijzelgracht is built 188 long (DeepStationTest checks it).
Known gap: the preset test asks every preset for a footbridge or a terminus (a concourse), which a deep station does not have, so the Angel preset fails that check until Jah decides how deep stations should count.

### Sources

- `docs/research/angel.md`, `docs/research/canary-wharf.md`, `docs/research/vijzelgracht.md` (Wikipedia figures; TfL and GVB pages not fetched)

## Adding a station

1. Add an entry to `RealStation` with its operator, islands, hall depth, height, number of waves and kind. Cite every number here.
2. If the station is not "one hall over island platforms, streets at both sides", it needs its own rule in the planner. Do not stretch Utrecht's.
3. Add its facts and its subset table to this page. Add a test for its parse result and for a plan with 0 violations that the audit can build.
