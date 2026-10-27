# Changelog

## 1.7.0

Airports and deep underground stations, both built by the station generator. Everything below was built and looked at in game on a dev server (10-27).

### Airports

- **Airports:** "airport", "2 runways", "6 gates", "small airport", "runway 05/23" plan a runway with numbers, markings and lights, a parallel taxiway with links, an apron with gates and MTR airplane routes, and a terminal with boarding bridges. The default is one runway of about 2,550 blocks, long and flat enough for player-flown aircraft; real airport presets set their own. Station Studio has its own Airport Generator section, and the operator-only Airport Wrench plans them in game.
- **Railway station under the terminal:** "airport with a train station" (or "transit", "rail link") digs a two-track cut-and-cover box with an island platform under the terminal, with escalators and a lift up into the hall and its MTR platforms made.
- **Terminal fit-out (Null's list):** walk-through metal detector, X-ray bag scanner, security inspection table, queue posts with belts (they join like fences), self check-in kiosk, self bag drop, baggage carousel (straight and corner), gate desk, luggage trolley and joining airport seats. The terminal builds a closed security line between check-in and the gates, a baggage hall with whole carousel loops, a desk, number sign and seat rows at every gate, and kiosks and trolleys by the doors.
- **Real airport sizes and markings:** the taxiway stands 158 m from the runway and parallel runways at least 210 m apart (ICAO's minimums; they were 54 m and 110 m). Threshold stripes, the aiming point and touchdown bars follow ICAO for the runway's width and length.
- **Planes use the whole runway:** they land at the threshold and roll to the far end, taxi back to the gate, and take off from the near end (they used to take off and stop in about 16 blocks). No floodlight mast stands in a plane's way any more, and each gate's siding is one straight rail with a push-back.
- **Words:** "airport without a train station" builds none; "Schiphol airport station, 4 tracks" builds a railway station, not an airport. An airport too small for planes says so in the chat.
- **MTR and undo:** undoing an airport before MTR has answered no longer leaves its route and depot in MTR; a changed MTR is logged instead of stopping the build. Station Studio sends big plans gzipped.
- **Airport blocks:** runway edge, threshold and taxiway lights, apron floodlight, check-in desk, baggage belt, departures board, windsock and terminal terrazzo, in a new Airport tab.

### Underground

- **Deep stations:** `deep tube` builds a bored tube 27 m down, `cut and cover` a deep box with lifts. Presets: Angel (tube), Canary Wharf (box), Vijzelgracht (island box). The look is generic by default; London only from words or presets.
- **Stacked platforms:** `stacked deep tube` and the Westminster preset build two deep platform levels, one over the other. Westminster is grey concrete and steel, like its Jubilee platforms.
- **Sounder linings:** a bored tube's lining joins face to face where it steps or curves, and its ticket hall's walls meet the roof with no notch.

### Build job

- **Big sites stream their chunks:** the survey, the build, undo and rebuild hold at most 1024 chunks loaded at a time, and a long survey carries on over the next ticks, so an airport-sized site no longer runs the server out of memory.

### Other

- **Station Codex:** an in-game book (Passenger Equipment tab; operators get it once on joining, `/ata_guide` gives another) with a tab per chapter, contents, search and block previews. It replaces the guide book.
- **Chat summaries:** an airport is summed up as runways, gates, terminal and station; a deep station by its kind and depth; a stacked station's upper rail nodes at their own height.
- **Standalone Station Studio** starts again (it crashed outside the game since 10-19: missing logger library).

## 1.6.1

Fixes from the 1.6.0 play-test (Jah, 10-26). No new features beyond joined benches.

### Fixes

- **Platform boards show their own platform:** a board over an island shows the departures of that island's two tracks, and a board on a side platform its own track, never every departure in the station (the search for a board's platform reached 5 blocks and missed the middle of wide islands). With no trains a platform board names its platforms ("Platform 5 / 6"); the big boards in the hall keep the whole station.
- **Generated rails are visible:** platform tracks are laid with MTR's default rail style; before, they were connected but drawn as nothing.
- **Board messages:** a long message on a platform board holds, scrolls once to its end and holds, instead of being cut off.
- **Terminus halls** get departure boards over the gate line, the main board two blocks high.
- **Help points** stand by the middle screen of a platform, not at its open tip.
- **Bed lights** no longer show the terrain through their light channel.
- **Handrails:** a piece broken out of a generated run and put back goes back into the run (corners too), and the rail is easier to aim at.
- **Station Studio** no longer adds travelators unless asked for.

### Stations

- **Real train sheds stand on their own column rows:** Frankfurt, Milan, Zurich, Munich, Mumbai and Hamburg rest only on the rows under the valleys between their vaults (Hamburg: between nave and aisles) and at their outer edges, with the platforms under each span free; before, every island had two rows of columns. Berlin's single barrel vault rests on its two outer rows only.
- **Amsterdam Centraal's real front:** two towers, the raised central gable, long banded brick wings and stepped-gable end pavilions, with a 9-wide entrance.

### Blocks

- **Benches join into one long bench:** the same bench facing the same way side by side shares one frame, with legs and armrests at the ends of the row and a centre support between (Null's request). Platform benches, placed in pairs, now read as one bench.

## Unreleased

### Station generator

- **Airports (1.7, milestone 1, Studio only):** "airport", "2 runways", "6 gates", "small airport" plan a runway with markings and lights, a parallel taxiway with links, an apron with gates and a terminal with boarding bridges on the station pipeline (`PlanAirport`, `AirportAudit`); the default is a 1,700 m regional runway at 1.5 blocks a metre. No grading, MTR route or presets yet.
- **Deep stations, sounder linings:** a bored tube's lining is joined face to face where it steps or curves (the shaft's ceiling, round bore crowns) and its ticket hall's walls meet its roof with no notch along the corners (`Carve.joinedLining`); the hall's and lift pavilions' roofs are roofing, not the wall stone; crown lamps have a tile behind them; a box's platform gutter beside an escalator bank is no longer shut off by a bin and a bench (`PlanDeep.openPockets`).
- **Stacked platforms, Westminster:** the planner takes a platform level per track and platform (`level`, default 0); `stacked deep tube` and the new Westminster preset build two deep platforms one over the other, 12 blocks apart in height, 58 blocks down, with an escalator bank between the levels and a three-stop lift. Every other station plans exactly as before.
- **Real stations, starting with Utrecht Centraal:** `utrecht centraal` builds the real station from published facts. It has one closed hall over its 8 island platforms (16 tracks), 127 blocks deep (85 m) and up to y 27 (18 m). The roof has three waves, the middle one the highest, and the hall has glass walls, white tree columns, ticket machines, signs hung from the roof, and a gate line at each end. Each end of the hall is entered from its square by a broad stair and a lift; nothing is walkable at track level beside the tracks. Under the hall the platforms have its floor overhead, with no canopy girders. Outside it there are plain steel canopies; the station is NS-operated and has escalators. Shorter platforms get a shallower hall, with a warning. Words that contradict the real station (terminus, station building, mezzanine, side platforms) are dropped, each with a warning. Sources and simplifications: `docs/REAL_STATIONS.md`.
- **Presets:** "Utrecht" is now "Utrecht Centraal", the real station; the earlier modern look is the new "Modern" preset.
- **Underground stations:** `underground`, `subway`, `tube` or `tunnel` builds a tiled box under the street, after Berlin's Rosenthaler Platz: stairs up through the street with a rail round each opening and a name pylon, tunnel mouths at both ends, columns and ceiling lights. Tile colour from the words or the look. New preset "Rosenthaler Platz".
- **British stations:** Victorian (Teddington), art deco (Surbiton) and modernist (Coventry) looks, with tarmac platforms, white-nosed coping and 14 new blocks; presets for all three.
- **Swap blocks you don't like:** "use stone bricks instead of sandstone" (or "replace ... with ...", "swap ... for ...", "a -> b"); slabs, stairs and walls follow. Studio has a Blocks section.
- **From a photo (Studio):** pick a photo of a station and Claude writes the description for you; needs an Anthropic API key, otherwise the Studio gives you the prompt to use in Claude yourself.
- **MTR platforms are made for you:** each platform track's rail is joined and numbered in MTR; no more numbering platforms by hand.
- **MTR's own ticket gates** by default (`ata gates` for the two-way ones); **wider entrances** (5 wide, 7 at a grand station, or as asked); no lone chairs upstairs; facing platforms get a footbridge; no ramps onto the tracks.
- **Help points** say the whole station name.
- **Presets:** "KL Sentral" (ten tracks, modern glass hall), SEPTA's "Frankford" and "69th Street" termini, Berlin's "Rosenthaler Platz" underground; the wrench shows presets in five columns.
- **Utrecht terminus fixes (Jah, 10-21):** ballast now runs between buffer stops that stand side by side; the strip beside an overrun is all platform floor (no concourse tile between paved edges); each MTR gate bank ends in the line's own glass fence, not a glass panel half a block out in front of it. Studio no longer draws a corner post on every edged railing (it guessed the first `corner` value; the game's default is none).
- **Grand Central's side lobbies open onto the hall:** where a real station's hall is narrower than its head building, the hall's side walls are a ground-floor arcade, so the outer lifts and stairs no longer sit behind a wall with a one-block slot.
- **No crawling in under stairs:** footbridge flights and station-building stairs have a stringer under each step too low to stand under (Utrecht, Amsterdam and Hamburg Hbf had walkable floor beneath their lowest steps).
- **No lift or stair to nowhere:** a test walks every preset and common station from outside the way a passenger would; every lift must open at two levels someone reaches, every stair and escalator must be walked from foot to top, and every platform must be reached.
- **City cards build the real station:** in Station Studio the Utrecht card builds Utrecht Centraal's hall and the Hamburg card Hamburg Hbf's shed; each other city with a real-station front (Frankfurt, St Pancras, Paris, Milan, New York, Munich, Antwerp, Zurich, Mumbai, Bangkok, Tokyo, Beijing, Kuala Lumpur, Berlin, Coventry, Surbiton) sets the shape that front needs (a terminus, or a through station with a building, with enough tracks and length). In words, "real <city>" asks for the same front. Settings set by hand win; where the shape cannot carry the front, the city's look is built without it. All 21 city cards stay.
- **New real-station fronts:** Tokyo (the red-brick Marunouchi building), Beijing, Kuala Lumpur, Surbiton, Munich, Berlin and Coventry. Tokyo is close to the real building; the others approximate its massing and materials.
- **A plain "amsterdam" station** gets a grand building by default.
- **Deep stations:** `deep tube` builds a bored tube 27 m down ("40 blocks deep"), escalator-only with `no ramps`; `cut and cover` builds a deep box with lifts. Presets: Angel (tube, TfL signs), Canary Wharf (box, TfL signs), Vijzelgracht (island box, GVB signs). Shallow underground is unchanged.
- **Airport blocks (1.7):** runway edge, threshold and taxiway lights, apron floodlight, check-in desk, baggage belt, departures board, windsock and terminal terrazzo, in a new Airport tab; the airport generator builds with them.
- **Runway numbers (1.7):** "runway 05/23" paints those numbers at the runway ends instead of 09/27; London Southend now carries 05/23.
- **Airport with a railway station under the terminal (1.7):** "airport with a train station" (or "transit", "rail link") digs a two-track cut-and-cover box with an island platform under the terminal, with escalators and a lift up into the hall and its MTR platforms numbered; new preset "Airport with station". The tracks end at the box walls; it fits regional-size terminals only (a compact airport says so).
- **Airport security and gate blocks (1.7, Null's list):** walk-through metal detector, X-ray bag scanner, security inspection table, queue posts with belts (they join like fences), self check-in kiosk, self bag drop, baggage carousel (straight and corner), gate desk, luggage trolley and linked airport seating, in the Airport tab. The terminal is now fitted out with them: a security line across the hall (lanes of arch, scanner, belts and table, queue posts before them, glass between, an exit by the far end wall; airside of the railway station so its escalators come up landside), bag drops and queue posts at check-in, kiosks and trolleys by the entrance, a baggage hall with carousels at the landside's far end, and at every gate a desk, a "Gate n" sign over the door and two rows of linked seats.
- **Seen in game (10-27):** an airport with a railway station under its terminal, and the Angel deep tube, a cut-and-cover box and the stacked Westminster, built on a dev server.
- **Westminster** is grey, not green: its Jubilee platforms are bare concrete and steel (the green Holden tiles belong to the sub-surface platforms, which are not built).
- **Chat summaries:** an airport plan is summed up as an airport (runways, gates, terminal, station), a deep station by its kind and depth, and a stacked station's upper rail nodes at their own height.

### Build job

- **Big sites stream their chunks:** the survey, the build, undo and rebuild hold at most 1024 chunks loaded at a time and let go of the rest as they go, and a survey that takes longer than a tick carries on over the next ticks. A site of any size (an airport) no longer keeps every chunk it touched loaded for ten minutes. On terrain never generated before, a build has at most 8 chunks loaded or generated per tick, so the server stays smooth.

### Station Codex

- **The Station Codex replaces the guide book:** a new item (Passenger Equipment tab; operators get it once on joining, `/ata_guide` gives another) that opens an in-game book with a tab per chapter, a contents page, search, and turning previews of the blocks it describes. Commands run on a click; the ones that build or undo, the examples, the real stations and every wrench preset (by its own name) go to the chat box ready to send; hover for what each does. It reopens where you left it. New pages for Station Studio, underground stations, swapping blocks, British looks and every `/ata_station` command (preview, rebuild, verify and help were missing). Platforms are now listed as 3 to 20 wide (the old book said 8). The written guide book is gone; old copies stay as they are, and players who had it get the Codex with `/ata_guide`.

## 1.6.0

### Passenger experience

- **Fare gates stand a full block and seal their ends:** the cabinet and paddle are a block tall (with a 1.5-block collision, like a fence, so they cannot be jumped) and an end cabinet is a solid panel, so no end of a bank is a hole to walk through. Slipping through an open gate behind the passenger it opened for is charged a modest maximum fare, through MTR's own balance.
- **Announcements always speak:** a station or route name with no recorded clip no longer silences the whole sentence; the voice says every part it has a clip for and the full sentence is shown as a subtitle.
- **Station Studio walk mode has sound:** it speaks a departure announcement and plays short cues when a fare gate opens and when a lift arrives.

### Station generator

- **Rebuild in place:** `/ata_station rebuild` builds a changed plan over your last station and writes only what differs (a rename touches only the name signs); cells the new plan drops go back to the original ground, and undo still restores the site from before the first build.
- **Typed words win in Station Studio:** where the words and a pinned or rolled setting disagree, the words decide; the page names the settings they overruled.
- **A second footbridge at the far end:** long city and town through stations with a canopy (or no roof) get a stairs-only footbridge across the far end of the platforms, so passengers at that end can change platforms without walking back to the building.
- **The wrench preview turns with "facing":** it now draws the station the way the Studio and the build do.
- **Built at 1.5 blocks to the metre:** storeys 4 m clear, the footbridge 4.7 m over the rails, canopies 4 m, taller sheds; stairs climb a whole storey. A station building needs platforms 24 long, a hub 20.
- **A real terminus concourse:** ticket hall with a ticket office, machines, toilets and a shop; one gate line with glass barriers to the walls; the paid concourse with the main board, seating islands and, in a big hall, a gallery each side with a lift and a stair.
- **Seeds hardened:** a seed is 1 to 12 letters or digits; anything else after `seed` is ignored with a warning (never an error), a seed given twice uses the first, and every plan has a **fingerprint** (a stable hash of its blocks) that Station Studio shows, so two people can tell whether they planned the same station. Tests hold reproducibility across fresh planners and threads, and that seeds that plan the same station always share their effective choices.
- **Schematic export:** Station Studio can save the planned station as a Sponge Schematic version 2 file (`/api/export`) that WorldEdit, FastAsyncWorldEdit and Litematica read, with the paste origin outside the street entrance, finished block states (joined panes, rails and girders, MTR escalator shapes) and the seed, fingerprint and description in its metadata. `/ata_station verify` (and `verify paste`) reads the world back against a plan, to check a build or a paste block by block. Game tests parse every exported state with Minecraft's own parser and compare four real builds with their files.
- **Checks say what to do:** every station check has a short title and, for a warning or failure, a plain-English next step; Station Studio's answer adds a pass/warn/fail summary. No check was loosened.
- **Station class and era:** `halt class`, `regional class`, `town class`, `city class` and `grand class` fill in whatever the description left open (a halt is one open 75-block platform with no building; a regional station a compact building and no train shed; a grand one a grand building under a train shed), and `historic era`, `interwar era`, `postwar era` and `modern era` choose the look when none was named. What is said always wins, and the bare words `halt`, `regional`, `town`, `city`, `grand` and `modern` mean what they meant. `facing north` (east, south, west) says which way the entrance looks: the studio and the export are drawn that way and the game builds it that way round instead of the way the player looks.
- **Bigger stations (phases 2 to 5):** a city or grand through station gives each wide platform two ways down (`single stairs` keeps one); open platforms get a walk-in waiting shelter; the `parking` lot gets a drop-off forecourt, cycle stands and trees (and its leaves no longer decay); a hub gets a forecourt with a taxi rank and a bus interchange. Tram stops, a second footbridge, wayfinding signs and a multi-level concourse are not built yet.
- **Bigger stations, the rest of phases 2 to 5** (none of it seen in game yet): an `underpass` (a stairs-only tunnel a storey under the tracks, dug only with replace-terrain); the far-end footbridge under hall roofs too, and at any footbridge station whose platforms are 96 long; signs over each stair head in the footbridge ("Platform N", down arrow) and an exit sign where it meets the building; shop units under a hub's concourse galleries; `tram` (or `tram stop`) puts a tram stop on a hub's forecourt in place of the taxi rank, with two new blocks, **Street Tram Track** and **Tram Stop Sign** (lay the MTR rail on the track yourself); `mezzanine`, `upper concourse` or `multi-level` widens the footbridge into a 9-wide concourse over the tracks with benches and departure screens; a terminus with `parking` gets a forecourt with a drop-off lay-by, cycle stands and trees; an open one- or two-track description with no class gets a halt's 5-wide platforms.
- **Fixes:** Station Studio keeps typed words in charge after a reload, and the second look pins the look; canopy girders cut off by a stairwell are left out or shortened instead of hanging on nothing.

- **Car park:** the words `parking`, `car park` or `park and ride` pave a surface car park in front of a station building — two rows of bays either side of a central aisle, a kerb round the outside and a driveway at the entrance, from the paving and bus kerb blocks already in the mod. The lot takes its own band in front of the street wall and moves the building back behind it, so it stands inside the station's footprint. It needs a station building beside the platforms (a terminus's street, past its head building, has no room yet), and it says so when it is left out.
- **Real hall depths:** a city or grand station building is now as deep as a real one (15 and 21 blocks, about a block a metre) instead of keeping the compact 9-block footprint, and a city or grand terminus head building is 20 or 30 deep instead of 12. Compact stations are unchanged. So a bigger station gets a genuinely larger ticket hall and concourse, not just more storeys over a small floor.
- **Train shed taller with the station:** the hall frame sits higher as a station grows (compact springs at y=8, a city shed at 11, a grand one at 15, its frame about 18 and roof about 20 — a real monumental train-shed height), so a city or grand hall is a genuine volume instead of a lid over the trains. Column bays (8) and roof spans (12) are unchanged: they were already at real-shed spacing.
- **Describe a station and it is built:** plain English in, ATA blocks out. 1 to 10 tracks (up to 20 with platforms one by one), 16 to 300 blocks long, side or island platforms, roofs, ramps, name signs, rail nodes at the platform ends. `/ata_station plan` shows an outline, `/ata_station confirm` builds it; cancel and undo work at any point. Fully offline, operators only. Terrain is only changed after you approve it, and existing MTR rail nodes are never touched. See docs/STATION_GENERATOR.md.
- **Station Wrench:** the generator without commands. Pick a city look (and one to mix in), station, terminus or hub, tracks, platforms, width, length, roof, building and name, then *Place in front of me*, *Build it!*, *Undo* or *Clear*. A 3D preview updates as you go. Eighteen presets and a typed description are on the Advanced page.
- **Station Studio:** the same generator in your browser, on your own computer (`/ata_studio` or the wrench button). Big 3D preview with the game's own models, several views (including a terminus front from the street), and buttons that build in your world. Listens on 127.0.0.1 only. **Walk** through the planned station in first person, from the street through the gates to the platforms, riding escalators, travelators and lifts.
- **Eighteen city looks:** Utrecht, Amsterdam, St Pancras, Antwerp, Frankfurt, Hamburg, Munich, Grand Central, Tokyo, Zurich, Paris, Milan, Berlin, Hong Kong, Beijing, Bangkok (a low glass vault over the entrance between columns), Kuala Lumpur (white walls, pinnacles, pointed arches and open domed kiosks on octagonal towers) and Mumbai (sandstone and red tiles, a banded pointed arch, an onion-domed clock tower and spired corner towers); towers, domes, arches and sheds follow each city's real station, plus words such as london, swiss or japan. Looks can be mixed: "american station, utrecht and hamburg combined".
- **Station buildings:** ticket hall with gates, office, MTR ticket machines, kiosk, clock and boards, in compact, city (three storeys with wings) and grand (four storeys, pavilions, clock tower, hipped roofs) sizes. Larger buildings get an upstairs waiting room.
- **Termini, hubs and train sheds:** tracks end at buffer stops in front of a head building with a gate line, and every platform is step-free from the concourse. A hub ends its middle tracks and runs the outer ones through. City and grand termini get a front modelled on real stations: brick with clock towers, giant arched windows, Beaux-Arts stone, or glass. Train sheds come flat, arched, pointed, as several arches or as ridge-and-furrow pitched roofs, framed in the new **station girder** block (four colours).
- **Canopies by look:** a **butterfly** (V) roof for the German looks, a **gable** for the heritage looks, flat for the rest. The butterfly is framed like the real thing: arms on the columns and a valley beam, with no girders hanging under its eaves. Say `flat`, `butterfly` or `gable` to pick one. Canopies stand 4 high on fewer columns.
- **Footbridges, lifts and more:** a footbridge with stairs to every platform, glazed where it rises through a canopy. Platforms with room get a working MTR lift (its track fixed inside the side glass, out of the doors' way, with call buttons on both levels), which undo removes; at the platform it opens both ways, under the bridge and onto the platform behind it. Options for up escalators, stairs both ways, enclosed stairwells, travelators along long platforms and a gravel or andesite track bed. Island platforms are never left to be reached across a track: without a building they get a footbridge, and with tracks along both edges a street entrance beside the first track with its own stair and lift.
- **Wide platforms:** platforms are 10 wide by default (islands 11), room for travelators; "wide" gives 12 (islands 14). Narrower ones can still be asked for.
- **Platforms one by one:** give each platform its own type, width (3-20), length and roof, as plain words (`platform 2: island 12 wide 120 long open;`).
- **Fitted out:** departure screens hang from the canopy, centred on the platform on a cross beam (back to back on wide islands); under a train shed too high to hang from, they span the whole gap from one column to the next on a girder, like a long TV (or two blocks fixed to a column where there is no gap), with help points, benches and platform numbers. A speaker every 16 blocks, never on the floor: under the roof, or high on a column or lamp standard. Open platforms get lamp standards. Platform ends without a ramp are fenced off, so nobody walks off onto the tracks. Every street door gets an MTR station name panel to write on, with an MTR tactile map inside. Each platform has its own colour stripe at the gates.
- **Registered in MTR:** a built station adds itself as an MTR station with its name and area, so screens and announcements work at once. An MTR station already there is left alone; undo removes the one it added.
- **Checked before building:** boarding edges, rail nodes, roof cover, headroom, step-free access, lighting and nothing left floating. A failed check stops the build.
- **Fast:** builds use a fifth of each server tick, so most stations finish in under a second. Big stations are planned on a separate thread, so the server keeps ticking. Stations longer than the render distance load the chunks they need.
- **Guide book:** operators get a *Station Generator Guide* on first join; `/ata_guide` gives another.

### Screens, signs and announcements

- **Glass panes join the roof and ironwork:** the mod's glass panes now connect to canopies, roof panels, columns, cast iron shed legs, girders and stairs (block tag `glass_pane_joins`), so glazing meets the frame without a gap. Station Studio and the wrench preview show every block joined as it will stand in the game: screens into one display, signs, panes, railings, girders, fences, walls and sign poles.
- **Turn and shift screens and signs:** sneak while placing a screen, clock, information case or help point and it turns to face you in 16 steps. Where you click also moves it half a block, so it can sit between two blocks.
- **Departure screens rebuilt as real boards:** four styles, each with its own layout and lettering, not a colour swap. **UK Dot Matrix** in amber LED dots: "1st 10:42 London Euston ... On time", a "Calling at:" line that scrolls column by column, the 2nd and 3rd trains in turn and a clock with small seconds. **Dutch Platform Screen** (deep blue): departure time with a red delay, the destination large with "via" below, the platform number in a white square, the next train along the bottom. **German Platform Screen** (blue): time, train and a white delay box on the left, the stops above a large destination, the platform number, following trains and a white scrolling notice band. **Metro Countdown** (black): line-coloured route badges and minutes to go. Concourse boards follow each country's columns. Times are clock times, matching MTR's timetable and the board's clock. Boards saved with the old European styles show the nearest new one. A new screen starts with the clock and calling points with times. No operator names, logos or typefaces.
- **Built-in voice:** announcements are spoken in English out of the box (Piper, voice "cori", public domain). Station and route names come from a voice pack, which `tools/make_voice.py --pack` makes in the same voice. See docs/VOICE_PACKS.md.
- **Talking help points:** right-click with an empty hand: "This is Central, platform 3."
- **British wording:** speakers have a *Wording* setting, British ("Please mind the gap") or Standard. New speakers start on British.
- **Works with Aurelia Transit Utilities** (optional): while you ride with its on-train announcer, station speakers hold off so nothing is said twice.

### Blocks

- **Curved platform edges both ways:** angled, convex and concave edges and curved screen doors come in left- and right-hand pieces in every direction. Mirrored structures now mirror them properly.
- **Platform slabs (8):** half-height versions of the walkable platform surfaces. Train doors open beside them.
- **Gravel and Andesite Bed Light:** ballast with a lit channel under the platform lip.
- **More realistic textures:** about 80 textures repainted (still 16x16): brick, stone, concrete, marble, terrazzo, ironwork, roofs, glazing, paving, stairs and sign faces. No block ids or states changed.

### Fixes

- **Station Studio: a typed description now describes the station.** Before, every control was written in front of your words, so the defaults silently won and typing "8 tracks 200 long" still built a 2-track 64-long station. Now only a control you actually change is sent in front of your words; everything you leave alone comes from the description. Picking a preset still fills in the whole station.
- Fare gates work like MTR's ticket barriers: no card needed, the fare comes from your MTR balance and MTR says why when the gate stays shut. The transit card still shows and tops up the balance. Before, a player without a card got the "carry a card" message and an inventory search on every tick in front of the gate.
- Fare gates open from either side, so passengers can pay their way out too, and stay shut to anyone who has not paid: in Station Studio's walk mode you can no longer walk through a closed gate.
- The MTR tactile map by the street doors faces the passenger instead of the wall.
- Berlin, Hong Kong and Beijing windows are glass panes, not full glass blocks.
- A platform built 200 long now shows as 200 in MTR.
- Joined signs and screens no longer keep invisible hit boxes in the middle of the row.
- A seated player can move straight to another seat, as you can hop between boats.
- Service messages can be up to 256 characters, and longer ones are refused instead of being cut off mid-word.
- Islands too narrow for a footbridge stair (under 5) or lift (under 6) are widened to fit, with a warning, instead of being left without a way up.
- "Grand Central, city" builds a city-size building: a size word now beats the landmark's own size.
- Short platforms crowded by stairs and a lift get a shorter name sign row instead of none.
- "Gravel bed" and "andesite track bed" are fully understood (the Studio no longer lists "bed" as not understood).
- Station Studio reports the footbridge a building brings, and no longer marks a platform list's own track count or layout as a mismatch.

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
