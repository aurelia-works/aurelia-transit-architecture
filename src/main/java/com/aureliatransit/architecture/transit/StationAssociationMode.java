package com.aureliatransit.architecture.transit;

/**
 * How a display, sign or speaker decides which MTR station/platforms it belongs to.
 */
public enum StationAssociationMode {
	/**
	 * Use the MTR station whose area contains the block, falling back to the closest platform nearby.
	 */
	AUTO,
	/**
	 * Use the station/platform ids chosen in the block's configuration screen.
	 */
	MANUAL;

	public static StationAssociationMode byOrdinal(int ordinal) {
		final StationAssociationMode[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AUTO;
	}
}
