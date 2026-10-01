# Aurelia Transit Architecture

A modular architecture kit for [Minecraft Transit Railway](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) (MTR), for building original modern European railway stations and bus stops, from a small local halt to a large central station.

The visual language is understated and contemporary, drawing loosely on Dutch, German, Swiss and neighbouring station design: galvanised and dark steel, light concrete, glass, muted blue/green accents and tactile yellow. It doesn't reproduce any real station and has no operator branding or logos. The pieces are meant to be combined.

## Supported environment (1.3)

| Component | Version |
|---|---|
| Minecraft (Java Edition) | **1.20.1** only |
| Mod loader | **Fabric** only (Fabric Loader ≥ 0.15.0) |
| Fabric API | ≥ 0.92.0+1.20.1 |
| Minecraft Transit Railway | **4.x** for Fabric 1.20.1 (built and tested against `4.0.5+1.20.1`) — **required** |
| Java | 17+ |

Forge, NeoForge and other Minecraft versions are **not supported**. Additional versions and loaders are planned as future work.

## Installation

1. Install Fabric Loader for Minecraft 1.20.1.
2. Put these in your `mods` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1
   - [Minecraft Transit Railway](https://modrinth.com/mod/minecraft-transit-railway) 4.x, **Fabric 1.20.1** build
   - `aurelia-transit-architecture-1.3.0+mc1.20.1-fabric.jar`
3. Start the game. The pieces are in four creative tabs: **ATA Architecture**, **ATA Wayfinding**, **ATA Passenger Equipment** and **ATA Bus / Street Transit**.

Install the mod on both the server and the clients.

## Feature families (83 blocks)

| Family | Pieces |
|---|---|
| **Platforms** (8) | Light and dark platform paving, tactile warning paving, platform edge, platform edge with warning line, angled / curved platform edge (45°, convex, concave), platform ramp (lower and upper halves) |
| **Passenger information** (12) | Platform CIS board and hanging CIS board, platform PIDS and hanging PIDS, concourse departure board (all live from MTR), wall and ceiling speakers, hanging and wall digital clocks, station analogue clock, passenger information terminal (joins side by side) and kiosk |
| **Signage** (6) | Freestanding and hanging station name signs, platform number sign, directional sign, information case, sign pole |
| **Furniture** (6) | Perforated steel bench, timber slat bench, waste bin, bollard, platform lamp, information pillar |
| **Architecture** (17) | Square and round steel columns, structural beam, branching roof support, framed glass wall, glass panel, glass barrier, flat canopy, canopy edge, sloped canopy (lower and upper), wave canopy rise, crest and flattening, canopy corner cap, canopy skylight, canopy light panel |
| **Catenary** (4) | Catenary mast, cantilever, gantry beam, insulator (decorative) |
| **Elevated** (10) | Viaduct column (heavy/narrow, steel/concrete), viaduct beam (crossbeam, girder, stringer, platform support; steel or concrete), brace (diagonal or canopy knee), station stair, stair enclosure panel (clad, windowed, glazed), platform wind screen (lower, upper), platform fascia (plain, panelled, ribbed), station fence (platform, trackside), utility run (cable tray, conduit), under-deck light |
| **Wayfinding** (6) | Entrance pylon (two blocks tall), wall and hanging wayfinding signs (join side by side), exit sign, street / landmark / connection blade, station information board (trains this side, platform, service changes, transfers, exits) |
| **Accessibility** (6) | Pictogram sign (accessible route, lift, stairs, help point, exit and more from one block), tactile guidance paving, tactile junction (turn, tee, crossing), help point, boarding marker (door / accessible / wait / ramp / assistance), handrail (handrail, glass balustrade, ramp edge rail) |
| **Bus** (8) | Bus stop sign, timetable case, shelter glass wall, shelter roof, shelter seat, boarding curb (full height and low), low-refresh e-paper bus arrival board |

### What 1.3 adds (Urban Infrastructure)

- **Elevated railway kit:** columns, beams, braces, stairs, stair enclosures, wind screens, fascias, fencing, utility runs and under-deck lighting for el-style lines above streets. Generic and original, usable for any elevated metro. Each piece is **one inventory item**: right-click it with an empty hand to change its style (sneak + right-click switches a beam between steel and concrete). See [docs/URBAN_INFRASTRUCTURE.md](docs/URBAN_INFRASTRUCTURE.md).
- **Station information board:** one wall board with five views of the shared wayfinding model: trains this side, platform/track, service changes, mezzanine transfers and a street/exit summary.
- **Several service messages at once:** network and station notices each have an id and a severity (info, notice, disruption, severe). Boards rotate through them one at a time in their bottom strip and scroll long ones; the terminal lists them all. `/ata_message add | remove | clear | list`.
- **Manual station association** for wayfinding signs, terminals and e-paper boards ("Station: Auto / Manual" in the editor), for overlapping, stacked or large stations.
- **Terminal polish:** joined terminals form one continuous screen, standalone help points nearby appear on the Accessibility page, terminal and board text is translatable, and the entrance pylon shows in full in the inventory.
- **Accessibility:** tactile junctions, handrails / glass balustrades / ramp edge rails, ramp and assistance boarding markers.
- **Angled and curved platform edges** that still open MTR train doors.

### What 1.2 adds (City Wayfinding)

- **One shared wayfinding model:** station name, line badges and colours, direction and destination, service type (local / express / limited / custom), station code, platform/track, exits, street and landmark labels, transfers, second-language name and pictogram. Every wayfinding sign reads the same model. MTR fills station name, lines and exits automatically; the rest is set in the sign's editor, and a manual value always wins. See [docs/WAYFINDING.md](docs/WAYFINDING.md).
- **Multilingual layout:** single, side by side, or stacked.
- **Service messages:** operators can post network-wide or per-station messages (`/ata_message`), and each display can have its own. PIDS, CIS and concourse boards show them in a strip along the bottom.
- **Top-aligned departure boards:** tall boards now start under the top bezel instead of floating in the middle. Each display can switch back to centred.
- **Creative tabs:** four dedicated tabs. No block or item ids changed.

### What 1.1 adds

- **Seating:** right-click a bench or shelter seat to sit. Each block seats two.
- **Editable information:** the information case, information pillar and bus timetable case have an editor with a heading, body lines, alignment and accent colour.
- **Station sign v2:** styles, accent stripe, platform badge, arrow, route badges and an **automatic station name** mode that shows the MTR station the sign stands in.
- **Live displays:** platform CIS/PIDS and concourse departure boards read MTR's own client-side arrival data. They come in three styles (European Amber, European Modern, Dutch Modern), join into bigger screens, and show "No station linked" or "No departures currently available" instead of inventing data. See [docs/MTR_INTEGRATION.md](docs/MTR_INTEGRATION.md).
- **Announcements:** speakers play chimes, with the sentence shown in the action bar. Resource-pack voice packs can add spoken fragments ([docs/VOICE_PACKS.md](docs/VOICE_PACKS.md)). There is no text-to-speech and nothing is fetched from the network.
- **Clocks:** show the in-game time of day.
- **Performance:** no block entity ticks. Displays only lay out about once a second while visible, and MTR data is shared through bounded caches. In-game test steps and the stress scenario are in [docs/LIVE_TESTING.md](docs/LIVE_TESTING.md).

### Editable signs

The station name signs, hanging signs, directional signs, platform number signs and bus stop signs have editable text. Right-click one with an empty hand to edit it. The editor has buttons for inserting wayfinding arrows (← → ↑ ↓ ↖ ↗).

Station name, hanging and directional signs placed side by side with the same facing **join into one wide sign**. The frame is continuous and the text is centred across the whole row.

### Placement rules

- **Signs, benches, cases, lamps and pillars** face you when placed.
- **Edges, curbs, slopes and ramps** point the way you are looking. A platform edge's coping, a curb's road face, a canopy edge's fascia, and the low end of a slope or ramp all end up on the side you are facing.
- **Wall-mounted pieces** (information case, shelter glass wall, shelter seat) attach to the side you are looking at.
- **Beams, gantries, roof supports and masts** run along the direction you are looking.
- **1.3 styled pieces** (viaduct, enclosure, wind screen, fascia, fence, handrail, junction, curved edge): right-click with an empty hand to cycle the style. Holding anything never changes the style.

### Composing canopies

Canopy plates sit at the bottom of their block, so they rest directly on columns, beams and roof supports placed below.

- **Slopes:** a *lower* piece rises half a block; an *upper* piece in the next block completes the rise. The next lower piece goes one block up.
- **Waves:** flat → *rise* → *crest* → *rise* placed facing the opposite way → flat. Together these make a smooth wave 3 blocks long.

### MTR compatibility

- Platform pieces are full-height blocks, the same height as MTR's platform blocks, so trains line up with them normally.
- **Train doors:** the platform edges (straight, angled and curved), platform paving and tactile paving count as platforms for MTR's door check, so train doors open beside them just as beside MTR's own platform blocks. Ramps and bus curbs don't count; keep an MTR platform block (or PSD/APG) within one block of the doors there. Details are in [docs/MTR_INTEGRATION.md](docs/MTR_INTEGRATION.md#platform-blocks-and-train-doors).
- MTR still defines platforms, stations and where trains stop with its own tools. Aurelia doesn't change MTR's rail, platform or station logic.
- Catenary pieces are decorative and can be combined with MTR's own catenary/wire system.
- The addon uses no mixins and does not modify MTR.

## Building from source

```sh
./gradlew clean build      # JAR in build/libs/
python3 tools/generate_assets.py   # regenerate textures/models/blockstates/loot/recipes/lang (needs Pillow)
python3 tools/verify_assets.py     # check every registered block has its assets and data
./gradlew runServer / runClient    # dev runs with MTR loaded
```

All textures and models are generated from code in `tools/generate_assets.py`, so the art is reproducible and original.

## Future work

- Other Minecraft versions and loaders (Forge/NeoForge) and backports
- Further architectural families such as lifts, escalators, underpasses and platform screen doors
- Trip planning on the terminal (needs MTR's server-side route finder)
- Connected textures, more colourways

## Credits

- Minecraft Transit Railway by Jonathan Ho and contributors (MIT). This addon depends on MTR but doesn't include or copy any MTR code or assets.
- Fabric Loader and Fabric API by the FabricMC team.
- The architectural inspiration comes from modern European transit design in general. No real station, operator identity or protected branding is reproduced.
- All textures, models and code in this repository are original work by the Aurelia Transit Architecture contributors.

## License

MIT. See [LICENSE](LICENSE).
