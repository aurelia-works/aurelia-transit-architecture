# Voice packs

Station announcements are built from **fragments** (short phrases, numbers, station and route names). A voice pack maps fragment keys to sound events. Packs are ordinary resource-pack content: no code, no network, no text-to-speech at runtime.

The mod ships only three original synthesised chimes (`chime.info`, `chime.alert`, `chime.delay`, made by `tools/make_live_sounds.py`). Without a voice pack an announcement is: chime, plus the sentence shown in the action bar. Announcements use the **Voice** sound category, so the player's Voice volume slider controls them.

## Adding a voice to a resource pack

1. Put your audio in your pack (Ogg Vorbis, **mono** so it attenuates with distance): `assets/mypack/sounds/voice/approaching.ogg`, ...
2. Declare the sound events in your own `assets/mypack/sounds.json`:
   ```json
   {
     "phrase.train_approaching_platform": { "sounds": ["mypack:voice/approaching"] },
     "number.3": { "sounds": ["mypack:voice/three"] }
   }
   ```
3. Add one or more pack files at `assets/mypack/aurelia_voice/<name>.json`:
   ```json
   {
     "priority": 10,
     "fragments": {
       "phrase.train_approaching_platform": "mypack:phrase.train_approaching_platform",
       "number.3": { "sound": "mypack:number.3", "duration_ms": 550 },
       "station.central_station": { "sound": "mypack:station.central_station", "duration_ms": 1100 }
     }
   }
   ```
   A value is either a sound event id, or an object with `sound` and `duration_ms` (how long to wait before the next fragment starts; default 900, limited to 50..15000).
4. Enable the resource pack (reload with F3+T). `/aurelia_live voices` lists the loaded packs.

### Layering and fallback

All packs are stacked: highest `priority` first, then pack id. A key is looked up in each pack in turn, and the built-in chimes are always at the bottom. So a small pack with only station names can sit on top of a fuller one.

An announcement is spoken **only if every fragment of the sentence has a sound**. A half-spoken sentence never plays: if anything is missing, the chime plays and the full sentence is shown as an action-bar message instead. Start with the phrases and numbers, then add station and route names; `/aurelia_live test <category>` plays a sample sentence at your position ("Test Central", platform 3, "Test Line", destination "Test Junction", stops "Alpha Street", "Beta Park", "Test Junction", 4 minute delay) so you can hear what your pack covers. Use `-Daurelia.live.subtitles=true` to always show the text too.

## Fragment keys

### Chimes

`chime.info`, `chime.alert` (safety), `chime.delay`. Played first; override them in your pack to replace the defaults.

### Phrases (`phrase.<name>`)

| Key | English text |
|---|---|
| `phrase.train_approaching_platform` | The train approaching platform |
| `phrase.train_at_platform` | The train at platform |
| `phrase.train_arriving_at_platform` | The train arriving at platform |
| `phrase.is_the` | is the |
| `phrase.service_to` | service to |
| `phrase.calling_at` | Calling at |
| `phrase.and` | and |
| `phrase.please_board_now` | Please board now. |
| `phrase.terminates_here` | terminates here. |
| `phrase.all_passengers_leave` | All passengers must leave the train. |
| `phrase.the` | The |
| `phrase.is_delayed_by` | is delayed by approximately |
| `phrase.minute` / `phrase.minutes` | minute / minutes |
| `phrase.apologise` | We apologise for the delay. |
| `phrase.stand_clear` | Please stand clear of the platform edge. |
| `phrase.train_is_approaching_platform` | A train is approaching platform |

### Numbers

`number.0` ... `number.99`: platform numbers and delay minutes. A platform name that is not a whole number up to 99 (for example `2a`) uses `platform.<normalised name>` instead (`platform.2a`).

### Stations and routes

`station.<normalised station name>` for the destination and every calling point, `route.<normalised route name>` for the route name. Normalisation: take the display name (the first Latin-script segment of MTR's `A|B` multilingual name), remove accents, lower-case, turn every run of non-letters/digits into one `_`. `Zürich Hbf (Main)` becomes `station.zurich_hbf_main`; `Red Line` becomes `route.red_line`. Non-Latin letters are kept as they are.

## Sentences

| Category | When | Sentence |
|---|---|---|
| Approaching | about 40 s before arrival | The train approaching platform **N** is the **route** service to **destination**. Calling at **a**, **b** and **z**. |
| Standing | train at the platform | The train at platform **N** is the **route** service to **destination**. Calling at ... Please board now. |
| Terminating | about 40 s before a terminating train arrives | The train arriving at platform **N** terminates here. All passengers must leave the train. |
| Delay | realtime lateness of 2+ minutes (again only if 3 more minutes late) | The **route** service to **destination** is delayed by approximately **N** minutes. We apologise for the delay. |
| Safety | about 12 s before arrival | Please stand clear of the platform edge. A train is approaching platform **N**. |

The route part is skipped when a route has no name; "Calling at" lists at most 5 stops and always ends on the final stop. Priority when several are waiting: safety, delay, terminating, approaching, standing.

## Notes for pack authors

- Keep fragments free of leading/trailing silence; `duration_ms` is the gap before the next fragment.
- Do not reuse third-party or operator recordings you do not have the rights to.
- Fragment keys and the `aurelia_voice` folder are the stable interface of the format; the English wording above is the contract for what each fragment should say.
