package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the station building, its entrance and stairs, and the footbridge go, in local coordinates; derived from the spec and
 * the railway layout only, so the planner, the audit and the summary agree. Without a building the building fields are unused;
 * without a footbridge {@link #bridge} is false.
 *
 * @param building      a station building stands on the street side (u = 0..buildingWidth-1)
 * @param bays          facade bays along the tracks
 * @param vStart        first v of the building (its end wall)
 * @param vEnd          last v of the building (the other end wall)
 * @param entranceV     centre of the street entrance, the gate bank and the footbridge exit
 * @param innerStairSide +1 when the building's stairs run from the entrance towards vEnd, -1 towards vStart
 * @param bridge        a footbridge crosses the tracks
 * @param bridgeV       centre line of the footbridge corridor ({@link StationGeometry#BRIDGE_WIDTH} wide)
 * @param deckFrom      first u of the footbridge deck
 * @param deckTo        last u of the footbridge deck (the far platform's back)
 * @param stairSide     +1 when platform stairs leave the footbridge towards higher v, -1 towards lower v; 0 when there is no
 *                      room on either side
 * @param served        platforms reached by footbridge stairs (platform numbers)
 * @param escalators    an up escalator beside each platform stair where the platform is wide enough
 */
public record StationLayout(boolean building, int bays, int vStart, int vEnd, int entranceV, int innerStairSide, boolean bridge, int bridgeV,
		int deckFrom, int deckTo, int stairSide, List<Integer> served, boolean escalators) {

	/** Narrowest island and side platform that take an escalator beside the stair and keep a walkway past both. */
	public static final int MIN_ESCALATOR_ISLAND = 11;
	public static final int MIN_ESCALATOR_SIDE = 9;

	public StationLayout {
		served = List.copyOf(served);
	}

	/** Street wall and platform wall of the building. */
	public static int streetWall() {
		return StationGeometry.APRON;
	}

	public int platformWall(TrackLayout layout) {
		return layout.buildingWidth() - 1;
	}

	/** The building's platform-side doorways open straight onto platform 1. */
	public static boolean platformBesideBuilding(TrackLayout layout) {
		return layout.buildingWidth() > 0 && !layout.platforms().isEmpty() && layout.platforms().get(0).uMin() == layout.buildingWidth();
	}

	public static StationLayout of(StationSpec spec, TrackLayout layout) {
		final int length = layout.platformLength();
		final boolean building = spec.sideBuilding();
		int bays = 0;
		int vStart = 0;
		int vEnd = 0;
		final int entranceV = entranceV(building, length);
		int innerSide = 1;
		if (building) {
			bays = bays(length);
			vStart = (length - (bays * StationGeometry.BAY + 1)) / 2;
			vEnd = vStart + bays * StationGeometry.BAY;
			innerSide = entranceV + 8 <= vEnd - 1 ? 1 : -1;
		}
		final List<Integer> served = new ArrayList<>();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (!(building && band.uMin() == layout.buildingWidth())) {
				served.add(band.number());
			}
		}
		// a big hub's outer islands have through tracks on both sides, so a footbridge links them to the bay platforms
		boolean bridge = (spec.footbridge() || building || outerIslands(layout)) && !served.isEmpty() && (building || layout.platforms().size() >= 2);
		final int bridgeV = entranceV;
		int stairSide = 0;
		if (bridgeV + 1 + 7 <= length - 1) {
			stairSide = 1;
		} else if (bridgeV - 1 - 7 >= 0) {
			stairSide = -1;
		}
		int deckFrom = 0;
		int deckTo = 0;
		if (bridge) {
			deckFrom = building ? layout.buildingWidth() : layout.platforms().get(0).uMin();
			deckTo = layout.platforms().get(layout.platforms().size() - 1).uMax();
		}
		if (stairSide == 0) {
			bridge = false;
		}
		return new StationLayout(building, bays, vStart, vEnd, entranceV, innerSide, bridge, bridgeV, deckFrom, deckTo, stairSide,
				bridge ? served : List.of(), spec.escalators());
	}

	/** Facade bays of a station building beside platforms this long. */
	private static int bays(int length) {
		return length >= 64 ? 5 : length >= 19 ? 3 : 2;
	}

	/** v of the street entrance and the footbridge line: the middle bay of a side building, else the middle of the platforms. */
	public static int entranceV(boolean building, int length) {
		if (!building) {
			return length / 2;
		}
		final int bays = bays(length);
		return (length - (bays * StationGeometry.BAY + 1)) / 2 + StationGeometry.BAY * (bays / 2) + 3;
	}

	/** A hub with platforms beside no bay track: islands cut off from the head building by tracks running through. */
	public static boolean outerIslands(TrackLayout layout) {
		if (!layout.hub()) {
			return false;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (!layout.bay(band)) {
				return true;
			}
		}
		return false;
	}

	/** v of the corridor's long wall that the platform stairs leave through. */
	public int stairWallV() {
		return stairSide > 0 ? bridgeV + 2 : bridgeV - 2;
	}

	/** v of the step at height y (1..{@link StationGeometry#STAIR_STEPS}) of a platform stair; the top step is next to the wall. */
	public int stairV(int y) {
		return stairWallV() + stairSide * (1 + StationGeometry.STAIR_STEPS - y);
	}

	/** v of the landing at the foot of a platform stair. */
	public int landingV() {
		return stairWallV() + stairSide * (StationGeometry.STAIR_STEPS + 1);
	}

	/** The way a platform stair climbs (its steps' front). */
	public LocalDir stairFront() {
		return stairSide > 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
	}

	/** True when this platform's stair has an up escalator beside it. */
	public boolean hasEscalator(TrackLayout.PlatformBand band) {
		return escalators && bridge && served.contains(band.number())
				&& band.width() >= (band.island() ? MIN_ESCALATOR_ISLAND : MIN_ESCALATOR_SIDE) && band.runs(escalatorFootV());
	}

	/**
	 * Columns of a platform's stair. Beside an escalator the stair, a rail column and the escalator form one group: centred
	 * on an island with the escalator on the right, and on a side platform with the stair against the back as without one.
	 */
	public List<Integer> stairs(TrackLayout.PlatformBand band) {
		if (!hasEscalator(band)) {
			return stairColumns(band);
		}
		final int s = band.island() ? band.uMin() + (band.width() - 5) / 2 : band.trackLeft() ? band.uMax() - 2 : band.uMin() + 1;
		return List.of(s, s + 1);
	}

	/** The escalator's two columns, ascending; empty without one. */
	public List<Integer> escalator(TrackLayout.PlatformBand band) {
		if (!hasEscalator(band)) {
			return List.of();
		}
		final int e = band.island() ? band.uMin() + (band.width() - 5) / 2 + 3 : band.trackLeft() ? band.uMax() - 5 : band.uMin() + 4;
		return List.of(e, e + 1);
	}

	/** Stair and escalator columns together: every column that opens out of the corridor and is kept clear down to the platform. */
	public List<Integer> flightColumns(TrackLayout.PlatformBand band) {
		final List<Integer> all = new ArrayList<>(stairs(band));
		all.addAll(escalator(band));
		all.sort(null);
		return all;
	}

	/**
	 * v of the escalator's step at height y (0..{@link StationGeometry#STAIR_STEPS}), counting from the top: the top landing
	 * is in the corridor wall row at deck level, the bottom landing two rows past the stair's foot, at platform floor level.
	 * Index 0 is the bottom landing, 7 the top landing.
	 */
	public int escalatorV(int index) {
		return stairWallV() + stairSide * (7 - index);
	}

	/** Height of the escalator step at index 0..7: two at the floor, the slope, two at the deck. */
	public static int escalatorY(int index) {
		return Math.max(0, Math.min(StationGeometry.UPPER_FLOOR_Y, index - 1));
	}

	/** v of the clear landing at the escalator's foot. */
	public int escalatorFootV() {
		return stairWallV() + stairSide * 8;
	}

	/** Columns of a platform's stair without an escalator; empty if the platform is too narrow to keep a walkway beside it. */
	public static List<Integer> stairColumns(TrackLayout.PlatformBand band) {
		final List<Integer> columns = new ArrayList<>();
		final int width = band.width();
		if (band.island()) {
			if (width < 5) {
				return columns;
			}
			final int steps = Math.min(2, width - 4);
			final int start = band.uMin() + (width - steps) / 2;
			for (int u = start; u < start + steps; u++) {
				columns.add(u);
			}
		} else if (band.trackLeft()) {
			final int steps = width >= 6 ? 2 : 1;
			for (int u = band.uMax() - steps; u <= band.uMax() - 1; u++) {
				columns.add(u);
			}
		} else {
			final int steps = width >= 6 ? 2 : 1;
			for (int u = band.uMin() + 1; u <= band.uMin() + steps; u++) {
				columns.add(u);
			}
		}
		return columns;
	}

	/** v of the building stair step at height y; the top step is nearest the entrance. */
	public int innerStairV(int y) {
		return entranceV + innerStairSide * (8 - y);
	}

	public LocalDir innerStairFront() {
		return innerStairSide > 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
	}

	/** Lowest and highest v of the building stair with both landings. */
	public int innerStairMin() {
		return Math.min(entranceV + 2 * innerStairSide, entranceV + 8 * innerStairSide);
	}

	public int innerStairMax() {
		return Math.max(entranceV + 2 * innerStairSide, entranceV + 8 * innerStairSide);
	}

	/**
	 * A lift shaft beside a stair: a 2×2 car at columns c0..c0+1 and rows doorV+out, doorV+2·out, landing doors in row doorV
	 * at the walk level and the upper walk level, the back wall in row doorV+3·out and side walls in columns c0-1 and c0+2.
	 *
	 * @param out +1 when the shaft lies towards higher v from its door row, -1 towards lower v
	 */
	public record Lift(int c0, int doorV, int out) {

		/** Lowest and highest v of the shaft, door row to back wall. */
		public int vMin() {
			return Math.min(doorV, doorV + 3 * out);
		}

		public int vMax() {
			return Math.max(doorV, doorV + 3 * out);
		}
	}

	/**
	 * The lift from a served platform up to the footbridge: on the stair columns, on the other side of the corridor from the
	 * stair, its doors in the corridor wall. Null when the platform has a one-column stair (too narrow for a car and a
	 * walkway beside it) or the shaft would run off the platform end.
	 */
	public Lift platformLift(TrackLayout.PlatformBand band, TrackLayout layout) {
		if (!bridge || !served.contains(band.number())) {
			return null;
		}
		final List<Integer> columns = stairs(band);
		if (columns.size() != 2) {
			return null;
		}
		final int c0 = columns.get(0);
		final boolean edgeLeft = band.trackLeft() && c0 - 1 == band.uMin();
		final boolean edgeRight = band.trackRight() && c0 + 2 == band.uMax();
		if (c0 - 1 < band.uMin() || c0 + 2 > band.uMax() || edgeLeft || edgeRight) {
			return null;
		}
		final int out = -stairSide;
		final Lift lift = new Lift(c0, bridgeV + 2 * out, out);
		return band.runs(lift.vMin()) && band.runs(lift.vMax()) ? lift : null;
	}

	/**
	 * The lift in the station building, from the paid hall up to the footbridge floor: on the inner stair's columns, on the
	 * other side of the entrance. Null without a footbridge or when the building is too short (two bays).
	 */
	public Lift buildingLift(TrackLayout layout) {
		if (!building || !bridge) {
			return null;
		}
		final int out = -innerStairSide;
		final Lift lift = new Lift(platformWall(layout) - 2, entranceV + 2 * out, out);
		return lift.vMin() >= vStart + 1 && lift.vMax() <= vEnd - 1 ? lift : null;
	}

	/** Centre v of each facade bay. */
	public int bayCentre(int bay) {
		return vStart + StationGeometry.BAY * bay + 3;
	}
}
