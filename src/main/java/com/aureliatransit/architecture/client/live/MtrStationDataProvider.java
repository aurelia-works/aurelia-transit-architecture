package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.LiveDebug;
import com.aureliatransit.architecture.live.NearestPlatformProvider;
import com.aureliatransit.architecture.live.cache.HeldServices;
import com.aureliatransit.architecture.live.cache.ServerClockOffset;
import com.aureliatransit.architecture.live.cache.ServiceOrder;
import com.aureliatransit.architecture.live.cache.SnapshotVersions;
import com.aureliatransit.architecture.live.cache.TimedLruCache;
import com.aureliatransit.architecture.live.display.CallingTimes;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationDataProvider;
import com.aureliatransit.architecture.transit.StationNames;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Platform;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.SimplifiedRoutePlatform;
import org.mtr.core.data.Station;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.ArrivalsCacheClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The shared MTR-backed {@link StationDataProvider}. Everything here is client-side and reads MTR's already-synced
 * client data plus its shared arrivals cache; nothing extra is polled from the server.
 *
 * <p>Three bounded caches, all refreshed lazily and only while somebody keeps asking:
 * <ol>
 *     <li><b>snapshots</b> per (block position, association, withServices): the final {@link StationSnapshot}, refreshed
 *     at most every {@value #REFRESH_MILLIS} ms;</li>
 *     <li><b>resolutions</b> per (position, association): which MTR station and platforms a block belongs to. This uses
 *     MTR's linear station/platform scans, so it is refreshed only every {@value #RESOLVE_MILLIS} ms;</li>
 *     <li><b>services</b> per platform-id set: built from {@code ArrivalsCacheClient.requestArrivals}, shared by every
 *     display and speaker of the same platforms, refreshed every {@value #REFRESH_MILLIS} ms (which also keeps the
 *     platforms in MTR's poll set). A refresh that briefly returns nothing keeps the not-yet-departed previous
 *     services for a short grace period ({@link HeldServices}). Never consulted for {@code withServices == false}.</li>
 * </ol>
 */
public final class MtrStationDataProvider implements StationDataProvider, NearestPlatformProvider {

	static final long REFRESH_MILLIS = 2_000;
	static final long RESOLVE_MILLIS = 5_000;
	static final long EVICT_MILLIS = 30_000;
	/**
	 * Platforms requested from MTR for one logical station (a safety bound for enormous AUTO stations).
	 */
	static final int MAX_PLATFORMS = 16;
	/**
	 * MTR's own PIDS looks for the closest platform within this many blocks of the block position lowered by 4.
	 */
	private static final int PLATFORM_SEARCH_RADIUS = 5;
	private static final int PLATFORM_SEARCH_DROP = 4;

	private record SnapshotKey(long pos, StationAssociation association, boolean withServices, boolean withTimes) {
	}

	private record ResolutionKey(long pos, StationAssociation association) {
	}

	/** {@code withTimes}: also request the calling points' platforms (bounded) to match calling-point times (A14). */
	private record ServiceKey(List<Long> platformIds, boolean withTimes) {
	}

	/** Calling points of one service: display names and, index for index, the platform MTR stops at. */
	private record Calling(List<String> names, List<Long> platformIds) {
		static final Calling NONE = new Calling(List.of(), List.of());
	}

	private record Resolution(StationReference station, List<PlatformReference> platforms, List<Long> platformIds, long nearestPlatformId) {
		static final Resolution EMPTY = new Resolution(null, List.of(), List.of(), 0);
	}

	private final LongSupplier clock;
	private final SnapshotVersions versions = new SnapshotVersions();
	private final TimedLruCache<SnapshotKey, StationSnapshot> snapshots;
	private final TimedLruCache<ResolutionKey, Resolution> resolutions;
	private final TimedLruCache<ServiceKey, HeldServices> services;
	private final ServerClockOffset serverOffset = new ServerClockOffset();

	private List<StationReference> stationList = List.of();
	private long stationListAt = Long.MIN_VALUE;

	public MtrStationDataProvider() {
		this(System::currentTimeMillis);
	}

	MtrStationDataProvider(LongSupplier clock) {
		this.clock = clock;
		this.snapshots = new TimedLruCache<>(512, REFRESH_MILLIS, EVICT_MILLIS, clock);
		this.resolutions = new TimedLruCache<>(512, RESOLVE_MILLIS, EVICT_MILLIS, clock);
		this.services = new TimedLruCache<>(128, REFRESH_MILLIS, EVICT_MILLIS, clock);
	}

	@Override
	public StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices) {
		return resolve(pos, association, withServices, false);
	}

	@Override
	public StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices, boolean withCallingTimes) {
		final long posKey = pos.asLong();
		final boolean times = withServices && withCallingTimes;
		return snapshots.get(new SnapshotKey(posKey, association, withServices, times), (key, previous) -> {
			LiveDebug.count(LiveDebug.Counter.PROVIDER_REFRESH);
			final Resolution resolution = resolution(pos, association);
			final List<ServiceSnapshot> list = withServices && !resolution.platformIds().isEmpty()
					? services.get(new ServiceKey(resolution.platformIds(), times),
					(serviceKey, old) -> HeldServices.next(old, buildServices(serviceKey.platformIds(), serviceKey.withTimes()), clock.getAsLong())).services()
					: List.of();
			return versions.stabilize(previous, resolution.station(), resolution.platforms(), list);
		});
	}

	@Override
	public long nearestPlatformId(BlockPos pos, StationAssociation association) {
		return association.isAuto() ? resolution(pos, association).nearestPlatformId() : 0;
	}

	@Override
	public List<StationReference> listStations(int limit) {
		final long now = clock.getAsLong();
		if (stationListAt == Long.MIN_VALUE || now - stationListAt > RESOLVE_MILLIS) {
			final List<StationReference> list = new ArrayList<>();
			for (final Station station : MinecraftClientData.getInstance().stations) {
				list.add(new StationReference(station.getId(), station.getName(), station.getColor()));
			}
			list.sort(Comparator.comparing(StationReference::displayName, String.CASE_INSENSITIVE_ORDER).thenComparingLong(StationReference::id));
			stationList = List.copyOf(list);
			stationListAt = now;
		}
		return stationList.size() > limit ? stationList.subList(0, Math.max(0, limit)) : stationList;
	}

	@Override
	public List<PlatformReference> listPlatforms(long stationId) {
		final Station station = MinecraftClientData.getInstance().stationIdMap.get(stationId);
		return station == null ? List.of() : platformReferences(station, station.savedRails);
	}

	// ---- resolution --------------------------------------------------------------------------------------------------

	private Resolution resolution(BlockPos pos, StationAssociation association) {
		return resolutions.get(new ResolutionKey(pos.asLong(), association), (key, previous) -> association.isAuto() ? resolveAuto(pos) : resolveManual(association));
	}

	private Resolution resolveAuto(BlockPos pos) {
		final Station station = InitClient.findStation(new org.mtr.mapping.holder.BlockPos(pos));
		final Platform[] nearest = new Platform[1];
		InitClient.findClosePlatform(new org.mtr.mapping.holder.BlockPos(pos.down(PLATFORM_SEARCH_DROP)), PLATFORM_SEARCH_RADIUS, platform -> nearest[0] = platform);
		final long nearestId = nearest[0] == null ? 0 : nearest[0].getId();
		if (station != null) {
			return build(station, station.savedRails, nearestId);
		}
		if (nearest[0] != null) {
			final Station owner = nearest[0].area;
			if (owner != null) {
				return build(owner, owner.savedRails, nearestId);
			}
			// A platform without a station: show just that platform.
			final Platform platform = nearest[0];
			final String stationName = platform.getStationName();
			final List<Long> ids = List.of(platform.getId());
			return new Resolution(stationName.isEmpty() ? null : new StationReference(-platform.getId(), stationName, platform.getColor()),
					List.of(new PlatformReference(platform.getId(), StationNames.display(platform.getName()), 0)), ids, nearestId);
		}
		return Resolution.EMPTY;
	}

	private Resolution resolveManual(StationAssociation association) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final Station station = association.stationId() == 0 ? null : data.stationIdMap.get(association.stationId());
		if (station == null) {
			return Resolution.EMPTY;
		}
		if (association.platformIds().isEmpty()) {
			return build(station, station.savedRails, 0);
		}
		final List<Platform> chosen = new ArrayList<>();
		for (final long id : association.platformIds()) {
			final Platform platform = data.platformIdMap.get(id);
			if (platform != null) {
				chosen.add(platform);
			}
		}
		return build(station, chosen, 0);
	}

	private static Resolution build(Station station, Iterable<Platform> platforms, long nearestPlatformId) {
		final List<PlatformReference> references = platformReferences(station, platforms);
		final List<Long> ids = new ArrayList<>(references.size());
		for (final PlatformReference reference : references) {
			ids.add(reference.id());
		}
		return new Resolution(new StationReference(station.getId(), station.getName(), station.getColor()), references, List.copyOf(ids), nearestPlatformId);
	}

	private static List<PlatformReference> platformReferences(Station station, Iterable<Platform> platforms) {
		final List<PlatformReference> list = new ArrayList<>();
		for (final Platform platform : platforms) {
			list.add(new PlatformReference(platform.getId(), StationNames.display(platform.getName()), station.getId()));
		}
		list.sort(Comparator.comparing(PlatformReference::name, MtrStationDataProvider::compareNames).thenComparingLong(PlatformReference::id));
		return list.size() > MAX_PLATFORMS ? List.copyOf(list.subList(0, MAX_PLATFORMS)) : List.copyOf(list);
	}

	/**
	 * Numeric-aware: "2" before "10", "2" before "2a".
	 */
	static int compareNames(String a, String b) {
		final Integer na = leadingNumber(a);
		final Integer nb = leadingNumber(b);
		if (na != null && nb != null && !na.equals(nb)) {
			return Integer.compare(na, nb);
		}
		return String.CASE_INSENSITIVE_ORDER.compare(a, b);
	}

	private static Integer leadingNumber(String s) {
		int end = 0;
		while (end < s.length() && end < 6 && Character.isDigit(s.charAt(end))) {
			end++;
		}
		return end == 0 ? null : Integer.valueOf(s.substring(0, end));
	}

	// ---- services ----------------------------------------------------------------------------------------------------

	private List<ServiceSnapshot> buildServices(List<Long> platformIds, boolean withTimes) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final LongArrayList ids = new LongArrayList(platformIds.size());
		for (final long id : platformIds) {
			ids.add(id);
		}
		if (withTimes) {
			addCallingPlatforms(data, platformIds, ids);
		}
		LiveDebug.count(LiveDebug.Counter.ARRIVAL_REQUESTS);
		final ObjectArrayList<ArrivalResponse> arrivals = ArrivalsCacheClient.INSTANCE.requestArrivals(ids);
		final long offset = serverOffset.stabilize(ArrivalsCacheClient.INSTANCE.getMillisOffset());
		final long now = clock.getAsLong();
		final List<CallingTimes.Arrival> all = new ArrayList<>(withTimes ? arrivals.size() : 0);
		if (withTimes) {
			for (final ArrivalResponse arrival : arrivals) {
				all.add(new CallingTimes.Arrival(arrival.getRouteId(), arrival.getDepartureIndex(), arrival.getPlatformId(),
						ServerClockOffset.toLocalSecond(arrival.getArrival(), offset)));
			}
		}
		final List<ServiceSnapshot> out = new ArrayList<>(arrivals.size());
		for (final ArrivalResponse arrival : arrivals) {
			if (withTimes && !platformIds.contains(arrival.getPlatformId())) {
				continue; // a calling point's platform, requested only for its times
			}
			// to local client time with a held offset, quantised to whole seconds, so jitter does not change snapshot content
			final long arrivalMillis = ServerClockOffset.toLocalSecond(arrival.getArrival(), offset);
			final long departureMillis = ServerClockOffset.toLocalSecond(arrival.getDeparture(), offset);
			if (departureMillis < now - 1_000) {
				continue;
			}
			final Calling calling = callingAt(data, arrival.getRouteId(), arrival.getPlatformId());
			out.add(new ServiceSnapshot(
					arrival.getRouteId(),
					StationNames.display(arrival.getRouteName()),
					arrival.getRouteNumber() == null ? "" : arrival.getRouteNumber(),
					arrival.getRouteColor(),
					StationNames.display(arrival.getDestination()),
					arrival.getPlatformId(),
					StationNames.display(arrival.getPlatformName()),
					arrivalMillis,
					departureMillis,
					arrival.getDeviation(),
					arrival.getRealtime(),
					arrival.getIsTerminating(),
					calling.names(),
					arrival.getDepartureIndex(),
					withTimes ? CallingTimes.match(arrival.getRouteId(), arrival.getDepartureIndex(), departureMillis, calling.platformIds(), all) : List.of(),
					origin(data, arrival.getRouteId())));
		}
		out.sort(ServiceOrder.COMPARATOR);
		return out.size() > StationSnapshot.MAX_SERVICES ? List.copyOf(out.subList(0, StationSnapshot.MAX_SERVICES)) : List.copyOf(out);
	}

	/**
	 * Adds the platforms of the calling points after {@code platformIds} on every route serving them, at most
	 * {@link CallingTimes#MAX_EXTRA_PLATFORMS} extra, nearest stops first and round-robin across routes, so one long
	 * route cannot use up the bound before the others get their next stop.
	 */
	private static void addCallingPlatforms(MinecraftClientData data, List<Long> platformIds, LongArrayList ids) {
		final int limit = ids.size() + CallingTimes.MAX_EXTRA_PLATFORMS;
		final List<List<Long>> ahead = new ArrayList<>();
		for (final SimplifiedRoute route : data.simplifiedRouteIdMap.values()) {
			final ObjectArrayList<SimplifiedRoutePlatform> stops = route.getPlatforms();
			for (int i = 0; i < stops.size() - 1; i++) {
				if (platformIds.contains(stops.get(i).getPlatformId())) {
					final List<Long> next = new ArrayList<>();
					for (int j = i + 1; j < stops.size() && next.size() < CallingTimes.MAX_EXTRA_PLATFORMS; j++) {
						next.add(stops.get(j).getPlatformId());
					}
					ahead.add(next);
				}
			}
		}
		for (int depth = 0; depth < CallingTimes.MAX_EXTRA_PLATFORMS && ids.size() < limit; depth++) {
			for (final List<Long> next : ahead) {
				if (depth < next.size() && ids.size() < limit && !ids.contains((long) next.get(depth))) {
					ids.add((long) next.get(depth));
				}
			}
		}
	}

	/** First stop of the route as MTR defines it, display name; empty when MTR has not sent the route. */
	private static String origin(MinecraftClientData data, long routeId) {
		final SimplifiedRoute route = data.simplifiedRouteIdMap.get(routeId);
		return route == null || route.getPlatforms().isEmpty() ? "" : StationNames.display(route.getPlatforms().get(0).getStationName());
	}

	/**
	 * Stops after this platform on the service's route, display names, consecutive duplicates removed.
	 */
	private static Calling callingAt(MinecraftClientData data, long routeId, long platformId) {
		final SimplifiedRoute route = data.simplifiedRouteIdMap.get(routeId);
		if (route == null) {
			return Calling.NONE;
		}
		final ObjectArrayList<SimplifiedRoutePlatform> platforms = route.getPlatforms();
		final int index = route.getPlatformIndex(platformId);
		if (index < 0 || index >= platforms.size() - 1) {
			return Calling.NONE;
		}
		String previous = StationNames.display(platforms.get(index).getStationName());
		final List<String> stops = new ArrayList<>();
		final List<Long> stopPlatforms = new ArrayList<>();
		for (int i = index + 1; i < platforms.size() && stops.size() < ServiceSnapshot.MAX_CALLING_AT; i++) {
			final String name = StationNames.display(platforms.get(i).getStationName());
			if (!name.isEmpty() && !name.equals(previous)) {
				stops.add(name);
				stopPlatforms.add(platforms.get(i).getPlatformId());
				previous = name;
			}
		}
		return new Calling(stops, stopPlatforms);
	}

	// ---- diagnostics -------------------------------------------------------------------------------------------------

	/**
	 * Diagnostic: per simplified route, its stops; then MTR's cached arrivals for every one of those platforms (route,
	 * departure index, platform, seconds to arrival). Run twice: the first call puts the platforms into MTR's poll.
	 */
	public List<String> describeRouteTimes() {
		final List<String> lines = new ArrayList<>();
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final LongArrayList ids = new LongArrayList();
		lines.add("Simplified routes: " + data.simplifiedRouteIdMap.size() + ", full routes: " + data.routes.size());
		for (final SimplifiedRoute route : data.simplifiedRouteIdMap.values()) {
			final StringBuilder stops = new StringBuilder();
			for (final SimplifiedRoutePlatform stop : route.getPlatforms()) {
				stops.append(StationNames.display(stop.getStationName())).append('/').append(stop.getPlatformId() % 1000).append(' ');
				if (ids.size() < 16 && !ids.contains(stop.getPlatformId())) {
					ids.add(stop.getPlatformId());
				}
			}
			lines.add(StationNames.display(route.getName()) + " (" + route.getId() % 1000 + "): " + stops.toString().trim());
		}
		final long now = System.currentTimeMillis();
		final long offset = ArrivalsCacheClient.INSTANCE.getMillisOffset();
		for (final ArrivalResponse arrival : ArrivalsCacheClient.INSTANCE.requestArrivals(ids)) {
			if (lines.size() > 40) {
				break;
			}
			lines.add("r" + arrival.getRouteId() % 1000 + " dep#" + arrival.getDepartureIndex() + " p" + arrival.getPlatformId() % 1000 + " arr+" + (arrival.getArrival() - offset - now) / 1000 + "s");
		}
		return lines;
	}

	public String describeCaches() {
		return "snapshots=" + snapshots.size() + " (hits " + snapshots.hitCount() + ", refreshes " + snapshots.refreshCount() + ", evicted " + snapshots.evictionCount()
				+ "), resolutions=" + resolutions.size() + " (refreshes " + resolutions.refreshCount() + "), services=" + services.size()
				+ " (refreshes " + services.refreshCount() + ")";
	}

	public void clear() {
		snapshots.clear();
		resolutions.clear();
		services.clear();
		serverOffset.reset();
		stationList = List.of();
		stationListAt = Long.MIN_VALUE;
	}
}
