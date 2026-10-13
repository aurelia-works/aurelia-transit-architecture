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
| 7 | Tokyo front detailing does not wrap round the corner | Jah's screenshot | OPEN |
| 8 | Grand size ignored ("-") on Jah's 10-platform Tokyo plan | Jah's screenshot | OPEN: probably the list; recheck |
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
