package com.aureliatransit.architecture.client.wayfinding.logic;

import com.aureliatransit.architecture.live.cache.TimedLruCache;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationNames;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.wayfinding.ExitInfo;
import com.aureliatransit.architecture.wayfinding.LineBadges;
import com.aureliatransit.architecture.wayfinding.StationFacts;
import com.aureliatransit.architecture.wayfinding.WayfindingSource;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Route;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.SimplifiedRoutePlatform;
import org.mtr.core.data.Station;
import org.mtr.core.data.StationExit;
import org.mtr.mod.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The MTR-backed {@link WayfindingSource}: line badges and exits of the station at a position, read from the data MTR
 * already synced to this client. Nothing is polled from the server and nothing blocks; any failure or missing data
 * gives empty lists.
 *
 * <p>Two bounded {@link TimedLruCache}s sit on top of the shared station provider's (already cached) AUTO resolution:
 * <ol>
 *     <li>per block position (refreshed every 1 s): which station the block is in, so a renderer asking every frame
 *     costs one map lookup;</li>
 *     <li>per station id (refreshed every 5 s): the lines and exits. Building them scans MTR's simplified routes once.</li>
 * </ol>
 * Both evict entries nobody asks for any more.
 *
 * <p>What is available client-side in MTR 4.0.5: {@code simplifiedRoutes} (id, name, colour, platforms with station
 * ids) is always synced; it has no route number and no hidden flag. The full {@code Route} (number, hidden) is used
 * when MTR has it in {@code routeIdMap} (e.g. after its dashboard loaded it); otherwise the label is derived from the
 * route name by {@link LineBadges}.
 */
public final class MtrWayfindingSource implements WayfindingSource {

	static final long POSITION_REFRESH_MILLIS = 1_000;
	static final long STATION_REFRESH_MILLIS = 5_000;
	static final long EVICT_MILLIS = 30_000;

	private record Resolved(StationReference station, List<Long> platformIds) {
	}

	/** A block position together with its association, so an AUTO block and a MANUAL one never share an entry. */
	private record Key(long pos, StationAssociation association) {
	}

	private final TimedLruCache<Key, Resolved> byPosition;
	private final TimedLruCache<Long, StationFacts> byStation;

	public MtrWayfindingSource() {
		this(System::currentTimeMillis);
	}

	MtrWayfindingSource(LongSupplier clock) {
		this.byPosition = new TimedLruCache<>(512, POSITION_REFRESH_MILLIS, EVICT_MILLIS, clock);
		this.byStation = new TimedLruCache<>(128, STATION_REFRESH_MILLIS, EVICT_MILLIS, clock);
	}

	@Override
	public StationFacts facts(BlockPos pos, boolean auto, StationAssociation association) {
		if (!auto) {
			return StationFacts.EMPTY;
		}
		final Resolved resolved = byPosition.get(new Key(pos.asLong(), association), (key, previous) -> resolve(pos, association, previous));
		if (resolved.station() == null) {
			return StationFacts.EMPTY;
		}
		return byStation.get(resolved.station().id(), (id, previous) -> build(resolved, previous));
	}

	private static final Resolved NONE = new Resolved(null, List.of());

	private static Resolved resolve(BlockPos pos, StationAssociation association, Resolved previous) {
		final StationSnapshot snapshot = StationData.provider().resolve(pos, association, false);
		final StationReference station = snapshot.station();
		if (station == null) {
			return NONE;
		}
		if (previous != null && previous.station() != null && previous.station().equals(station) && sameIds(previous.platformIds(), snapshot.platforms())) {
			return previous;
		}
		final List<Long> ids = new ArrayList<>(snapshot.platforms().size());
		for (final PlatformReference platform : snapshot.platforms()) {
			ids.add(platform.id());
		}
		return new Resolved(station, List.copyOf(ids));
	}

	private static boolean sameIds(List<Long> ids, List<PlatformReference> platforms) {
		if (ids.size() != platforms.size()) {
			return false;
		}
		for (int i = 0; i < ids.size(); i++) {
			if (ids.get(i) != platforms.get(i).id()) {
				return false;
			}
		}
		return true;
	}

	private static StationFacts build(Resolved resolved, StationFacts previous) {
		try {
			final MinecraftClientData data = MinecraftClientData.getInstance();
			final long stationId = resolved.station().id();
			final Set<Long> platformIds = new HashSet<>(resolved.platformIds());
			final List<LineBadges.RawLine> raw = new ArrayList<>();
			for (final SimplifiedRoute route : data.simplifiedRoutes) {
				if (serves(route, stationId, platformIds)) {
					final Route full = data.routeIdMap.get(route.getId());
					raw.add(new LineBadges.RawLine(route.getId(), route.getName(), full == null ? "" : full.getRouteNumber(), route.getColor(),
							full != null && full.getHidden()));
				}
			}
			final List<ExitInfo> exits = new ArrayList<>();
			final Station station = stationId > 0 ? data.stationIdMap.get(stationId) : null;
			if (station != null) {
				for (final StationExit exit : station.getExits()) {
					final List<String> destinations = new ArrayList<>();
					for (final String destination : exit.getDestinations()) {
						destinations.add(StationNames.display(destination));
					}
					exits.add(new ExitInfo(StationNames.display(exit.getName()), destinations));
				}
				exits.sort((a, b) -> LineBadges.compareLabels(a.label(), b.label()));
			}
			final StationFacts facts = new StationFacts(resolved.station(), LineBadges.build(raw), exits);
			return facts.equals(previous) ? previous : facts;
		} catch (RuntimeException unavailable) {
			// MTR data changing under us or an API difference: show what we know (the station), never throw into a renderer
			return new StationFacts(resolved.station(), List.of(), List.of());
		}
	}

	private static boolean serves(SimplifiedRoute route, long stationId, Set<Long> platformIds) {
		for (final SimplifiedRoutePlatform platform : route.getPlatforms()) {
			if ((stationId > 0 && platform.getStationId() == stationId) || platformIds.contains(platform.getPlatformId())) {
				return true;
			}
		}
		return false;
	}

	public String describeCaches() {
		return "positions=" + byPosition.size() + ", stations=" + byStation.size() + " (refreshes " + byStation.refreshCount() + ")";
	}
}
