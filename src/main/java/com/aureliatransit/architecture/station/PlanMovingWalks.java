package com.aureliatransit.architecture.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Escalators and travelators.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanMovingWalks extends PlanBridges {

	PlanMovingWalks(StationSpec spec) {
		super(spec);
	}

	void escalators() {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final List<Integer> columns = site.escalator(band);
			if (columns.isEmpty()) {
				continue;
			}
			escalator(columns, site.stairSide(), site.stairWallV());
			// the second flight, on the corridor's other side, is an escalator too
			final StationLayout.Flight reverse = site.reverseEscalator(band);
			if (reverse != null) {
				escalator(reverse.columns(), reverse.side(), reverse.wallV());
			}
		}
	}

	/**
	 * An up escalator on {@code columns} from the corridor wall at {@code wallV} down towards {@code side}: its top landing
	 * in the wall row at deck level, the bottom landing at platform level, a clear row at its foot.
	 */
	void escalator(List<Integer> columns, int side, int wallV) {
		final LocalDir up = side > 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
		// MTR's left half is on the left looking up the escalator; +U is to the right of +V
		final int left = up == LocalDir.PLUS_V ? columns.get(0) : columns.get(1);
		for (int i = 0; i <= StationLayout.ESCALATOR_TOP; i++) {
			final int v = wallV + side * (StationLayout.ESCALATOR_TOP - i);
			final int y = StationLayout.escalatorY(i);
			for (final int u : columns) {
				final String half = u == left ? "left" : "right";
				put(Role.ESCALATOR, u, y, v, up, StylePalette.MTR_ESCALATOR_STEP, "side=" + half + ",direction=true,status=true");
				put(Role.ESCALATOR, u, y + 1, v, up, StylePalette.MTR_ESCALATOR_SIDE, "side=" + half);
				for (int below = StationGeometry.WALK_Y; below < y; below++) {
					put(Role.BRIDGE_PIER, u, below, v, up, palette.building().pier());
				}
				if (i < StationLayout.ESCALATOR_TOP) {
					keepClear(u, y + 2, v);
					keepClear(u, y + 3, v);
				}
			}
		}
		for (final int u : columns) {
			for (int y = StationGeometry.WALK_Y; y <= 3; y++) {
				keepClear(u, y, wallV + side * (StationLayout.ESCALATOR_TOP + 1));
			}
		}
	}

	/** Shortest travelator run, landings included; and the longest platform stretch before a gap to cross. */
	static final int MIN_TRAVELATOR = 8;

	static final int MAX_PLATFORM_TRAVELATOR = 16;

	/**
	 * MTR escalators laid flat as moving walkways, planned before the floors so those leave them their cells. Only on the
	 * platforms (in the footbridge corridor they looked wrong and are left out). Along a platform at least {@link #TRAVELATOR_PLATFORM} long: two wide in a lane
	 * clear of the column lines, away from the stairs and platform ends, in stretches of at most
	 * {@link #MAX_PLATFORM_TRAVELATOR} with a gap to cross between, a lane each way in every stretch, the same hand all along the platform.
	 */
	void travelators() {
		if (!spec.travelators()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (band.length() >= TRAVELATOR_PLATFORM) {
				platformTravelators(band);
			}
		}
	}

	void platformTravelators(TrackLayout.PlatformBand band) {
		final int c = travelatorLane(band);
		if (c < 0) {
			return;
		}
		// rows kept for the stairs, escalators, lifts and the bridge's own piers
		final java.util.Set<Integer> blocked = new java.util.HashSet<>();
		for (final StationLayout bridge : far == null ? List.of(site) : List.of(site, far)) {
			for (final StationLayout.Flight flight : bridge.flights(band)) {
				final int a = Math.min(flight.wallV(), flight.landingV());
				final int z = Math.max(flight.wallV(), flight.landingV());
				for (int v = a - 3; v <= z + 3; v++) {
					blocked.add(v);
				}
			}
			if (bridge.hasEscalator(band)) {
				for (int v = bridge.escalatorFootV() - 3; v <= bridge.escalatorFootV() + 3; v++) {
					blocked.add(v);
				}
			}
			final StationLayout.Lift lift = bridge.platformLift(band, layout);
			if (lift != null) {
				for (int v = lift.vMin() - 2; v <= lift.vMax() + 2; v++) {
					blocked.add(v);
				}
			}
		}
		for (final StationLayout bridge : far == null ? List.of(site) : List.of(site, far)) {
			if (bridge.bridge()) {
				for (int v = bridge.bridgeV() - 3; v <= bridge.bridgeV() + 3; v++) {
					blocked.add(v);
				}
			}
		}
		final int first = band.vFrom() + 6;
		final int last = band.vTo() - 6;
		int v = first;
		while (v <= last) {
			if (blocked.contains(v)) {
				v++;
				continue;
			}
			int end = v;
			while (end + 1 <= last && !blocked.contains(end + 1) && end + 1 - v + 1 <= MAX_PLATFORM_TRAVELATOR) {
				end++;
			}
			if (end - v + 1 >= MIN_TRAVELATOR) {
				for (int w = v; w <= end; w++) {
					// a row has both lanes or neither: a cell something else holds must not leave a lane running one way alone
					if (!freeColumn(c, StationGeometry.PLATFORM_Y, StationGeometry.PLATFORM_Y + 1, w)
							|| !freeColumn(c + 1, StationGeometry.PLATFORM_Y, StationGeometry.PLATFORM_Y + 1, w)) {
						continue;
					}
					// two lanes side by side, one each way, as real paired walkways run: the balustrades meet in the
					// middle (both "right" of their own direction) and passengers step on from the outside. The lower
					// column always runs towards +v, so a stretch past the footbridge keeps the same hand as one before it
					// (choosing the lane by the footbridge crossed the pair over from one stretch to the next)
					travelatorStep(c, StationGeometry.PLATFORM_Y, w, LocalDir.PLUS_V, "right");
					travelatorStep(c + 1, StationGeometry.PLATFORM_Y, w, LocalDir.MINUS_V, "right");
				}
			}
			// a gap of three to cross before the next stretch
			v = end + 4;
		}
	}

	/**
	 * The travelator's two columns on a platform (the first returned): off the column lines and the bridge pier lines, a
	 * walkway beside both, nearest the platform's middle; -1 if the platform has no room.
	 */
	int travelatorLane(TrackLayout.PlatformBand band) {
		if (band.width() < (band.island() ? StationLayout.MIN_TRAVELATOR_ISLAND : StationLayout.MIN_TRAVELATOR_SIDE)) {
			return -1;
		}
		final java.util.Set<Integer> columns = new java.util.HashSet<>(columnLines(band, roof(band)));
		final java.util.Set<Integer> lines = new java.util.HashSet<>(columns);
		lines.addAll(bridgePierLines(band));
		final double middle = (band.uMin() + band.uMax() - 1) / 2.0;
		int best = -1;
		for (int c = band.uMin() + 2; c + 1 <= band.uMax() - 2; c++) {
			// never on a line; and not squeezed between two column lines (a pier stands only at the footbridge)
			if (lines.contains(c) || lines.contains(c + 1) || columns.contains(c - 1) && columns.contains(c + 2)) {
				continue;
			}
			if (best < 0 || Math.abs(c - middle) < Math.abs(best - middle)) {
				best = c;
			}
		}
		return best;
	}

	/** One flat escalator step running {@code way}, with its handrail block above and headroom over that. */
	void travelatorStep(int u, int y, int v, LocalDir way, String side) {
		put(Role.ESCALATOR, u, y, v, way, StylePalette.MTR_ESCALATOR_STEP, "side=" + side + ",direction=true,status=true");
		put(Role.ESCALATOR, u, y + 1, v, way, StylePalette.MTR_ESCALATOR_SIDE, "side=" + side);
		keepClear(u, y + 2, v);
		keepClear(u, y + 3, v);
	}

	/** The columns a flight opens out of the corridor: the main stair's include the escalator beside it. */
	List<Integer> opening(TrackLayout.PlatformBand band, StationLayout.Flight flight) {
		return flight.side() == site.stairSide() ? site.flightColumns(band) : flight.columns();
	}
}
