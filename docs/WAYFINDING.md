# Wayfinding model and live information (1.2)

Wayfinding blocks (pylons, directional, platform, exit, street and bus signs) share one model with two halves:

- **Manual**: `WayfindingData`, stored in the block entity (server NBT, synced by the shared `update_wayfinding` packet).
- **Automatic**: `StationFacts`, what Minecraft Transit Railway actually knows about the station at the block, built client-side by `MtrWayfindingSource` from data MTR already synced. Nothing is polled from the server.

`Wayfinding.resolve(pos, data)` merges them (`WayfindingResolver.merge`) into a `ResolvedWayfinding`. `WayfindingLayout.layout(...)` turns that into rectangles and labels, which renderers cache and redraw.

## Auto versus manual

| Field | Source | Notes |
|---|---|---|
| Station name | MTR station at the block (`autoStation`), manual name wins | Display segment of MTR's "a\|b" name. |
| Secondary name | Manual; or, with a second-language layout and no manual text, the next "\|" segment of MTR's own station name | Single-language MTR names leave it empty. Never guessed or translated. |
| Line badges | MTR routes serving the station (`autoLines`), a non-empty manual list wins | See *Line badges*. |
| Exit destinations | MTR `Station.getExits()`, matched by the configured exit label | Case-insensitive; no label or no match gives nothing. |
| Station code | Manual only | MTR has no station codes. |
| Service type and label | Manual only | MTR has no stopping-pattern data. "Local" / "Express" / "Limited" / custom text. |
| Destination, arrow, platform, exit label, street label, transfers, pictogram, accent, language layout | Manual only | |

If MTR is missing, the station does not resolve or a call fails, the automatic half is empty and only manual content shows. Nothing is invented.

## Line badges

Client-side MTR has `simplifiedRoutes` (id, name, colour, platforms with their station ids) for every route. It has **no route number and no hidden flag**; the full `Route` (with both) is used only when MTR happens to hold it in `routeIdMap`. So:

1. Label = MTR route number when present and at most 4 characters.
2. Otherwise derived from the route name (never invented): the first word of at most 4 characters that contains a digit ("Line 15" gives `15`); else the initials of the words after dropping *line / lines / route / service* (up to 4: "Market Frankford Line" gives `MF`); a single short word is upper-cased; a longer single word gives its first letter.
3. Colour = route colour. Routes with the same label and colour are one badge. Hidden routes are skipped where MTR tells us.
4. Order: numeric-aware by label, then colour, then id. At most 6 (`WayfindingData.MAX_LINES`).

Routes are matched to the station by station id or by the ids of the station's platforms.

## Caches and cost

`MtrWayfindingSource` keeps two bounded `TimedLruCache`s on top of the shared station provider's own cached AUTO resolution: per block position (1 s refresh, 512 entries) and per station id (5 s refresh, 128 entries). Unused entries are swept after 30 s. No block-entity ticking; layouts are pure functions, so callers cache them keyed by `ResolvedWayfinding` and panel size and rebuild only when those change.

## Layout

`WayfindingLayout` covers every `WayfindingPanelKind`. Rules: empty fields are skipped; text is scaled to fit (long names on tall pylons wrap on spaces to at most three lines); at least 45 % of the width stays with the text, so arrow, platform/exit badge, pictogram, service tag, station-code tag and line badges are dropped in that order of priority when there is no room; rectangles never overlap (the drawer draws them in one plane); everything stays inside `w x h`.

- `SIDE_BY_SIDE`: the name area is split into a primary and a secondary half with a thin divider. It needs an area at least 2.2 times wider than tall, otherwise it is drawn stacked.
- `STACKED`: secondary below the primary at 60 % size.
- `SINGLE`: primary only.
- Pictograms are generic originals made from a few same-coloured rectangles plus arrow glyphs, drawn in the sign's text colour.
- Arabic-script text is laid out as plain left-to-right text, which is what Minecraft draws (no shaping, no right-to-left ordering). Prefer a Latin name where one exists.

## Service messages

Short notices shown one at a time in a strip at the bottom of PIDS, platform CIS and concourse boards, listed in full on the terminal's Service info page and summarised by the station information board's "Service changes" view.

- **Server state** (`ServiceMessages`): any number of NETWORK and STATION messages, at most 32 in total, in the overworld's persistent state (`ata_service_messages`). Each message has a **stable id** that is never reused (ids carry on after removals and restarts), a **scope** (network, or a station matched by display name, case-insensitive), a **severity** (`info`, `notice`, `disruption`, `severe`) and sanitised text of up to 96 characters. Adding a message never overwrites another one. 1.2 data (one network message plus one per station) is migrated on load. The server never references MTR.
- **DISPLAY scope**: the message typed into a display's own config screen. It stays in the display's config, not in the server list.
- **Commands** (permission level 2):
  - `/ata_message add network <info|notice|disruption|severe> <text...>`
  - `/ata_message add station "<name>" <severity> <text...>`
  - `/ata_message remove <id>`
  - `/ata_message clear` (everything), `/ata_message clear network`, `/ata_message clear station "<name>"`
  - `/ata_message list` (shows ids)
  - 1.2 forms still work: `network set ...` / `station "<name>" set ...` replace that scope's messages with one; `... clear` clears that scope. `warning` is accepted for `notice`.
- **Sync**: the complete list goes to a player on join and to everyone when it changes (packet `service_messages`, bounded reads: count checked before allocation, strings length-capped, invalid entries dropped). Clients drop it on disconnect.
- **Order** (`ServiceMessages.applicable`, deterministic): display message, then station messages, then network messages. Within a group the most severe comes first, then by id. Lower groups are never hidden; they come later in the same cycle.

### Rotation and scrolling (`MessageRotation`)

- Each message gets a time slot of **5 s** (dwell). A message that has to scroll gets a longer slot: 1.2 s hold, then the scroll, then 1.2 s hold at the end.
- The message shown and the scroll position are pure functions of the **wall clock**, never of frames. The board model is rebuilt at its normal cadence (about once a second), and every 140 ms only while the current message is scrolling. Nothing is laid out per frame.
- **Zero** messages: no strip, and the departure rows get the space back. **One**: it never rotates. **Many**: they rotate in order.
- **Long text** is squeezed at most to 85 % width (never unreadable), then scrolled one way, one character at a time from its start. Every new message starts from its beginning.
- Colours: info uses the board style, notice is amber, disruption red, and severe bright red with a "!!" prefix.

## Station association (Auto / Manual)

Entrance pylons, exit signs, passenger information terminals, kiosks, station information boards and bus e-paper boards have a **Station** button in their editor (directional, street and pictogram signs have no station field, so no button):

- **Auto** (default, the 1.2 behaviour): the MTR station at the block, falling back to the nearest platform.
- **Manual**: pick the station, and optionally platforms, from MTR's list. This is for overlapping or vertically stacked stations, big complexes, and decorative terminals just outside a station's area.

The choice is stored in the block's wayfinding data (`association`) and used everywhere that block resolves MTR data: names, lines and exits, terminal departures and station info, and e-paper arrivals. Caches key on position *and* association, so an Auto block and a Manual block never share an entry. This is a block-level override only; ATA has no network-wide overlapping-station system.

## Station information board

One wall board (joins side by side; 2–3 wide reads best) with a **view** chosen in its editor. Every view lays out facts the shared model already resolved (`StationBoardLayout`); nothing is stored twice.

| View | Shows |
|---|---|
| Trains this side | heading, then arrow, direction/destination and line badges (the directional-sign layout) |
| Platform / track | heading, then platform number, direction and lines (the platform-sign layout) |
| Service changes | the applicable service messages with severity markers; "+N more" when they do not fit; "No service changes" when there are none |
| Transfer board | line badges serving the station, transfer note, street/landmark |
| Street / exit summary | every MTR exit of the station with its destinations; "+N more" when they do not fit |

The layout is static. It rebuilds only when the resolved data, the board width or the service-message list changes.

## Passenger information terminal

Logic behind the terminal screens (`terminal/*`, `client/terminal/logic/MtrTerminalSource`, installed with `Terminals.install` when MTR is loaded).

| Data | Source |
|---|---|
| System map | MTR `simplifiedRoutes` (automatic). `SystemMapBuilder`: routes of one line (same label and colour) collapse into the variant with the most distinct stations (ties: lower route id); consecutive duplicate stops removed; `transfer` = station on two or more map lines; lines serving the current station first, then label, colour, id; at most 24 lines of 48 stops. Deterministic for shuffled input. Cached 10 s per current station. |
| Station info | Wayfinding facts (name, lines, exits) and the station snapshot's platforms (automatic); station code, transfers, street label and a manual name come from the terminal's own wayfinding data (manual wins). |
| Accessibility notes | ATA metadata only: loaded block entities implementing `WayfindingEditable` within 24 blocks whose pictogram is accessible route, lift, escalator, stairs or help point (text = destination, else street label, else pictogram name), **plus standalone help point blocks** within 24 blocks (nearest 4, listed with their distance). Help points have no block entity, so they are found through the chunk sections of the same loaded chunks; a section whose palette holds no help point is skipped without visiting its blocks. Runs only when station info is requested, cached 10 s, never per tick and never beyond the radius. Empty when nothing is configured. |
| Departures | `TerminalDepartures.select` = the station-wide PIDS rule (not yet departed, `ServiceOrder` order kept). |
| Service messages | `ClientServiceMessages.allFor(station)`: every applicable station and network message, in rotation order. The Service info page lists them all, grouped by scope with severity; Home shows them one at a time (5 s each, with an "n/N" counter), only while the screen is open. |

Trip planning is deferred: MTR's directions finder is server-side only.

## Limitations

- Station codes, stopping patterns and street names do not exist in MTR; they are manual.
- Derived line labels are a heuristic from route names; set a route number in MTR for exact labels.
- Service messages match stations by display name, so renaming a station in MTR detaches its message.
- Circular or multi-platform stations list each line once; a line that only calls at a far platform of a huge station (more than 16 platforms) may be missing.
