package com.aureliatransit.architecture.station;

/**
 * What a planned cell is for, independent of style. The declaration order is the build order: floors before what stands on
 * them, columns before beams and roofs, block-entity signs last.
 */
public enum Role {
	PLATFORM_SURFACE,
	/** The platform cell next to a track; its front points at the track. Must be an MTR platform block so doors open. */
	PLATFORM_EDGE,
	/** Low half of a ramp at a platform end; its front (low end) points away from the platform. */
	RAMP_LOWER,
	/** High half of a ramp, next to the platform end; front points away from the platform. */
	RAMP_UPPER,
	/** An unconnected MTR rail node at a platform end of a track; the only role allowed inside a track envelope. */
	RAIL_NODE,
	COLUMN,
	/** Top block of a column, under the roof (a style may use a capital such as the Utrecht tree column). */
	COLUMN_CAP,
	/** Hall roof beam across the station, carried by platform columns. */
	BEAM,
	ROOF,
	/** Outer roof cell; its front points outwards. */
	ROOF_EDGE,
	ROOF_LIGHT,
	/** Free-standing platform lamp (open platforms); front points at the track it lights. */
	LAMP,
	/** Front (seat side) points at the track. */
	BENCH,
	BIN,
	/** Station name sign, in rows of three along the platform; front (text side) points at the track. */
	NAME_SIGN,
	/** Ground fill under unsupported cells; only used by {@link SiteSurvey} when terrain changes are approved. */
	FOUNDATION;

	/** Things standing on the walking surface, which must keep off the edge cells. */
	public boolean obstructsWalkway() {
		return this == COLUMN || this == COLUMN_CAP || this == LAMP || this == BENCH || this == BIN || this == NAME_SIGN;
	}

	public boolean platformFloor() {
		return this == PLATFORM_SURFACE || this == PLATFORM_EDGE;
	}

	public boolean roofPart() {
		return this == BEAM || this == ROOF || this == ROOF_EDGE || this == ROOF_LIGHT;
	}
}
