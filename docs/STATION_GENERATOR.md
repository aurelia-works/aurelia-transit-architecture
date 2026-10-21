# Smart Station Generator (first version)

`/ata_station` turns a short English description into a railway station built from ATA blocks. It runs fully locally and deterministically: it uses a fixed vocabulary, with no network, no language model and no randomness. The same description in the same place always gives the same station.

Operators only (permission level 2), like `/ata_message`.

## Use

| Command | What it does |
|---|---|
| `/ata_station plan <description>` | Plans a station in front of you (rail level = your feet, tracks run the way you face, the footprint starts 2 blocks ahead and is centred on you). Prints a summary and shows a 30 s particle outline. Nothing is placed. |
| `/ata_station preview` | Shows the outline again. |
| `/ata_station confirm` | Builds the pending plan where it was planned, as fast as a fifth of each server tick allows (about a thousand blocks a tick). Refuses if any terrain would change. |
| `/ata_station confirm replace_terrain` | Builds and approves the terrain changes the summary listed. This literal token is the **only** way to approve them. The description never can. |
| `/ata_station cancel` | Stops a running build (what is built stays) or discards the pending plan. |
| `/ata_station undo` | Reverts your last build, batched. Cells changed since the build are left alone. |
| `/ata_station rebuild [replace-terrain]` | Builds the pending plan where your last station stands (same origin and facing, not the plan's own place) and writes only the cells that differ: a cell that would be written the same (block state, sign or screen text) is kept, a cell the new plan leaves out goes back to the ground it replaced, and the rest is written, batched like a build. The site is surveyed as it was before the first build, so your own blocks never need approval; anything else the new plan touches does, as for `confirm`. The old MTR lifts and station area are removed and formed again. One undo afterwards gives back the site from before either build. |
| `/ata_station verify` | Reads the world back against the pending plan, or the last build, where it was planned, and lists the blocks that differ. Read only, never loads a chunk. |
| `/ata_station verify paste` | The same with the plan's schematic paste origin on your feet: stand where you pasted an exported `.schem` and run it. |
| `/ata_station status` | Pending plan, job progress, undo. |

## Station Wrench

The **Station Wrench** (Passenger Equipment tab, operators only) does the same without typing commands. Right-click the air to open its screen.

**Simple page (buttons).** Pick a city look (Utrecht, Amsterdam, Hamburg, Munich, Frankfurt, St Pancras, Antwerp, New York, Tokyo, Zurich, Paris, Milan, Berlin, Hong Kong, Beijing, Bangkok, Kuala Lumpur, Mumbai; four to a row), optionally a second city to *mix with* (its train shed, or its platforms when the first city has no shed), through station, terminus or hub (switching a station still at the default size to a terminus or hub makes it 8 tracks, 160 long; a hub's track buttons step by two), 1-10 tracks, platforms (auto, side, island), length (32 to 300), roof (the city's own, canopies, wave, butterfly or gable canopies, flat, arched, pointed-arch or multi-arch train shed, or none), a station building and its size (Yes sizes it by the station; or Compact, City, Grand) and a name. Then **Place in front of me** shows the outline and chat offers **Build it** / **Cancel** / **Details**; **Build it!** builds the placed plan and **Undo last build** reverts it. **Clear** forgets what the wrench remembers (choices, typed text, a Studio design) and starts again from the defaults. The buttons only write a plain description (`StationRecipe`), so they go through exactly the same parser, design rules and checks as typed text; a unit test builds every look x mix x type x roof combination.

**3D preview.** When the window has room, both pages show the station beside the buttons in 3D, drawn from the game's own block models and textures with every block joined as it will stand (screens into one display, panes into the frame, railings, girders and signs; `station/studio/Connections`), and update it as you change a choice or stop typing: drag to turn it, scroll to zoom, right-drag to move it, R to reset the view. Under it, the block count, the size and whether it is ready to build. The preview plans on your own computer with the same parser, planner and checks as the server, so what it shows is what *Place in front of me* plans. On a small window or a large GUI scale it is left out; Station Studio has the bigger view.

**Advanced page.** The typed description with the eight preset buttons, and every action: Plan in front of me, Show preview, Build, **Build, replace terrain**, Cancel and Undo last build. A typed description reopens on this page. Closing the screen saves the choices and the description on the wrench.

| With the wrench | What it does |
|---|---|
| Right-click the air | Opens the wrench screen. |
| Right-click a block | Plans the wrench's description on top of that block, facing the way you look: rail level is the top of the block, and the footprint starts at the block, centred on it. The short summary with buttons and the preview appear as with `plan`. |
| Sneak + right-click a block | Builds the pending plan (`confirm`). Never approves terrain changes. |
| Left-click a block | Cancels the running build or discards the plan. The wrench never breaks blocks. Left-click in the air does nothing. |
| Sneak + right-click the air | Shows the preview again. |

**Chat buttons.** A plan's chat summary is short: the headline, the mix, warnings, then **Build it** (or **Clear and build** when terrain is in the way, or the reason it cannot be built), **Cancel** and **Details** (`/ata_station details`, the full summary). A finished build offers **Undo**.

Every wrench action checks on the server that the player is an operator (level 2). A non-operator's clicks and screen packets do nothing.

**Presets.** Twenty-seven buttons (five columns) on the wrench screen fill in a complete description: the real Utrecht Centraal and Hamburg Hbf (see **Real stations**), one per building look (Modern, Amsterdam, St Pancras, Antwerp, Frankfurt, Hamburg, Munich, Grand Central, Tokyo, Zurich, Paris, Milan, Berlin, Hong Kong, Beijing, Bangkok, Kuala Lumpur, Mumbai), the British Teddington, Surbiton and Coventry, the underground Rosenthaler Platz, and three after real stations researched on 2026-10-20: KL Sentral (ten tracks on five islands under a glass canopy, grand hall with footbridge and escalators), Frankford (SEPTA's two-track concrete terminus, one island) and 69th Street (SEPTA's three-track brick terminus, island and side platforms; the name is cut to fit). These three are the honest subset: KL Sentral's tracks run under its hall and on several levels, Frankford is on a viaduct with a bus loop, and 69th Street has its 1907 hall beside, not across, the tracks; the generator builds none of that. Amsterdam, Hamburg, Munich and Kuala Lumpur are 2-4 track stations with a building and a footbridge, Modern a 4-track one; the other looks are big termini (hover a button to read it). A preset only fills the text field; nothing is planned until you press Plan. They live in `StationPresets`, and a unit test keeps each one fully understood, free of design-rule breaks and buildable.

**Guide book.** An operator gets the *Station Generator Guide*, a written book, the first time they join as an operator (a command tag remembers it, so it is given once). `/ata_guide` (operators) gives another copy.

Examples:

- `two track dutch station, island platform, 64 long, glass canopy, named Leidsche Rijn`
- `small single track s-bahn halt, no roof`
- `4 track metro station, 96 m long, hall roof, terrazzo floors`

## What the description understands

| Field | Words | Default |
|---|---|---|
| Tracks (1-10) | `3 tracks`, `two-track`, `single/double track`, `two island platforms` (4 tracks) | 2 |
| Platforms | `island`, `side`, both (an odd last track then gets a side platform) | side for 1-2 tracks, island for 3-4 |
| Length (16-300) | `64 blocks long`, `100 m`, `length 80`, `short/medium/long/very long`, `small` (32), `large` (96, 4 tracks) | 48; a terminus 160 |
| Width (3-20) | `5 wide`, `width 6`, `wide`, `narrow` | 4 side (6 with a footbridge), 6 island; islands under a footbridge are widened to 6 for their lift (5 with `no ramps`), with a warning |
| Style | modern; dutch/utrecht/leidsche rijn; german/s-bahn/hamburg; metro/u-bahn | modern |
| Roof | `no roof`/`open`; `canopy`/`covered`; `hall`/`train shed` | canopy |
| Canopy shape | `flat`; `butterfly`/`v roof`; `gable`/`gabled`/`heritage`/`pitched` (asks for canopies when no roof is given) | the building look's own (see **Canopies**) |
| Train shed shape | `flat roof`; `arched`/`barrel`/`vault`/`curved`; `pointed`; `arches`/`vaults`/`multi span`/`several arches`; `ridge`/`furrow` (ridge-and-furrow pitched roofs) (asks for a train shed) | the building look's own |
| Roof material | `glass`, `timber`/`wooden roof`, `steel` | style's own |
| Floor | `light/dark paving`, `concrete`, `terrazzo`, `granite` | style's own |
| Access | `step-free` (ramps at both platform ends), `no ramps`; `no tactile` | ramps and tactile edges on |
| Track bed | `gravel` (or `ballast`), `andesite`: ballast from wall to wall, between neighbouring tracks too (on to the buffer stops of a terminus), the row against each platform wall a Bed Light (the same ballast with a lit channel under the platform's lip), and black concrete under every platform and ramp. It replaces ground, so the build asks for terrain approval where it does. Studio: *Track bed* under Roof and building | the ground as it is |
| Escalators | `escalator`/`escalators`: an MTR up escalator beside each footbridge stair, with a rail between, on islands at least 11 wide and side platforms at least 9 wide (narrower platforms keep the stair only). Studio: *Footbridge stairs* | stairs only |
| Stairs both ways | `stairs both ways`, `reverse stairs`: a second stair down to each platform wide enough (islands 11, side platforms 9), from the corridor's other side beside the lift, facing the other way. Studio: *Footbridge stairs* | one stair per platform |
| Enclosed stairs | `enclosed stairs`, `walled stairwells`: solid walls round every footbridge stairwell instead of glass. Studio: *Stairwells* | glass |
| Travelators | `travelators`, `moving walkways`: flat MTR escalators, only on the platforms (none in the footbridge corridor): along platforms at least 71 long and wide enough (islands 10, side 9; the parser warns when none is), in a lane clear of the column lines, in stretches of up to 16 with 3-row gaps to cross, moving towards the footbridge or the terminus head. Studio: *Travelators* | none |
| Car park | `parking`, `car park`, `park and ride`: a surface car park in front of a station building's street wall, 14 deep and about 29 along: a kerb along the street edge with a driveway at the entrance, two runs of bays with their heads and separators marked out, a lane either side of a central planter (vanilla leaves), from the platform paving and bus kerb blocks. The lot moves the building back by its own depth, so it stands inside the footprint. It needs a station building beside the platforms (a terminus's street, past its head building, has no room yet) | none |
| Underpass | `underpass`: a stairs-only pedestrian tunnel a storey under the tracks at the end away from the footbridges, with a walled stair well down from every platform wide enough. It digs, so build it with `replace-terrain`. Not at a terminus | none |
| Mezzanine | `mezzanine`, `upper concourse`, `multi-level`: the footbridge widened into a 9-wide concourse over the tracks, with piers under its edges, benches facing out over the tracks and a hanging departure screen at each end. Asks for a footbridge; not at a terminus or with one track | none |
| Tram stop | `tram`, `trams`, `tram stop`: on a hub's forecourt, in place of the taxi rank: a run of Street Tram Track from the forecourt's side edge to a bollard, a kerbed platform with a shelter and two Tram Stop Signs. Lay the MTR rail (and its platform) on the track yourself. Hubs only | none |
| Other | `no benches`, `no rail nodes`, `named "X"` / `called X` | benches, rail nodes, no name signs |
| Building | `station building`, `ticket hall`, `concourse`, `furnished`, `centraal`, `hauptbahnhof`, `no station building` | none |
| Building look | `utrecht`/`rotterdam` (modern glass), `amsterdam`/`brick`, `st pancras`/`london`/`gothic`, `antwerp`/`marble`, `frankfurt`/`sandstone`, `hamburg`/`clinker`, `munich`/`brutalist`, `grand central`/`union station`/`washington` (Beaux-Arts), `tokyo`/`japan` (red brick with domes), `zurich`/`swiss` (sandstone with a giant arch), `paris`/`french` (limestone with statues), `milan`/`milano`/`italian` (monumental stone), `berlin` (glass and steel), `hong kong`/`kowloon` (white, wave roof), `beijing`/`china`/`shanghai` (glass, deep roof), `bangkok`/`thai`/`hua lamphong` (cream, great arched window, white columns), `kuala lumpur`/`malaysia`/`moorish` (white, dome, minarets, domed kiosks), `mumbai`/`bombay`/`india`/`csmt` (sandstone, great dome, Gothic turrets). Also `american`/`usa`/`new york`/`boston` (Beaux-Arts), `british`/`london`, `leipzig`/`cologne` (sandstone), `brussels`. Landmarks (`st pancras`, `antwerp`, `grand central`, `marunouchi`, `kowloon`, `hua lamphong`, `csmt`...) also ask for a building. | the style's own look |
| Mix | two or more looks with `and`, `combined`, `mix`, `x`, `hybrid`: the first gives the building, the first with a platform style gives the platforms, the last with an arched shed gives the train shed (e.g. `american station, utrecht and hamburg combined`) | one look |
| Footbridge | `footbridge`, `bridge`, `overbridge`, `skywalk`, `no footbridge` | none; a through station with island platforms and no building gets one (see *Street entrance*); `no footbridge` is kept with a warning |
| Terminus | `terminus`, `terminal`, `kopfbahnhof` (asks for a head building; a footbridge is dropped with a warning). Without a number a terminus is a main station: 8 tracks, 160 long; `large`/`big` 10 tracks, 200 long; `city` 4 tracks, 96 long; `small`, `compact`, `halt`, `village` or `rural` the ordinary 2 tracks. Any track count or length you give wins. | through station |
| Platform list | `platform 1: island 12 wide 120 long open; platform 2: side canopy;` (up to 10, left to right; each clause ends with `;`). Each platform brings its tracks (a side platform one, an island two), so the list sets the tracks, up to 20. Per platform: `side`/`island`, `bay` (a through station's dead-end platform: its tracks come in from the station's start and end at buffer stops just past its far end; in a terminus every track already does), `N wide` (3-20), `N long` (16-300), `canopy`/`open` (under a train shed every platform is covered). A shorter terminus platform ends at the head building; a shorter through-station platform is centred on the footbridge line and always reaches 12 blocks past it, so its stairs and lift fit. The station is as long as its longest platform. Two side platforms next to each other go back to back; a side platform with an island after it gets a one-block gap behind it. A hub needs islands only. | not listed: every platform alike |
| Hub | `hub`, `interchange`: a terminus whose outer tracks run on past the head building (SCR-style): one a side up to 6 tracks, two a side from 8. Island platforms only, an even number of tracks, at least 4; an odd count is rounded up and side platforms become islands, both with a warning. | no |
| Seed | `seed k7f2ra`: 1 to 12 letters or digits (any case, read in base 36). Varies only what the words left open; see **Seeds and fingerprints**. Anything else after `seed` is ignored with a warning | none (same as `seed 0`) |
| Class | `halt class`, `regional class`, `town class`, `city class`, `grand class`: fills in everything the words left open; see **Station class and era** | none (the plan reports the class its numbers amount to) |
| Era | `historic era`, `interwar era`, `postwar era`, `modern era`: chooses the look when none was named | none (the plan reports the era of its building look) |
| Facing | `facing north` (east, south, west): the way the entrance, the street side, looks. Places the station; no block changes | the way the player looks in the game; north in the studio |

Out-of-range values are clamped with a warning. A contradiction gives a warning and keeps the first value (an explicit "no roof" always wins). Words it did not understand are listed as "Ignored". `underground` and `elevated` are not built yet: the station is built at ground level and a warning says so.

## Canopies

A platform canopy stands on one column line down the middle of the platform (more on a wide one), with girders at y = 4 above 4 blocks of headroom, and reaches one cell out over each boarding edge. Its roof comes in three shapes, chosen by the building look or by a shape word. Only the roof changes: the columns, girders and headroom are the same in every shape, and a hall roof ignores the shape.

| Shape | Look | Roof |
|---|---|---|
| Flat | Utrecht, Hong Kong, Beijing, or no look and Dutch, Modern or Metro platforms | Level at y = 5. (Utrecht's wave is its hall roof; its platform canopies are flat.) |
| Butterfly | Berlin, Munich, Frankfurt, Hamburg, S-Bahn platforms | A V across the platform: level over the columns, then rising a block over two cells (`canopy_slope_lower`, `canopy_slope_upper`) to both edges. |
| Gable | St Pancras, Amsterdam, Antwerp, Paris, Grand Central, Tokyo, Zurich, Milan | A ridge over the columns, one block above the canopy (two on a roof 11 or more wide), falling the same way to the eaves, which stay at y = 5 clear of the trains. |

A girder block under each ridge, and butterfly fold joins the raised roof to the sloping blocks beside it, so no part hangs by an edge (it also reads as a post). A butterfly is framed as a thin V on cantilevers: an arm across the platform on each column with the fold braces on it, the valley beam along the column line, and only on a canopy reaching more than four cells from its columns a girder up under each side. Nothing runs along under its eaves. A canopy narrower than three cells from its columns to its edge stays level. Lamps sit at the same density as on a flat roof: beside the girder on a flat roof, in the valley or on the ridge for a butterfly or gable. Where a footbridge or stairwell passes through the canopy, a raised roof leaves that cell open, so nothing ever sits inside a corridor. The sloping blocks are the existing canopy blocks; the roof material option changes the flat parts only.

## Train sheds

A hall roof is a train shed over every track and platform, framed in **station girders**: a connecting I-section block that joins girders on all six sides and any full wall face, so a frame reads as one piece instead of loose beams. Its colour follows the building look (steel, white, Gothic blue, Antwerp/Amsterdam/Grand Central/Paris green) and cycles with an empty-hand right-click.

- **Frame:** columns on the platforms carry cross girders at regular rows (shifted a block or two where stairs, screens or doors are in the way), and longitudinal girders run along every column line, the eaves, the valleys and purlins so no roof span is longer than 8. Cross girders over a footbridge stand on short piers on its roof.
- **Shapes:** flat; arched (a parabolic barrel vault); pointed (a two-centred Gothic arch, St Pancras); several arches (one vault per track pair, valleys over the platforms, Frankfurt); ridge and furrow (one pitched roof per track pair with glass along each ridge, Mumbai, Kuala Lumpur and Zurich). Arched sheds rise less for flatter real halls (Berlin, Amsterdam, Bangkok, Hamburg). Vaults are glass above a clad lower band, with stepped girder ribs at every frame row and both ends, and a terminus gets a glass end screen over the head building.
- **Heights:** the beams rise to 8 for an arched shed 16 or more wide, and to 11 over a footbridge or a building beside the tracks, so a shed now combines with both.
- Every shape x building look x layout (1020 plans) passes the design rules and the station checks in a unit test.

## Station buildings and footbridge

A building stands on the street side over the middle of the platforms: 9 deep when compact, 15 for a **city** building and 21 for a **grand** one, so a bigger station gets a hall the depth of a real one (about a block a metre). It holds an entrance ramp from the street, a ticket hall with a fare gate line (a wide gate in the middle), a ticket office window, MTR ticket machines, an information kiosk, benches, a clock over the entrance and departure boards on the end walls. An MTR station name panel (3 wide) hangs over every street door and an MTR tactile map stands just inside it; the name is left for you to write. A stair in the hall leads to the upper floor and the footbridge, whose stairs go down to every platform it serves.

- **Building size** (`compact`, `city`/`regional`, `grand`/`monumental`/`hauptbahnhof`): a compact building is the two-storey block above, 9 deep. A **city** building is deeper (15 blocks, a real ticket-hall depth), adds a third storey and two-storey wings along the platform on both sides, covering about 70% of its length. A **grand** building is deeper still (21 blocks, a monumental hall), has four storeys, wings along about 92% of the platform ending in three-storey pavilions, a clock tower over the entrance (clock faces on three sides, a pyramid cap), and a taller train shed (its frame about y=18, a 20 m roof; a city shed's frame is about y=14, the compact one y=11). Masonry looks get hipped roofs (green copper for Amsterdam, Antwerp and Beaux-Arts; slate for St Pancras, Frankfurt and Hamburg); Modern and Munich stay flat. The wings' ground floors are waiting rooms on the paid side with a doorway onto platform 1 in every bay and benches along the street wall. Without a size word, a building is grand for 3 or more tracks or a platform of 100 or more, city for 2 tracks and 48 or more, otherwise compact. A terminus's head building grows the same way: a city one is 20 deep, a grand one 30, a city one gets a storey over the whole concourse, a grand one two, and its street front follows the look (see **Terminus fronts** below).
- **Upstairs waiting room** (buildings of three bays or more, with furniture): the end bay away from the hall stair is closed off by a framed glass partition with one doorway. Inside are benches along both long walls, bins in the far corners, a departure board on the end wall and a hanging clock. It is left out when that bay would touch the stair or the footbridge door.
- **Terminus** (`terminus`): the tracks end at buffer stops (steel beams across each track's clearance, just past the last rail node) in front of a head building 12 deep across the whole width (20 for a city terminus, 30 for a grand one, for a real concourse depth). Its floor runs level from the platform ends; the platform side is an open arcade under a lintel with clerestory windows. Two rows in from the buffers a gate line crosses every platform (a wide gate in the middle of each, glass barriers behind the tracks); departure boards and a clock hang over it facing the concourse. The concourse has the ticket office, MTR ticket machines, the kiosk and benches, a 3-wide street entrance with ramps, and roof lights. Every platform, islands included, is reached step-free through the gates, so a terminus needs no footbridge, and it may have a hall roof (a train shed) over the platforms. Ramps are only built at the open end.
- **Terminus fronts** (city and grand termini; `BuildingStyle.Facade`): the street front of the head building is one of four kinds, from a photo study of 15 real terminals. Compact termini keep the plain front.
  - **Red brick with towers** (Amsterdam, St Pancras, Tokyo): a hipped roof; Amsterdam gets twin clock towers at a quarter and three quarters of the front with a stepped gable between, St Pancras one tower with a slate spire and pinnacles, Tokyo slate-domed octagonal corner pavilions and a domed clock tower (a gable with a clock at city size) over a round-arched entrance and arcade. The twin towers and the St Pancras spire also come at city size. Pointed arches frame the St Pancras and Mumbai entrances.
  - **Giant arched windows** (Zurich, Antwerp, Hamburg, Frankfurt): whole-block glass in a stone ring, with glazing bars, rising almost to the roof; one arch over the entrance (Zurich, Antwerp), two (Hamburg, with its clock tower near one end) or three (Frankfurt). Antwerp's tower has a dome; Zurich has an attic with a clock and statues instead of a tower.
  - **Beaux-Arts** (Grand Central, Paris, Milan): a flat roof, tall round-headed windows between pairs of quartz columns (three, or one great window for Paris and narrow fronts), and a raised attic over the entrance with a clock and a statue group. Paris adds statues along the parapet; a grand Milan steps up to a second, taller attic.
  - **Glass** (Utrecht, Munich, Berlin, Hong Kong, Beijing): a curtain wall with mullions every three blocks and a band at each floor; Munich adds a deep concrete canopy over the street, a grand Berlin a tall glass tower near one corner (fronts 20 or more wide), Hong Kong a white roof that sweeps up to a crest with glazed ends, Utrecht a low wave roof, and Beijing a roof running two blocks out over the street with its edge and corners turned up. Munich has ribbon windows without mullions.
  - **Asian heritage** (Bangkok, Kuala Lumpur, Mumbai): Bangkok a low segmental glass vault over the entrance with a clock, columns along the front and flat-roofed end pavilions; Kuala Lumpur a raised attic with pinnacles, octagonal corner towers with open domed kiosks (chhatris), kiosks along the parapet and pointed arches along the ground floor; Mumbai a red-tiled roof, a banded pointed entrance arch, an onion-domed octagonal clock tower in the middle, spired corner towers and smaller domes between.
- **Hub** (`hub`): a terminus whose outer tracks carry on through. The split grows with size (`StationGeometry.hubThrough`): 4 and 6 tracks keep one through track a side; 8 and 10 tracks get two a side, so the outermost islands have trains running through on both sides (like a big interchange where the outer pairs run on and the middle ones end). Narrow platforms (under 6 wide, too narrow for a lift) keep one a side. The inner tracks end at buffer stops in front of the head building as above; the head building spans the platforms beside a bay track and its side walls stand just outside the outer bay tracks, so every buffer stop is indoors (`TrackLayout.bay(PlatformBand)`, `headFrom()`, `headTo()`); the through tracks pass beside it to the far end of the site (their rail nodes stay at the platform ends; extend the line from there past the hall). The street in front of the head is open ground. Islands with no bay track get a footbridge across every platform with stairs and a lift on each (`StationLayout.outerIslands`), so every platform stays step-free through the gates. The train shed covers all tracks.
- **Wayfinding at a terminus:** each platform gets its own colour (blue, green, orange, yellow in turn, from the metro tiles) as a floor stripe either side of its gates and on the lintel above them, with a big platform number sign hung under the lintel. On a footbridge, each platform also gets a number sign under the deck beside its pier.
- **Street entrance:** a through station with island platforms needs a way onto them that does not cross a track. A building's footbridge gives one; without a building the station gets a footbridge of its own. When a side platform is on an edge, its stair is the way up. When tracks run along both edges, a street entrance 6 wide stands beside the first track (`StationGeometry.streetEntrance`, `StationLayout.street()`): paved like the platforms with ramps at both ends, railed off along the track, with a glazed stair and a lift up to the footbridge, laid out as on a side platform. A train shed starts at the first track, so the entrance stays open to the sky.
- **Lifts:** every footbridge platform wide enough for a two-column stair gets an MTR lift beside the corridor, on the side away from the stair: a glass shaft with a 2×2 car, landing doors on the platform and in the corridor wall, and a call button at hand height beside each pair of doors. Where the platform runs on behind the shaft, the shaft also has doors in its back wall at platform level only, with three rows kept clear in front of them; the car is formed double-sided, and MTR opens each side only where it finds doors at that floor, so it opens both ways at the platform and only into the corridor at the top; the car rests in a one-block pit so its floor does not flicker against the platform. Buildings of three bays or more get a lift beside the hall stair, behind the gate line, up to the footbridge floor. When the build finishes, the generator forms each lift in MTR itself, as the Lift Refresher would, numbers the stops 0 and 1 and links the call buttons; undo deletes the lifts before it removes the blocks. Footbridge side platforms are 6 wide unless you give a width, so each gets a lift; platforms of 4 or 5 wide (one-column stairs) and two-bay buildings have no room for one. If MTR's lift code ever changes, the shafts are still built and the chat says to start each lift with the Lift Refresher.
- **MTR station zone:** when a build finishes, the generator adds the station to MTR's dashboard itself, as if you had drawn it: an MTR station with the station's name (or "New Station"), a colour from the name, and an area covering everything built plus one block round (every height, like the dashboard). Departure screens, speakers and help points then know the station straight away. If an MTR station already covers that ground, it is left as it is and the chat says so. Undo deletes the station it added. If MTR's station code ever changes, the station is still built and you draw the area on the dashboard as before (`MtrStationContract`).
- **Departure screens** (every furnished station, with or without a building): one screen point on platforms shorter than 48, two (at a quarter and three quarters of the length) on longer ones. Under a canopy, hall roof or footbridge the screen hangs right below the ceiling, double-sided and facing along the platform, four blocks wide across the middle of an island where it fits, else two blocks wide where the platform is four or more wide (never over an edge cell). The screen is centred on the platform, not on the girder line (an even-width island's girder is half a cell off its middle); a cross beam joins the girder over every screen cell that is not under it. Under a train shed too high to hang from, the screen spans the whole gap between two columns of a row (three or more apart) on a girder from one column to the other, like a long TV; with no such gap it is two blocks fixed to a column. On an open platform a standing screen is fixed to the back of a lamp standard (the lamp three blocks up on a sign pole). Screens keep 2 blocks clear of footbridge stairs and landings. Each platform gets one help point, a wall unit fixed to the column or lamp post nearest its first screen.
- **Speakers:** one every 16 blocks of platform, never at floor level: a ceiling speaker under a roof low enough to hang from, else a wall speaker fixed high on a column or lamp standard (3 blocks up, or 2 where that is taken).
- **End fences:** a handrail across each platform end without a ramp, edge cells included, so passengers cannot walk off onto the tracks; a terminus keeps its head end open into the concourse.
- **Width:** platforms are 10 wide by default and islands 11, room for travelators; `wide` gives 12 (islands 14), and any width from 3 can still be asked for.
- **Curved platform edges by hand:** the generator builds straight stations only, but the Curved Platform Edge (45 degree, convex and concave) and the curved platform screen doors can finish a platform by hand in any direction. Each comes as a right-hand piece (facing the track, the corner on your right is cut) and a left-hand piece (`mirrored=true`, the corner on your left); the half of the spot you click, or the side of the edge you click against, picks one, and sneak + right-click with an empty hand swaps it (`FacingKindBlock.MIRRORED`). The left-hand piece is the right-hand one turned a quarter, because the shapes are mirror images of themselves across a diagonal; mirroring a structure swaps the hand. A screen door placed on a curved edge copies its facing, hand and curve. Door opening is unchanged: MTR looks only for the platform marker on these blocks, never their shape.
- **Moving screens by hand:** generated screens, signs and clocks sit square in the middle of their block. To place one yourself at an angle or off-centre, sneak while placing it (`FreePlacement`). Screens, clocks, information cases and help points turn to face you in 16 steps, like a vanilla sign. Every screen, sign, clock, case and help point is also nudged by where you click on the face: the middle third keeps it centred, a bit off-centre sits it flush with that edge, and right by the edge moves it half a block that way (left, right, forward or back). Only screens and signs that sit the same way join into one; a turned screen stands alone. This adds two block-state properties (`angle`, `offset`), both none by default, so blocks placed before load unchanged; no block entities or ticking are added.


1. **Parse** (`StationDescriptionParser`) → `StationSpec`. Every field is resolved, and the summary shows what was understood, what was defaulted and what was ignored.
2. **Railway geometry** (`StationGeometry`) → `TrackLayout`, the cross-section of tracks and platforms. It knows nothing about styles.
   - Each platform edge sits exactly 2 columns from its track's rail node column, at rail level, facing the track. This is the same offset as the hand-built platforms in this repo's release-check world. Doors are expected to open because the edge and floor blocks carry MTR's platform marker (see [MTR_INTEGRATION.md](MTR_INTEGRATION.md#platform-blocks-and-train-doors)), but a train stopping at a generated platform has not been checked live yet.
   - Neighbouring tracks are at least 4 apart.
   - Each track keeps a clearance envelope of 3 columns by 5 blocks free.
3. **Styling** (`StationPlanner` + `StylePalette`) gives every cell a role (floor, edge, ramp, rail node, column, beam, roof, lamp, bench, bin, name sign) and then an ATA block id. Every edge and floor block carries MTR's platform marker, which is what MTR's door check looks for.
4. **Design check** (`LayoutValidator`), with no world involved:
   - Nothing other than a rail node may stand in a track envelope.
   - Edge offset and facing must be correct.
   - Track spacing must be at least 4.
   - Headroom under roofs must be 3 blocks.
   - Nothing may stand on boarding edges.
   - Columns must be unbroken from platform to roof.
   - Roofs may cantilever at most 4 from a support.
   - Beam spans may be at most 12.
   - Fixtures must stand on a platform floor.
   - A plan that breaks any rule is never built. Example: side platforms with 3-4 tracks cannot carry a hall roof, because the beam span would be too long.
5. **Station checks** (`StationAudit`), measured from the planned blocks, never copied from the description, so a planner bug shows up here:
   - Track corridors: as many as requested.
   - Boarding faces: the platform edge cells facing each track are counted; every face must be one unbroken run of the requested length, and no edge may face a track without a planned platform.
   - Rail nodes at both ends of every face (when on).
   - Roof coverage of the platform area: 100% with a canopy or hall, 0% without a roof.
   - Headroom: at least 3 blocks over every walkable platform and ramp cell.
   - Access: a walk from beside the station (u < 0 or past the last column; the tracks run on past both ends, so nobody walks around them) to every boarding edge cell. Ramps are climbed only along their facing. Each platform is graded by its worst boarding cell: step-free, a one-block step (fails if step-free access was asked for), only by walking across a track (a warning), only by stairs (a warning: a footbridge platform with no room for a lift). A lift counts as a step-free link between its two landings, or unreachable (fails). Without a footbridge, island platforms are reached across a track (a warning).
   - Name signs on every platform when a name is given (a warning if one did not fit), none otherwise.
   - No floating parts: every block is joined through faces or edges to a block standing on the support layer.
   - Overall size.
   - A failed check stops the build like a design rule. The summary prints one line with the counts and the measured faces and size, then each warning and failure.
6. **Site check** (`SiteSurvey` over `WorldSiteProbe`):
   - Planned cells must be air.
   - Track envelopes and platform headroom must be free of anything solid or liquid. Grass and flowers there are left alone.
   - Platforms, ramps and tracks need sturdy ground below.
   - Grass, water, logs or stone in a planned cell, obstructions, and missing ground all need approval. With approval they are replaced, cleared, or filled with stone bricks down to sturdy ground (at most 4 deep).
   - These are refused even with approval: block entities (chests, signs), unbreakable blocks, chunks that cannot be loaded (the survey and the build load and hold the chunks they need, so a station may be longer than the render distance), the world height limit, gaps deeper than 4, and an existing MTR rail node anywhere other than a planned rail node cell or a track's clearance.
   - An existing MTR rail node is never replaced or removed. One already in a planned rail node cell is kept, and one in a track's clearance is left alone, so a station can be planned onto a line that is already connected.
7. **Build** (`StationBuildJob`):
   - Jobs run in batches of 64 changes, taking turns, until 10 ms of the tick is used (at most 4096 changes per tick across all jobs). A placement costs about 10 µs, so a 6000-block station builds in well under a second; a job waiting for a chunk is asked once per tick.
   - Order: clears top-down, fills bottom-up, then floors, edges, ramps, rail nodes, columns, roof, fixtures, and signs last.
   - Every cell is re-checked just before it is written. A cell that changed since the survey stops the job, and nothing more is placed.
   - A chunk that unloads pauses the job (chunks are never loaded on purpose); after 10 s the job stops.
   - Name sign text goes to each joined row's owner.
8. **Undo** walks the log backwards. It only touches cells that still hold the block the build placed. Grass or mycelium that vanilla turned into dirt under the station is put back once the block above it is gone, unless that cell was changed to something other than dirt.

## Tracks and MTR

The generator places MTR rail nodes at both platform ends of every platform track and, since 2026-10-20, joins each pair as MTR's **Platform Rail Connector** would, so MTR's platform matches the built platform and is numbered (see Enviro's list below). Extend the line from the nodes with normal rail connectors.

The platform rail is the only connection the generator makes, sent to MTR as its own connector's `update_data` (no MTR core changes); the line beyond the platforms is yours to lay. Undo leaves a node alone once you have connected it (MTR sets `is_connected`), since removing it with a block change would orphan MTR's rail data. Without MTR loaded, the nodes are skipped and the summary says so.

## Walk-through fixes (Studio)

- **No falls** is an audit topic: wherever a passenger can stand (floor, deck, stair, escalator) the audit looks sideways for an open cell with a drop of three or more below it, or one inside a track envelope. A stair flank on a boarding edge takes its rail from y = 4 up to the cell the passenger stands in (below that the edge stays clear to board from).
- A doorway in the building's platform wall exists only where a platform stands beside it; where a track runs along the wall it is a window.
- Escalators can be the second flight too (`reverseEscalator`). The approach to every stair and escalator foot (5 cells out along the platform, one either side, floor to head height) is kept clear of screens, signs, benches, bins and lamps (`stairFeet`); route maps no longer stand on the enclosure posts there (MTR shows a route on one only once the player picks the platform with its brush, so a plan could not fill it in).
- Travelators keep clear of the stairs, escalators and lifts of both footbridges, the far one included. Each stretch is a pair, one lane each way, and the lower column always runs towards +v, so the pair keeps one hand along the whole platform. (Islands with room for side lanes: see the 2026-10-20 notes below.)
- The hall's end-wall departure boards hang only where a wall is behind them (with wings the end walls are open).
- Open: the "no three stacked girders" rule is not in. A rib under a raised roof carries the roof, so leaving it out over a column line's girder floated the whole row; a different fix is needed.

## Hub play-test fixes (2026-10-20)
- **Side-lane travelators:** an island wide enough gets two full MTR walkways (a left and a right half each), one just inside each column line, the lower one running towards +v and the other back; the strip between them (at least 3, `PlanMovingWalks.SIDE_LANE_MIDDLE`) takes the benches, bins and a row of name signs in every other stretch (`Planning.stretchSigns`) instead of one row by the stairs. Side platforms and narrower islands keep the two one-wide lanes in the middle.
- **No piers on the footbridge roof** under a hall when the column rows either side are within a girder's span (`PlanRoofs.girdersNeedBridge`); they only poked up through the bridge roof beside columns already carrying the frame.
- **Screens hang on drop rods:** a pole-to-pole screen is held by its columns and two `drop_rod`s up to the roof frame (up to 24, `PlanState.MAX_DROP`); no girder over it. The roof-hung screen's one-block hanger is a rod too. Over a hanging screen the rod (`DropRodBlock`, `facing`/`hanger`, worked out from the block below; `PlanState.rodHanger` gives the planned state) splits into hangers onto the stubs on its top: both on a one-block screen or a hanging wayfinding sign, only the outer one on the end block of a wide screen. A wide screen's middle blocks have no stubs, so its rods hang over its two end blocks (Jah 10-20: the rod has to meet the little stub at the end).
- **Roof truss:** a shed's frame is `roof_truss` (the girder's block, states and joins, with a Warren-truss model) where the palette's beam is the plain girder.
- **Speaker bracket:** `wall_speaker` has `post`; on a column or pole (no full face behind it, also worked out when placed by hand) it stands on a short arm back to the post's face.
- **Ticket vending machine:** `ticket_vending_machine` replaces MTR's machine in the generator. Using it opens MTR's own ticket screen (`MtrFareContract.openTicketMachine`), so it sells MTR tickets and tops up the same balance; without MTR fares it is a prop. Two blocks tall (`half`, like a door; each half has its own hitbox, so the whole machine can be clicked): ledge, card reader, keypad and ticket tray below, the hooded touch screen and lit header above.
- **MTR glass fences** (`mtr:glass_fence_plain`, two high, set to one side of the cell) in the ground-floor gate lines, where the cell above is free (`PlanState.fence`).
- **Gate banks:** a terminus or hub ticket hall's gate line is banks of four gates and a wide one (`PlanConcourse.GATE_BANK_HALF`), one on the walk from the entrance and in a hall wide enough with six or more tracks one more each side, glass fence between (`GATE_BANK_GAP`) and out to the walls, instead of one bank across the hall.
- **Clear ends:** an island's middle-strip benches stay between its side lanes (`PlanFurniture.pastTheLanes`), so none stands in the walk off the platform; a concourse gallery has no bench in the landing at its stair's head, and its edge rail turns the corner onto the stair's rail (they only touched corner to corner).
- **Gate signs:** a hanging sign on a split rod over each bank's wide gate, facing the ticket hall, names the platforms nearest that bank ("Platforms 1–2", `PlanConcourse.gateSigns`).
- **Gate booth:** a 3 x 3 staff booth on the ticket-hall side just past the middle bank's end: counter wall, glass between corner posts, a `booth_window` looking at the wide gate and one at the gate line, a door at the back (`PlanConcourse.gateBooth`; only where it fits clear of the rooms).
- **Hall lamps:** `hall_pendant_lamp` (green enamel shade, lit underneath, light 15, no block entity) on a plain drop rod under every other cross girder of a train shed, over the middle of each platform, 10 above it (`PlanRoofs.hallLamps`). Hung after the screens and signs; where one hangs under the spot, from the next girder along, so no screen loses its rod.
- **City operators:** with no operator named, the first city or country word with one gives it (`Operator.ofCity`): Dutch cities NS, German DB, Swiss SBB, Japanese JR, London National Rail (the Underground with `metro`/`underground`). `no operator` or `no logos` keeps plain signs.

## Enviro's list (2026-10-20)
- **Help point:** says the whole station name (it was cut off after a word); with no voice installed it falls back to the system voice (`SystemVoice`).
- **MTR platforms by themselves:** each platform track's two rail nodes are joined as MTR's Platform Rail Connector would (`MtrRails`, through `MtrRailContract`), and MTR's platform is numbered like the dashboard does. No manual platform numbers. Without MTR the nodes are skipped as before.
- **Gates:** MTR's own ticket barriers by default (one-way, entry and exit lanes; the line's MTR glass fence closes each bank's end, in line with the rest of it). `ata gates` / `two-way gates` keeps ATA's fare gates (`StationOptions.GateStyle`).
- **No stray chairs upstairs:** benches only where people wait (platforms, ticket hall), none alone on an upper floor.
- **Wider entrance:** the street door is 5 wide by default, 7 at a grand station, 3 to 9 when asked ("7 wide entrance").
- **Block swaps:** "use stone bricks instead of sandstone", "replace sandstone with stone bricks", "swap sandstone for stone bricks" or "sandstone -> stone bricks". A swap takes the block's slab, stairs and wall with it where both have them (`BlockFamilies.expand`). Studio has a Blocks section for the same.
- **Platform logic:** facing side platforms get a footbridge by default; no ramp runs onto the track bed; presets fixed where their geography was wrong; a tram stop can stand alone. `PlatformLogicTest`.
- **British stations:** looks after Teddington (Victorian: yellow stock brick, timber canopy with a valance, slate roofs), Surbiton (art deco: white render, concrete canopies) and Coventry (modernist: blue engineering brick, hardwood canopies). Platforms are tarmac with a white-nosed coping and a yellow line. 14 `uk_*` blocks (`StationBlocksUk`, `tools/assets_stations_uk.py`). Words: victorian, british, english, uk, suburban, teddington, lswr; surbiton, art deco; coventry, modernist. "london" stays the Gothic brick look. A Victorian station stands under canopies unless "train shed" is asked. "Island and side platforms" with an even track count warns (the tracks pair off onto islands).
- **Underground stations** (`PlanUnderground`, after Berlin's Rosenthaler Platz): a box under the street. Platforms at the bottom, the ceiling slab at the footbridge deck's level (`StationGeometry.UPPER_FLOOR_Y`) whose top is the street; the footbridge flights are the stairs straight up, with a rail round each opening and an `entrance_pylon` with the station's name beside it. Tiled side walls, end walls with a lined tunnel mouth over every track, columns down wide platforms, lights in the ceiling. The frame is sunk so the street is at the player's feet (`StationPlacement.depth`, `sunk`). Words: underground, subway, tube, tunnel; tile colours (orange, green, yellow, blue, grey) or from the look. Through stations only: a terminus, a station building, a platform roof, an underpass, a tram stop or a mezzanine is said and left out. Preset `rosenthaler_platz`. Studio hides the street at first (the Street layer) so the view looks into the box. The lights hang one block under the slab (`ceilingLights`), so the street on top is one material; the lift comes up in a kiosk on the street, as at Berlin's U-Bahn.
- **From a photo** (Studio): the "From a photo" button shrinks the photo to 1568 px and the Studio sends it to Claude (`PhotoDescriber`, model `claude-opus-5-5`, server-side refusal fallback on). Claude answers in the generator's own words (the prompt lists them; `PhotoDescriberTest` checks every listed phrase is read), and the answer is applied like a preset. Needs `ANTHROPIC_API_KEY` (or `-Data.anthropicKey`); without it the page shows the prompt to paste into Claude with the photo. One photo at a time, only when asked. Plain `java.net.http`, so the jar carries no extra libraries.

## Operator logo signs, and the simpler Studio
- **Operator signs:** `ns`, `deutsche bahn` / `db` and `gvb` (with or without "operator", "logo" or "signs" after) put that operator's name sign on every platform: `ns_station_sign`, `db_station_sign`, `gvb_station_sign`. They are the ordinary joined name sign (editable text, no new block entity, no ticking); the mark is painted at the left end of a row (model pixels 0-8, `StationBlocksOperators.MARK`, `TextLayout.textInset`) and the station name sits beside it. A bare `ns` or `db` still sets the Dutch or German look as before; `ns operator` is only the signs. Also `tfl` / `roundel` / `london underground signs` (`underground_station_sign`, roundel on white, name in the roundel's blue), `national rail` (`national_rail_station_sign`), `sbahn signs` (`sbahn_station_sign`; bare `sbahn` stays the look), `sbb` (`sbb_station_sign`, white on Swiss station blue) and `jr` / `jr east` (`jr_station_sign`). Bare `london underground` is still a London station underground. Marks from Wikimedia Commons (Underground (no text), National Rail logo, S-Bahn-Logo, SBB logo simplified, JR logo (east)). Marks are in `tools/logos/` (SVG and a rendered PNG each) and painted into the sign textures by `tools/assets_operators.py`. The repo no longer limits itself to generic designs (Jah, 2026-10-19); these are real operators' marks, so a public release page may draw a takedown.
- **Studio, simpler:** the sidebar opens with "What do you want to build?" (the words box), then City, Type, Size (tracks, length), Name and logo. Everything else (mix, class and era, seed, platform type, building details, platforms one by one) is under *More options*; the report's layers, cutaway, materials and raw data are under *Extra tools*. Typing words updates the headline, the city buttons, tracks, length and type from what they came to (`StudioApi.wordsTitle`, `wordsLook`), not from the dice behind them. "300 blocks" alone is read as a length.

## Performance and safety

- One server tick listener for the whole feature, which returns at once when no build or preview is running.
- One new block (the station girder, no block entity), no mixins. The wrench adds one item, one client-to-server packet (hand, action, a description of at most 256 characters and the screen's choices of at most 160, all bounded on read and handled on the server thread) and one client screen.
- The preview is server-side particles sent only to the planning player (at most about 600 per refresh, every 10 ticks, for 30 s), and it changes nothing.
- Pending plans, jobs and undo logs live in memory: a server restart forgets them, and a player who leaves loses their pending plan (a running build continues).

## Station class and era

A **class** says how big a place the station serves; an **era** says when it was built. Both only fill in what the words and the
Studio's touched controls left open, so anything said wins: `halt class, 3 tracks, 100 long, station building` is a halt with three
tracks, 100 long and a building. They are written as phrases ("halt class", "postwar era") because the bare words keep their older
meaning: `halt`, `regional`, `town`, `city` and `grand` on their own still pick a station building's size (compact, city, city, city,
grand) and `modern` still picks the modern platform style, so every saved description plans as it did.

| Class | Tracks | Platforms | Length | Width | Building | Roof |
|---|---|---|---|---|---|---|
| halt | 1 | side | 75 | 5 | none | open (no roof) |
| regional | 2 | historic, interwar, postwar: side platforms and a footbridge; modern: one island | 180 | 10 (island 11) | compact | gable canopies when historic; glass canopies when modern; never a train shed |
| town | 3 | island | 180 | 11 | city size | canopies (gable when historic) |
| city | 4 | island | 200 | 11 | city size | canopies |
| grand | 6 (terminus 8) | island | 210 (terminus 160) | 11 | grand | train shed: several arches when historic, one arch otherwise; escalators when modern |

(Lengths and widths are in blocks, 1.5 to the metre: a halt's 75 is 50 m, a regional platform's 180 is 120 m. They follow the
Deutsche Bahn archetypes of docs/STUDIO_API.md's research notes; the platform widths DB publishes are only a 2.75 m minimum, so these
are estimates.) A terminus keeps its own defaults and takes the class's building size and look; a halt class with a terminus keeps
the head building a terminus needs, with the usual warning.

| Era | Look when none was named (building, platforms) |
|---|---|
| historic | sandstone, Frankfurt's look (German S-Bahn platforms) |
| interwar | Hamburg's clinker brick (German S-Bahn platforms); a grand station Milan's monumental stone |
| postwar | Munich's concrete (German S-Bahn platforms) |
| modern | Utrecht's glass (Dutch platforms); a grand station Berlin's glass and steel (German S-Bahn platforms); a halt the modern platforms |

There is no look drawn from the 1920s and 30s yet, so **interwar uses the nearest existing ones** (clinker brick, Milan). The class
with no era asked uses its own: halt postwar, regional, town and grand historic, city modern. Without either, the plan reports the
class and era it amounts to (`resolved.class`, `resolved.era` in Studio's answer): no building and up to two tracks is a halt, a compact
building regional, a city-sized one on up to three tracks town (more tracks, or a terminus: city), a grand one grand; the era is the one
of the building's look (platform style when there is none).

Station Studio sends `class`, `era` and `facing` as request fields; with a class or an era chosen only the controls the player has
touched (the `pinned` list) go in, even when no words are typed, so the class decides everything else.

## Facing

`facing north` (also east, south, west) says which way the entrance looks. A through station's building is on the left of its tracks'
direction and a terminus's head building across them, so the station is placed with its street side that way. It changes no block (the
plan's fingerprint is the same). In the game it is built that way round instead of the way the player looks; Station Studio draws
and exports it that way, and `/ata_station verify paste` expects it. Without the words the game builds the way the player looks, and the
studio and the export use their own north-up view: a terminus's front to the north, a through station's street to the west.

## Bigger stations: two ways down, shelters, forecourts

- **Two ways down.** A through station with a city or grand building gives every footbridge platform wide enough for it (islands 11,
  side platforms 9) a second stair from the other side of the corridor, so a train's passengers spread over two stairs. Both flights
  leave the one footbridge. `single stairs` keeps one; a compact building, a terminus and a station with no building are unchanged.
- **Shelters.** An open platform (no roof over it) 24 or more blocks long gets a walk-in waiting shelter near its middle, from the bus kit:
  a glass wall two high and three long, a seat in front of it facing the track, a roof over both. An island at least 7 wide gets the wall
  down its middle with a seat on each side. Platforms under a canopy or train shed, stations with `no benches` and short platforms get none.
- **Car park forecourt.** The `parking` word's lot has a plain drop-off forecourt by the door (closed by bollards), ten cells of cycle stands by
  the kerb (a bollard and a station-fence rail each: there is no bike block), and trees in the planter (oak trunks, leaf crowns). All its
  leaves are planned persistent, so they never decay.
- **Hub forecourt.** A hub (`hub`, `interchange`) has 18 rows past its head building's entrance ramps: a way across from the door, a taxi
  rank of three marked bays with a bollard at the head of each on one side, and a bus interchange on the other (a kerbed island with a
  waiting shelter, two stop signs, a timetable case, and a marked carriageway beside it). There is no tram stop (the mod has no tram
  blocks) and no taxi sign block. The footprint grows by the forecourt, so a hub is 18 blocks longer than before.

## Seeds and fingerprints

`seed k7f2ra` (Station Studio's dice writes it for you) varies the choices the words left open, and nothing the words decided.
A seed is 1 to 12 characters of `a-z`/`0-9`, any case, read in base 36 (`StationSeed`); the biggest, `zzzzzzzzzzzz`, is 36^12 - 1 and fits
a long. Anything else after the word (13 or more characters, a space or symbol inside, other scripts) is **ignored with a warning**
and the station is planned as if there were no seed; the Studio's seed field does the same (the warning is in the plan's
`warnings`). A seed given twice uses the first. `seed 0` (or `000`) is the plain plan, exactly as planned without a seed, and the
report says so.

A seed changes these, each only when the words did not say it: the platform floor (3 in 5), the roof material (1 in 2), the canopy
profile (2 in 5), one arch or several in a barrel-vaulted shed (2 in 5), which of the four colours platform 1 starts on at a
terminus gate line, and where the first bench stands (4 to 12 in). That is the whole list.

**Theoretical limit.** Seeds are many, but the stations they can reach from one description are few: the product of the choices
above that this station shows (at most 3,000 or so: 4 colours x 9 bench positions x about 6 floors x 4 roofs x 3 canopies), however
long the seed is. A roll of random seeds therefore repeats a station after a few dozen (`3 tracks, 80 long, hamburg, station building`
has 77 distinct stations among 200 random seeds, a utrecht island canopy station 130). Two seeds with the same effective choices
(`StationSeed.effective`) plan the same blocks; two with different ones never do. A test holds both directions over 200 seeds and
four descriptions.

**Fingerprint.** `PlanFingerprint.of(plan)` is a 16-hex-digit SHA-256 prefix over the blocks sorted by (u, y, v), one line each:
`u,y,v,blockId,facing,state,text`. The same words and seed give the same fingerprint on any machine, in any order of planning,
on any thread; any change to a block's position, id, facing, state or sign text changes it. Roles, and where or which way the
station is built, are not part of it. Station Studio sends it as `fingerprint`.

## Real stations

`utrecht centraal` builds the real Utrecht Centraal rather than a look after it.
It has a closed hall over 8 island platforms with a three-wave roof, glass walls, tree columns, and a gate line at each end, where a broad stair and a lift come up from the square.
The facts, sources and what is simplified are in `docs/REAL_STATIONS.md`.
The station wrench's "Utrecht" look is unchanged; the Studio/wrench preset "Utrecht Centraal" now builds the real station, and the old modern look moved to the "Modern" preset.

`hamburg hauptbahnhof` (or `hamburg hbf`) builds the real Hamburg Hbf: a train shed over 5 island platforms, its 73 m nave rising to 37 m over two lower aisles, the grand building beside the tracks and a 45 m clock tower, with DB signs.
It is a second kind of real station (`RealStation.Kind.TRAIN_SHED`); the hall-over-the-tracks rules stay Utrecht's (`hallOverTracks()`).
A tower taller than the usual 11 blocks gets stone storeys of 8 with string courses and windows, and its belfry under the clock (`PlanBuilding.tower`).
The Hamburg *look* is unchanged.

## Known limitations

- No elevated stations; underground ones are shallow through stations (see above). Straight stations along one axis. No curves, no catenary, no platform screen doors.
- Lifts need a platform at least 6 wide (a two-column stair) and a building of three bays or more; narrower footbridge platforms keep a "not step-free" warning.
- The building and footbridge roofs count as shelter in the checks, but the design rules do not check their spans; they rest on the building and footbridge walls.
- Rail nodes for tracks along X (`facing=true`) match the live-tested release-check scene. Nodes for tracks along Z (`facing=false`) follow from MTR's code and are covered by game tests, but not yet by a live MTR connection.
- The preview particles were not seen in a client in this version (headless tests only check that planning and preview change nothing).
- Undo history is one build per player, in memory.

## Station Studio

A browser page for designing stations, opened from the Station Wrench screen (*Station Studio*) or with `/ata_studio`. The game
client serves it on `http://127.0.0.1:25680/` (the next free port up to 25689) and opens your browser.

- **Settings:** a description of up to 1000 characters, station mode, platform layout, tracks and platform length sliders, look,
  mix, roof, station building and name. Only a setting you actually change is written in front of what the planner reads; every
  setting you leave alone is taken from your words, so a typed description describes the whole station unless you overrule a part
  of it with a button.
- **Platforms one by one (Advanced, off by default):** tick *Set each platform myself*, choose 1 to 10 platforms and give
  each its own type (side, island, side bay or island bay), width, length and roof (*Station roof*, *Canopy* or *Open*); *Auto* uses the station's.
  The rows are written as a platform list (see the table above) ahead of your words, so they are never cut off, and the
  wrench keeps them: after *Save*, *Plan* or *Build* in Studio the wrench opens on its Advanced page with the same text. While
  the list is on it sets the tracks, so the tracks and platform buttons step aside.
- **Preview:** the plan drawn from the game's own blockstates, models and textures (resource packs included). Views: Overview,
  Reverse side, Street, Head (a terminus front from the street; only shown for a terminus or hub), Passenger (standing on
  platform 1) and Plan. *Cut* slices the station by height, across or along the
  tracks. Left-drag rotates, Shift + left-drag or right-drag pans, scroll zooms, *Invert X/Y* flips the drag, a click names a block.
- **Walk:** *Walk* puts you on the street in front of the entrance (or on platform 1 without a building) and lets you walk
  the station as a player would: a 0.6 × 1.8 body that collides with every block's model boxes, steps up half a block (stairs
  and ramps), falls and jumps. Fare gates stand open and lift doors let you through. Escalators and travelators carry you the
  way they run in the game (the plan's `moving` list gives each step's direction, which MTR's model does not show). In a lift
  car, *E* rides to the next floor. A fall of more than 4.5 blocks, or below the ground, puts you back where you last stood.
  Keys are Minecraft's: WASD or arrows, Ctrl or a double tap of W to sprint, Shift to sneak, Space to jump and a double tap of Space to fly
  (Space up, Shift down; G through walls). Opened from the game they are the player's own bindings for those keys (`/api/keys`, read
  from the game's options; a browser reserves some combinations, e.g. Ctrl+W closes a tab on Windows, so double-tap W works anywhere). 1 for platform 1, 2 for the
  street, Esc to free the mouse and Esc again to leave. Changing a setting while walking re-plans around you.
- **Report:** requested and built side by side, what the parser recognised, filled in or did not understand, and every station
  check and design rule, with *Show raw blueprint JSON* for the plan itself.
- **In the game:** *Plan in front of me* plans the design in front of you facing the way you look (with the usual particle
  outline and chat summary), then *Build it*, *Cancel plan* and *Undo last build*. The design is also saved on a Station Wrench
  you hold, so right-clicking the ground with it places the same station.

- **Export a schematic:** `GET /api/export` (or a POST) with the plan parameters gives the planned station as a gzipped Sponge
  Schematic version 2 file that WorldEdit, FastAsyncWorldEdit and Litematica read, written by the studio itself with no Minecraft
  classes, so it works outside the game too. The paste origin is the ground just outside the street entrance: stand there and
  `//paste`. Block states include the joins (panes, rails, girders) and MTR's escalator shapes. No block entities, so sign text,
  screens, lifts' MTR data and the MTR station zone come from building with the generator. Details: `docs/STUDIO_API.md`.
- **Check a paste:** `/ata_station verify` reads the world back against the pending plan (or the last build) and says
  `Exact: all N blocks ... are as planned` or lists the first differing blocks; `/ata_station verify paste` puts the plan's paste
  origin on your feet first, to check a schematic you pasted standing there. Operators only, read only.

- **Reloads by itself:** the page asks the studio every 1.5 s (5 s in a hidden tab) for `/api/version`: a start id and the
  modification times of the page files. When either changes (the page files on disk, or the studio restarted, e.g. a new game
  session) the page reloads, keeping your settings; while the studio is stopped the top-right pill says so. A restarted studio
  has a new token, and the reload brings it, so the in-game buttons keep working. Page files are served from
  `build/resources/main`, so run `./gradlew processResources` after editing `src/main/resources/studio/`.

Safety: the studio only listens on this computer, refuses requests that name another host, and only the page the game served
(which carries a per-session token) can act in the game. The game still checks operator rights for every action.

Outside the game, `tools/station-studio.sh` starts the same studio from this repo (textures from the repo, the Minecraft client
jar and the MTR jar in the Gradle cache) for designing and checking without Minecraft running; the in-game buttons are hidden
there. The page uses three.js r149 (MIT), bundled in the mod.

## Plan viewer

`tools/StationPlanExport.java` writes plans as JSON, with every block (role, block id, front, state), the rule breaks, the station checks and a "suspect" flag. A block is flagged when it touches no other block, when no chain of touching blocks joins it to the ground, or when it should stand on something and has nothing under it: no span between neighbours, no wall behind it, nothing above it to hang from. `tools/station-plan-viewer.html` shows such an export in 3D: orbit, cut by height, filter by role, hover a block for its cell and id, and click a problem to fly to it.

```
./gradlew compileJava
java -cp build/classes/java/main tools/StationPlanExport.java build/station-plans.json            # every preset and the guide's examples
java -cp build/classes/java/main tools/StationPlanExport.java out.json "two track metro station"  # your own descriptions
```

Open the viewer in a browser and use **Open an export** to load the JSON.

The planner tests hold every plan in the spec matrix and every preset to the same ground rule, so a floating block fails the build.

