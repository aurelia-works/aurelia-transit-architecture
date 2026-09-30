package com.aureliatransit.architecture.wayfinding;

import net.minecraft.util.math.BlockPos;

/**
 * Supplies {@link StationFacts} for a block position. The MTR-backed implementation is client-side, cached and bounded
 * (it reuses the existing station-data caches); {@link #NONE} is used when MTR is absent and on the dedicated server.
 * Callers may ask every frame; implementations must be cheap on repeat calls.
 */
public interface WayfindingSource {

	/**
	 * @param auto  resolve the station at {@code pos} (AUTO association); when false nothing is looked up
	 */
	StationFacts facts(BlockPos pos, boolean auto);

	WayfindingSource NONE = (pos, auto) -> StationFacts.EMPTY;
}
