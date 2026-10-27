# Station Studio: the backend API

What `StudioServer` (`src/main/java/.../station/studio/`) answers. The page (`src/main/resources/studio/`) is the only client; the
game's own studio and `tools/station-studio.sh` serve the same API. Everything listens on `127.0.0.1` only and refuses a `Host`
that is not `localhost`/`127.0.0.1`.

Requests carry the **same parameters** wherever they say "the plan parameters": the form fields the page sends (`look`, `mix`,
`mode`, `tracks`, `length`, `layout`, `building`, `size`, `roof`, `esc`, `walls`, `trav`, `bed`, `name`, `text`, `platforms`,
`pinned`, `seed`, and from this round `class`, `era`, `facing`). `StudioApi.description(q)` turns them into the words the planner
reads, so the preview, the export and the game all plan the same station. Optional `rolled` (10-24) lists the pinned controls only
the dice set; words naming a city or a look take the look, building and roof among them (see `STUDIO_DESIGN.md`). Without it every
pin holds.

| Endpoint | Method | What |
|---|---|---|
| `/api/plan` | POST (token) | plans and describes a station: JSON, below |
| `/api/export` | GET (`token=` or a typed address) or POST (token) | the same station as a Sponge schematic file |
| `/api/send` | POST (token) | plan / build / undo / cancel in the game (only when opened from the game) |
| `/api/options` | GET | the choices and limits |
| `/api/version` | GET | page-file and start ids for the self-reload |

"Token" is the per-session `X-Studio-Token` header (the page carries it as `data-token`).

## `/api/plan` answer (additions this round are marked **new**)

```
text, title, seed, fingerprint (new), resolved{roof,size}, encoded, typed,
understood[], defaulted[], warnings[], ignored[], requested{...}, built{...},
size{width,length,height,blocks}, buildable,
checks[{topic,status,message, title (new), fix (new)}], summary{pass,warn,fail} (new),
violations[{kind,x,y,z,message}], faceLabels[], passenger, street, head, roles[], models[], cells[], moving[], ms
```

### Checks: title, fix, summary (new)

Each `checks[]` item gains `title` (a short human name for the topic: "Tracks", "Boarding edges", "Rail nodes", "Roof cover", "Headroom",
"Step-free access", "Name signs", "Nothing floating", "Size", "Lighting", "Furnishing") and `fix` (a short plain-English next step, an
empty string for a PASS; always non-empty for a WARN or FAIL). The existing keys are unchanged and no audit rule was loosened:
the advice is added beside the findings. `summary` is `{"pass": n, "warn": n, "fail": n}`, the counts over `checks`.

### Class, era and facing (new)

Request fields: `class` = `auto|halt|regional|town|city|grand`, `era` = `auto|historic|interwar|postwar|modern`, `facing` =
`auto|north|east|south|west`. Anything else (or nothing) is `auto`; no value can make a request fail. `/api/options` lists them as
`classes`, `eras` and `facings`, each `[["key", "Label"], ...]`.

- The answer's `resolved` gains `class` and `era`: the class and era asked for, or, for `auto`, the ones the planned station amounts to
  (`halt` for no building on up to two tracks, `regional` for a compact building, `town` for a city-sized building on up to three tracks,
  `city` for more or a terminus, `grand`; the era of the building's look). The answer gains `facing`: `"auto"` or the direction asked.
- **Class and era only fill what is open.** With a class or an era chosen, only the controls named in `pinned` go into the words, even
  when no words are typed, so the class decides every control the player has not touched (the page should send `pinned` as it does for
  typed words; the controls it sends are otherwise ignored for that). A touched control, a pinned look and any typed word win. Class and
  era are written into the description as `halt class` and `postwar era`; a halt and a regional station plan genuinely small (see the
  table in `docs/STATION_GENERATOR.md`: a halt is one open 75-block platform, a regional station a compact building with no train shed).
- **Deviation from the first contract**, as agreed with the coordinator: the contract asked for the words "halt", "regional station",
  "town station", "city station", "grand station" and bare "historic", "interwar", "postwar", "modern". Those words already mean something
  else (`halt` and `compact` a compact building, `regional`, `town` and `city` a city-sized one, `grand` a grand one, `modern` the modern
  platform style) and saved descriptions must plan as before, so they keep that meaning and the class and era use the unambiguous phrases
  `<class> class` and `<era> era`. The request fields are as the contract has them. **Interwar** has no look of its own yet: it uses the
  nearest existing ones (Hamburg's clinker brick, and Milan's monumental stone for a grand station).
- **Facing** is the direction the station's entrance (its street side) looks: `facing north` in the words. It does not change a block (the
  plan's `fingerprint` is the same). The preview (`cells`, `passenger`, `street`, `head`, `faceLabels`, `moving`, `violations`) is drawn in
  the matching frame, so the street really is on that side of the picture; the export is written that way round, with the paste origin
  outside that entrance; and the game builds it that way round instead of the way the player looks. `auto` is the studio's own view: a
  terminus's front to the north, a through station's street to the west (and, in the game, the way the player looks).

### Seed and fingerprint (new)

- `seed` is the request's generator seed as the planner read it: 1 to 12 characters of `a-z`/`0-9` (any case on input, lower case in
  the answer), or `""` for none or `0`. Anything else in the `seed` field, or after the word `seed` in the words, is **ignored with a
  note** in `warnings` (never an error), and the station is planned as if there were no seed.
- `fingerprint` is the first 16 hex digits of a SHA-256 over the plan's blocks sorted by `(u, y, v)`, each as
  `u,y,v,blockId,facing,state,text`. Local coordinates are used (the studio's `x` is `u`, its `z` is `-v`), so the fingerprint does not
  depend on which way the station is drawn or built. Same words + seed + generator => same fingerprint, on any machine and thread.
  See `docs/STATION_GENERATOR.md`, *Seeds and fingerprints*, for what a seed can change and the theoretical limit on variety.

## `/api/export`

`GET /api/export?<plan parameters>` or `POST /api/export` with the plan parameters as a form body.

- **200**: the body is a gzipped NBT **Sponge Schematic version 2** (`.schem`), which WorldEdit, FastAsyncWorldEdit and Litematica's
  importer read. `Content-Type: application/octet-stream`, `Content-Disposition: attachment; filename="<slug-of-name-or-station>-<seed or noseed>.schem"`,
  `X-ATA-Blocks: <planned block count>`, `X-ATA-Fingerprint: <the plan's fingerprint>`.
- **400** with `{"error": "..."}`: the station cannot be built (a design rule or station check fails: nothing is exported that the
  game would refuse to build), or the request is unreadable (too long).
- **403**: a POST without the studio token (header `X-Studio-Token`, or a `token` field), or a GET with neither `token=<the studio token>`
  among its fields nor the browser's own word that it came from this page (`Sec-Fetch-Site: same-origin`) or from the address bar
  (`none`). The page's download link should carry `token=`; an address typed in the bar works as it is; a page on another site, a
  browser that sends no `Sec-Fetch-Site` and a plain `curl` without the token are refused, so a web page elsewhere cannot make
  the studio plan in a loop.

The file is written by `SchematicExport`/`Nbt` with no Minecraft classes, so it works in the standalone studio too, and it is
deterministic: the same request gives the same bytes.

### Layout

| Tag | Value |
|---|---|
| root | compound `Schematic` |
| `Version` | 2 |
| `DataVersion` | 3465 (Minecraft 1.20.1) |
| `Width`, `Height`, `Length` | shorts: the planned blocks' bounding box, enlarged to hold the paste origin |
| `Offset` | `[0, 0, 0]` (as WorldEdit writes it) |
| `PaletteMax`, `Palette` | `blockId[property=value,...]` (properties in name order) to index; `minecraft:air` is 0 |
| `BlockData` | one varint per cell, index `(y * Length + z) * Width + x` |
| `BlockEntities` | empty |
| `Metadata.WEOffsetX/Y/Z` | the clipboard corner minus the **paste origin** (so `//paste` puts the origin on your feet) |
| `Metadata.Name`, `Author` | the station name (or "Station"); "Aurelia Transit Architecture" |
| `Metadata.RequiredMods` | `aurelia_transit_architecture`, `mtr` |
| `Metadata.ATA*` | `ATAFingerprint`, `ATAGenerator`, `ATAVersion`, `ATASeed`, `ATAFacing`, `ATAClass`, `ATAEra`, `ATABlocks`, `ATAOrigin`, `ATADescription` (the words that were planned) |

**Paste origin.** On the ground just outside the footprint, in line with the street entrance: the column before a through
station's apron or car park, the cell past a terminus's front; in front of platform 1's first ramp when there is no building. Nothing
is planned there, so there is room to stand. Stand there and `//paste`: the station is in front of you.

**Block states.** Facing or axis follow the plan's front (the ticket machine and tactile map face away, as in the game), the plan's own
properties (a stair's half, a lift door's side) are kept, MTR's rail node gets its boolean flags, and the joins the game works out
from neighbours (panes, railings, girders, screens, signs, fences and walls) are written in, with the rules the preview uses
(`Connections`). So the file holds the finished station, not what the game would build before neighbours update.

**What the file cannot carry** (no block entities, by design): a name sign's text and the station name panels, a screen's display
style, MTR's lift data, the MTR station zone and rail connections, and the foundation under unsupported ground. They are made by the
in-game build, and `/ata_station verify` (below) reads the blocks back. A pasted station is a shell of correct blocks; build it with
the generator when it should work.

### Checking a paste

- **In the studio build**: `SchematicExportTest` reads every preset's file back and compares each block's position, id and state with
  the plan (and a hand-written NBT test fixes the byte layout).
- **In the game** (`StationGameTests`): every block state the exporter writes, for every preset and all four headings, is parsed by
  Minecraft's own `BlockArgumentParser` and must match what the game's build gives; four real built stations (a terminus, a side
  building, a lift station and an escalator station) are compared with their schematic cell by cell; and a schematic is pasted into
  the world at its origin and verified.
- **For a person**: `/ata_station verify` (operators) reads the world back against the pending plan, or against the last build, where it
  was planned. `/ata_station verify paste` anchors the pending (or last built) plan with its paste origin on your feet: plan the
  station, export it, stand where you will paste, `//paste`, then run it. It says `Exact: all N blocks ... are as planned` or lists the
  first blocks that differ. Read only; it never loads a chunk.

## Airports (1.7)

- **Page:** `GET /airport` (also `/airport/`, `/airport.html`) answers the same page as `/` with `data-page="airport"`.
- **Request:** the plan parameters gain `page` (`airport` or nothing). With `page=airport`, or when the typed `text` names an airport, the
  description is the typed words alone (plus the swaps, `facing` and `seed`); the station controls are ignored. With `page=airport` the words
  are prefixed `airport, ` when they do not name one.
- **`/api/options`** gains `airports`: `[{"key","label","text"}]`, the page's airport choices.
- **`/api/plan`** answer: `built.mode` is `"Airport"` and `built.airport` is `{runways, length, width, gates, terminal, metres, real}` (null for
  a station); `views` is null for a station and, for an airport, `{terminal, apron, threshold}` as `{x, y, z, tx, ty, tz}` (cell x, height y, cell z
  of the eye, and of the point it looks at; `terminal` and `apron` are left out without a terminal); `checks` use the topics Runways, Taxiways,
  Gates and Terminal; `size.length` is the runway direction. `passenger` stands on the apron, `street` on the road before the terminal.

