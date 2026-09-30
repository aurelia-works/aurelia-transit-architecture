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
| Server/client clock skew | `ArrivalsCacheClient.INSTANCE.getMillisOffset()` | Local time = arrival - offset. |
| Fields of a train | `ArrivalResponse`: `getDestination`, `getArrival`, `getDeparture`, `getDeviation`, `getRealtime`, `getIsTerminating`, `getRouteId/Name/Number/Color`, `getPlatformId/Name` | |
| Calling points | `SimplifiedRoute.getPlatforms()` (`SimplifiedRoutePlatform.getStationName`), `SimplifiedRoute.getPlatformIndex(platformId)` | Stops after the current platform index, consecutive duplicate stations removed. |

MTR uses a relocated fastutil (`org.mtr.libraries.it.unimi.dsi.fastutil...`); that is the only place those types appear (`MtrStationDataProvider`).

## The provider (`MtrStationDataProvider`)

Installed with `StationData.install(...)` from `LiveClient.init()` when the `mtr` mod is loaded. Three bounded `TimedLruCache`s:

1. **snapshots** keyed by (block position, association, withServices): the final `StationSnapshot`. Refreshed at most every 2 s; callers may ask every frame or every second and will mostly hit the cache.
2. **resolutions** keyed by (position, association): which MTR station and platforms a block belongs to, plus the nearest platform. This is the expensive part (two linear scans), so it is refreshed every 5 s.
3. **services** keyed by the platform-id set: the `ServiceSnapshot` list built from `requestArrivals`. Shared by every display and speaker that uses the same platforms. A platform stays in MTR's poll set only while somebody keeps requesting it, so a refresh (every 2 s) is also the keep-alive, and it happens only while something still asks. `withServices == false` never touches this cache.

Every cache evicts least-recently-used entries beyond its bound (512 / 512 / 128) and sweeps entries that were not requested for 30 s.

`StationSnapshot.version` is stabilised by `SnapshotVersions`: a refresh producing content equal to the previous snapshot returns the previous object with the same version. Arrival and departure times are quantised to whole seconds so sub-second jitter from MTR does not bump the version.

### AUTO association

`AUTO` = the station whose area contains the block; otherwise the nearest platform within 5 blocks of the position lowered by 4 (its station is used when it has one). A platform CIS/PIDS with an AUTO association additionally filters to the platform next to it (`NearestPlatformProvider`, an optional interface implemented by our provider, not part of the shared `StationDataProvider` contract); concourse boards always show the whole station. `MANUAL` uses the chosen station; an empty platform list means all of its platforms. A station's platform list is capped at 16 (requests to MTR stay bounded for enormous stations).

### Lists for config screens

`listStations(limit)` is cached for 5 s and sorted by display name. `listPlatforms(stationId)` is sorted numerically-aware ("2" before "10").

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
