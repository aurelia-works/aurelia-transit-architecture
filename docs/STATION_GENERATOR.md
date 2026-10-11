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

## Station Wrench

The **Station Wrench** (Passenger Equipment tab, operators only) does the same without typing commands. It remembers one description.

| With the wrench | What it does |
|---|---|
| Right-click a block | Plans the wrench's description on top of that block, facing the way you look: rail level is the top of the block, and the footprint starts at the block, centred on it. The summary and preview appear as with `plan`. |
| Sneak + right-click a block | Builds the pending plan (`confirm`). Never approves terrain changes. |
| Left-click a block | Cancels the running build or discards the plan. The wrench never breaks blocks. Left-click in the air does nothing. |
| Right-click the air | Shows the preview again. |
| Sneak + right-click the air | Opens the wrench screen: edit the description or pick a preset, then Plan in front of me, Show preview, Build, **Build, replace terrain** (the wrench's only way to approve terrain changes), Cancel or Undo last build. Closing the screen saves the description. |

Every wrench action checks on the server that the player is an operator (level 2). A non-operator's clicks and screen packets do nothing.

**Presets.** Eight buttons on the wrench screen fill in a complete description, one per building look: Utrecht, Amsterdam, St Pancras, Antwerp, Frankfurt, Hamburg, Munich and Grand Central. Each is a 2-4 track station with a building, a footbridge and a name (hover a button to read it). A preset only fills the text field; nothing is planned until you press Plan. They live in `StationPresets`, and a unit test keeps each one fully understood, free of design-rule breaks and buildable.

**Guide book.** An operator gets the *Station Generator Guide*, a written book, the first time they join as an operator (a command tag remembers it, so it is given once). `/ata_guide` (operators) gives another copy.

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
| Building | `station building`, `ticket hall`, `concourse`, `furnished`, `centraal`, `hauptbahnhof`, `no station building` | none |
| Building look | `utrecht`/`rotterdam` (modern glass), `amsterdam`/`brick`, `st pancras`/`london`/`gothic`, `antwerp`/`marble`, `frankfurt`/`sandstone`, `hamburg`/`clinker`, `munich`/`brutalist`, `grand central`/`union station`/`washington` (Beaux-Arts). Landmarks (`st pancras`, `antwerp`, `grand central`...) also ask for a building. | the style's own look |
| Footbridge | `footbridge`, `bridge`, `overbridge`, `skywalk`, `no footbridge` | none |
| Terminus | `terminus`, `terminal`, `kopfbahnhof` (asks for a head building; a footbridge is dropped with a warning) | through station |

Out-of-range values are clamped with a warning. A contradiction gives a warning and keeps the first value (an explicit "no roof" always wins). Words it did not understand are listed as "Ignored". `underground` and `elevated` are not built yet: the station is built at ground level and a warning says so.

## Station buildings and footbridge

A building stands on the street side, 9 deep, over the middle of the platforms: an entrance ramp from the street, a ticket hall with a fare gate line (a wide gate in the middle), a ticket office window, MTR ticket machines, an information kiosk, benches, a clock over the entrance and departure boards on the end walls. A stair in the hall leads to the upper floor and the footbridge, whose stairs go down to every platform it serves.

- **Upstairs waiting room** (buildings of three bays or more, with furniture): the end bay away from the hall stair is closed off by a framed glass partition with one doorway. Inside are benches along both long walls, bins in the far corners, a departure board on the end wall and a hanging clock. It is left out when that bay would touch the stair or the footbridge door.
- **Terminus** (`terminus`): the tracks end at buffer stops (steel beams across each track's clearance, just past the last rail node) in front of a head building 12 deep across the whole width. Its floor runs level from the platform ends; the platform side is an open arcade under a lintel with clerestory windows. Two rows in from the buffers a gate line crosses every platform (a wide gate in the middle of each, glass barriers behind the tracks); departure boards and a clock hang over it facing the concourse. The concourse has the ticket office, MTR ticket machines, the kiosk and benches, a 3-wide street entrance with ramps, and roof lights. Every platform, islands included, is reached step-free through the gates, so a terminus needs no footbridge, and it may have a hall roof (a train shed) over the platforms. Ramps are only built at the open end.
- **Departure screens** (every furnished station, with or without a building): one screen point on platforms shorter than 48, two (at a quarter and three quarters of the length) on longer ones. Islands four or more wide get two screens back to back, one facing each track; side platforms get one at the back facing the track. Screens keep 2 blocks clear of footbridge stairs and landings. The first point on each platform also gets a help point.


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
   - Access: a walk from beside the station (u < 0 or past the last column; the tracks run on past both ends, so nobody walks around them) to every boarding edge cell. Ramps are climbed only along their facing. Each platform is graded by its worst boarding cell: step-free, a one-block step (fails if step-free access was asked for), only by walking across a track (a warning), only by stairs (a warning, since there are no lifts yet: platforms reached over the footbridge), or unreachable (fails). Without a footbridge, island platforms are reached across a track (a warning).
   - Name signs on every platform when a name is given (a warning if one did not fit), none otherwise.
   - No floating parts: every block is joined through faces or edges to a block standing on the support layer.
   - Overall size.
   - A failed check stops the build like a design rule. The summary prints one line with the counts and the measured faces and size, then each warning and failure.
6. **Site check** (`SiteSurvey` over `WorldSiteProbe`):
   - Planned cells must be air.
   - Track envelopes and platform headroom must be free of anything solid or liquid. Grass and flowers there are left alone.
   - Platforms, ramps and tracks need sturdy ground below.
   - Grass, water, logs or stone in a planned cell, obstructions, and missing ground all need approval. With approval they are replaced, cleared, or filled with stone bricks down to sturdy ground (at most 4 deep).
   - These are refused even with approval: block entities (chests, signs), unbreakable blocks, unloaded chunks, the world height limit, gaps deeper than 4, and an existing MTR rail node anywhere other than a planned rail node cell or a track's clearance.
   - An existing MTR rail node is never replaced or removed. One already in a planned rail node cell is kept, and one in a track's clearance is left alone, so a station can be planned onto a line that is already connected.
7. **Build** (`StationBuildJob`):
   - At most 64 changes per job per tick, and 256 per tick across all jobs.
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
- No new blocks, block entities or mixins. The wrench adds one item, one client-to-server packet (hand, action and a description of at most 256 characters, all bounded on read and handled on the server thread) and one client screen.
- The preview is server-side particles sent only to the planning player (at most about 600 per refresh, every 10 ticks, for 30 s), and it changes nothing.
- Pending plans, jobs and undo logs live in memory: a server restart forgets them, and a player who leaves loses their pending plan (a running build continues).

## Known limitations

- Ground-level stations only: no underground or elevated. Straight stations along one axis. No curves, no catenary, no platform screen doors, no lifts.
- Buildings and footbridges are reached by stairs only: no lifts, so platforms reached over a footbridge get a "not step-free" warning. A hall roof cannot be combined with a footbridge or a building beside the tracks yet (the parser falls back to canopies); a terminus can have one.
- The building and footbridge roofs count as shelter in the checks, but the design rules do not check their spans; they rest on the building and footbridge walls.
- Rail nodes for tracks along X (`facing=true`) match the live-tested release-check scene. Nodes for tracks along Z (`facing=false`) follow from MTR's code and are covered by game tests, but not yet by a live MTR connection.
- The preview particles were not seen in a client in this version (headless tests only check that planning and preview change nothing).
- Undo history is one build per player, in memory.
