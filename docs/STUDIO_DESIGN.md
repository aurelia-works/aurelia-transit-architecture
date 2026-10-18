# Station Studio: design notes

The page served by `StudioServer` (`src/main/resources/studio/`). Laid out after Indus Studio's Structure Generator, which Jah
pointed at: a tool sidebar, a column of settings, a large 3D view with a view bar, and a column of build facts and checks with
one main button. ATA's own colours and type, not Indus's purple.

## Concept card

```
Idea:        a station generator you steer with a seed, the way a departure board steers a station
Metaphor:    the split-flap departure board
Signature:   the dice: the seed's six letters flip like a board's flaps while the new station builds in the view
Emotion:     a control room: quiet, dense, exact; one moving thing at a time
Motion verb: flip like a board (seed), settle (everything else: 120-180 ms, no bounce)
Screenshot:  a seed mid-flip beside a freshly rolled terminus
```

## Seeds

One seed, two jobs:

- **Settings seed** (the page). The dice picks every setting that is not locked: Look (city, mix), Shape (type, tracks,
  length, platforms), Building (building and size, roof, stairs, stairwells, travelators, track bed). Typing a seed rolls the
  same settings again. Changing a control afterwards marks the seed as edited.
- **Generator seed** (the planner). Sent as the words `seed k7f2ra`, so the wrench, `/ata_station plan` and the studio all
  read it. It varies only what the words left open: the canopy profile (two in five), one arch or several (barrel-vaulted
  sheds only), the platform floor (three in five), the roof material (one in two), which colour platform 1 starts on and where
  the first bench stands. No seed, or seed 0, plans exactly as before. Lock **Details** to keep these while re-rolling the rest.

## System

| Role | Value |
|---|---|
| Ground | `#07080c` with the Silk shader behind the chrome |
| Panel / raised | `#0d0e13` / `#14151b` |
| Ink / muted | `#f4f4f5` / `#a1a1aa` |
| Line | `rgba(255,255,255,.08)` |
| Accent (actions, selection) | `#3d63ff` ATA blue |
| Go (build) | `#22c55e` |

- Type: Clash Grotesk (Fontshare; the CruiseControl wordmark face) for UI and the wordmark (600 "Station", 200 italic
  "Generator"); the system mono for numbers, seeds and block ids. Falls back to the system sans when offline.
- Section labels: 11 px caps, 0.08 em tracking, muted. Field labels 13 px semibold. Values in mono, accent.
- Radius: 10 px controls, 14 px panels. Hairline borders, no shadows except the main button.
- Motion: one treatment per role. Segments and chips fill on press. Primary buttons lift 1 px and brighten. The seed flips.
  Reduced motion: no flips, no glide; the Silk shader stops.
- Idle: the Silk background drifts at rest; nothing else moves on its own.

## Layout

```
| sidebar 232 | settings 316 | 3D view (1fr)             | build 316   |
| wordmark    |  top bar: Station Generator v1.6.0 · where              |
| profile     | STATION      |                           | BUILD       |
| nav         | SEED         |      canvas               | CHECKS      |
|             | SHAPE        |                           | LAYERS      |
|             | BUILDING     | status line               | CUTAWAY     |
|             | NAME         | [view bar ........ Fit]   | MATERIALS   |
| status      | ADVANCED     |                           | [main CTA]  |
|             |  disclaimer line                                        |
```

Under 1180 px the build column moves under the view; under 860 px everything stacks with the view first, and the sidebar
becomes a top bar.
