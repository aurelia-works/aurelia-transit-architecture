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

Short notices shown in a strip at the bottom of PIDS, platform CIS and concourse boards.

- **Server state**: one network message and up to 16 station messages (keyed by station display name, case-insensitive), in the overworld's persistent state (`ata_service_messages`). Text is sanitised and capped at 96 characters; severity is `info`, `warning` or `disruption`. The server never references MTR.
- **Commands** (permission level 2):
  - `/ata_message network set <info|warning|disruption> <text...>` and `/ata_message network clear`
  - `/ata_message station "<name>" set <info|warning|disruption> <text...>` and `/ata_message station "<name>" clear`
  - `/ata_message list`
- **Sync**: the complete set is sent to a player on join and to everyone when it changes (packet `service_messages`, bounded reads). Clients drop it on disconnect.
- **Priority on a board**: the display's own message (the field in its config screen) over the station message for the board's resolved station name, over the network message.
- **Drawing**: `BoardBuilder` adds a strip along the bottom (INFO uses the style's header and accent colours, WARNING amber, DISRUPTION red with white text and a "! " prefix). Long text is squeezed, then scrolled by the existing marquee, so the board rebuilds at the existing cadence (about once a second, faster only while scrolling). The strip height (10 virtual units) is added to the board's content height only while a message exists, so departure rows end above it; idle boards show it too.

Wiring note if the vertical anchoring in `BoardBuilder` changes: the strip is drawn by `messageStrip(m, tr, message, vw, vh, style, now)` at `y = vh - MESSAGE_STRIP_H`, and `contentH` must keep including `stripH` so that rows, anchored from the top or centred, never reach the strip.

## Passenger information terminal

Logic behind the terminal screens (`terminal/*`, `client/terminal/logic/MtrTerminalSource`, installed with `Terminals.install` when MTR is loaded).

| Data | Source |
|---|---|
| System map | MTR `simplifiedRoutes` (automatic). `SystemMapBuilder`: routes of one line (same label and colour) collapse into the variant with the most distinct stations (ties: lower route id); consecutive duplicate stops removed; `transfer` = station on two or more map lines; lines serving the current station first, then label, colour, id; at most 24 lines of 48 stops. Deterministic for shuffled input. Cached 10 s per current station. |
| Station info | Wayfinding facts (name, lines, exits) and the station snapshot's platforms (automatic); station code, transfers, street label and a manual name come from the terminal's own wayfinding data (manual wins). |
| Accessibility notes | ATA metadata only: loaded block entities implementing `WayfindingEditable` within 24 blocks whose pictogram is accessible route, lift, escalator, stairs or help point (text = destination, else street label, else pictogram name), scanned only when station info is requested, cached 10 s. Help point blocks have no block entity, so they are not found (only help-point signs are). Empty when nothing is configured. |
| Departures | `TerminalDepartures.select` = the station-wide PIDS rule (not yet departed, `ServiceOrder` order kept). |
| Service messages | `ClientServiceMessages.allFor(station)`: station message, then network message. |

Trip planning is deferred: MTR's directions finder is server-side only.

## Limitations

- Station codes, stopping patterns and street names do not exist in MTR; they are manual.
- Derived line labels are a heuristic from route names; set a route number in MTR for exact labels.
- Service messages match stations by display name, so renaming a station in MTR detaches its message.
- Circular or multi-platform stations list each line once; a line that only calls at a far platform of a huge station (more than 16 platforms) may be missing.
