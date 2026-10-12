package com.aureliatransit.architecture.station;

/**
 * What a planned cell is for, independent of style. The declaration order is the build order: floors before what stands on
 * them, walls and piers before decks and roofs, block-entity signs last.
 */
public enum Role {
	PLATFORM_SURFACE,
	/** The platform cell next to a track; its front points at the track. Must be an MTR platform block so doors open. */
	PLATFORM_EDGE,
	/** Low half of a ramp at a platform end or a building entrance; its front (low end) points away from the higher floor. */
	RAMP_LOWER,
	/** High half of a ramp, next to the higher floor; front points away from it. */
	RAMP_UPPER,
	/** An unconnected MTR rail node at a platform end of a track; the only role allowed inside a track envelope. */
	RAIL_NODE,
	/** Station building floor or upper-floor slab: a full block passengers walk on. */
	FLOOR,
	/** A floor or roof cell that is a light (a full block, walkable when it is part of a floor). */
	LIGHT_PANEL,
	/** Station building wall, pilaster, plinth or band. */
	WALL,
	/** A window in a station building or footbridge wall; front points outwards. */
	WINDOW,
	/** One step of a stair flight; front points the way the flight climbs. */
	STAIR,
	/** Footbridge support, from the ground or a platform floor up to the deck. */
	BRIDGE_PIER,
	/** Footbridge floor over the tracks, a full block passengers walk on. */
	BRIDGE_DECK,
	COLUMN,
	/** Top block of a column, under the roof (a style may use a capital such as the Utrecht tree column). */
	COLUMN_CAP,
	/** Hall roof girder across the station or along a column line, eaves or valley, carried by platform columns. */
	BEAM,
	/** Arch rib or hanger of a vaulted hall: girders under the vault, joined to the frame. */
	RIB,
	ROOF,
	/** Outer roof cell; its front points outwards. */
	ROOF_EDGE,
	ROOF_LIGHT,
	/** Flat roof of a station building or footbridge, its parapet and cornice. */
	BUILDING_ROOF,
	/** Fare gate cabinet; a gate or wide gate opens for a passenger with a card. */
	GATE,
	/** Glass barrier closing a gate line. */
	BARRIER,
	/** Handrail or guard rail. */
	RAILING,
	/** Buffer stop across the end of a terminus track, behind its last rail node; front along the beam. */
	BUFFER_STOP,
	/** Free-standing platform lamp (open platforms); front points at the track it lights. */
	LAMP,
	/** Front (seat side) points at the track or into the room. */
	BENCH,
	BIN,
	/**
	 * Station equipment: ticket windows, ticket machines, information kiosks, departure boards, clocks, help points,
	 * cameras, speakers. Its front faces the passengers.
	 */
	FIXTURE,
	/** Station name sign, in rows of three along the platform; front (text side) points at the track. */
	NAME_SIGN,
	/** Ground fill under unsupported cells; only used by {@link SiteSurvey} when terrain changes are approved. */
	FOUNDATION;

	/** Things standing in the walking space, which must keep off platform edge cells. */
	public boolean obstructsWalkway() {
		return switch (this) {
			case COLUMN, COLUMN_CAP, LAMP, BENCH, BIN, NAME_SIGN, STAIR, BRIDGE_PIER, WALL, WINDOW, GATE, BARRIER, RAILING, BUFFER_STOP, FIXTURE -> true;
			default -> false;
		};
	}

	public boolean platformFloor() {
		return this == PLATFORM_SURFACE || this == PLATFORM_EDGE;
	}

	public boolean roofPart() {
		return this == BEAM || this == RIB || this == ROOF || this == ROOF_EDGE || this == ROOF_LIGHT;
	}

	/** A full block a passenger can stand on top of (ramps and stairs have their own rules). */
	public boolean flatFloor() {
		return platformFloor() || this == FLOOR || this == LIGHT_PANEL || this == BRIDGE_DECK;
	}

	/** Shelters what is below it: roofs, upper floors, footbridge decks and roofs. */
	public boolean covers() {
		return roofPart() || this == FLOOR || this == LIGHT_PANEL || this == BRIDGE_DECK || this == BUILDING_ROOF;
	}

	/** Structure a passenger could hit their head on; wall-mounted fixtures and signs do not count for headroom. */
	public boolean overhead() {
		return covers() || this == STAIR || this == WALL || this == COLUMN_CAP;
	}

	/** A passenger can walk through this cell (an open fare gate, a rail lying on the ground). */
	public boolean passable() {
		return this == GATE || this == RAIL_NODE;
	}
}
