package com.aureliatransit.architecture.wayfinding;

/**
 * Generic, original pictograms (no operator symbols).
 */
public enum Pictogram {
	/** No pictogram. */
	NONE,
	/** Step-free / accessible route. */
	ACCESSIBLE_ROUTE,
	/** Lift. */
	ELEVATOR,
	/** Escalator. */
	ESCALATOR,
	/** Stairs. */
	STAIRS,
	/** Exit. */
	EXIT,
	/** Entrance. */
	ENTRANCE,
	/** Transfer / interchange. */
	TRANSFER,
	/** Bus. */
	BUS,
	/** Train / metro. */
	TRAIN,
	/** Trolley / tram. */
	TROLLEY,
	/** Help point. */
	HELP_POINT,
	/** Information. */
	INFORMATION,
	/** Tickets. */
	TICKETS;

	public static Pictogram byOrdinal(int ordinal) {
		final Pictogram[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
