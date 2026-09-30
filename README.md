# Aurelia Transit Architecture

A modular architecture kit for [Minecraft Transit Railway](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) (MTR), for building original modern European railway stations and bus stops, from a small local halt to a large central station.

The visual language is understated and contemporary, drawing loosely on Dutch, German, Swiss and neighbouring station design: galvanised and dark steel, light concrete, glass, muted blue/green accents and tactile yellow. It doesn't reproduce any real station and has no operator branding or logos. The pieces are meant to be combined.

## Supported environment (V1)

| Component | Version |
|---|---|
| Minecraft (Java Edition) | **1.20.1** only |
| Mod loader | **Fabric** only (Fabric Loader ≥ 0.15.0) |
| Fabric API | ≥ 0.92.0+1.20.1 |
| Minecraft Transit Railway | **4.x** for Fabric 1.20.1 (built and tested against `4.0.5+1.20.1`) — **required** |
| Java | 17+ |

Forge, NeoForge and other Minecraft versions are **not supported in V1**. Additional versions and loaders are planned as future work.

## Installation

1. Install Fabric Loader for Minecraft 1.20.1.
2. Put these in your `mods` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1
   - [Minecraft Transit Railway](https://modrinth.com/mod/minecraft-transit-railway) 4.x, **Fabric 1.20.1** build
   - `aurelia-transit-architecture-1.0.0+mc1.20.1-fabric.jar`
3. Start the game. Every piece is in the **Aurelia Transit Architecture** creative tab.

Install the mod on both the server and the clients.

## V1 feature families (45 blocks)

| Family | Pieces |
|---|---|
| **Platforms** (7) | Light and dark platform paving, tactile warning paving, platform edge, platform edge with warning line, platform ramp (lower and upper halves) |
| **Signage** (6) | Freestanding and hanging station name signs, platform number sign, directional sign, information case, sign pole |
| **Furniture** (6) | Perforated steel bench, timber slat bench, waste bin, bollard, platform lamp, information pillar |
| **Architecture** (15) | Square and round steel columns, structural beam, branching roof support, framed glass wall, glass panel, glass barrier, flat canopy, canopy edge, sloped canopy (lower and upper), wave canopy rise and crest, canopy skylight, canopy light panel |
| **Catenary** (4) | Catenary mast, cantilever, gantry beam, insulator (decorative) |
| **Bus** (7) | Bus stop sign, timetable case, shelter glass wall, shelter roof, shelter seat, boarding curb (full height and low) |

### Editable signs

The station name signs, hanging signs, directional signs, platform number signs and bus stop signs have editable text. Right-click one with an empty hand to edit it. The editor has buttons for inserting wayfinding arrows (← → ↑ ↓ ↖ ↗).

Station name, hanging and directional signs placed side by side with the same facing **join into one wide sign**. The frame is continuous and the text is centred across the whole row.

### Placement rules

- **Signs, benches, cases, lamps and pillars** face you when placed.
- **Edges, curbs, slopes and ramps** point the way you are looking. A platform edge's coping, a curb's road face, a canopy edge's fascia, and the low end of a slope or ramp all end up on the side you are facing.
- **Wall-mounted pieces** (information case, shelter glass wall, shelter seat) attach to the side you are looking at.
- **Beams, gantries, roof supports and masts** run along the direction you are looking.

### Composing canopies

Canopy plates sit at the bottom of their block, so they rest directly on columns, beams and roof supports placed below.

- **Slopes:** a *lower* piece rises half a block; an *upper* piece in the next block completes the rise. The next lower piece goes one block up.
- **Waves:** flat → *rise* → *crest* → *rise* placed facing the opposite way → flat. Together these make a smooth wave 3 blocks long.

### MTR compatibility

- Platform pieces are full-height blocks, the same height as MTR's platform blocks, so trains line up with them normally.
- MTR still defines platforms and stations with its own tools. Aurelia blocks are decorative and don't change MTR's rail, platform or station logic.
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

## Future work (not in V1)

- Other Minecraft versions and loaders (Forge/NeoForge) and backports
- Station-name signs driven by MTR station data, PIDS integration
- Further architectural families such as stairs, lifts, underpasses and platform screen elements
- Seating interaction, connected textures, more colourways

## Credits

- Minecraft Transit Railway by Jonathan Ho and contributors (MIT). This addon depends on MTR but doesn't include or copy any MTR code or assets.
- Fabric Loader and Fabric API by the FabricMC team.
- The architectural inspiration comes from modern European transit design in general. No real station, operator identity or protected branding is reproduced.
- All textures, models and code in this repository are original work by the Aurelia Transit Architecture contributors.

## License

MIT. See [LICENSE](LICENSE).
