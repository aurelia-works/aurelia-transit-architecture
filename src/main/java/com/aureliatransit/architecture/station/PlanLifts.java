package com.aureliatransit.architecture.station;

import java.util.List;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Lift shafts, their stops and call buttons.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanLifts extends PlanBuilding {

	PlanLifts(StationSpec spec) {
		super(spec);
	}

	/**
	 * Lift shafts: one per footbridge platform wide enough for a two-column stair, beside the corridor on the side away from
	 * the stair, and one in the station building beside the inner stair. Planned before the building and the footbridge so
	 * the slab, deck and corridor walls leave the shafts open; MTR turns each column of stops into a lift when the build is
	 * done.
	 */
	void lifts() {
		final StylePalette.Building b = palette.building();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final StationLayout.Lift lift = site.platformLift(band, layout);
			if (lift != null) {
				// at the platform the car also opens at the back, onto the platform, when the platform runs on past it;
				// at the footbridge it opens only into the corridor
				shaft(lift, b.bridgeWall(), true, b.bridgeRoof(), band.runs(lift.doorV() + 4 * lift.out()));
			}
		}
		if (site.street() != null) {
			final StationLayout.Lift street = site.platformLift(site.street(), layout);
			if (street != null) {
				shaft(street, b.bridgeWall(), true, b.bridgeRoof(), site.street().runs(street.doorV() + 4 * street.out()));
			}
		}
		final StationLayout.Lift hall = site.buildingLift(layout);
		if (hall != null) {
			// the building's own platform wall closes the shaft's far side, and its roof covers it
			shaft(hall, b.interiorWall(), false, null, false);
		}
	}

	/**
	 * Lift call buttons, at hand height beside each door. MTR's button is a thin plate on its block's front side, meant to
	 * stand in front of a wall; in the shaft's own wall it would sit flush with the car, which clips it. So it stands on the
	 * landing against a corner post, on whichever side of the doors has a free landing cell (a corridor's glass wall takes
	 * one side at the footbridge); then against the wall; and only with both sides taken does it stay in the shaft wall.
	 */
	/**
	 * The approach to every stair and escalator foot is kept clear of screens, signs, benches, bins and lamps: {@link #STAIR_APPROACH}
	 * cells out along the platform, one cell either side, from the platform floor to head height. Real platforms keep the
	 * queuing space in front of a stair free; furnishing is planned after this, so it goes elsewhere. (A route map used to stand
	 * on each enclosure's end post, right in that space; MTR shows a route on it only once it is picked with the brush, so a
	 * plan could not fill it in anyway.)
	 */
	void stairFeet() {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (final StationLayout bridge : far == null ? List.of(site) : List.of(site, far)) {
				for (final StationLayout.Flight flight : bridge.flights(band)) {
					final List<Integer> columns = opening(band, flight);
					final int from = Math.min(flight.wallV(), flight.landingV()) - (flight.side() < 0 ? STAIR_APPROACH : 0);
					final int to = Math.max(flight.wallV(), flight.landingV()) + (flight.side() > 0 ? STAIR_APPROACH : 0);
					clearApproach(columns, from, to);
				}
				if (bridge.hasEscalator(band)) {
					clearApproach(bridge.flightColumns(band), bridge.escalatorFootV() - STAIR_APPROACH, bridge.escalatorFootV() + STAIR_APPROACH);
				}
			}
		}
	}

	void clearApproach(List<Integer> columns, int vFrom, int vTo) {
		if (columns.isEmpty()) {
			return;
		}
		final int u0 = columns.stream().mapToInt(Integer::intValue).min().getAsInt() - 1;
		final int u1 = columns.stream().mapToInt(Integer::intValue).max().getAsInt() + 1;
		for (int u = u0; u <= u1; u++) {
			for (int v = vFrom; v <= vTo; v++) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.HEADROOM + 1; y++) {
					air.add(new Cell(u, y, v));
				}
			}
		}
	}

	String blockId(int u, int y, int v) {
		final PlannedBlock block = cells.get(new Cell(u, y, v));
		return block == null ? null : block.blockId();
	}

	void liftButtons() {
		final String post = palette.building().pier();
		for (final LiftButton button : buttons) {
			final int landing = button.v() - button.o();
			Integer at = null;
			for (final boolean onPost : new boolean[]{true, false}) {
				for (final int u : new int[]{button.u(), button.otherU()}) {
					final PlannedBlock holder = cells.get(new Cell(u, button.y(), button.v()));
					if (at == null && holder != null && (!onPost || holder.blockId().equals(post)) && !taken(u, button.y(), landing)) {
						at = u;
					}
				}
			}
			if (at != null) {
				// MTR's button plate is a full block wide and the post only half, so the plate would hang in the air beside
				// it: the post is clad full width from foot to head where the button is fixed
				for (final int step : new int[]{-1, 1}) {
					for (int y = step < 0 ? button.y() : button.y() + 1; ; y += step) {
						final PlannedBlock holder = cells.get(new Cell(at, y, button.v()));
						if (holder == null || !holder.blockId().equals(post)) {
							break;
						}
						cells.put(new Cell(at, y, button.v()), new PlannedBlock(at, y, button.v(), holder.role(), palette.building().plinth(),
								holder.front(), holder.text(), holder.state()));
					}
				}
				put(Role.LIFT, at, button.y(), landing, button.in(), StylePalette.MTR_LIFT_BUTTONS, "unlocked=true");
			} else {
				final Cell wall = new Cell(button.u(), button.y(), button.v());
				cells.put(wall, new PlannedBlock(button.u(), button.y(), button.v(), Role.LIFT, StylePalette.MTR_LIFT_BUTTONS, button.in(), "",
						"unlocked=true"));
			}
		}
	}

	/**
	 * One shaft: a track column fixed to the inside of the side glass on the left as you step in (in the car's back row, out
	 * of the way of the doors, the glass wall left whole), with a stop at the walk level and at the upper walk
	 * level, a pair of landing doors at each stop with a call button beside them, walls, and a roof unless {@code roofId}
	 * is null. The car space stays empty from the walk level to the roof. With {@code backDoor} the back wall also has a pair of doors at the
	 * walk level only, and the car is formed double-sided: MTR opens each side only where it finds doors at that floor.
	 */
	void shaft(StationLayout.Lift lift, String wallId, boolean farWall, String roofId, boolean backDoor) {
		final int o = lift.out();
		final LocalDir in = o > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
		final int c0 = lift.c0();
		final int c1 = c0 + 1;
		final int vD = lift.doorV();
		final int vBack = vD + 3 * o;
		// seen from the landing, facing in, the left door leaf is c0 facing +v and c1 facing -v
		final int cT = in == LocalDir.PLUS_V ? c0 : c1;
		final int low = StationGeometry.WALK_Y;
		final int high = StationGeometry.UPPER_WALK_Y;
		final int roof = StationGeometry.BUILDING_ROOF_Y;
		// u is to the right of v, so the left wall is c0 - 1 facing +v and c1 + 1 facing -v; a shaft without a far wall
		// (the building's wall closes it) keeps its track against its own glass wall
		final boolean left = !farWall || o > 0;
		// the track is fixed to the inside of the side glass, in the car's back row, out of the way of both door pairs; its
		// plate is on the side it faces, so it faces the glass, and the stop's build finds the doors two cells along it
		final int cTrack = left ? c0 : c1;
		final LocalDir toGlass = left ? LocalDir.MINUS_U : LocalDir.PLUS_U;
		for (int y = low; y <= high + 1; y++) {
			put(Role.LIFT, cTrack, y, vD + 2 * o, toGlass, y == low || y == high ? StylePalette.MTR_LIFT_FLOOR : StylePalette.MTR_LIFT_TRACK);
		}
		for (int c = c0; c <= c1; c++) {
			for (int v = vD + o; v != vBack; v += o) {
				for (int y = low; y < roof; y++) {
					keepClear(c, y, v);
				}
			}
			for (int y = low; y <= low + 2; y++) {
				keepClear(c, y, vD - o);
				if (backDoor) {
					// three rows: a roof column just past the landing would still stand in the way of whoever steps out
					for (int k = 1; k <= 3; k++) {
						keepClear(c, y, vBack + k * o);
					}
				}
			}
			// MTR draws the car's floor level with the stop; a floor block there flickers through it, so the car rests in a pit
			for (int v = vD + o; v != vBack; v += o) {
				final Cell pit = new Cell(c, low - 1, v);
				cells.remove(pit);
				air.add(pit);
			}
		}
		for (final int stop : new int[]{low, high}) {
			for (int c = c0; c <= c1; c++) {
				final String side = c == cT ? "side=left" : "side=right";
				put(Role.LIFT, c, stop, vD, in, StylePalette.MTR_LIFT_DOOR, "half=lower," + side + ",unlocked=true");
				put(Role.LIFT, c, stop + 1, vD, in, StylePalette.MTR_LIFT_DOOR, "half=upper," + side + ",unlocked=true");
			}
			buttons.add(new LiftButton(c0 - 1, stop + 1, vD, o, in, c1 + 1));
		}
		if (backDoor) {
			// seen from the platform behind the shaft the pair is mirrored: the left leaf is the one away from the track
			for (int c = c0; c <= c1; c++) {
				final String side = c == cT ? "side=right" : "side=left";
				put(Role.LIFT, c, low, vBack, in.opposite(), StylePalette.MTR_LIFT_DOOR, "half=lower," + side + ",unlocked=true");
				put(Role.LIFT, c, low + 1, vBack, in.opposite(), StylePalette.MTR_LIFT_DOOR, "half=upper," + side + ",unlocked=true");
			}
			buttons.add(new LiftButton(c1 + 1, low + 1, vBack, -o, in.opposite(), c0 - 1));
		}
		final int cMax = farWall ? c1 + 1 : c1;
		for (int y = low; y < roof; y++) {
			for (int c = c0 - 1; c <= cMax; c++) {
				// a glass shaft stands on solid corner posts: the panes end on them and the call buttons are fixed to them
				final boolean corner = farWall && (c == c0 - 1 || c == cMax);
				final String id = corner ? palette.building().pier() : wallId;
				// the door row's floor at the upper level is the deck or the slab
				if (y != StationGeometry.UPPER_FLOOR_Y) {
					put(Role.WALL, c, y, vD, in.opposite(), id);
				}
				if (!backDoor || y > low + 1 || c < c0 || c > c1) {
					put(Role.WALL, c, y, vBack, in, id);
				}
			}
			for (int v = vD + o; v != vBack; v += o) {
				put(Role.WALL, c0 - 1, y, v, LocalDir.MINUS_U, wallId);
				if (farWall) {
					put(Role.WALL, c1 + 1, y, v, LocalDir.PLUS_U, wallId);
				}
			}
		}
		if (roofId != null) {
			for (int c = c0 - 1; c <= c1 + 1; c++) {
				for (int v = vD + o; v != vBack + o; v += o) {
					put(Role.BUILDING_ROOF, c, roof, v, LocalDir.PLUS_V, roofId);
				}
			}
		}
	}

	/** True when the platform ends at a terminus head's gate line, which carries its big number. */
	boolean gateNumbered(TrackLayout.PlatformBand band) {
		return layout.terminus() && band.uMax() > layout.headFrom() && band.uMin() < layout.headTo();
	}
}
