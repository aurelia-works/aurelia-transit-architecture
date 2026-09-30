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
	 * @return the platform id close to {@code pos}, or 0 when there is none (or the association is MANUAL)
	 */
	long nearestPlatformId(BlockPos pos, StationAssociation association);
}
