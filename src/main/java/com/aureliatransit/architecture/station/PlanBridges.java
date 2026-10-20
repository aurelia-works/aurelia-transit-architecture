package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Footbridges and underpasses.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanBridges extends PlanConcourse {

	PlanBridges(StationSpec spec) {
		super(spec);
	}

	/**
	 * The footbridge over the tracks: deck, glazed corridor walls, roof with lights, support piers on every platform
	 * and six-foot piers where the span exceeds 12, and a stair with a landing down to every served platform.
	 */
	/** The far-end footbridge, planned by {@link #footbridge} itself with the far bridge as the layout. */
	void farFootbridge() {
		if (far == null) {
			return;
		}
		final StationLayout main = site;
		site = far;
		try {
			footbridge();
		} finally {
			site = main;
		}
	}

	/** True on the centre line of either footbridge. */
	boolean bridgeRow(int v) {
		return site.bridge() && v == site.bridgeV() || far != null && v == far.bridgeV();
	}

	void footbridge() {
		if (!site.bridge()) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int vB = site.bridgeV();
		final int from = site.building() ? site.platformWall(layout) : site.deckFrom();
		final int to = site.deckTo();
		final int wallLo = vB - bridgeWall();
		final int wallHi = vB + bridgeWall();
		final boolean hall = spec.real() != RealStation.NONE;
		// keep the stair corridors and landings clear before the deck and walls go up
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			for (final StationLayout.Flight flight : site.flights(band)) {
				for (final int u : flight.columns()) {
					for (int y = 1; y <= 3; y++) {
						keepClear(u, y, flight.landingV());
					}
					// two clear above every step, and a third: stepping up from it your head passes under the cell above it
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						keepClear(u, y + 1, flight.stepV(y));
						keepClear(u, y + 2, flight.stepV(y));
						keepClear(u, y + 3, flight.stepV(y));
					}
				}
			}
		}
		// deck, out to and under both corridor walls, so the stair exits have a floor and the walls do not float
		for (int u = from; u <= to; u++) {
			for (int v = wallLo; v <= wallHi; v++) {
				// lights only under the walkway: in a wall row one sits under the glass, or in a lift's doorway, half seen
				// through the glass from the platform
				if ((u + v) % 5 == 0 && Math.abs(v - vB) <= bridgeHalf()) {
					// the building's recessed light: a full block with the light underneath, so the walkway has no pit
					put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.deck());
				}
			}
		}
		// a real station's hall builds its own walls and roof, and its flights open in its floor (see realHall)
		if (!hall) {
			// corridor walls (glass, facing outwards); each wall opens where a flight leaves through it
			for (int u = from; u <= to; u++) {
				boolean exitLo = false;
				boolean exitHi = false;
				for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
					if (u < band.uMin() || u > band.uMax()) {
						continue;
					}
					for (final StationLayout.Flight flight : site.flights(band)) {
						if (opening(band, flight).contains(u)) {
							exitLo |= flight.wallV() == wallLo;
							exitHi |= flight.wallV() == wallHi;
						}
					}
				}
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					final boolean low = y <= StationGeometry.UPPER_WALK_Y + 1;
					if (!(low && exitLo)) {
						put(Role.WINDOW, u, y, wallLo, LocalDir.MINUS_V, b.bridgeWall());
					}
					if (!(low && exitHi)) {
						put(Role.WINDOW, u, y, wallHi, LocalDir.PLUS_V, b.bridgeWall());
					}
				}
			}
			// glass across the corridor's open ends (the building closes its own end)
			for (final int u : site.building() ? new int[]{to} : new int[]{from, to}) {
				for (int v = wallLo + 1; v < wallHi; v++) {
					for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
						put(Role.WINDOW, u, y, v, u == to ? LocalDir.PLUS_U : LocalDir.MINUS_U, b.bridgeWall());
					}
				}
			}
			// roof over the corridor and the stairs
			for (int u = from; u <= to; u++) {
				for (int v = wallLo; v <= wallHi; v++) {
					if ((u + v) % 4 == 0) {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.bridgeLight());
					} else {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.bridgeRoof());
					}
				}
			}
			// each flight glazed (or walled) and roofed like the corridor it leaves
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				for (final StationLayout.Flight flight : site.flights(band)) {
					if (roof(band) == RoofType.CANOPY) {
						stairShaft(band, flight, b);
					} else {
						stairHood(band, flight, b);
					}
				}
			}
		}
		if (hall) {
			// under a hall the flights go down through openings in its floor: glass on both flanks from the platform up to
			// the floor (never on a boarding edge); round the openings above it, see hallOpenings
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				for (final StationLayout.Flight flight : site.flights(band)) {
					hallFlanks(band, flight, b);
				}
			}
		}
		// piers on every platform at the bridge line, up to the deck: down the middle, or evenly spaced on a platform too wide
		// for the deck to overhang one (a real station's floor rests on the walls under its ends instead, see plateaus)
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			if (site.plateau(band)) {
				continue;
			}
			for (final int uPier : bridgePierLines(band)) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					put(Role.BRIDGE_PIER, uPier, y, vB, LocalDir.PLUS_V, b.pier());
				}
				// a mezzanine's deck is too wide to overhang one pier line: a pier under each edge as well, where it is free
				for (final int vEdge : spec.mezzanine() ? new int[]{vB - bridgeHalf(), vB + bridgeHalf()} : new int[0]) {
					if (freeColumn(uPier, StationGeometry.WALK_Y, StationGeometry.UPPER_FLOOR_Y - 1, vEdge)) {
						for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
							put(Role.BRIDGE_PIER, uPier, y, vEdge, LocalDir.PLUS_V, b.pier());
						}
					}
				}
			}
		}
		// a hall's floor is carried on more rows of piers than its middle one: out to its walls, wherever the platform is free
		for (final int vRow : pierRows()) {
			if (vRow == vB) {
				continue;
			}
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				if (site.plateau(band)) {
					continue;
				}
				for (final int uPier : bridgePierLines(band)) {
					if (freeColumn(uPier, StationGeometry.WALK_Y, StationGeometry.UPPER_FLOOR_Y - 1, vRow)) {
						for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
							put(Role.BRIDGE_PIER, uPier, y, vRow, LocalDir.PLUS_V, b.pier());
						}
					}
				}
			}
		}
		// flanking piers at the stair landings (never on the landing itself) so the canopy ends stay carried; they stop below
		// the canopy, so it runs over them. Under a hall or shed roof, or none, there is no canopy end to carry and they
		// stood as two short posts carrying nothing at the stair's foot
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			if (roof(band) != RoofType.CANOPY) {
				continue;
			}
			final int flankTop = StationGeometry.CANOPY_Y;
			for (final StationLayout.Flight flight : site.flights(band)) {
				final List<Integer> open = opening(band, flight);
				final int landing = flight.landingV();
				for (final int su : open) {
					for (final int du : new int[]{-1, 1}) {
						final int u = su + du;
						if (u < band.uMin() || u > band.uMax() || isEdge(band, u) || open.contains(u)) {
							continue;
						}
						boolean free = true;
						for (int y = StationGeometry.WALK_Y; y < flankTop; y++) {
							if (taken(u, y, landing)) {
								free = false;
								break;
							}
						}
						if (free) {
							for (int y = StationGeometry.WALK_Y; y < flankTop; y++) {
								put(Role.BRIDGE_PIER, u, y, landing, LocalDir.PLUS_V, b.pier());
							}
							break;
						}
					}
				}
			}
		}
		// platform stairs with landings kept clear
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			for (final StationLayout.Flight flight : site.flights(band)) {
				final List<Integer> columns = flight.columns();
				for (final int u : columns) {
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						put(Role.STAIR, u, y, flight.stepV(y), flight.front(), b.stair());
					}
				}
				// balustrades on the outer flanks of the whole flight, from the floor up to each step, so no rail hangs at
				// head height over the platform (never at the canopy roof level, which the canopy fills, never on boarding
				// edges)
				final int left = columns.get(0) - 1;
				final int right = columns.get(columns.size() - 1) + 1;
				for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
					final int v = flight.stepV(y);
					for (int rail = StationGeometry.WALK_Y; rail <= y + 1; rail++) {
						if (rail == StationGeometry.CANOPY_Y && roof(band) == RoofType.CANOPY) {
							continue;
						}
						// on a boarding edge the rail starts above head height (the edge stays clear to board from) and runs up
						// to the cell a passenger stands in, so nobody steps off the high steps onto the platform below
						final boolean edgeRail = rail >= EDGE_RAIL_Y;
						if (isEdge(band, left) ? edgeRail : rail <= y) {
							put(Role.RAILING, left, rail, v, LocalDir.MINUS_U);
						}
						if (isEdge(band, right) ? edgeRail : rail <= y) {
							put(Role.RAILING, right, rail, v, LocalDir.PLUS_U);
						}
					}
				}
			}
		}
		// six-foot piers where the deck span between supports exceeds 12, on every pier row
		for (final int vRow : pierRows()) {
			sixFootPiers(from, to, vRow, b);
		}
		if (hall) {
			plateaus(from, to, wallLo, wallHi);
			realHall(from, to, wallLo, wallHi);
		}
	}

	/** The solid block under a real station's broad stairs, their side walls and the walls closing the space under its floor. */
	static final String PLATEAU_WALL = ata("metro_pale_concrete");

	/**
	 * The ends of a real station's hall (Utrecht Centraal: the Stationsplein and the Jaarbeursplein are at the hall's level,
	 * with a broad stair down to the street): from the square a broad stair up to an opening in the hall's glass end wall, on
	 * solid ground, with a side wall and a rail each side; the space under the hall's floor walled in from the square and the
	 * track, so nothing is left at track level beside the tracks; and a lobby off the square for the lift up to the hall.
	 */
	void plateaus(int from, int to, int wallLo, int wallHi) {
		final StylePalette.Building b = palette.building();
		final int vB = site.bridgeV();
		final int half = StationLayout.PLATEAU_STAIR_HALF;
		final int floor = StationGeometry.UPPER_FLOOR_Y;
		for (final TrackLayout.PlatformBand band : site.streets()) {
			final boolean west = band.equals(site.street());
			// the hall's end wall column, the way out to the square, and the column against the track
			final int edge = west ? from : to;
			final int out = west ? -1 : 1;
			final int track = west ? band.uMax() : band.uMin();
			final LocalDir up = west ? LocalDir.PLUS_U : LocalDir.MINUS_U;
			// the opening in the end wall the stair arrives at, three high
			for (int v = vB - half; v <= vB + half; v++) {
				for (int y = StationGeometry.UPPER_WALK_Y; y < StationGeometry.UPPER_WALK_Y + 3; y++) {
					keepClear(edge, y, v);
				}
			}
			// step k at the k-th column out from the wall, k high, solid underneath; a side wall up to each step with a rail on it
			for (int k = 1; k <= StationGeometry.STAIR_STEPS; k++) {
				final int u = edge + out * (StationGeometry.STAIR_STEPS + 1 - k);
				for (int v = vB - half - 1; v <= vB + half + 1; v++) {
					final boolean side = Math.abs(v - vB) > half;
					for (int y = StationGeometry.WALK_Y; y < (side ? k + 1 : k); y++) {
						put(Role.WALL, u, y, v, up, PLATEAU_WALL);
					}
					if (side) {
						put(Role.RAILING, u, k + 1, v, v < vB ? LocalDir.MINUS_V : LocalDir.PLUS_V);
					} else {
						put(Role.STAIR, u, k, v, up, b.stair());
					}
				}
			}
			// under the floor: walled from the square and from the track, and across at the long walls
			for (int y = StationGeometry.WALK_Y; y < floor; y++) {
				for (int v = wallLo; v <= wallHi; v++) {
					put(Role.WALL, edge, y, v, up.opposite(), PLATEAU_WALL);
					put(Role.WALL, track, y, v, up, PLATEAU_WALL);
				}
				for (int u = Math.min(edge, track) + 1; u < Math.max(edge, track); u++) {
					put(Role.WALL, u, y, wallLo, LocalDir.MINUS_V, PLATEAU_WALL);
					put(Role.WALL, u, y, wallHi, LocalDir.PLUS_V, PLATEAU_WALL);
				}
			}
			// the lift's lobby, three high, closed on its inner side, towards the stair and overhead
			final StationLayout.Lift lift = site.plateauLift(band);
			final int row = lift.doorV() - 1;
			final int inner = west ? lift.c0() + 2 : lift.c0() - 1;
			for (int y = StationGeometry.WALK_Y; y < floor; y++) {
				put(Role.WALL, inner, y, row, up, PLATEAU_WALL);
				for (final int c : new int[]{edge, lift.c0(), lift.c0() + 1}) {
					put(Role.WALL, c, y, row - 1, LocalDir.MINUS_V, PLATEAU_WALL);
					if (y > StationGeometry.WALK_Y + 2) {
						put(Role.WALL, c, y, row, LocalDir.PLUS_V, PLATEAU_WALL);
					}
				}
			}
		}
	}

	/**
	 * Piers between the tracks on row {@code vRow} where the deck span between its supports (the deck's ends and the piers
	 * on the platforms) exceeds 12. On the bridge line every column is planned; on a hall's other rows only free ones.
	 */
	void sixFootPiers(int from, int to, int vRow, StylePalette.Building b) {
		final boolean line = vRow == site.bridgeV();
		final List<Integer> supports = new ArrayList<>();
		supports.add(from);
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			if (site.plateau(band)) {
				// the wall under the floor along the track
				supports.add(band.equals(site.street()) ? band.uMax() : band.uMin());
				continue;
			}
			for (final int u : bridgePierLines(band)) {
				if (line || has(u, StationGeometry.UPPER_FLOOR_Y - 1, vRow, Role.BRIDGE_PIER)) {
					supports.add(u);
				}
			}
		}
		supports.add(to);
		supports.sort(Comparator.naturalOrder());
		for (int i = 0; i + 1 < supports.size(); i++) {
			int a = supports.get(i);
			int b2 = supports.get(i + 1);
			while (b2 - a > StationGeometry.MAX_BEAM_SPAN) {
				// the farthest free column within 12 of the last support, so no span is left longer (halving a span over 24
				// would leave both halves too long)
				int pierU = -1;
				for (int u = Math.min(a + StationGeometry.MAX_BEAM_SPAN, b2 - 1); u > a && pierU < 0; u--) {
					if (nearestFreeColumn(u, vRow) == u && (line || freeColumn(u, 0, StationGeometry.UPPER_FLOOR_Y - 1, vRow))) {
						pierU = u;
					}
				}
				if (pierU < 0) {
					break;
				}
				for (int y = 0; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					put(Role.BRIDGE_PIER, pierU, y, vRow, LocalDir.PLUS_V, b.pier());
				}
				extraSupport.add(new Box(pierU, -1, vRow, pierU, -1, vRow));
				a = pierU;
			}
		}
	}

	boolean has(int u, int y, int v, Role role) {
		final PlannedBlock block = at(u, y, v);
		return block != null && block.role() == role;
	}

	/** Rows a hall's piers keep from its middle: clear of the flights, their landings and the way off them. */
	static final int HALL_CLEAR = 16;
	/** Rows between a hall's pier rows; every other one carries a column up to the roof as well. */
	static final int HALL_BAY = 12;
	/** Rows of the public passage along a hall's low wall, open from street to street without gates. */
	static final int HALL_PASSAGE = 6;
	/** Gates either side of a gate bank's middle. */
	static final int HALL_GATES = 6;
	/** Rows from a hall's middle to its ticket machines and name signs: past the flights' openings in the floor. */
	static final int HALL_FITTINGS = 12;
	/** Height of a hall's eaves over its floor. */
	static final int HALL_EAVES = 9;
	/** Columns between a hall roof's light strips. */
	static final int HALL_LIGHTS = 6;
	static final String HALL_ROOF = ata("utrecht_wave_roof_panel");
	static final String HALL_RISER = ata("metro_steel_cladding");
	static final String HALL_LIGHT = ata("canopy_light");
	static final String HALL_GLASS = ata("utrecht_hall_glass_facade");
	static final String HALL_SHAFT = ata("steel_column_round");
	static final String HALL_CROWN = ata("utrecht_tree_column_white");

	/** Rows of piers under the deck: the bridge line, and under a real station's hall every HALL_BAY rows out to its walls. */
	List<Integer> pierRows() {
		final List<Integer> rows = new ArrayList<>();
		rows.add(site.bridgeV());
		if (spec.real() != RealStation.NONE) {
			final int vB = site.bridgeV();
			for (int k = HALL_CLEAR; k < bridgeWall() - 1; k += HALL_BAY) {
				rows.add(vB - k);
				rows.add(vB + k);
			}
			// and one inside each wall, so the floor's edge is carried
			rows.add(vB - bridgeWall() + 1);
			rows.add(vB + bridgeWall() - 1);
		}
		return rows;
	}

	/**
	 * A real station's hall, on the deck that spans the whole site at the bridge line (Utrecht Centraal: one hall over all the
	 * tracks, reached from the street at both ends). Glass walls all round; the roof's waves across the tracks, the middle
	 * one highest, with light strips under it; white tree columns up to it on the platforms' pier lines; glass round every
	 * opening in the floor where the flights go down; gate lines just inside each street end, with a bank at the way up from
	 * the street and glass along the rest; a public passage along the low wall from street to street, fenced from the paid
	 * area and gateless; ticket machines and the station's name in the unpaid ends.
	 */
	void realHall(int from, int to, int wallLo, int wallHi) {
		final int vB = site.bridgeV();
		final int floor = StationGeometry.UPPER_WALK_Y;
		final int[] surface = spec.real().roofProfile(to - from, floor + HALL_EAVES);
		// full blocks under each step up, meeting the lower neighbour's top, so the shell is closed
		final int[] low = steps(surface, true);
		for (int u = from; u <= to; u++) {
			final int i = u - from;
			for (int v = wallLo; v <= wallHi; v++) {
				for (int y = low[i]; y < surface[i]; y++) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, HALL_RISER);
				}
				final boolean light = i % HALL_LIGHTS == HALL_LIGHTS / 2 && v > wallLo && v < wallHi && Math.floorMod(v - vB, 4) == 0;
				put(Role.BUILDING_ROOF, u, surface[i], v, LocalDir.PLUS_V, light ? HALL_LIGHT : HALL_ROOF);
			}
			// the long walls, glass up to the roof
			for (int y = floor; y < low[i]; y++) {
				put(Role.WINDOW, u, y, wallLo, LocalDir.MINUS_V, HALL_GLASS);
				put(Role.WINDOW, u, y, wallHi, LocalDir.PLUS_V, HALL_GLASS);
			}
		}
		// the street ends, glass up to the roof
		for (final int u : new int[]{from, to}) {
			for (int v = wallLo + 1; v < wallHi; v++) {
				for (int y = floor; y < low[u - from]; y++) {
					put(Role.WINDOW, u, y, v, u == to ? LocalDir.PLUS_U : LocalDir.MINUS_U, HALL_GLASS);
				}
			}
		}
		// tree columns on every other pier row, over the piers on the platforms
		for (int k = HALL_CLEAR + HALL_BAY; k < bridgeWall() - 1; k += 2 * HALL_BAY) {
			for (final int v : new int[]{vB - k, vB + k}) {
				for (final TrackLayout.PlatformBand band : layout.platforms()) {
					for (final int u : bridgePierLines(band)) {
						final int crown = low[u - from] - 1;
						if (has(u, StationGeometry.UPPER_FLOOR_Y - 1, v, Role.BRIDGE_PIER) && freeColumn(u, floor, crown, v)) {
							for (int y = floor; y < crown; y++) {
								put(Role.BRIDGE_PIER, u, y, v, LocalDir.PLUS_V, HALL_SHAFT);
							}
							put(Role.BRIDGE_PIER, u, crown, v, LocalDir.PLUS_V, HALL_CROWN);
						}
					}
				}
			}
		}
		hallOpenings(from, to, wallLo, wallHi);
		// gate lines just inside each street end: a bank at the way up from the street, glass along the rest (green towards
		// the street, where people come in); the passage along the low wall stays open
		final int fence = wallLo + HALL_PASSAGE + 1;
		final int gateLo = site.street().uMax() + 1;
		final int gateHi = site.farStreet().uMin() - 1;
		for (final int u : new int[]{gateLo, gateHi}) {
			final LocalDir green = u == gateLo ? LocalDir.MINUS_U : LocalDir.PLUS_U;
			for (int v = fence; v < wallHi; v++) {
				final int d = v - vB;
				if (Math.abs(d) <= HALL_GATES) {
					final String kind = Math.abs(d) == HALL_GATES ? "kind=end" : d % 4 == 0 ? "kind=wide" : "kind=gate";
					put(Role.GATE, u, floor, v, green, palette.blockId(Role.GATE), kind);
				} else {
					put(Role.BARRIER, u, floor, v, LocalDir.PLUS_U);
				}
			}
		}
		for (int u = gateLo + 1; u < gateHi; u++) {
			put(Role.BARRIER, u, floor, fence, LocalDir.PLUS_V);
		}
		// ticket machines along each end wall, either side of the way up from the street, and the station's name facing the
		// street by each gate line: past the openings in the floor and inside the walls even in the shallowest hall
		for (final int side : new int[]{-1, 1}) {
			for (int i = 0; i < 3; i++) {
				final int v = vB + side * (HALL_FITTINGS + i);
				if (v > wallLo && v < wallHi) {
					tallFixture(from + 1, floor, v, LocalDir.PLUS_U, "mtr:ticket_machine");
					tallFixture(to - 1, floor, v, LocalDir.MINUS_U, "mtr:ticket_machine");
				}
			}
		}
		if (!spec.name().isEmpty()) {
			for (int i = 0; i < 3 && vB + HALL_FITTINGS + i < wallHi; i++) {
				put(Role.NAME_SIGN, gateLo - 1, floor, vB + HALL_FITTINGS + i, LocalDir.MINUS_U);
				put(Role.NAME_SIGN, gateHi + 1, floor, vB + HALL_FITTINGS + i, LocalDir.PLUS_U);
			}
		}
	}

	/** Glass on both flanks of a flight under a hall's floor, from its top step to its foot, where the flank is on the platform. */
	void hallFlanks(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b) {
		final List<Integer> open = opening(band, flight);
		final int top = flight.stepV(StationGeometry.STAIR_STEPS);
		final int foot = flight.stepV(1);
		for (final int u : new int[]{open.get(0) - 1, open.get(open.size() - 1) + 1}) {
			if (u < band.uMin() || u > band.uMax() || isEdge(band, u)) {
				continue;
			}
			final LocalDir out = u < open.get(0) ? LocalDir.MINUS_U : LocalDir.PLUS_U;
			for (int v = top; v != foot + flight.side(); v += flight.side()) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					put(Role.WINDOW, u, y, v, out, b.bridgeWall());
				}
			}
		}
	}

	/**
	 * Glass round every opening in a hall's floor, on the floor's edge: where a flight goes down, the way onto its top step
	 * (a step one below the floor, straight ahead along the tracks) stays open.
	 */
	void hallOpenings(int from, int to, int wallLo, int wallHi) {
		final int deck = StationGeometry.UPPER_FLOOR_Y;
		for (int u = from + 1; u < to; u++) {
			for (int v = wallLo + 1; v < wallHi; v++) {
				if (!has(u, deck, v, Role.BRIDGE_DECK)) {
					continue;
				}
				for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					final int nu = u + d[0];
					final int nv = v + d[1];
					if (!cleared(nu, deck, nv)) {
						continue;
					}
					final PlannedBlock step = at(nu, deck - 1, nv);
					if (d[0] == 0 && step != null && (step.role() == Role.STAIR || step.role() == Role.ESCALATOR)) {
						continue;
					}
					put(Role.BARRIER, u, StationGeometry.UPPER_WALK_Y, v, d[0] == 0 ? LocalDir.PLUS_V : LocalDir.PLUS_U);
					break;
				}
			}
		}
	}

	/** A platform's boarding edge column. */
	boolean onEdge(int u) {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (isEdge(band, u)) {
				return true;
			}
		}
		return false;
	}

	/** A free column near u0 at the bridge line that is not inside any track envelope or on a boarding edge; -1 if none nearby. */
	int nearestFreeColumn(int u0) {
		return nearestFreeColumn(u0, site.bridgeV());
	}

	/** {@link #nearestFreeColumn(int)} on row v. */
	int nearestFreeColumn(int u0, int v) {
		for (int d = 0; d <= 6; d++) {
			for (final int u : new int[]{u0 - d, u0 + d}) {
				if (u < 0 || u >= layout.width()) {
					continue;
				}
				boolean envelope = false;
				for (final TrackLayout.Track track : layout.tracks()) {
					if (Math.abs(u - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH) {
						envelope = true;
						break;
					}
				}
				if (!envelope && !onEdge(u) && !taken(u, 1, v)) {
					return u;
				}
				if (d == 0) {
					break;
				}
			}
		}
		return -1;
	}

	/**
	 * MTR up escalators beside the platform stairs, planned first so the platform floor and the deck leave their landings
	 * to them. The slope runs level with the stair beside it; a flat landing step at each end lies in the platform floor
	 * and in the corridor wall row. Each step has its handrail block above it, and a casing fills under the slope.
	 */
	/**
	 * Phase 2: an underpass ("underpass"), a stairs-only pedestrian tunnel under every track near the platforms' far end from
	 * the footbridges, a storey below the platforms. The corridor is three wide and three clear, walled, floored and roofed
	 * with ceiling lights, closed at both ends; each platform wide enough has a stair down to it in a walled well on two
	 * columns clear of the edges and the column lines, railed round its opening at platform level. The tunnel and its wells
	 * are dug: the site survey asks for terrain approval. Planned first, so no later pass builds into the wells.
	 */
	void underpass() {
		if (!spec.underpass() || layout.terminus() || layout.platforms().size() < 2) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int floorY = StationGeometry.UNDERPASS_FLOOR_Y;
		final int ceilY = StationGeometry.UNDERPASS_CEILING_Y;
		// at the end away from the footbridges (or from the entrance with none)
		final int ref = far != null ? far.bridgeV() : site.bridge() ? site.bridgeV() : site.entranceV();
		final boolean low = ref > length / 2;
		final int vU = low ? UNDERPASS_END : length - 1 - UNDERPASS_END;
		final int side = low ? 1 : -1;
		final int reach = 2 + StationGeometry.STAIR_STEPS + 1;
		// the stairs, chosen first: an underpass reaching fewer than two platforms is no way across
		final java.util.Map<TrackLayout.PlatformBand, Integer> stairs = new java.util.LinkedHashMap<>();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (!band.runs(vU - 2) || !band.runs(vU + side * reach)) {
				continue;
			}
			final List<Integer> lines = columnLines(band, roof(band));
			int best = -1;
			for (int c = band.uMin() + 1; c + 2 <= band.uMax(); c++) {
				boolean ok = true;
				for (int u = c - 1; u <= c + 2; u++) {
					ok &= u >= band.uMin() && u <= band.uMax() && !isEdge(band, u) && !lines.contains(u);
				}
				final int centre = (band.uMin() + band.uMax()) / 2;
				if (ok && (best < 0 || Math.abs(c - centre) < Math.abs(best - centre))) {
					best = c;
				}
			}
			if (best >= 0) {
				stairs.put(band, best);
			}
		}
		if (stairs.size() < 2) {
			return;
		}
		final int uA = Math.max(1, layout.platforms().get(0).uMin());
		final int uB = Math.min(layout.width() - 2, layout.platforms().get(layout.platforms().size() - 1).uMax());
		// the wells: steps from the corridor's wall up to the platform, a stringer under each, walls either side, a rail round
		// the opening; dug clear above every step
		for (final java.util.Map.Entry<TrackLayout.PlatformBand, Integer> stair : stairs.entrySet()) {
			final int c = stair.getValue();
			final LocalDir up = side > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			for (int u = c; u <= c + 1; u++) {
				for (int y = floorY + 1; y <= floorY + 3; y++) {
					keepClear(u, y, vU + side * 2);
				}
				for (int i = 1; i < StationGeometry.STAIR_STEPS; i++) {
					final int v = vU + side * (2 + i);
					final int stepY = floorY + i;
					for (int y = stepY + 1; y <= stepY + 3; y++) {
						keepClear(u, y, v);
						if (y == -1) {
							holes.add(new Cell(u, y, v));
						}
					}
					// dug out up to the platform floor, which the well replaces from the fourth step on
					extraHeadroom.add(new Box(u, stepY + 1, v, u, Math.min(stepY + 3, StationGeometry.PLATFORM_Y), v));
					put(Role.STAIR, u, stepY, v, up, b.stair());
					for (int y = floorY; y < stepY; y++) {
						put(Role.WALL, u, y, v, up, b.trim());
					}
				}
			}
			for (int i = 0; i < StationGeometry.STAIR_STEPS; i++) {
				final int v = vU + side * (2 + i);
				for (final int u : new int[]{c - 1, c + 2}) {
					for (int y = floorY; y < StationGeometry.PLATFORM_Y; y++) {
						put(Role.WALL, u, y, v, LocalDir.PLUS_V, b.interiorWall());
					}
					// the rail along the opening, where the platform floor is cut away beneath it
					if (floorY + i + 3 >= StationGeometry.PLATFORM_Y) {
						put(Role.RAILING, u, StationGeometry.WALK_Y, v, u < c ? LocalDir.MINUS_U : LocalDir.PLUS_U);
					}
				}
				// across the opening's corridor end, where the floor still stands over the steps
				if (floorY + i + 3 == StationGeometry.PLATFORM_Y - 1) {
					for (int u = c; u <= c + 1; u++) {
						put(Role.RAILING, u, StationGeometry.WALK_Y, v, side > 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V);
					}
				}
			}
		}
		// the corridor: floor, walls (open where a stair leaves), ceiling with lights, end walls; dug clear inside
		extraHeadroom.add(new Box(uA, floorY + 1, vU - 1, uB, floorY + 3, vU + 1));
		for (int u = uA - 1; u <= uB + 1; u++) {
			final boolean end = u == uA - 1 || u == uB + 1;
			for (int v = vU - 2; v <= vU + 2; v++) {
				put(Role.FLOOR, u, floorY, v, LocalDir.PLUS_V, b.deck());
				put((u + v) % 4 == 0 && !end && Math.abs(v - vU) <= 1 ? Role.LIGHT_PANEL : Role.BUILDING_ROOF, u, ceilY, v, LocalDir.PLUS_V,
						(u + v) % 4 == 0 && !end && Math.abs(v - vU) <= 1 ? b.light() : b.deck());
				final boolean wallRow = Math.abs(v - vU) == 2;
				for (int y = floorY + 1; y < ceilY; y++) {
					if (end || wallRow && !air.contains(new Cell(u, y, v))) {
						put(Role.WALL, u, y, v, LocalDir.PLUS_V, b.interiorWall());
					}
				}
			}
		}
	}
}
