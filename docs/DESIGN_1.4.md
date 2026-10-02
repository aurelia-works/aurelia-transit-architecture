# ATA 1.4 design notes

Short notes written before the code, one per 1.4 request (STATUS → *Requests raised during the 1.3 check*). Constraints for all of them: ATA stays an MTR addon (no mixins, no MTR changes), the potato-PC contract (triage §11: no per-tick block-entity work, no new server polling, bounded caches, static first, fail visibly), and the door-open contract in [MTR_INTEGRATION.md](MTR_INTEGRATION.md#platform-blocks-and-train-doors): MTR opens a doorway when a block near it implements `PlatformHelper` or is an unlocked MTR PSD/APG door, by block type only.

## a. Concourse board: Platform heading, departures/arrivals, station summary

**Want:** an explicit "Platform" column heading, a Departures / Arrivals choice, and a station summary (number of platforms, "your" platform).

**Data, all already on the client:**

- Rows already carry MTR's platform name (`ServiceSnapshot.platformName`). The heading is layout only.
- Arrivals: MTR's `ArrivalResponse` has an arrival time at this station for every train, including terminating ones. "From" is the first stop of the train's route (`SimplifiedRoute.getPlatforms().get(0)`), which is MTR data. Arrivals mode sorts by arrival and shows "from <origin>" and the arrival time. Nothing is guessed: a route MTR has not sent shows no origin.
- Summary: the platform count is the resolution's platform list (capped at 16, as today). "Your platform" is the platform nearest the board (`NearestPlatformProvider`, 5 blocks, the same rule MTR's PIDS uses). With none nearby the part is left out.

**Config:** `DisplayConfig` gains `arrivals` (default departures) and `summary` (default off), stored like `callingTimes`. Concourse boards only.

**Cost:** none new. The same snapshot and request; the board rebuilds at the same once-per-second rate.

## b. Curved platform screen doors

**Want:** PSD/APG pieces that follow ATA's 45°, convex and concave edges.

**Constraint:** MTR's PSDs are straight blocks with MTR-side door logic. ATA cannot add doors that MTR opens and closes, and it cannot animate per train without per-tick work on the server.

**Design (static, honest):** a `curved_screen_door` family matching the three curve kinds plus a straight piece:

- **screen**: a fixed glass screen with a header band, following the curve's edge line (`CurveKind.reach`), solid collision.
- **doorway**: the same frame with an open doorway: no collision in the opening, and it implements MTR's `PlatformHelper` marker, so train doors beside it open exactly as beside a platform edge. It is always open; there is no closing leaf.

Builders line a curved platform with screens and put doorways where the train doors stop. This meets MTR's door check as it is. A moving door is not provided: it would need MTR door state (not exposed) or per-tick work. Documented as such.

## c. Platform-integrated drop-down screen door

**Want:** an edge block whose barrier lowers into the block while a train is in.

**Design:** `drop_barrier_edge`: a platform edge (walkable top, `PlatformHelper` marker, so doors open) with barrier posts and bars on the coping.

- **The visual is client-side and keyed to the train:** a block entity *without* a ticker; its renderer runs only while the block is visible and in range. At most once a second per block, it asks the existing provider whether a train is standing at the nearest platform: `ServiceSnapshot.isStanding`, from the same cached snapshot a PIDS at that platform uses, so one request per platform set, never per block. The bars slide down over 1 s when a train stands and rise after it leaves.
- **Collision:** the server cannot know without polling. The bars have no collision, and the edge surface is the only collision. The barrier is a visual cue, not a safety barrier. Documented.
- **Fails visibly:** with no MTR platform within 5 blocks, the bars stay up.

**Contract check:** no ticker and no server work. The client lookup is the cached 2 s provider snapshot, at most 1/s per visible block, and the renderer draws only when visible. Measured with the stress counters before it is called done.

## d. Automatic boarding step / gap filler

**Want:** a platform-edge piece that pushes a step toward the train while one is stopping.

**Design:** `boarding_step_edge`. The same mechanism as (c): an edge block (`PlatformHelper`) whose renderer slides a thin step plate out over the gap (up to 4 px beyond the block face) while a train stands at the nearest platform. The step is visual only, with no collision change, because MTR moves players on and off trains itself and the walkable surface is the edge block. Keyed to the train in the same way: the cached snapshot of the nearest platform, checked at most once a second. With no platform nearby it stays retracted.

## Blocked items, built as far as honest (package 5)

- **A17 PSD text panel:** a header panel above PSDs with text typed by hand (the `TextSignBlock` system, new style). MTR's own PSD text is not reachable without MTR support.
- **A16 "Stand back, non-stopping trains" sign:** a static warning sign whose text and pictogram are set by hand. MTR exposes no through trains, so it is never automatic.
- **A18 Train composition / coach board:** a board with car positions typed by hand (car letters or numbers, class or accessibility marks, sector letters), drawn as car boxes. MTR exposes car count (`ArrivalResponse.getCarCount`) but no per-car class or layout. The manual board stays manual. A later "car count from MTR" option is recorded as a request, not built.
