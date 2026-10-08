# Aurelia Transit Architecture

A building kit for [Minecraft Transit Railway](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) (MTR): modern European railway stations and bus stops, from a small halt to a large central station.

The look is understated: steel, light concrete, glass, muted blue/green accents and tactile yellow. No real stations, operators or logos are copied. The pieces are meant to be combined.

## Supported environment

| Component | Version |
|---|---|
| Minecraft (Java Edition) | **1.20.1** only |
| Mod loader | **Fabric** only (Fabric Loader ≥ 0.15.0) |
| Fabric API | ≥ 0.92.0+1.20.1 |
| Minecraft Transit Railway | **4.x** for Fabric 1.20.1 (built and tested against `4.0.5+1.20.1`) — **required** |
| Java | 17+ |

Forge, NeoForge and other Minecraft versions are not supported.

## Installation

1. Install Fabric Loader for Minecraft 1.20.1.
2. Put these in your `mods` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1
   - [Minecraft Transit Railway](https://modrinth.com/mod/minecraft-transit-railway) 4.x, **Fabric 1.20.1** build
   - `aurelia-transit-architecture-1.5.0+mc1.20.1-fabric.jar`
3. Start the game. The pieces are in five creative tabs: **ATA Architecture**, **ATA Wayfinding**, **ATA Passenger Equipment**, **ATA Bus / Street Transit** and **ATA Glass**.

Install the mod on both the server and the clients.

## Feature families (118 blocks)

| Family | Pieces |
|---|---|
| **Platforms** (12) | Platform screen panel and doorway, drop-barrier and boarding-step edges (move while a train stands), light and dark paving, tactile warning paving, platform edge (plain, warning line, angled / curved), platform ramp |
| **Passenger information** (12) | CIS and PIDS boards (also hanging), concourse board (all live from MTR), speakers, clocks, information terminal and kiosk |
| **Signage** (9) | PSD text panel, stand-back sign, train composition board (typed by hand), station name signs, platform number sign, directional sign, information case, sign pole |
| **Furniture** (6) | Perforated steel bench, timber slat bench, waste bin, bollard, platform lamp, information pillar |
| **Architecture** (17) | Steel columns, beam, roof support, framed glass wall, glass panel, glass barrier, canopies (flat, edge, sloped, wave, corner cap), skylight, canopy light |
| **Catenary** (4) | Catenary mast, cantilever, gantry beam, insulator (decorative) |
| **Elevated** (11) | Noise barrier, viaduct column and beam, brace, station stair, stair enclosure, wind screen, platform fascia, station fence, utility run, under-deck light |
| **Wayfinding** (6) | Entrance pylon, wall and hanging wayfinding signs, exit sign, street / landmark blade, station information board |
| **Accessibility** (7) | Lift status panel, pictogram sign, tactile guidance paving and junction, help point, boarding marker, handrail |
| **Station equipment** (4) | Fare gate (charges MTR fares), card reader, booth window, CCTV camera |
| **Bus** (8) | Bus stop sign, timetable case, shelter glass and roof, shelter seat, bus curb (full and low), e-paper arrival board |
| **Glass** (22) | Clear float, low-iron, grey / bronze / blue tinted, reflective, frosted, fritted and wired glass (each a block and a pane), glass brick, curtain wall glass, structural glass fin, glass floor panel |

### What 1.5 adds

- **ATA Glass tab:** realistic architectural glass, light on the game: no block entities, no ticking, vanilla-style culling.
- **Shorter, plainer tooltips.**

### What 1.4 adds

- **Station suffixes** (`/ata_suffix`): "Aurelia Airport" while MTR keeps "Aurelia".
- **Per-exit settings** on the information board; **calling-point times** on PIDS/CIS.
- **Lift status panel, noise barriers, station equipment, working fare gates.**
- **Platform screen doors**, **drop-barrier and boarding-step edges**, and hand-typed PSD, stand-back and composition signs. See [CHANGELOG.md](CHANGELOG.md) and [docs/DESIGN_1.4.md](docs/DESIGN_1.4.md).

### What 1.3 adds (Urban Infrastructure)

- **Elevated railway kit:** columns, beams, braces, stairs, enclosures, wind screens, fascias, fencing, utility runs and lighting. Each piece is **one item**: right-click with an empty hand to change its style. See [docs/URBAN_INFRASTRUCTURE.md](docs/URBAN_INFRASTRUCTURE.md).
- **Station information board:** one wall board with five views: trains this side, platform, service changes, transfers, street / exits.
- **Several service messages at once**, each with an id and severity; boards rotate through them. `/ata_message add | remove | clear | list`.
- **Manual station association** ("Station: Auto / Manual") for entrance pylons, exit signs, terminals, kiosks and boards.
- **Terminal polish:** joined terminals form one screen, nearby help points show on the Accessibility page, and terminal text is translatable.
- **Accessibility:** tactile junctions, handrails, ramp and assistance boarding markers.
- **Angled and curved platform edges** that still open MTR train doors.

### What 1.2 adds (City Wayfinding)

- **One shared wayfinding model:** station, line badges, direction, service type, code, platform, exits, street labels, transfers, second language and pictogram. MTR fills in station, lines and exits; anything set by hand wins. See [docs/WAYFINDING.md](docs/WAYFINDING.md).
- **Multilingual layout:** single, side by side or stacked.
- **Service messages** (`/ata_message`), network-wide or per station, shown in a strip on PIDS, CIS and concourse boards.
- **Top-aligned boards**, with a centred option.
- **Creative tabs:** four dedicated tabs. No ids changed.

### What 1.1 adds

- **Seating:** right-click a bench or shelter seat to sit; each seats two.
- **Editable information** cases, pillars and timetable cases (heading, lines, alignment, accent colour).
- **Station sign v2:** styles, accent stripe, platform badge, arrows, route badges and an automatic station name.
- **Live displays:** CIS/PIDS and concourse boards read MTR's arrival data, in three styles, and join into bigger screens. See [docs/MTR_INTEGRATION.md](docs/MTR_INTEGRATION.md).
- **Announcements:** speakers play chimes with the text in the action bar; voice packs are possible ([docs/VOICE_PACKS.md](docs/VOICE_PACKS.md)). No text-to-speech, no network use.
- **Clocks** show the in-game time.
- **Performance:** no block entity ticks; displays lay out about once a second while visible. See [docs/LIVE_TESTING.md](docs/LIVE_TESTING.md).

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
- **Manual station picker:** lists only the stations MTR has sent to your client, which are the ones near you (in testing, a station ~100 blocks away was listed and one ~210 blocks away was not). Once picked, the link is kept when you move away.

### Fixed in 1.3.1

- Terminals and boards with a manual station name now show that MTR station's messages.
- Small e-paper boards scale their text instead of cutting it off.
- Joined hanging signs and displays use at most two rods.
- The station information board editor shows the direction field.

## Building from source

```sh
./gradlew clean build      # JAR in build/libs/
python3 tools/generate_assets.py   # regenerate textures/models/blockstates/loot/recipes/lang (needs Pillow)
python3 tools/verify_assets.py     # check every registered block has its assets and data
python3 tools/check_zfighting.py   # flag model faces that would flicker (z-fighting)
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
