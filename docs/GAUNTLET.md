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
