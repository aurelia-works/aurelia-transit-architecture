# Testing the live systems in game

## Blocks (creative tab, Passenger information)

Platform CIS board, hanging platform CIS board, platform PIDS, hanging platform PIDS, concourse departure board, wall speaker, ceiling speaker.

Displays with the same facing that touch each other **join into one bigger screen** (like joined signs); the top-left block (from the viewer's side) owns the configuration and draws the whole screen. Right-click any block of it to configure: style (European Amber, European Modern, Dutch Modern), rows, clock, calling-at lines, page time, and the station (Automatic, or pick a station and platforms; no platform ticked = all platforms).

Suggested sizes: CIS 4-6 wide x 1 high, PIDS 6 wide x 1-2 high, concourse board 6-8 wide x 3-4 high. Text scales to the screen and squeezes or scrolls long destinations.

## Quick checklist

1. Build a station in MTR with a route that has a depot/siding running trains. Stand on a platform.
2. Place a platform PIDS on the wall or a hanging PIDS above the platform (wide enough), leave the station on Automatic. It should list the next trains of the platform next to it. A station-wide concourse board lists all platforms and pages through them every few seconds when it has more trains than rows.
3. With no trains: "Welcome to <station>" / "No departures currently available". With no station nearby: "No station linked".
4. Place a speaker within ~16 blocks of the platform. When a train is about 40 s away you hear the chime and see the announcement text in the action bar (no voice pack loaded). `/aurelia_live test approaching` plays a sample immediately wherever you stand.
5. Walk out of range of the speaker: nothing plays. Two speakers at the same station: only one plays each announcement (the closest in range).

## Client commands

- `/aurelia_live stats` prints provider cache sizes, engine queue and the debug counters.
- `/aurelia_live debug true|false` turns the counters on or off (also `-Daurelia.live.debug=true`).
- `/aurelia_live test <safety|delay|terminating|approaching|standing>` plays a sample announcement at your position.
- `/aurelia_live voices` lists loaded voice packs.
- `/aurelia_live edge` (1.4): for the block under the crosshair, what a drop-barrier / boarding-step edge sees (station, nearest platform, its trains, standing now).
- `/aurelia_live routes` (1.4) lists MTR's simplified routes and the arrivals MTR reports for their platforms (route, departure index, platform, seconds to arrival): the data calling-point times are matched from. It adds those platforms to MTR's poll only when run.

## Stress scenario (about 50 displays, 20 speakers)

Op only (permission level 2), in creative/cheats:

```
/aurelia_live_stress place 50 20
/aurelia_live debug true
/aurelia_live stats
...look around, walk the grid for a minute...
/aurelia_live stats
/aurelia_live_stress clear
```

`place <displays> <speakers>` fills a grid (10 columns, every second block so nothing joins) 3+ blocks in front of you for displays and behind you for speakers, cycling the five display kinds and both speaker kinds; all use Automatic association. Run it inside an MTR station (or with the defaults changed through a screen) so there is data to draw. `clear` removes every live display and speaker within 24 blocks.

What to watch: `board_rebuilds` should grow by roughly one per visible display per second (faster only while a long destination scrolls), `provider_refresh` by one per distinct block per 2 s, and `arrival_requests` by one per distinct platform set per 2 s, not per display. `board_frames` counts draws and is expected to be large. Frame rate should stay close to baseline; with no speaker loaded `engine_updates` stays at zero.

# 1.2 in-game checklist: wayfinding, accessibility, street and bus blocks

## Board alignment fix (live displays)

1. Place a 6 wide x 3 high concourse board in a station (or any tall PIDS). The header and first departure row must start at the **top** under a small bezel margin; unused height stays empty at the **bottom**. Boards saved by 1.1 load as Top.
2. Right-click the board: the left column ends with "Align content: Top". Click it to switch to Centre (the 1.1 look, content centred vertically), press Done, and the board updates. Switch back to Top.
3. A content-filling board (for example a 6 x 1 CIS) looks the same in both modes. The idle text ("Welcome to ...", "No station linked") follows the same anchoring.

## New blocks (creative tabs: Wayfinding, Passenger equipment, Bus / Street)

Signs with content are edited with an empty-hand right-click: one editor for all of them that shows only the fields the sign uses (station or stop name and Auto/Manual, second language and layout, station code, exit, street or landmark, transfer note, direction/destination, arrow, service type, symbol, accent colour, and up to six line badges with label and colour). Leave the station on Auto to use the MTR station next to the block. The preview uses the same layout as the world. Changes are sent when you press Done.

| Block (tab) | Try this |
|---|---|
| Station Entrance Pylon (Wayfinding) | Place it on the ground: it is two blocks tall and needs a free block above. Break either half in survival: exactly one item drops. In creative no extra item drops. Station name, line badges and the exit label show on both faces. |
| Wall Wayfinding Sign, Hanging Wayfinding Sign (Wayfinding) | Place several side by side: they join into one wide panel owned by the leftmost block; right-click any of them to edit the row. The hanging sign is readable from both sides. |
| Exit Sign (Wayfinding) | Set exit "A"; with Auto station the destinations of MTR exit A appear if the station defines them. Hanging, double sided, joins. |
| Street Sign (Bus / Street) | A pole with a side blade: street, landmark or connection text. |
| Pictogram Sign (Passenger equipment) | Defaults to the accessible-route symbol; change Symbol in the editor for lift, stairs, exit, help point and others. |
| Tactile Guidance Paving (Passenger equipment) | The bars run the way you were looking. Next to an MTR track it must open train doors like the other tactile paving. |
| Help Point (Passenger equipment) | Lit wall unit; right-click shows a line on the action bar. |
| Boarding Marker (Passenger equipment) | Flat plate, no collision: walk over it. Right-click cycles door / accessible / wait. It breaks when the block under it is removed. |
| Bus E-Paper Board (Bus / Street) | Wall-mounted, joins side by side (2-3 wide reads best). Grey paper look, no glow. Shows the stop name, then up to three next arrivals of the nearest MTR station; with no departures "No departures currently available", with no station nearby "No stop linked". |

## E-paper board refresh

The board updates only every 15 to 30 seconds (each board has its own offset), so times may lag by up to that long; it never scrolls or pages. Check that nearby boards do not all change in the same frame and that frame rate is unaffected with a dozen of them in view. Saving it in the editor updates it immediately.

## Performance checks

- Stand 40+ blocks away: no text is drawn for any of the new signs. Walk behind a wall sign or e-paper board: nothing is drawn from behind.
- Place about 50 mixed wayfinding blocks in view: frame rate stays close to baseline. The signs do not tick; they re-check their station data once a second and rebuild only when it changes.

## Passenger Information Terminal and Kiosk (Passenger equipment tab)

1. Place a Passenger Information Terminal (wall, joins side by side; 2 wide reads best) and a Kiosk (freestanding) inside an MTR station. The idle face is static: station name, line badges, "Touch for information". No animation; it changes only when the station or the terminal's data changes.
2. Right-click with an empty hand: the terminal UI opens (the game keeps running). Sneak + right-click with an empty hand opens the wayfinding editor instead (station name override, code, lines, street, transfers); saving updates the idle face.
3. Tabs: **Home** (name, clock, next departures, active service message), **Departures** (same rows and order as a station-wide PIDS; arrows or scroll wheel page through more than fit), **System map** (line strips with stops; the current station is highlighted, transfers have a ring; page through lines), **Station** (platforms, exits with destinations, transfers, street/landmark), **Service info** (station and network messages set with the service message command; "No service notices" when none), **Accessibility** (only what is configured near the terminal; otherwise "No accessibility information has been provided for this station").
4. No station nearby: "No station linked" / empty map text, no errors. Remove the terminal block while the UI is open: the screen closes.
5. Performance: departures refresh about once a second while the UI is open, map and station info about every 10 seconds; nothing runs when it is closed.

## 1.3 checklist

1. **Joined terminals:** place two Passenger Information Terminals side by side, same facing. They show one continuous screen with no vertical line at the joint, and the outer bezel stays on both ends. Break one: the other gets its full bezel back at once. Try three wide. Look from the front and at an angle, with and without shaders.
2. **Several service messages:** `/ata_message add network notice Engineering work after 22:00`, `/ata_message add station "<your station>" severe Severe delays`, `/ata_message add station "<your station>" notice Platform 2 closed`, `/ata_message add network info Bus 37 diversion`. Boards show one message at a time in the strip: severe first, then the station notice, then the network ones. Each stays about 5 s, and order and timing are the same on every board. Add a long message (90 characters): it holds, scrolls smoothly to its end, holds, then the next message starts from its beginning. `/ata_message list` shows ids. `/ata_message remove <id>` removes only that message. `/ata_message clear` removes the strip and the rows get the space back. One message never rotates. Restart the server: the messages and their ids are still there.
3. **Terminal service page:** lists every applicable notice grouped Station / Network with severity marks. Home rotates them with an "n/N" counter.
4. **Manual station:** in an overlapping or stacked station, sneak + right-click a terminal (or right-click a sign or e-paper board) and press **Station: Auto**. Pick another station and Done: the idle face, departures and station page follow the pick. Switch back to Auto: the nearest station again.
5. **Help points:** place two standalone Help Points within 24 blocks of a terminal. Its Accessibility page lists them nearest first, with their distance (after up to 10 s).
6. **Station information board:** place one (2–3 wide) and cycle the five views in its editor. Service changes follows `/ata_message` within a second. Exits lists the MTR exits.
7. **Elevated kit:** build a short viaduct: columns, crossbeams, girders, stringers and braces. Then a platform with platform support beam, fascia, wind screens (lower + upper), knee braces under a canopy, fences, handrails, glass balustrade, ramp rail, utility runs and under-deck lights. Check that each item cycles its styles with an empty-hand right-click and that a beam switches steel/concrete with sneak. Look for model gaps, fence/handrail joins at corners and T-junctions, and the stair in every shape inside an enclosure.
8. **Curved edges:** line an MTR platform rail on a 45° or curved track with angled/curved edge pieces. Doors open beside them, exactly as with straight edges.
9. **Inventory:** the Architecture tab gained 11 items (10 elevated + the curved edge), Wayfinding 1, Passenger equipment 2. The entrance pylon icon shows the whole pylon.

## Automatic checks without playing (1.5 harness)

One command runs everything that can be checked without a person in the game:

    tools/harness.sh

It runs six steps and prints PASS or FAIL for each, then a one-line verdict. Every step runs even if an earlier one fails, and the log of each is in `build/harness/`. Add `GRADLE_ARGS=--offline` in front if Gradle should not touch the network.

1. **Assets up to date.** `generate_assets.py` is run and must change nothing (stale checked-in models or textures fail here).
2. **`verify_assets.py`.** The Java block list against blockstates, models, textures, loot tables and names, plus a limit of 6 cuboids per block model. Old models over the limit only give warnings; a new one fails.
3. **`check_zfighting.py`.** Flat faces that would flicker.
4. **`./gradlew test`.** The unit tests.
5. **`./gradlew runGametest`.** Starts a real (headless) game server and runs the tests in `src/gametest`. They are not part of the mod jar. The run ends with a failure code if any test fails. For every block of the mod it places up to 64 block states and checks they stay put and have shapes, that decorative blocks have no block entity or ticking (functional blocks are on a list in `AureliaGameTests`; a new block with a block entity must be added there on purpose), and that breaking it drops what the loot table says. It also checks car stop board clicking, the editable regional signs, glass pane and sign pole connections, and that blocks end up facing the way their tooltip says when placed looking north, east, south and west.
6. **`render_preview.py --changed`.** Draws pictures of the changed blocks without a graphics card, into `build/previews/`:
   - `tab_<tab>.png`: one sheet per creative tab (look for missing textures, odd shapes);
   - `orientation_<tab>.png`: every block with a facing, shown for north, east, south and west with a top view, a side view and an arrow for the direction the player looks while placing it (check that arches and slopes rise the way the tooltip says);
   - `panes.png`: glass panes as a post, with one side, corner, T and cross;
   - `tiled_walls.png`: 3x3 walls of every full glass and station block (check the textures tile).

   Run it by hand with `python3 tools/render_preview.py --tab glass` or `--block frankfurt_hall_truss_arch`. It needs Pillow and reads the Minecraft pictures from the jar Gradle already downloaded.

What this cannot tell you: how it looks with lighting and shaders, sounds, and whether a build feels right to walk through. Those still need a quick look in game.

## In-game screenshot tour

`bash tools/release_check/shoot.sh [--views views.json] [--keep]` flies a spectator camera through the 1.5 scene and captures only the
Minecraft window (CoreGraphics window id, `screencapture -l`; no Accessibility permission, no keystrokes). It sets `fov:0.0`, `hideGui`
and hidden chat in `run/client/options.txt` (restored on exit), regenerates the scene, runs `tools/release_check/tour.py` (load tag,
tick tag with a per-run tag, one function per view, 100 ticks apart, first 200 ticks after joining), launches the client, and waits for
each view's `ATA_TOUR_VIEW n name` log line before capturing, so shots line up with views. Output: `build/tour/<view>.png`,
`build/tour/sheet.png`. The tour ends in `tour_end`; the script quits the client and removes the tags and functions
(`tour.py --clean`). Coordinates come from the layout constants in `gen_v15.py`.
