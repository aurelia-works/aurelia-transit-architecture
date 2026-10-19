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
		// piers on every platform at the bridge line, up to the deck: down the middle, or evenly spaced on a platform too wide
		// for the deck to overhang one
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
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
		// flanking piers at the stair landings (never on the landing itself) so the canopy ends stay carried; under a canopy
		// they stop below it, so the canopy runs over them
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			final int flankTop = roof(band) == RoofType.CANOPY ? StationGeometry.CANOPY_Y : StationGeometry.UPPER_FLOOR_Y;
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
		// six-foot piers where the deck span between supports exceeds 12
		final List<Integer> supports = new ArrayList<>();
		supports.add(from);
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			supports.addAll(bridgePierLines(band));
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
					if (nearestFreeColumn(u) == u) {
						pierU = u;
					}
				}
				if (pierU < 0) {
					break;
				}
				for (int y = 0; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					put(Role.BRIDGE_PIER, pierU, y, vB, LocalDir.PLUS_V, b.pier());
				}
				extraSupport.add(new Box(pierU, -1, vB, pierU, -1, vB));
				a = pierU;
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
				if (!envelope && !onEdge(u) && !taken(u, 1, site.bridgeV())) {
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
