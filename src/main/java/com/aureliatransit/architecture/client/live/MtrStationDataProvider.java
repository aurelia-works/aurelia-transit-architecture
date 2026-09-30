package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.LiveDebug;
import com.aureliatransit.architecture.live.NearestPlatformProvider;
import com.aureliatransit.architecture.live.cache.HeldServices;
import com.aureliatransit.architecture.live.cache.ServerClockOffset;
import com.aureliatransit.architecture.live.cache.ServiceOrder;
import com.aureliatransit.architecture.live.cache.SnapshotVersions;
import com.aureliatransit.architecture.live.cache.TimedLruCache;
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

	private record SnapshotKey(long pos, StationAssociation association, boolean withServices) {
	}

	private record ResolutionKey(long pos, StationAssociation association) {
	}

	private record ServiceKey(List<Long> platformIds) {
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
		final long posKey = pos.asLong();
		return snapshots.get(new SnapshotKey(posKey, association, withServices), (key, previous) -> {
			LiveDebug.count(LiveDebug.Counter.PROVIDER_REFRESH);
			final Resolution resolution = resolution(pos, association);
			final List<ServiceSnapshot> list = withServices && !resolution.platformIds().isEmpty()
					? services.get(new ServiceKey(resolution.platformIds()), (serviceKey, old) -> HeldServices.next(old, buildServices(serviceKey.platformIds()), clock.getAsLong())).services()
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
		if (now - stationListAt > RESOLVE_MILLIS) {
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

	private List<ServiceSnapshot> buildServices(List<Long> platformIds) {
		final LongArrayList ids = new LongArrayList(platformIds.size());
		for (final long id : platformIds) {
			ids.add(id);
		}
		LiveDebug.count(LiveDebug.Counter.ARRIVAL_REQUESTS);
		final ObjectArrayList<ArrivalResponse> arrivals = ArrivalsCacheClient.INSTANCE.requestArrivals(ids);
		final long offset = serverOffset.stabilize(ArrivalsCacheClient.INSTANCE.getMillisOffset());
		final long now = clock.getAsLong();
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final List<ServiceSnapshot> out = new ArrayList<>(arrivals.size());
		for (final ArrivalResponse arrival : arrivals) {
			// to local client time with a held offset, quantised to whole seconds, so jitter does not change snapshot content
			final long arrivalMillis = ServerClockOffset.toLocalSecond(arrival.getArrival(), offset);
			final long departureMillis = ServerClockOffset.toLocalSecond(arrival.getDeparture(), offset);
			if (departureMillis < now - 1_000) {
				continue;
			}
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
					callingAt(data, arrival)));
		}
		out.sort(ServiceOrder.COMPARATOR);
		return out.size() > StationSnapshot.MAX_SERVICES ? List.copyOf(out.subList(0, StationSnapshot.MAX_SERVICES)) : List.copyOf(out);
	}

	/**
	 * Stops after this platform on the service's route, display names, consecutive duplicates removed.
	 */
	private static List<String> callingAt(MinecraftClientData data, ArrivalResponse arrival) {
		final SimplifiedRoute route = data.simplifiedRouteIdMap.get(arrival.getRouteId());
		if (route == null) {
			return List.of();
		}
		final ObjectArrayList<SimplifiedRoutePlatform> platforms = route.getPlatforms();
		final int index = route.getPlatformIndex(arrival.getPlatformId());
		if (index < 0 || index >= platforms.size() - 1) {
			return List.of();
		}
		String previous = StationNames.display(platforms.get(index).getStationName());
		final List<String> stops = new ArrayList<>();
		for (int i = index + 1; i < platforms.size() && stops.size() < ServiceSnapshot.MAX_CALLING_AT; i++) {
			final String name = StationNames.display(platforms.get(i).getStationName());
			if (!name.isEmpty() && !name.equals(previous)) {
				stops.add(name);
				previous = name;
			}
		}
		return stops;
	}

	// ---- diagnostics -------------------------------------------------------------------------------------------------

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
