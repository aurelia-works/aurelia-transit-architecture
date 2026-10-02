package com.aureliatransit.architecture.transit;

import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * The single shared source of MTR station data for every Aurelia display, sign and speaker.
 *
 * <p>Contract for implementations (the MTR-backed one lives client-side):
 * <ul>
 *     <li>All methods are called on the client render/tick thread and must be cheap: they return cached data and
 *     refresh a given key at most every ~2 seconds. They never scan the whole world.</li>
 *     <li>Caches are bounded (LRU) and entries unused for a while are evicted.</li>
 *     <li>Station lookups for {@code withServices == false} must not add platforms to MTR's arrival polling.</li>
 *     <li>Never returns null; returns {@link StationSnapshot#EMPTY} when nothing can be resolved.</li>
 * </ul>
 */
public interface StationDataProvider {

	StationDataProvider NONE = new StationDataProvider() {
		@Override
		public StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices) {
			return StationSnapshot.EMPTY;
		}

		@Override
		public List<StationReference> listStations(int limit) {
			return List.of();
		}

		@Override
		public List<PlatformReference> listPlatforms(long stationId) {
			return List.of();
		}
	};

	StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices);

	/**
	 * As {@link #resolve(BlockPos, StationAssociation, boolean)}, optionally with calling-point times
	 * ({@code ServiceSnapshot.callingAtMillis}). Asking for times may add the calling points' platforms (bounded) to the
	 * same arrivals request; implementations without times return the plain snapshot.
	 */
	default StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices, boolean withCallingTimes) {
		return resolve(pos, association, withServices);
	}

	/**
	 * Stations for manual-selection screens, sorted by name.
	 */
	List<StationReference> listStations(int limit);

	List<PlatformReference> listPlatforms(long stationId);
}
