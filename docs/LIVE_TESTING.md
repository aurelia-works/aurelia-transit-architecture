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
