# MTR integration (live transit systems)

All live data is read **client-side** from Minecraft Transit Railway, in the same way MTR's own PIDS does. The server only stores per-block configuration (NBT, synced by normal block-entity update packets) and validates the config packets. No Aurelia code polls the server for train data.

Everything that touches MTR lives in `client/live/**`. Common and server classes (`live/`, `registry/LiveBlocks`) never reference MTR or `net.minecraft.client.*`, so a dedicated server never loads them.

## What we rely on (MTR 4.0.5, Minecraft 1.20.1)

| Need | MTR API | Notes |
|---|---|---|
| Station / platform / route tables | `org.mtr.mod.client.MinecraftClientData.getInstance()` (`stations`, `platforms`, `stationIdMap`, `platformIdMap`, `simplifiedRouteIdMap`) | Already synced to the client by MTR. |
| Station containing a block | `org.mtr.mod.InitClient.findStation(BlockPos)` | Linear scan over all stations. |
| Platform next to a block | `InitClient.findClosePlatform(BlockPos, radius, Consumer<Platform>)` | Linear scan over all platforms. Same parameters as MTR's own PIDS: position lowered by 4 blocks, radius 5. |
| Station's platforms | `Station.savedRails` (`Platform`s), `Platform.area` (its `Station`) | |
| Upcoming trains | `org.mtr.mod.data.ArrivalsCacheClient.INSTANCE.requestArrivals(LongCollection platformIds)` | Returns MTR's cached `ObjectArrayList<ArrivalResponse>`. MTR batches and rate-limits the server request itself. |
| Server/client clock skew | `ArrivalsCacheClient.INSTANCE.getMillisOffset()` | Local time = arrival - offset. MTR re-measures it on every response (with network jitter); we hold it steady, see below. |
| Train doors beside platforms | `org.mtr.mod.block.PlatformHelper` (marker interface) | See *Platform blocks and train doors*. |
| Fields of a train | `ArrivalResponse`: `getDestination`, `getArrival`, `getDeparture`, `getDeviation`, `getRealtime`, `getIsTerminating`, `getRouteId/Name/Number/Color`, `getPlatformId/Name` | |
| Calling points | `SimplifiedRoute.getPlatforms()` (`SimplifiedRoutePlatform.getStationName`), `SimplifiedRoute.getPlatformIndex(platformId)` | Stops after the current platform index, consecutive duplicate stations removed. |

MTR uses a relocated fastutil (`org.mtr.libraries.it.unimi.dsi.fastutil...`); that is the only place those types appear (`MtrStationDataProvider`).

## The provider (`MtrStationDataProvider`)

Installed with `StationData.install(...)` from `LiveClient.init()` when the `mtr` mod is loaded. Three bounded `TimedLruCache`s:

1. **snapshots** keyed by (block position, association, withServices): the final `StationSnapshot`. Refreshed at most every 2 s; callers may ask every frame or every second and will mostly hit the cache.
2. **resolutions** keyed by (position, association): which MTR station and platforms a block belongs to, plus the nearest platform. This is the expensive part (two linear scans), so it is refreshed every 5 s.
3. **services** keyed by the platform-id set: the `ServiceSnapshot` list built from `requestArrivals`. Shared by every display and speaker that uses the same platforms. A platform stays in MTR's poll set only while somebody keeps requesting it, so a refresh (every 2 s) is also the keep-alive, and it happens only while something still asks. `withServices == false` never touches this cache.

Every cache evicts least-recently-used entries beyond its bound (512 / 512 / 128) and sweeps entries that were not requested for 30 s.

`StationSnapshot.version` is stabilised by `SnapshotVersions`: a refresh producing content equal to the previous snapshot returns the previous object with the same version. Identical timetable data must give an identical snapshot, so:

- **Held clock offset** (`ServerClockOffset`): MTR's offset moves by network/tick jitter on every response. Converting with the raw value and rounding to whole seconds made times flip between neighbouring seconds, which changed content, swapped rows and flipped minute texts with no real change. The held offset follows MTR only when it moves by more than 1 s.
- **Total row order** (`ServiceOrder`): arrival, departure, platform id, route id, destination, route number, route name. Services due in the same second no longer take MTR's response order, which varies.
- **Short hold of the last valid list** (`HeldServices`): MTR replaces its client arrivals cache on every response. If a refresh comes back empty, the previous services that have not yet departed stay for up to 10 s. After that the board goes idle. Nothing is invented.

These are covered by `BoardStabilityTest`. It also reproduces the 1.1.0 behaviour, where the same timetable churned versions and swapped rows across 50 simulated refreshes.

### AUTO association

`AUTO` = the station whose area contains the block; otherwise the nearest platform within 5 blocks of the position lowered by 4 (its station is used when it has one). A platform CIS/PIDS with an AUTO association shows only the platform next to it (`NearestPlatformProvider`, an optional interface implemented by our provider, not part of the shared `StationDataProvider` contract). It requests that one platform's services, not the whole station's first 12, so its trains can't drop out behind other platforms' trains at a large station. Concourse boards always show the whole station. `MANUAL` uses the chosen station; an empty platform list means all of its platforms. A station's platform list is capped at 16 (requests to MTR stay bounded for enormous stations).

### Lists for config screens

`listStations(limit)` is cached for 5 s and sorted by display name. `listPlatforms(stationId)` is sorted numerically-aware ("2" before "10").

## Platform blocks and train doors

MTR 4 opens a vehicle doorway only when a block near it is one of MTR's platform blocks. The check is `RenderVehicleHelper.canOpenDoors`, which looks at blocks from one block beside the doorway box and from two blocks below to two above. A block counts if it implements `org.mtr.mod.block.PlatformHelper`, or if it is an unlocked PSD/APG door. The check looks at the block's **type only**. Shapes and collision are never consulted.

In ATA 1.1.0, platform pieces were plain blocks. A platform edge built from them kept train doors shut, because MTR found no platform block beside the doorway.

From this build, ATA's walkable platform surfaces implement MTR's `PlatformHelper` marker, so doors open next to them exactly as beside MTR's own platform blocks:

- platform edge
- platform edge with warning line
- angled / curved platform edge (1.3: 45°, convex and concave; same marker, see [URBAN_INFRASTRUCTURE.md](URBAN_INFRASTRUCTURE.md#curved-platforms-what-ata-provides))
- light and dark platform paving
- tactile warning paving and tactile guidance paving

- `PlatformHelper` has no abstract methods. In MTR 4.0.3 and 4.0.5, MTR tests it only with `instanceof`: in the door check, and to show rails while you hold a platform item. MTR reads no block-state properties through it. There is no mixin, and nothing in MTR changes.
- `MtrPlatformContract` checks at startup that the interface exists and is still a pure marker. Only then does it load the block classes that implement it. If a future MTR changes it, ATA registers plain blocks (doors then need an MTR platform block nearby) and logs a warning instead of failing to load. The startup log line reports `MTR platform door contract: enabled|unavailable`.
- Ramps are not platforms and do not carry the marker. Neither do bus curbs, nor the 1.3 tactile junctions (decorative paving for mezzanines and streets).
- MTR still decides where trains stop from its own platform rails and schedules. The marker only satisfies the "is there a platform beside this door" check.

## Known limitations (deliberately not faked)

- **Non-stopping / through trains** never appear in MTR's arrival data, so there are no "through service" announcements or displays. Nothing is invented.
- **Platform alterations / cancellations** are not exposed by MTR. `ServiceSnapshot.platformId` is whatever MTR reports.
- **Delays** are only known when MTR reports `realtime` data; `deviation` below one minute is not flagged.
- Times shown as "N min" are relative to the local clock; the clock option shows in-game time of day (tick 0 = 06:00), not MTR schedule time.
- Arrivals exist only for routes MTR is currently dispatching. A station without scheduled trains shows the idle message.
- Everything is client-side: a player only hears announcements for speakers within range of themselves, and every player's client makes its own (MTR-cached) request.

## Safeguards summary

- No block entity ticking: displays are drawn by a block-entity renderer that only runs when visible (range, facing), and speakers are found through Fabric's client block-entity load/unload events.
- Announcement engine runs every 20 client ticks and does nothing when no speaker is loaded; it only resolves speakers whose radius contains the player.
- Debug counters (`-Daurelia.live.debug=true` or `/aurelia_live debug true`, then `/aurelia_live stats`) are off by default.
