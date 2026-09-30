package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.wayfinding.WayfindingData;
import net.minecraft.util.math.BlockPos;

/**
 * Data behind the passenger terminal UI that is not already available through {@code StationData} (departures),
 * {@code Wayfinding} (station facts) and the client service messages. Client-side, cached and bounded; called only
 * while a terminal screen is open (never per frame from the idle renderer). {@link #NONE} without MTR.
 */
public interface TerminalSource {

	/**
	 * @param currentStationId MTR id of the terminal's station, 0 when none
	 */
	SystemMap systemMap(long currentStationId);

	/**
	 * @param data the terminal's own wayfinding metadata (station override, code, transfers, street)
	 */
	StationInfo stationInfo(BlockPos pos, WayfindingData data);

	TerminalSource NONE = new TerminalSource() {
		@Override
		public SystemMap systemMap(long currentStationId) {
			return SystemMap.EMPTY;
		}

		@Override
		public StationInfo stationInfo(BlockPos pos, WayfindingData data) {
			return StationInfo.EMPTY;
		}
	};
}
