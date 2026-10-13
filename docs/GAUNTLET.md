# 1.6.0 gauntlet (started 2026-10-13)

Jah's goal: find and fix everything wrong with the mod, from Station Studio to the buildings, the departure
screens and the wrench. If a block looks off, in game or in a render, fix it. One line per finding:
what it is, the evidence, and its state.

States: OPEN, FIXED (commit), CHECKED (seen fixed), WONTFIX (why).

## From Jah (13 Oct)

| # | Finding | Evidence | State |
|---|---|---|---|
| 1 | Wave canopy reads as square plateaus with floating fascia planks | Jah's screenshot; `waveAlong` is a one-block plateau; Utrecht's real wave is the hall roof, its platform canopies are flat | FIXED: wave removed, Utrecht/HK/Dutch get flat canopies |
| 2 | Departure screens look fake | `DisplayStyle` is only a colour swap on one layout in the vanilla font | FIXED: four real boards (UK dot-matrix in LED dots, Dutch, German, metro countdown) with their own layouts and fonts; the colour-swap presets are gone. Later: SCR-style option |
| 3 | Travelators look weird in the footbridge | Jah | FIXED: platforms only |
| 4 | "Stairs both ways" makes no second stair | Studio plan view: identical with and without at default widths (needs islands 11, side 9) | FIXED: the option widens platforms to fit, warns when a given width is too narrow |
| 5 | Studio stuck at 11 tracks | "Platforms one by one" list (saved in the browser) dims the track and type buttons; 10 side platforms plan as 11 tracks; a hub falls back to a terminus | FIXED: buttons stay live and rebuild the list; hub lists are islands |
| 6 | Two name signs stacked | Jah | OPEN: check in game |
| 7 | Tokyo front detailing does not wrap round the corner | Jah's screenshot; a head building's upper storeys used the side-building rhythm on its 12-deep side walls (two window groups) | FIXED: head buildings' side walls take the street front's rhythm; CHECKED in a render |
| 8 | Grand size ignored ("-") on Jah's 10-platform Tokyo plan | Jah's screenshot; the parser keeps Grand, but the Studio's built column printed the size for side buildings only | FIXED: a terminus reports its head building's size; StudioTest |
| 9 | Lights where there should be none, under the footbridge | Jah's screenshot; deck lights fell in the glass-wall rows (under the glazing and lift doorways) | FIXED bbbe998: walkway rows only; CHECKED in a render |
| 10 | Lifts should open both ways at the bottom only | Jah | FIXED bbbe998: back doors at platform level, car formed double-sided; CHECKED: back doors open on a call |
| 11 | Departure screens not aligned with the platform | Jah; the girder-centred span sat half a cell off an even island's middle | FIXED bbbe998: centred 4-wide with girder hangers; CHECKED in a render |
| 12 | Board message ends "is replace" | the message was exactly 96 characters, cut silently | FIXED bbbe998: 256 allowed, longer refused |

## From the random sweep (13 Oct night)

2,500 random descriptions and 500 random platform lists through parse, plan, validator and audit.

| # | Finding | Evidence | State |
|---|---|---|---|
| 13 | "terminus/hub ... no building" crashed the parser (373 of 3,000) | `IllegalArgumentException: a terminus needs a head building` from `StationSpec` | FIXED: keeps the head building and warns |
| 14 | Back-to-back side platforms reported "no free place for a name sign" | the shared row stands on the neighbour; the audit credited only the platform under it | FIXED: the audit credits the shared row |
| 15 | Name signs given up when the middle or back column is taken | sign rows tried every second cell of one column only | FIXED: every cell, then the next column in; 29 left, all 16-long platforms filled by a building |
| 16 | Validator and audit took 1.8 s on a 140k-block plan | `unsupportedRoof` boxed 81 map merges per beam; `grounded` boxed 18 lookups per block | FIXED: dense grids; about 0.6 s, same results across the sweep |
| 17 | Planning a 20-track, 300-long station stalled the server for over a second | plan, validate and audit all ran on the server thread | FIXED: above 2,000 track-blocks the plan is worked out on a planner thread; confirm waits for it; game test |
| 18 | Any web page could make the Studio plan stations in a loop | `/api/plan` took a POST without the page token | FIXED: token required, as for `/api/send` |
| 19 | Parser robustness | 20,000 fuzzed inputs (word soup, numeric extremes, control characters, 5,000-character input) | CHECKED: nothing throws; StationDescriptionFuzzTest keeps 3,000 |

## Second sweep (13 Oct evening)

2,000 random descriptions with every audit warning and parser warning tallied, not only failures.

| # | Finding | Evidence | State |
|---|---|---|---|
| 20 | Island platforms of a station without a building were reached only across a track (291 of 2,000) | audit ACCESS warnings; no footbridge unless asked, and a building-less footbridge stopped at the outer platforms, glazed shut at both ends | FIXED: islands get a footbridge; with tracks on both edges a street entrance with stair and lift beside the first track; IslandAccessTest, CHECKED in a render |
| 21 | Islands 3-4 wide under a footbridge got no stair at all, with no warning | `stairColumns` needs 5; 103 stations | FIXED: widened to 6 for the lift (5 without ramps), with a warning |
| 22 | "grand central, city" built a grand building and warned "size given twice" | the landmark's implied size counted as said | FIXED: a size word beats the landmark's own size |
| 23 | Name signs given up on 16-long platforms full of stairs and lifts (29) | no free run of three cells | FIXED: two, else one sign; none left in the sweep |
| 24 | 16-long stations with a building are not step-free (67 of 2,000) | a two-bay building has no room for its lift | WONTFIX: shortest stations only; the audit says so before building |

