package com.aureliatransit.architecture.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Stair shafts, their hoods and the posts beside them.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanStairs extends PlanFurniture {

	PlanStairs(StationSpec spec) {
		super(spec);
	}

	/**
	 * Under a canopy, a platform stair is enclosed in the footbridge's glazing (or walls): both flanks of the whole flight
	 * from the platform up to the bridge roof (no metal rails; never on a boarding edge), across its far end above the
	 * canopy, and a roof over just that shaft. So the steps are not left standing in the open above the canopy.
	 */
	void stairShaft(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b) {
		final List<Integer> open = opening(band, flight);
		final int left = open.get(0) - 1;
		final int right = open.get(open.size() - 1) + 1;
		// steps whose headroom reaches the canopy level pass through it; the step below them ends the shaft
		final int lowest = Math.max(1, StationGeometry.CANOPY_Y - 2);
		final int top = flight.stepV(StationGeometry.STAIR_STEPS);
		final int end = lowest > 1 ? flight.stepV(lowest - 1) : flight.landingV();
		final int side = flight.side();
		final LocalDir out = side > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
		final int foot = flight.stepV(1);
		final java.util.Set<Integer> shaftRows = new java.util.HashSet<>();
		for (int v = top; v != end + side; v += side) {
			shaftRows.add(v);
		}
		for (int y = StationGeometry.WALK_Y; y < StationGeometry.BUILDING_ROOF_Y; y++) {
			for (int v = top; v != foot + side; v += side) {
				// past the shaft the flight is under the canopy: its flanks stop below the roof, which covers them
				if (!shaftRows.contains(v) && y >= StationGeometry.CANOPY_Y) {
					continue;
				}
				// below the canopy a flank on a boarding edge stays open
				final boolean high = y > StationGeometry.CANOPY_Y;
				if (high || !isEdge(band, left) && left >= band.uMin()) {
					flank(left, y, v, LocalDir.MINUS_U, b);
				}
				if (high || !isEdge(band, right) && right <= band.uMax()) {
					flank(right, y, v, LocalDir.PLUS_U, b);
				}
			}
			if (y > StationGeometry.CANOPY_Y) {
				for (int u = left + 1; u < right; u++) {
					flank(u, y, end, out, b);
				}
			}
		}
		for (int u = left; u <= right; u++) {
			for (int v = top; v != end + side; v += side) {
				put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, (u + v) % 4 == 1 ? b.bridgeLight() : b.bridgeRoof());
			}
		}
		flankPosts(band, flight, left, right, b);
	}

	/**
	 * Posts under the deck at the top of a flight's glazed flanks, in the corridor's wall row: the panes would otherwise end
	 * in the air there, below the deck edge.
	 */
	void flankPosts(TrackLayout.PlatformBand band, StationLayout.Flight flight, int left, int right, StylePalette.Building b) {
		if (spec.enclosedStairs()) {
			return;
		}
		for (final int u : new int[]{left, right}) {
			if (u < band.uMin() || u > band.uMax() || isEdge(band, u) || flight.columns().contains(u)) {
				continue;
			}
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				if (!taken(u, y, flight.wallV())) {
					put(Role.BRIDGE_PIER, u, y, flight.wallV(), LocalDir.PLUS_V, b.pier());
				}
			}
		}
	}

	/**
	 * Without a canopy, a stair flight is glazed (or walled) like the corridor it leaves: walls on both flanks from the
	 * platform up to a roof over the flight alone, open at the bottom step. A flank on a boarding edge stays open.
	 */
	void stairHood(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b) {
		final List<Integer> open = opening(band, flight);
		final int left = Math.max(band.uMin(), open.get(0) - 1);
		final int right = Math.min(band.uMax(), open.get(open.size() - 1) + 1);
		final boolean wallLeft = left < open.get(0) && !isEdge(band, left);
		final boolean wallRight = right > open.get(open.size() - 1) && !isEdge(band, right);
		final int top = flight.stepV(StationGeometry.STAIR_STEPS);
		final int bottom = flight.stepV(1);
		final int side = flight.side();
		for (int v = top; v != bottom + side; v += side) {
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.BUILDING_ROOF_Y; y++) {
				if (wallLeft) {
					flank(left, y, v, LocalDir.MINUS_U, b);
				}
				if (wallRight) {
					flank(right, y, v, LocalDir.PLUS_U, b);
				}
			}
			for (int u = left; u <= right; u++) {
				put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, (u + v) % 4 == 1 ? b.bridgeLight() : b.bridgeRoof());
			}
		}
		flankPosts(band, flight, wallLeft ? left : -1, wallRight ? right : -1, b);
	}

	/** True within 2 of a footbridge stair flight or its landing on this platform, or within 3 of its lift shaft. */
	boolean nearStairs(TrackLayout.PlatformBand band, int v) {
		final StationLayout.Lift lift = site.platformLift(band, layout);
		// the shaft and the landings at both its doors stay clear: nobody wants a bench or a screen against the lift
		if (lift != null && v >= lift.vMin() - 3 && v <= lift.vMax() + 3) {
			return true;
		}
		for (final StationLayout.Flight flight : site.flights(band)) {
			final int a = Math.min(flight.wallV(), flight.landingV());
			final int z = Math.max(flight.wallV(), flight.landingV());
			if (v >= a - 2 && v <= z + 2) {
				return true;
			}
		}
		return false;
	}

	/** True in a flight's columns or the one beside them, from its top step to two rows past its landing. */
	boolean atStairFoot(TrackLayout.PlatformBand band, int u, int v) {
		for (final StationLayout.Flight flight : site.flights(band)) {
			final int a = Math.min(flight.wallV(), flight.landingV() + 2 * flight.side());
			final int z = Math.max(flight.wallV(), flight.landingV() + 2 * flight.side());
			if (v >= a && v <= z && u >= flight.columns().get(0) - 1 && u <= flight.columns().get(flight.columns().size() - 1) + 1) {
				return true;
			}
		}
		return false;
	}
}
