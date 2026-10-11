# Smart Station Generator (first version)

`/ata_station` turns a short English description into a railway station built from ATA blocks. It runs fully locally and deterministically: it uses a fixed vocabulary, with no network, no language model and no randomness. The same description in the same place always gives the same station.

Operators only (permission level 2), like `/ata_message`.

## Use

| Command | What it does |
|---|---|
| `/ata_station plan <description>` | Plans a station in front of you (rail level = your feet, tracks run the way you face, the footprint starts 2 blocks ahead and is centred on you). Prints a summary and shows a 30 s particle outline. Nothing is placed. |
| `/ata_station preview` | Shows the outline again. |
| `/ata_station confirm` | Builds the pending plan where it was planned, 64 blocks per tick. Refuses if any terrain would change. |
| `/ata_station confirm replace_terrain` | Builds and approves the terrain changes the summary listed. This literal token is the **only** way to approve them. The description never can. |
| `/ata_station cancel` | Stops a running build (what is built stays) or discards the pending plan. |
| `/ata_station undo` | Reverts your last build, batched. Cells changed since the build are left alone. |
| `/ata_station status` | Pending plan, job progress, undo. |

Examples:

- `two track dutch station, island platform, 64 long, glass canopy, named Leidsche Rijn`
- `small single track s-bahn halt, no roof`
- `4 track metro station, 96 m long, hall roof, terrazzo floors`

## What the description understands

| Field | Words | Default |
|---|---|---|
| Tracks (1-4) | `3 tracks`, `two-track`, `single/double track`, `two island platforms` (4 tracks) | 2 |
| Platforms | `island`, `side`, both (an odd last track then gets a side platform) | side for 1-2 tracks, island for 3-4 |
| Length (16-160) | `64 blocks long`, `100 m`, `length 80`, `short/medium/long/very long`, `small` (32), `large` (96, 4 tracks) | 48 |
| Width (3-8) | `5 wide`, `width 6`, `wide`, `narrow` | 4 side, 6 island |
| Style | modern; dutch/utrecht/leidsche rijn; german/s-bahn/hamburg; metro/u-bahn | modern |
| Roof | `no roof`/`open`; `canopy`/`covered`; `hall`/`train shed` | canopy |
| Roof material | `glass`, `timber`/`wooden roof`, `steel` | style's own |
| Floor | `light/dark paving`, `concrete`, `terrazzo`, `granite` | style's own |
| Access | `step-free` (ramps at both platform ends), `no ramps`; `no tactile` | ramps and tactile edges on |
| Other | `no benches`, `no rail nodes`, `named "X"` / `called X` | benches, rail nodes, no name signs |

Out-of-range values are clamped with a warning. A contradiction gives a warning and keeps the first value (an explicit "no roof" always wins). Words it did not understand are listed as "Ignored". `underground` and `elevated` are not built yet: the station is built at ground level and a warning says so.

## How a plan is made

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
5. **Site check** (`SiteSurvey` over `WorldSiteProbe`):
   - Planned cells must be air.
   - Track envelopes and platform headroom must be free of anything solid or liquid. Grass and flowers there are left alone.
   - Platforms, ramps and tracks need sturdy ground below.
   - Grass, water, logs or stone in a planned cell, obstructions, and missing ground all need approval. With approval they are replaced, cleared, or filled with stone bricks down to sturdy ground (at most 4 deep).
   - These are refused even with approval: block entities (chests, signs), unbreakable blocks, unloaded chunks, the world height limit, gaps deeper than 4, and an existing MTR rail node anywhere other than a planned rail node cell or a track's clearance.
   - An existing MTR rail node is never replaced or removed. One already in a planned rail node cell is kept, and one in a track's clearance is left alone, so a station can be planned onto a line that is already connected.
6. **Build** (`StationBuildJob`):
   - At most 64 changes per job per tick, and 256 per tick across all jobs.
   - Order: clears top-down, fills bottom-up, then floors, edges, ramps, rail nodes, columns, roof, fixtures, and signs last.
   - Every cell is re-checked just before it is written. A cell that changed since the survey stops the job, and nothing more is placed.
   - A chunk that unloads pauses the job (chunks are never loaded on purpose); after 10 s the job stops.
   - Name sign text goes to each joined row's owner.
7. **Undo** walks the log backwards. It only touches cells that still hold the block the build placed. Grass or mycelium that vanilla turned into dirt under the station is put back once the block above it is gone, unless that cell was changed to something other than dirt.

## Tracks and MTR

The generator places **unconnected MTR rail nodes** at both platform ends of every platform track. The summary prints their coordinates. Connect them with an MTR **Platform Rail Connector**, so MTR's platform matches the built platform, then extend the line with normal rail connectors.

The generator never creates rail connections itself, because those live in MTR's simulation core. Undo leaves a node alone once you have connected it (MTR sets `is_connected`), since removing it with a block change would orphan MTR's rail data. Without MTR loaded, the nodes are skipped and the summary says so.

## Performance and safety

- One server tick listener for the whole feature, which returns at once when no build or preview is running.
- No new blocks, items, block entities, packets, client code or mixins.
- The preview is server-side particles sent only to the planning player (at most about 600 per refresh, every 10 ticks, for 30 s), and it changes nothing.
- Pending plans, jobs and undo logs live in memory: a server restart forgets them, and a player who leaves loses their pending plan (a running build continues).

## Known limitations

- Ground-level stations only: no underground, elevated, footbridge or underpass. Island platforms are reached at grade from their ramps; there is no step-free route across the tracks.
- Straight stations only, along one axis. No curves, no catenary, no platform screen doors, no stairs or lifts.
- Rail nodes for tracks along X (`facing=true`) match the live-tested release-check scene. Nodes for tracks along Z (`facing=false`) follow from MTR's code and are covered by game tests, but not yet by a live MTR connection.
- The preview particles were not seen in a client in this version (headless tests only check that planning and preview change nothing).
- Undo history is one build per player, in memory.
