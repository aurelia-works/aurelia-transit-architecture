package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.transit.StationAssociation;
import net.minecraft.util.math.BlockPos;

/**
 * Optional extension of the station data provider used by platform displays: the id of the MTR platform right next to
 * a block, so an AUTO-associated CIS/PIDS can show only its own platform instead of the whole station.
 * Implemented by the MTR-backed provider (an additive helper, not part of the shared StationDataProvider contract).
 */
public interface NearestPlatformProvider {

	/**
	 * A display looks for the closest platform within this many blocks of its position lowered by {@link #SEARCH_DROP}.
	 * MTR's own PIDS uses 5, which misses the platforms of a screen on the middle column of a generated 11-wide island
	 * (6-7 blocks from either track; Jah 10-26: every board in a 5-island hub showed the same station-wide list) and the
	 * wall screens of a cut-and-cover box's wide side platforms (10-11). The closest platform still wins, so a wider
	 * search cannot pick a farther one. The planner keeps every platform screen within reach ({@code StationPlannerTest}).
	 */
	int SEARCH_RADIUS = 12;
	int SEARCH_DROP = 4;

	/**
	 * @return the platform id close to {@code pos}, or 0 when there is none (or the association is MANUAL)
	 */
	long nearestPlatformId(BlockPos pos, StationAssociation association);
}
