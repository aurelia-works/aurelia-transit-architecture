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

**Presets.** Eighteen buttons on the wrench screen fill in a complete description, one per building look: Utrecht, Amsterdam, St Pancras, Antwerp, Frankfurt, Hamburg, Munich, Grand Central, Tokyo, Zurich, Paris, Milan, Berlin, Hong Kong, Beijing, Bangkok, Kuala Lumpur and Mumbai. Utrecht, Amsterdam, Hamburg, Munich and Kuala Lumpur are 2-4 track stations with a building and a footbridge; the others are big termini (hover a button to read it). A preset only fills the text field; nothing is planned until you press Plan. They live in `StationPresets`, and a unit test keeps each one fully understood, free of design-rule breaks and buildable.

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
| Track bed | `gravel` (or `ballast`), `andesite`: ballast from wall to wall, between neighbouring tracks too, the row against each platform wall a Bed Light (the same ballast with a lit channel under the platform's lip), and black concrete under every platform and ramp. It replaces ground, so the build asks for terrain approval where it does. Studio: *Track bed* under Roof and building | the ground as it is |
| Escalators | `escalator`/`escalators`: an MTR up escalator beside each footbridge stair, with a rail between, on islands at least 11 wide and side platforms at least 9 wide (narrower platforms keep the stair only). Studio: *Footbridge stairs* | stairs only |
| Stairs both ways | `stairs both ways`, `reverse stairs`: a second stair down to each platform wide enough (islands 11, side platforms 9), from the corridor's other side beside the lift, facing the other way. Studio: *Footbridge stairs* | one stair per platform |
| Enclosed stairs | `enclosed stairs`, `walled stairwells`: solid walls round every footbridge stairwell instead of glass. Studio: *Stairwells* | glass |
| Travelators | `travelators`, `moving walkways`: flat MTR escalators, only on the platforms (none in the footbridge corridor): along platforms at least 71 long and wide enough (islands 11, side 9), in a lane clear of the column lines, in stretches of up to 16 with 3-row gaps to cross, moving towards the footbridge or the terminus head. Studio: *Travelators* | none |
| Other | `no benches`, `no rail nodes`, `named "X"` / `called X` | benches, rail nodes, no name signs |
| Building | `station building`, `ticket hall`, `concourse`, `furnished`, `centraal`, `hauptbahnhof`, `no station building` | none |
| Building look | `utrecht`/`rotterdam` (modern glass), `amsterdam`/`brick`, `st pancras`/`london`/`gothic`, `antwerp`/`marble`, `frankfurt`/`sandstone`, `hamburg`/`clinker`, `munich`/`brutalist`, `grand central`/`union station`/`washington` (Beaux-Arts), `tokyo`/`japan` (red brick with domes), `zurich`/`swiss` (sandstone with a giant arch), `paris`/`french` (limestone with statues), `milan`/`milano`/`italian` (monumental stone), `berlin` (glass and steel), `hong kong`/`kowloon` (white, wave roof), `beijing`/`china`/`shanghai` (glass, deep roof), `bangkok`/`thai`/`hua lamphong` (cream, great arched window, white columns), `kuala lumpur`/`malaysia`/`moorish` (white, dome, minarets, domed kiosks), `mumbai`/`bombay`/`india`/`csmt` (sandstone, great dome, Gothic turrets). Also `american`/`usa`/`new york`/`boston` (Beaux-Arts), `british`/`london`, `leipzig`/`cologne` (sandstone), `brussels`. Landmarks (`st pancras`, `antwerp`, `grand central`, `marunouchi`, `kowloon`, `hua lamphong`, `csmt`...) also ask for a building. | the style's own look |
| Mix | two or more looks with `and`, `combined`, `mix`, `x`, `hybrid`: the first gives the building, the first with a platform style gives the platforms, the last with an arched shed gives the train shed (e.g. `american station, utrecht and hamburg combined`) | one look |
| Footbridge | `footbridge`, `bridge`, `overbridge`, `skywalk`, `no footbridge` | none; a through station with island platforms and no building gets one (see *Street entrance*); `no footbridge` is kept with a warning |
| Terminus | `terminus`, `terminal`, `kopfbahnhof` (asks for a head building; a footbridge is dropped with a warning). Without a number a terminus is a main station: 8 tracks, 160 long; `large`/`big` 10 tracks, 200 long; `city` 4 tracks, 96 long; `small`, `compact`, `halt`, `village` or `rural` the ordinary 2 tracks. Any track count or length you give wins. | through station |
| Platform list | `platform 1: island 12 wide 120 long open; platform 2: side canopy;` (up to 10, left to right; each clause ends with `;`). Each platform brings its tracks (a side platform one, an island two), so the list sets the tracks, up to 20. Per platform: `side`/`island`, `bay` (a through station's dead-end platform: its tracks come in from the station's start and end at buffer stops just past its far end; in a terminus every track already does), `N wide` (3-20), `N long` (16-300), `canopy`/`open` (under a train shed every platform is covered). A shorter terminus platform ends at the head building; a shorter through-station platform is centred on the footbridge line and always reaches 12 blocks past it, so its stairs and lift fit. The station is as long as its longest platform. Two side platforms next to each other go back to back; a side platform with an island after it gets a one-block gap behind it. A hub needs islands only. | not listed: every platform alike |
| Hub | `hub`, `interchange`: a terminus whose outer tracks run on past the head building (SCR-style): one a side up to 6 tracks, two a side from 8. Island platforms only, an even number of tracks, at least 4; an odd count is rounded up and side platforms become islands, both with a warning. | no |

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

The generator places **unconnected MTR rail nodes** at both platform ends of every platform track. The summary prints their coordinates. Connect them with an MTR **Platform Rail Connector**, so MTR's platform matches the built platform, then extend the line with normal rail connectors.

The generator never creates rail connections itself, because those live in MTR's simulation core. Undo leaves a node alone once you have connected it (MTR sets `is_connected`), since removing it with a block change would orphan MTR's rail data. Without MTR loaded, the nodes are skipped and the summary says so.

## Performance and safety

- One server tick listener for the whole feature, which returns at once when no build or preview is running.
- One new block (the station girder, no block entity), no mixins. The wrench adds one item, one client-to-server packet (hand, action, a description of at most 256 characters and the screen's choices of at most 160, all bounded on read and handled on the server thread) and one client screen.
- The preview is server-side particles sent only to the planning player (at most about 600 per refresh, every 10 ticks, for 30 s), and it changes nothing.
- Pending plans, jobs and undo logs live in memory: a server restart forgets them, and a player who leaves loses their pending plan (a running build continues).

## Known limitations

- Ground-level stations only: no underground or elevated. Straight stations along one axis. No curves, no catenary, no platform screen doors.
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
  Keys: WASD or arrows, Shift to run, Space to jump, F to fly (Space up, C down, G through walls), 1 for platform 1, 2 for the
  street, Esc to free the mouse and Esc again to leave. Changing a setting while walking re-plans around you.
- **Report:** requested and built side by side, what the parser recognised, filled in or did not understand, and every station
  check and design rule, with *Show raw blueprint JSON* for the plan itself.
- **In the game:** *Plan in front of me* plans the design in front of you facing the way you look (with the usual particle
  outline and chat summary), then *Build it*, *Cancel plan* and *Undo last build*. The design is also saved on a Station Wrench
  you hold, so right-clicking the ground with it places the same station.

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

