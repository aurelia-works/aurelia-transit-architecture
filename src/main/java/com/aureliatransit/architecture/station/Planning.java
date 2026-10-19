package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Signs, mezzanine, forecourt, car park, and the final plan.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
final class Planning extends PlanStairs {

	Planning(StationSpec spec) {
		super(spec);
	}

	/**
	 * Wayfinding in the footbridge corridors (both, with a far bridge): a direction sign hung from the roof over the head of each platform's stair
	 * ("Platform 2" with a down arrow, both faces readable along the corridor), and where the corridor meets the station
	 * building an exit sign facing the platforms. Hung at the top of the corridor, so the walkway keeps three blocks of
	 * headroom. A sign's text is "ARROW:destination"; the build writes it into the sign.
	 */
	void wayfinding() {
		if (!site.bridge()) {
			return;
		}
		final int y = StationGeometry.BUILDING_ROOF_Y - 1;
		for (final StationLayout bridge : far == null ? List.of(site) : List.of(site, far)) {
			for (final TrackLayout.PlatformBand band : bridge.bridgeBands(layout)) {
				final List<StationLayout.Flight> flights = bridge.flights(band);
				if (band.number() <= 0 || flights.isEmpty()) {
					continue;
				}
				final List<Integer> columns = flights.get(0).columns();
				if (putText(Role.FIXTURE, columns.get(columns.size() / 2), y, bridge.bridgeV(), LocalDir.MINUS_U, WAYFINDING_SIGN,
						"DOWN:Platform " + band.number()) && spec.real() != RealStation.NONE) {
					hangFromRoof(columns.get(columns.size() / 2), y, bridge.bridgeV());
				}
			}
		}
		if (site.building()) {
			final int from = site.platformWall(layout);
			putText(Role.FIXTURE, from + 1, y, site.bridgeV(), LocalDir.PLUS_U, EXIT_SIGN, "NONE:Way out");
		}
	}

	void nameSigns() {
		if (spec.name().isEmpty()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			// a row of three where one fits in any column; on a short platform full of stairs and a lift, two, else one
			rows:
			for (int n = 3; n >= 1; n--) {
				if (band.island()) {
					// one row down the middle: the signs are double-sided, so it reads from both tracks; where stairs, a lift
					// or a walkway fill the middle column, the one beside it
					final int middle = (band.uMin() + band.uMax()) / 2;
					for (final int u : new int[]{middle, middle + 1, middle - 1}) {
						if (!isEdge(band, u) && u >= band.uMin() && u <= band.uMax() && signRow(band, u, LocalDir.MINUS_U, n)) {
							break rows;
						}
					}
				} else if (!signBeside(band)) {
					// against the back, else a column further in where the back is walled (an underpass stair, a building)
					final int step = towardTrack(band) == LocalDir.PLUS_U ? 1 : -1;
					for (int u = backColumn(band), k = 0; k < 3 && !isEdge(band, u) && u >= band.uMin() && u <= band.uMax(); u += step, k++) {
						if (signRow(band, u, towardTrack(band), n)) {
							break rows;
						}
					}
				} else {
					break;
				}
			}
		}
	}

	/**
	 * True when a side platform backs onto one that already has its name signs: the two backs touch with nothing between,
	 * and the signs are double-sided, so the row already there reads from this track too. A second row beside it read as
	 * two signs stacked.
	 */
	boolean signBeside(TrackLayout.PlatformBand band) {
		final int u = backColumn(band);
		for (final Map.Entry<Cell, PlannedBlock> entry : cells.entrySet()) {
			final Cell cell = entry.getKey();
			if (entry.getValue().role() == Role.NAME_SIGN && cell.u() != u && Math.abs(cell.u() - u) <= 2
					&& cell.v() >= band.vFrom() && cell.v() <= band.vTo()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Three signs side by side along v, centred on the platform's middle; shifted along v until the cells are free. Shifts
	 * run towards the platform end first and, if the end is reached, back towards its start. False if there is no room.
	 */
	boolean signRow(TrackLayout.PlatformBand band, int u, LocalDir front, int n) {
		final int start = band.vFrom() + band.length() / 2 - n / 2;
		for (int v = start; v + n - 1 <= band.vTo(); v++) {
			if (placeRow(band, u, v, front, n)) {
				return true;
			}
		}
		for (int v = start - 1; v >= band.vFrom(); v--) {
			if (placeRow(band, u, v, front, n)) {
				return true;
			}
		}
		return false;
	}

	boolean placeRow(TrackLayout.PlatformBand band, int u, int v, LocalDir front, int n) {
		for (int i = 0; i < n; i++) {
			// never across the foot of a stair: a sign there walled in whoever came down it
			if (taken(u, StationGeometry.WALK_Y, v + i) || atStairFoot(band, u, v + i)) {
				return false;
			}
		}
		for (int i = 0; i < n; i++) {
			put(Role.NAME_SIGN, u, StationGeometry.WALK_Y, v + i, front);
		}
		return true;
	}

	/** A real station's hall roof is far over its floor: a rod from a sign hung at the corridor's height up to the roof. */
	void hangFromRoof(int u, int y, int v) {
		for (int h = y + 1; h <= spec.real().roofTopY() && !taken(u, h, v); h++) {
			put(Role.FIXTURE, u, h, v, LocalDir.PLUS_V, "minecraft:chain", "axis=y");
		}
	}

	/** A car park: a kerb, a run of bays, a lane, a central planter, a lane and a second run of bays, across PARKING_DEPTH cells. */
	static final int LOT_HALF = 14;

	static final int LOT_BAY = 4;

	static final int LOT_PITCH = 3;

	static final String LOT_PAVING = "aurelia_transit_architecture:platform_paving_light";

	static final String LOT_MARK = "aurelia_transit_architecture:platform_paving_dark";

	static final String LOT_KERB = "aurelia_transit_architecture:bus_curb";

	static final String LOT_HEDGE = "minecraft:oak_leaves";

	/** The way from a hub's door across its forecourt is this far either side of the door, in cells. */
	static final int FORECOURT_WAY = 2;

	/** The width of a hub's bus island. */
	static final int FORECOURT_ISLAND = 3;

	/** Taxi bays: how many, how wide and how long. */
	static final int FORECOURT_BAYS = 3;

	static final String WAYFINDING_SIGN = "aurelia_transit_architecture:hanging_wayfinding_sign";

	static final String EXIT_SIGN = "aurelia_transit_architecture:exit_sign";

	/** Rows of a terminus forecourt's drop-off lay-by, between its kerb and the street. */
	static final int TERMINUS_LAY_BY = 4;

	static final int FORECOURT_BAY = 3;

	static final int FORECOURT_BAY_LENGTH = 5;

	static final String FORECOURT_STOP_SIGN = "aurelia_transit_architecture:bus_stop_sign";

	static final String TRAM_TRACK = "aurelia_transit_architecture:tram_track";

	static final String TRAM_STOP_SIGN = "aurelia_transit_architecture:tram_stop_sign";

	/** A hub's tram stop: rows from the forecourt's start of the track's centre line and of the platform's kerb. */
	static final int TRAM_CENTRE = 8;

	static final int TRAM_EDGE = TRAM_CENTRE - StationGeometry.EDGE_OFFSET;

	static final String FORECOURT_TIMETABLE = "aurelia_transit_architecture:bus_timetable_case";

	static final String LOT_TRUNK = "minecraft:oak_log";

	static final String LOT_BOLLARD = "aurelia_transit_architecture:bollard";

	/** A cycle stand: a post and a rail beside it, from the station fence and bollard blocks. */
	static final String LOT_STAND_POST = "aurelia_transit_architecture:bollard";

	static final String LOT_STAND_RAIL = "aurelia_transit_architecture:station_fence";

	/** The drop-off reaches this far either side of the door, in cells. */
	static final int LOT_DROP_OFF = 4;

	/** Pairs of cycle stands in the row by the kerb. */
	static final int LOT_CYCLE_ROWS = 5;

	/** A tree every this many cells along the planter. */
	static final int LOT_TREE_PITCH = 8;

	/** u of the building's entrance apron (its two ramp cells on the street side): past a car park band when there is one. */
	int apronU() {
		return spec.parking() && spec.sideBuilding() ? StationGeometry.PARKING_DEPTH : 0;
	}

	/** u of the building's street wall: a car park stands in front of it (see {@link StationGeometry#PARKING_DEPTH}). */
	int streetWall() {
		return apronU() + StationGeometry.APRON;
	}

	/**
	 * A surface car park in front of the street wall (the "parking" / "car park" word): a kerb along the street edge, two runs
	 * of bays with their heads and separators marked out, a lane either side of a central planter, and a driveway onto the
	 * street at the entrance. Built from the platform paving and bus kerb blocks already in the mod (and vanilla leaves for
	 * the planter). It fills the band the layout leaves in front of the building ({@link StationGeometry#PARKING_DEPTH}
	 * cells, u = 0..depth-1), so nothing stands outside the footprint.
	 */
	/**
	 * A hub's forecourt, past the head building's entrance ramps: paving to the street kerb, a pedestrian way from the door, a taxi
	 * rank on one side (three marked bays with a bollard at the head of each) and a bus interchange on the other (a kerbed island
	 * with a walk-in waiting shelter, stop signs and a timetable case, and a marked carriageway beside it). Built from the paving,
	 * bus kerb, bus kit and bollard blocks already in the mod; there is no taxi sign block. With the "tram" word a tram stop takes
	 * the taxi rank's side (see {@link #tramStop}).
	 */
	/** Rows either side of a mezzanine's centre line where its benches stand, facing out over the tracks. */
	static final int MEZZANINE_BENCH_ROW = 2;

	/** A pair of benches and a bin every this many cells along a mezzanine. */
	static final int MEZZANINE_BENCH_PITCH = 8;

	/**
	 * A mezzanine's furnishing (the "mezzanine" / "upper concourse" word; the footbridge pass builds its wide deck, walls, roof
	 * and edge piers): benches with a bin in two rows either side of the centre line, facing out through the glass over the
	 * tracks, kept clear of every stair and lift opening, and a hanging departure screen over the centre line at each end.
	 * There is no lower concourse: the ticket hall stays in the building.
	 */
	void mezzanine() {
		if (!spec.mezzanine() || !site.bridge()) {
			return;
		}
		final int vB = site.bridgeV();
		final int from = site.building() ? site.platformWall(layout) + 1 : site.deckFrom();
		final int to = site.deckTo();
		final Set<Integer> openings = new HashSet<>();
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			for (final StationLayout.Flight flight : site.flights(band)) {
				openings.addAll(opening(band, flight));
			}
		}
		for (final PlannedBlock block : cells.values()) {
			// lift doors on the deck
			if (block.y() == StationGeometry.UPPER_WALK_Y && Math.abs(block.v() - vB) == bridgeWall() && block.role() != Role.WINDOW) {
				openings.add(block.u());
			}
		}
		final int y = StationGeometry.UPPER_WALK_Y;
		// the first clear spot from each pitch mark on: a pair and its bin with a cell free of openings either side
		for (int u = from + 2; u + 2 <= to - 2; u++) {
			boolean clear = true;
			for (int du = -1; du <= 3; du++) {
				clear &= !openings.contains(u + du);
			}
			if (!clear) {
				continue;
			}
			final int at = u;
			u += MEZZANINE_BENCH_PITCH - 1;
			for (final int side : new int[]{-1, 1}) {
				final int v = vB + side * MEZZANINE_BENCH_ROW;
				if (taken(at, y, v) || taken(at + 1, y, v) || taken(at + 2, y, v)) {
					continue;
				}
				final LocalDir front = side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
				put(Role.BENCH, at, y, v, front);
				put(Role.BENCH, at + 1, y, v, front);
				put(Role.BIN, at + 2, y, v, front);
			}
		}
		final int screenY = StationGeometry.BUILDING_ROOF_Y - 1;
		for (final int u : new int[]{from + 1, to - 1}) {
			if (!taken(u, screenY, vB)) {
				put(Role.FIXTURE, u, screenY, vB, u == to - 1 ? LocalDir.MINUS_U : LocalDir.PLUS_U, HANGING_PIDS);
			}
		}
	}

	void forecourt() {
		if (!layout.terminus() || layout.forecourtDepth() <= 0) {
			return;
		}
		if (!spec.hub()) {
			terminusForecourt();
			return;
		}
		final int u0 = layout.headFrom();
		final int u1 = layout.headTo();
		final int door = headDoor();
		final int f0 = layout.forecourtFrom();
		final int f1 = layout.vMax();
		final int busKerb = door + FORECOURT_WAY + FORECOURT_ISLAND + 1;
		final int taxiTo = door - FORECOURT_WAY - 1;
		for (int u = u0; u <= u1; u++) {
			for (int v = f0; v <= f1; v++) {
				final int c = u - door;
				final int d = v - f0;
				final boolean way = Math.abs(c) <= FORECOURT_WAY || d < 3;
				final boolean street = v == f1;
				String block = LOT_PAVING;
				LocalDir front = LocalDir.PLUS_V;
				if (street && !way) {
					// the kerb along the street, open where the pedestrian way meets it
					block = LOT_KERB;
				} else if (!way && c > 0 && u == busKerb && d >= 3 && d < f1 - f0) {
					block = LOT_KERB;
					front = LocalDir.PLUS_U;
				} else if (!way && c > 0 && u > busKerb) {
					// the bus carriageway
					block = LOT_MARK;
				} else if (!way && c < 0 && u <= taxiTo && spec.tram()) {
					// the tram stop: a platform to a kerb, the track's envelope in dark paving with the street track at its centre
					if (d == TRAM_EDGE) {
						block = LOT_KERB;
					} else if (d == TRAM_CENTRE && u < taxiTo) {
						block = TRAM_TRACK;
						front = LocalDir.PLUS_U;
					} else if (Math.abs(d - TRAM_CENTRE) <= StationGeometry.ENVELOPE_HALF_WIDTH) {
						block = LOT_MARK;
					}
				} else if (!way && c < 0 && u <= taxiTo) {
					// the taxi rank: bays of paving three wide, five long, between dark lines
					final int bay = (taxiTo - u) / (FORECOURT_BAY + 1);
					final boolean line = (taxiTo - u) % (FORECOURT_BAY + 1) == FORECOURT_BAY || d < 3 || d >= 3 + FORECOURT_BAY_LENGTH;
					block = bay < FORECOURT_BAYS && !line ? LOT_PAVING : LOT_MARK;
				}
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y - 1, v, front, block);
			}
		}
		if (spec.tram()) {
			tramStop(u0, taxiTo, f0);
		}
		// taxi rank: a bollard at the head of each bay
		for (int bay = 0; bay < FORECOURT_BAYS && !spec.tram(); bay++) {
			final int u = taxiTo - bay * (FORECOURT_BAY + 1) - FORECOURT_BAY / 2;
			if (u > u0) {
				put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, f0 + 2, LocalDir.PLUS_V, LOT_BOLLARD);
			}
		}
		// bus interchange: the island between the way and the kerb, a shelter on its way side facing the buses, signs at both ends
		if (busKerb < u1) {
			final int wall = door + FORECOURT_WAY + 1;
			shelter(StationGeometry.PLATFORM_Y, wall, wall + 1, LocalDir.MINUS_U, f0 + 5, null, 0);
			put(Role.FIXTURE, wall + 2, StationGeometry.PLATFORM_Y, f0 + 3, LocalDir.MINUS_V, FORECOURT_STOP_SIGN);
			put(Role.FIXTURE, wall + 2, StationGeometry.PLATFORM_Y, f1 - 3, LocalDir.MINUS_V, FORECOURT_STOP_SIGN);
			put(Role.FIXTURE, wall, StationGeometry.PLATFORM_Y, f0 + 9, LocalDir.PLUS_U, FORECOURT_TIMETABLE);
		}
	}

	/**
	 * The tram stop on a hub's forecourt (the "tram" word), on the taxi rank's side: the track runs along the forecourt from its
	 * side edge, where the player carries the MTR rail on to the street, to a bollard by the pedestrian way. The floor pass lays
	 * the street track blocks and the kerb; this adds the shelter against the way's rows (glass, seats facing the track, roof),
	 * a stop sign at each end of the platform and a bollard at the track's end. The rail, its nodes and the stop's MTR platform
	 * are the player's to lay, as on the main tracks.
	 */
	void tramStop(int u0, int taxiTo, int f0) {
		final int y = StationGeometry.PLATFORM_Y;
		final int mid = (u0 + taxiTo) / 2 - SHELTER_LENGTH / 2;
		for (int i = 0; i < SHELTER_LENGTH; i++) {
			final int u = mid + i;
			put(Role.FIXTURE, u, y, f0 + 3, LocalDir.MINUS_V, SHELTER_GLASS);
			put(Role.FIXTURE, u, y + 1, f0 + 3, LocalDir.MINUS_V, SHELTER_GLASS);
			put(Role.FIXTURE, u, y, f0 + 4, LocalDir.MINUS_V, SHELTER_SEAT);
			put(Role.FIXTURE, u, y + 2, f0 + 3, LocalDir.MINUS_V, SHELTER_ROOF);
			put(Role.FIXTURE, u, y + 2, f0 + 4, LocalDir.MINUS_V, SHELTER_ROOF);
		}
		put(Role.FIXTURE, u0 + 1, y, f0 + TRAM_EDGE - 1, LocalDir.PLUS_U, TRAM_STOP_SIGN);
		put(Role.FIXTURE, taxiTo - 1, y, f0 + TRAM_EDGE - 1, LocalDir.MINUS_U, TRAM_STOP_SIGN);
		put(Role.FIXTURE, taxiTo, y, f0 + TRAM_CENTRE, LocalDir.MINUS_U, LOT_BOLLARD);
	}

	/**
	 * A terminus's forecourt ("parking"), past its head building: a paved way from the door to the street, a drop-off lay-by
	 * open to the street on one side behind a kerb with bollards, cycle stands on the other side, and a tree on each side.
	 */
	void terminusForecourt() {
		final int u0 = layout.headFrom();
		final int u1 = layout.headTo();
		final int door = headDoor();
		final int f0 = layout.forecourtFrom();
		final int f1 = layout.vMax();
		final int layBy = f1 - TERMINUS_LAY_BY;
		for (int u = u0; u <= u1; u++) {
			for (int v = f0; v <= f1; v++) {
				final int c = u - door;
				final boolean way = Math.abs(c) <= FORECOURT_WAY || v - f0 < 2;
				String block = LOT_PAVING;
				LocalDir front = LocalDir.PLUS_V;
				if (!way && c < 0 && v == layBy) {
					// the kerb between the footway and the lay-by
					block = LOT_KERB;
				} else if (!way && c < 0 && v > layBy) {
					// the lay-by itself, open to the street
					block = LOT_MARK;
				} else if (v == f1 && !way) {
					block = LOT_KERB;
				}
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y - 1, v, front, block);
			}
		}
		// bollards along the lay-by kerb keep cars off the footway
		for (int u = door - FORECOURT_WAY - 2; u > u0; u -= 3) {
			put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, layBy - 1, LocalDir.PLUS_V, LOT_BOLLARD);
		}
		// cycle stands: a post and a rail each, in two rows beside the way
		for (int u = door + FORECOURT_WAY + 2; u < u1 - 1; u += 2) {
			put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, f0 + 3, LocalDir.PLUS_V, LOT_STAND_POST);
			put(Role.RAILING, u, StationGeometry.PLATFORM_Y, f0 + 4, LocalDir.PLUS_V, LOT_STAND_RAIL);
		}
		// a tree each side, clear of the lay-by and the stands
		for (final int[] at : new int[][]{{u0 + 2, f0 + 3}, {u1 - 2, f0 + 8}}) {
			if (at[0] > u0 && at[0] < u1 && Math.abs(at[0] - door) > FORECOURT_WAY + 1 && at[1] < (at[0] < door ? layBy - 2 : f1 - 1)) {
				put(Role.FLOOR, at[0], StationGeometry.PLATFORM_Y - 1, at[1], LocalDir.PLUS_V, LOT_TRUNK, "axis=y");
				tree(at[0], at[1]);
			}
		}
	}

	void parking() {
		if (!spec.parking() || !spec.sideBuilding()) {
			return;
		}
		final int front = site.entranceV();
		final int vFrom = Math.max(layout.vMin(), front - LOT_HALF);
		final int vTo = Math.min(layout.vMax(), front + LOT_HALF);
		final int first = vFrom + 2;
		for (int a = 0; a < StationGeometry.PARKING_DEPTH; a++) {
			final boolean kerb = a == 0;
			final boolean bays = a >= 1 && a <= LOT_BAY || a >= LOT_BAY + 6;
			final boolean head = a == LOT_BAY + 1 || a == LOT_BAY + 5;
			final boolean lane = a == LOT_BAY + 2 || a == LOT_BAY + 4;
			final boolean planter = a == LOT_BAY + 3;
			for (int v = vFrom; v <= vTo; v++) {
				final boolean entrance = Math.abs(v - front) <= 1;
				final boolean end = v == vFrom || v == vTo;
				// the drop-off: the bays by the building's door are one plain forecourt, with no bay markings to park in
				final boolean dropOff = a >= LOT_BAY + 6 && Math.abs(v - front) <= LOT_DROP_OFF;
				// cycle parking: the bays at the street end of the lot, by the kerb
				final boolean cycles = a >= 1 && a <= 3 && v >= first && v < first + LOT_CYCLE_ROWS * 2;
				final boolean separator = bays && !dropOff && !cycles && Math.floorMod(v - vFrom, LOT_PITCH) == 0;
				final String block;
				String state = "";
				boolean tree = false;
				if (kerb && !entrance) {
					block = LOT_KERB;
				} else if (planter && !end && !entrance) {
					tree = Math.abs(v - front) > LOT_DROP_OFF && Math.floorMod(v - vFrom, LOT_TREE_PITCH) == LOT_TREE_PITCH / 2 && v > vFrom + 1 && v < vTo - 1;
					// a tree stands in its own cell of the planter; the hedge round it is leaves that stay (persistent, so they never decay)
					block = tree ? LOT_TRUNK : LOT_HEDGE;
					state = tree ? "axis=y" : "persistent=true";
				} else if (dropOff && !lane && !head) {
					block = LOT_PAVING;
				} else if (cycles) {
					block = LOT_PAVING;
				} else if (lane || head || end || separator) {
					block = LOT_MARK;
				} else {
					block = LOT_PAVING;
				}
				put(Role.FLOOR, a, StationGeometry.PLATFORM_Y - 1, v, LocalDir.MINUS_U, block, state);
				if (tree) {
					tree(a, v);
				}
				if (cycles && (a == 2 || a == 3) && Math.floorMod(v - first, 2) == 0) {
					// a stand: a post and a rail beside it, two cells long
					put(Role.FIXTURE, a, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, LOT_STAND_POST);
					put(Role.RAILING, a, StationGeometry.PLATFORM_Y, v + 1, LocalDir.PLUS_V, LOT_STAND_RAIL);
				}
			}
		}
		// bollards close the ends of the drop-off so nothing parks across the way to the door
		for (final int v : new int[]{front - LOT_DROP_OFF - 1, front + LOT_DROP_OFF + 1}) {
			if (v > vFrom && v < vTo) {
				put(Role.FIXTURE, StationGeometry.PARKING_DEPTH - 1, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, LOT_BOLLARD);
				put(Role.FIXTURE, LOT_BAY + 6, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, LOT_BOLLARD);
			}
		}
	}

	/** A tree in the planter: a trunk four high (the first block is the planter cell itself) and a crown of leaves round its top. */
	void tree(int a, int v) {
		for (int y = StationGeometry.PLATFORM_Y; y <= StationGeometry.PLATFORM_Y + 3; y++) {
			put(Role.FIXTURE, a, y, v, LocalDir.PLUS_V, LOT_TRUNK, "axis=y");
		}
		final int top = StationGeometry.PLATFORM_Y + 3;
		for (int da = -1; da <= 1; da++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (da != 0 || dv != 0) {
					put(Role.FIXTURE, a + da, top, v + dv, LocalDir.PLUS_V, LOT_HEDGE, "persistent=true");
				}
				put(Role.FIXTURE, a + da, top + 1, v + dv, LocalDir.PLUS_V, LOT_HEDGE, "persistent=true");
			}
		}
	}

	/**
	 * Where two walls meet (a corner, or a wall butting into another), a window becomes a solid post in the building's
	 * trim. Most window blocks are thin (a panel in the middle of the cell, or a pane), so at a corner their edge showed
	 * as a sliver and the corner looked open; a full glass block at a corner had no frame. Decided on the walls as
	 * planned, so one post never turns its neighbour into another.
	 */
	void cornerPosts() {
		final String post = palette.building().trim();
		final List<PlannedBlock> corners = new ArrayList<>();
		for (final PlannedBlock block : cells.values()) {
			if (block.role() != Role.WINDOW) {
				continue;
			}
			final boolean alongU = wallAt(block.u() - 1, block.y(), block.v()) || wallAt(block.u() + 1, block.y(), block.v());
			final boolean alongV = wallAt(block.u(), block.y(), block.v() - 1) || wallAt(block.u(), block.y(), block.v() + 1);
			if (alongU && alongV) {
				corners.add(block);
			}
		}
		for (final PlannedBlock block : corners) {
			cells.put(new Cell(block.u(), block.y(), block.v()), new PlannedBlock(block.u(), block.y(), block.v(), Role.WALL, post, block.front(), "", ""));
		}
	}

	boolean wallAt(int u, int y, int v) {
		final PlannedBlock block = cells.get(new Cell(u, y, v));
		return block != null && (block.role() == Role.WALL || block.role() == Role.WINDOW);
	}

	StationPlan finish() {
		final List<PlannedBlock> blocks = new ArrayList<>(cells.values());
		blocks.sort(BUILD_ORDER);
		final List<Box> envelopes = new ArrayList<>();
		final List<Box> supports = new ArrayList<>();
		for (final TrackLayout.Track track : layout.tracks()) {
			envelopes.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.PLATFORM_Y, layout.vMin(),
					track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.ENVELOPE_HEIGHT - 1, layout.trackEnd(track)));
		}
		final List<Box> headroom = new ArrayList<>();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			headroom.add(new Box(band.uMin(), StationGeometry.WALK_Y, band.vFrom(), band.uMax(), StationGeometry.WALK_Y + StationGeometry.HEADROOM - 1,
					band.vTo()));
			supports.add(new Box(band.uMin(), -1, layout.vMin(), band.uMax(), -1, layout.trackEnd()));
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			supports.add(new Box(track.u(), -1, layout.vMin(), track.u(), -1, layout.trackEnd(track)));
			if (spec.trackBed().falls()) {
				// gravel needs a block under it (here and between tracks)
				supports.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, -2, layout.vMin(), track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, -2,
						layout.trackEnd(track)));
			}
			if (layout.deadEnd(track)) {
				// the ground under a bay track's buffer stop
				final int v = layout.trackEnd(track) + 1;
				supports.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, -1, v, track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, -1, v));
			}
		}
		if (spec.trackBed().falls()) {
			for (final Box gap : bedGaps()) {
				supports.add(new Box(gap.uMin(), -2, gap.vMin(), gap.uMax(), -2, gap.vMax()));
			}
		}
		if (layout.terminus()) {
			supports.add(new Box(layout.headFrom(), -1, length, layout.headTo(), -1, layout.headEnd()));
			supports.add(new Box(headDoor() - 1, -1, layout.headEnd() + 1, headDoor() + 1, -1, layout.vMax()));
			if (layout.forecourtDepth() > 0) {
				supports.add(new Box(layout.headFrom(), -1, layout.forecourtFrom(), layout.headTo(), -1, layout.vMax()));
			}
		}
		if (site.building()) {
			supports.add(new Box(0, -1, buildingFrom(), layout.buildingWidth() - 1, -1, buildingTo()));
		}
		supports.addAll(extraSupport);
		headroom.addAll(extraHeadroom);
		return new StationPlan(spec, layout, blocks, envelopes, headroom, holes.isEmpty() ? supports : without(supports, holes),
				palette.blockId(Role.FOUNDATION));
	}

	/** {@code boxes} with the {@code holes} taken out: a box holding a hole is cut into runs along u that miss it. */
	static List<Box> without(List<Box> boxes, Set<Cell> holes) {
		final List<Box> out = new ArrayList<>();
		for (final Box box : boxes) {
			boolean hit = false;
			for (final Cell hole : holes) {
				hit |= box.contains(hole.u(), hole.y(), hole.v());
			}
			if (!hit) {
				out.add(box);
				continue;
			}
			for (int y = box.yMin(); y <= box.yMax(); y++) {
				for (int v = box.vMin(); v <= box.vMax(); v++) {
					int start = box.uMin();
					for (int u = box.uMin(); u <= box.uMax() + 1; u++) {
						if (u > box.uMax() || holes.contains(new Cell(u, y, v))) {
							if (u > start) {
								out.add(new Box(start, y, v, u - 1, y, v));
							}
							start = u + 1;
						}
					}
				}
			}
		}
		return out;
	}
}
