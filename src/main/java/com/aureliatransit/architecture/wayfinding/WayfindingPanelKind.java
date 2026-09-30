package com.aureliatransit.architecture.wayfinding;

/**
 * Which consumer draws a wayfinding panel. Layout code uses it to decide which fields show and how.
 */
public enum WayfindingPanelKind {
	/** Tall entrance totem: station name, lines, entrance/exit label. */
	ENTRANCE_PYLON,
	/** Wall directional sign: arrow, destination, lines, service label. */
	WALL_DIRECTION,
	/** Hanging directional sign (same content as wall). */
	HANGING_DIRECTION,
	/** Street / landmark / connection blade. */
	STREET,
	/** Small pictogram sign with a short caption. */
	PICTOGRAM,
	/** Platform/track sign: platform, direction, destination, lines. */
	PLATFORM,
	/** Exit sign: exit label and exit destinations. */
	EXIT,
	/** Bus stop / e-paper header: stop name, routes. */
	BUS_STOP;

	public static WayfindingPanelKind byOrdinal(int ordinal) {
		final WayfindingPanelKind[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : WALL_DIRECTION;
	}
}
