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
