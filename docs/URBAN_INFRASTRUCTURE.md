# Urban infrastructure (1.3)

Pieces for elevated railways above streets, as on el-style lines (Philadelphia, Chicago, New York, Berlin and others). All art is original and generic: there are no operator colours, logos or copies of real stations.

## One item, several looks

Every 1.3 piece is **one inventory item**. Its look is a block state:

- **Right-click with an empty hand** to cycle the style. The new style's name appears on the action bar.
- **Sneak + right-click** a viaduct beam with an empty hand to switch between steel and concrete.
- Holding anything, including a block, never cycles. Placing against a styled block works normally.

| Item | Styles (block state) | Placement |
|---|---|---|
| Viaduct Support Column | heavy steel (H-section), narrow steel, concrete pier, narrow concrete | vertical; stacks; meets beams directly |
| Viaduct Beam | crossbeam, girder, stringer (under-rail I-beam), platform support; each steel or concrete | along the way you look |
| Viaduct Brace | diagonal (45°), canopy knee bracket | rises toward the way you look |
| Station Stair | normal stair shapes (straight, inner, outer, upside down) | like any stair |
| Stair Enclosure Panel | clad, windowed, glazed | panel on the side you look at |
| Platform Wind Screen | lower (kick plate), upper (capping rail) | panel on the side you look at; stack upper on lower |
| Platform Fascia | plain, panelled, ribbed | panel on the side you look at |
| Station Fence | platform fencing, trackside safety mesh | joins neighbours |
| Utility Run | cable tray, conduit | along the way you look; vertical when placed against a wall |
| Under-Deck Light | (one look) | along the way you look; hangs under the block above |
| Handrail and Balustrade | handrail, glass balustrade, ramp edge rail | joins neighbours |
| Tactile Guidance Junction | turn, tee, crossing | bars follow the way you look |
| Angled / Curved Platform Edge | 45° edge, convex quarter, concave quarter | track side points the way you look |

There are no separate end pieces or left/middle/right items. Columns fill their block from top to bottom, and beams meet a column at the block boundary.

Fences and handrails join each other (any style), and they also join any full side face. Their collision is fence height (1.5 blocks), whatever their drawn height.

## Reused rather than duplicated

| Need | Existing piece used |
|---|---|
| Station glass, curtain walls | glass wall, glass panel, glass barrier |
| Canopy over an elevated platform | flat / sloped / wave canopy, canopy light and skylight, held up by the viaduct brace (knee) or the roof support |
| Platform surface and edge | platform paving and edges, now with angled and curved edge pieces |
| Lamps on the platform | platform lamp, canopy light |
| Vertical circulation signs (stairs, lift, accessible route, exit) | pictogram sign, which has all four symbols |
| Lift lobby information sign | pictogram sign (lift symbol plus a caption), or the station information board |
| Platform/track number, trains this side, service changes, transfers, exits | station information board views. They read the shared wayfinding model and the service-message list; nothing is stored twice |

## Building an elevated station

1. Columns every few blocks, with crossbeams across the top.
2. Girders along the line between crossbeams, and stringers under each rail. MTR rails go on top.
3. A platform support beam under the platform deck. Finish the deck edge with platform edge blocks (straight, angled or curved) and hang fascia below it.
4. Wind screens along the back of the platform. Use canopies on knee braces for shelter, station fence or handrails at open ends, and under-deck lights and utility runs below.
5. Station stairs inside stair enclosure panels down to the street. Station information boards on the mezzanine.

## Curved platforms: what ATA provides

**Provided:** angled and curved **platform edge pieces** (45°, convex quarter round, concave quarter round). They have the same section as the straight edge: walking surface at full height, face 2 px back under the coping. They use the **same MTR door marker** as the straight edge.

**Why that is safe.** MTR 4 opens a train doorway when a block in a small box around it is an instance of its marker interface `PlatformHelper` (or an unlocked PSD/APG). It checks only the block type, never the shape (see [MTR_INTEGRATION.md](MTR_INTEGRATION.md#platform-blocks-and-train-doors)). An angled outline therefore changes nothing for doors. Collision follows the drawn edge in 2 px steps, so you cannot walk past the coping.

**Not provided, on purpose: "functional" curved MTR platforms.** In MTR, a platform is a stretch of *rail* placed with MTR's platform tool. Its position, length, the dwell point and the stop logic come from rail nodes, not from blocks. Making ATA blocks define or bend a platform would mean changing MTR's rail and platform data or its station logic, which needs MTR internals or mixins. ATA rules both out. Curved stations work today: build MTR's platform rail along the curve as usual, then line it with ATA edge pieces.

**Limits:**

- The pieces follow a one-block radius. Wider curves are built from straight edges in a staircase, with an angled piece where the line changes direction.
- Gaps between a curved coping and a straight train body are cosmetic, as on real curved platforms.
- Doors still need an ATA platform piece, an MTR platform block or a PSD/APG within MTR's search box of each doorway.

## 1.4 additions

| Item | Styles | Notes |
|---|---|---|
| Noise Barrier | solid, solid half, glass, glass half, solid + glass | Panel on the far side of the block; stack for taller walls. |
| Fare Gate | gate, wide, end | Place side by side for a bank. A prop with no fare logic: the paddles have no collision, so the passage beside each cabinet is always walkable. |
| Card Reader | post, wall | Prop. |
| Booth Window | (one look) | Ticket-booth window with counter shelf; prop. |
| CCTV Camera | wall, pendant, dome | Prop, no collision. |
| Lift Status Panel | in service, out of service, maintenance | An editable sign: lift name, levels served; status set by hand in its editor. MTR lift state is not read. |
| Platform Screen Panel / Doorway | straight, 45°, convex, concave | The fixed panel and the always-open doorway follow the edge pieces. The doorway carries MTR's door marker. There is no moving leaf: MTR exposes no door state. |
| Drop Barrier / Boarding Step Platform Edge | (one look each) | Platform edges with MTR's door marker. The bars drop, or the step slides out, while MTR's arrival data says a train stands at the nearest platform. This is drawn client-side only, with no collision change. `/aurelia_live edge` shows what an edge sees. |

Design notes: [DESIGN_1.4.md](DESIGN_1.4.md).

## Performance

None of these blocks ticks. Only the 1.4 drop-barrier and boarding-step edges have a block entity, and it has no data and no ticker: its renderer runs only while the block is visible and finds its platform every 5 s, and the cached arrivals are checked once a second per platform. Each state's shapes are computed once and cached. Fence and handrail connections are block states, updated only when a neighbour changes. Models are static, with no dynamic textures.
