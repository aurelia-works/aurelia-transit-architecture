package com.aureliatransit.architecture.station;

import java.util.List;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * The deep underground stations of 1.7.0, planned from the research in {@code docs/research/} (Angel, Westminster, Canary
 * Wharf, Vijzelgracht). Both are dug as a {@link Carve}: the shapes of the void are added, and the walls, floors and roofs are
 * whatever touches the void, so tunnels, shafts and halls that meet need no joint of their own.
 * <ul>
 * <li>A <b>deep tube</b> ({@link #deepTube}): twin round bores, a track and its platform in each, the platforms facing each
 * other across a one-column pillar of lining with cross-passages through it; one long escalator shaft from the platforms' low
 * end up to a ticket hall on the surface (Angel is 27 m down, with the longest escalators on the Underground).</li>
 * <li>A <b>cut-and-cover box</b> ({@link #cutAndCover}): one deep rectangular box, a mezzanine at each end, long escalator
 * banks and a glass canopy over each street entrance (Canary Wharf, Vijzelgracht).</li>
 * </ul>
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanDeep extends PlanUnderground {

	/** Cells of void over an escalator step: the step, two to walk through and two clear. */
	static final int STEP_CLEAR = 4;
	/** Rows of ticket hall between a tube shaft's top landing and the hall's street wall. */
	static final int HALL_DEPTH = 14;
	/** Rows of paved forecourt in front of the hall, inside the footprint, where the entrance pylon stands. */
	static final int FORECOURT = 2;
	/** Rows of the hall that overlap the shaft's last steps: the shaft comes up inside the hall. */
	static final int HALL_OVER_SHAFT = 7;
	/** Cells of hall above its floor. */
	static final int HALL_HEIGHT = 5;
	/** Rows the platform rows are open across the pillar at the shaft end, so the two bores are one concourse there. */
	static final int JUNCTION = 4;
	/** Rows between cross-passages through the pillar. */
	static final int CROSS_PITCH = 14;
	static final String ESCALATOR_STEP = StylePalette.MTR_ESCALATOR_STEP;
	static final String ESCALATOR_SIDE = StylePalette.MTR_ESCALATOR_SIDE;

	PlanDeep(StationSpec spec) {
		super(spec);
	}

	/** Rows before a tube's platforms: the shaft ({@code depth + 1} rows of run and landings), the hall and its forecourt. */
	static int tubeAccessRows(int depth) {
		return depth - 1 + 4 + HALL_DEPTH + 1 + FORECOURT - StationGeometry.RAMP_LENGTH + 1;
	}

	/** Rise of a box's street escalator bank: from the mezzanine up to the street, a third of the depth between 4 and 12. */
	static int streetRise(int depth) {
		return Math.min(12, Math.max(4, (depth - 1) / 3));
	}

	/** Level of a box's mezzanine floor: the street's escalators land on it, the platforms' start from it. */
	static int mezzY(int depth) {
		return depth - 1 - streetRise(depth);
	}

	/**
	 * Rows past each platform end of a box (the end wall included): the street bank's run and landing, the gate line, and the
	 * platform bank's top landing, in that order from the wall.
	 */
	static int boxEndRows(int depth) {
		return streetRise(depth) + 8;
	}

	/** Shortest platform a box can have: both platform banks (their run and landings) and some platform between. */
	static int boxMinLength(int depth) {
		return 2 * (mezzY(depth) + 4) + 8;
	}

	@Override
	void deepStation() {
		if (spec.options().type() == UndergroundType.DEEP_TUBE) {
			deepTube();
		} else {
			cutAndCover();
		}
	}


	/** y of the escalator step {@code i} (0..H+2) of a flight that climbs {@code rise} from {@code base}: two flat, the slope, two flat. */
	static int stepY(int base, int rise, int i) {
		return base + Math.max(0, Math.min(rise, i - 1));
	}

	/**
	 * One escalator of two columns {@code a} and {@code a + 1}, climbing towards {@code up}, its first (lowest) step at
	 * ({@code vFirst}) and each next one a row further on; {@code direction} false for one that runs down. Only the steps and
	 * handrails: the void over it is the caller's.
	 */
	void escalatorRun(int a, int vFirst, int dv, int base, int rise, LocalDir up, boolean runsUp) {
		escalatorRun(a, vFirst, dv, base, rise, up, runsUp, false);
	}

	/** As above; {@code replace} takes the cell of a floor already planned there (the platform an escalator lands on). */
	void escalatorRun(int a, int vFirst, int dv, int base, int rise, LocalDir up, boolean runsUp, boolean replace) {
		// MTR's left half is on the left looking up the escalator; +U is to the right of +V
		final int left = up == LocalDir.PLUS_V ? a : a + 1;
		for (int i = 0; i <= rise + 2; i++) {
			final int v = vFirst + dv * i;
			final int y = stepY(base, rise, i);
			for (final int u : new int[]{a, a + 1}) {
				final String half = u == left ? "left" : "right";
				if (replace) {
					cells.remove(new Cell(u, y, v));
				}
				put(Role.ESCALATOR, u, y, v, up, ESCALATOR_STEP, "side=" + half + ",direction=" + runsUp + ",status=true");
				put(Role.ESCALATOR, u, y + 1, v, up, ESCALATOR_SIDE, "side=" + half);
			}
		}
	}

	// ---- deep tube ------------------------------------------------------------------------------------------------------

	/** The street's walking level, local y. */
	int streetY() {
		return spec.options().streetDepth();
	}

	/** Column of the pillar between the two platforms. */
	int pillarU() {
		return layout.platforms().get(0).uMax() + 1;
	}

	/** v of the last step of the tube's shaft: its top landing. */
	int shaftTopV() {
		return -2 - (streetY() - 1 + 2);
	}

	void deepTube() {
		final int w = layout.width();
		final int d = streetY();
		final int rise = d - 1;
		final int vMin = layout.vMin();
		final int vMax = layout.vMax();
		final int platformRows = layout.platformLength();
		final TrackLayout.PlatformBand a = layout.platforms().get(0);
		final TrackLayout.PlatformBand b = layout.platforms().get(1);
		final int uc = pillarU();
		final StylePalette.Building building = palette.building();
		final Carve carve = new Carve(new Box(0, -2, vMin, w - 1, d + HALL_HEIGHT + 1, vMax));

		// the track bores: the clearance envelope in a round tunnel, the whole way, with the bore past the platforms
		for (final TrackLayout.Track track : layout.tracks()) {
			final int t = track.u();
			final boolean leftBore = t < uc;
			carve.box(t - 1, StationGeometry.PLATFORM_Y, vMin, t + 1, StationGeometry.ENVELOPE_HEIGHT - 1, vMax);
			carve.tube(t, 2, 2.9, StationGeometry.PLATFORM_Y, vMin, vMax, u -> leftBore ? u <= t + 1 : u >= t - 1);
		}
		// the platform bores: wider round tunnels round a track and its platform, three clear cells over the platform's far edge
		final int tA = layout.tracks().get(0).u();
		final int tB = layout.tracks().get(1).u();
		bore(carve, tA - 2, a.uMax(), platformRows);
		bore(carve, b.uMin(), tB + 2, platformRows);
		// the pillar between them is open for a few rows at the shaft end (one concourse), and has a cross-passage every so often
		carve.box(uc, 1, 0, uc, 4, JUNCTION - 1);
		for (int v = JUNCTION + CROSS_PITCH / 2; v <= platformRows - 1 - CROSS_PITCH / 2; v += CROSS_PITCH) {
			carve.box(uc, 1, v - 1, uc, 3, v + 1);
		}
		// the shaft: three escalators side by side, climbing towards -v from the bores' low end to the surface
		final int cols = uc - 3;
		for (int i = 0; i <= rise + 2; i++) {
			final int v = -2 - i;
			final int y = stepY(0, rise, i);
			carve.box(cols, y, v, cols + 5, y + STEP_CLEAR, v);
		}
		carve.box(cols, 1, -1, cols + 5, STEP_CLEAR, -1);
		escalatorRun(cols, -2, -1, 0, rise, LocalDir.MINUS_V, true);
		escalatorRun(cols + 2, -2, -1, 0, rise, LocalDir.MINUS_V, true);
		escalatorRun(cols + 4, -2, -1, 0, rise, LocalDir.MINUS_V, false);
		// the ticket hall on the surface over the shaft's top
		final int hallFront = vMin + FORECOURT;
		final int hallFrom = hallFront + 1;
		final int hallTo = shaftTopV() + HALL_OVER_SHAFT;
		final int hallU0 = 3;
		final int hallU1 = w - 4;
		carve.box(hallU0, d, hallFrom, hallU1, d + HALL_HEIGHT - 1, hallTo);
		final int doorHalf = 2;
		carve.box(uc - doorHalf, d, hallFront, uc + doorHalf, d + 2, hallFront);

		if (spec.stepFree()) {
			// the lift beside the shaft's foot, up to a pavilion on the surface over the platforms' low end
			deepLift(carve, uc + 4, -1, -1, new int[]{StationGeometry.WALK_Y, d}, building.pier());
		}
		lineVoid(carve, d, hallFront, hallU0, hallU1, hallFrom, hallTo, building);
		extraHeadroom.addAll(carve.runs());
		hallFitOut(carve, d, hallFront, hallU0, hallU1, hallFrom, hallTo, uc, building);
	}

	/** A round platform bore: the circle that clears {@code uLo..uHi} at the walking surface and three over it. */
	void bore(Carve carve, int uLo, int uHi, int platformRows) {
		final double centre = (uLo + uHi) / 2.0;
		final double half = (uHi - uLo) / 2.0;
		final double radius = Math.sqrt(half * half + 1.5 * 1.5) + 0.3;
		carve.tube(centre, 2.5, radius, 1, 0, platformRows - 1, u -> u >= uLo && u <= uHi);
	}

	/**
	 * The walls, floors and roofs of the void: the bore's tiles, a hall's own materials above the surface, light panels hung in the
	 * crown over the platforms. Planned after the fixtures and escalators, so it only fills what they left.
	 */
	void lineVoid(Carve carve, int d, int hallFront, int hallU0, int hallU1, int hallFrom, int hallTo, StylePalette.Building building) {
		final String tile = undergroundTile();
		final String floor = palette.blockId(Role.PLATFORM_SURFACE);
		final int rows = layout.platformLength();
		for (final Carve.Lining l : carve.lining()) {
			final int u = l.u();
			final int y = l.y();
			final int v = l.v();
			if (y < -1) {
				continue;
			}
			final boolean surface = y >= d - 1;
			// the plug a door would leave outside the facade
			if (surface && v < hallFront) {
				continue;
			}
			if (surface) {
				if (l.floor()) {
					put(Role.FLOOR, u, y, v, LocalDir.PLUS_V, building.floor());
				} else if (l.ceiling()) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, building.roof());
				} else {
					put(Role.WALL, u, y, v, LocalDir.PLUS_V, building.wall());
				}
				continue;
			}
			final boolean station = v >= 0 && v < rows || u >= pillarU() - 4 && u <= pillarU() + 3;
			if (l.floor()) {
				put(Role.FLOOR, u, y, v, LocalDir.PLUS_V, floor);
			} else if (l.ceiling() && station && y >= 6 && Math.floorMod(u + v, LIGHT_EVERY) == 0) {
				put(Role.LIGHT_PANEL, u, y, v, LocalDir.PLUS_V, building.light());
			} else {
				put(Role.WALL, u, y, v, LocalDir.PLUS_V, station ? tile : TUNNEL_LINING);
			}
		}
	}

	/**
	 * A lift (MTR's own, formed when the build ends from its column of stops, as {@link PlanLifts#shaft} does) dug through the
	 * ground: a 2x2 car in the two rows past the door row, a track column in its back row fixed to the side wall, a pair of landing
	 * doors and a call button at every stop of {@code stops} (walking levels, ascending), walls round it from the lowest stop to a
	 * roof over the highest, and a pit under the car.
	 *
	 * @param c0   first column of the car (the second is {@code c0 + 1})
	 * @param vD   the door row; the landing is the row before it, the car the two rows after it in {@code out}'s direction
	 * @param out  +1 or -1: the way the shaft lies from its door row
	 */
	void deepLift(Carve carve, int c0, int vD, int out, int[] stops, String wallId) {
		final int c1 = c0 + 1;
		final LocalDir in = out > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
		final int vBack = vD + 3 * out;
		final int low = stops[0];
		final int high = stops[stops.length - 1];
		final int top = high + 3;
		final int cT = in == LocalDir.PLUS_V ? c0 : c1;
		// anything of the platform's furniture standing in the shaft or on its landing comes out
		for (int c = c0 - 1; c <= c1 + 1; c++) {
			for (int y = low; y <= top + 1; y++) {
				for (int k = -2; k <= 3; k++) {
					final PlannedBlock block = at(c, y, vD + k * out);
					// the landing is cleared up to a lamp's head (WALK_Y + 3 = low + 3): a lamp standing on the landing's pole goes with it
					final boolean landing = k < 0 && c >= c0 && c <= c1 && y <= low + 3;
					if (block != null && !block.role().flatFloor() && (landing || k >= 0)) {
						cells.remove(new Cell(c, y, vD + k * out));
					}
				}
			}
		}
		// the void: the car with its pit (the door row is planned blocks, doors and wall)
		carve.box(c0, low - 1, Math.min(vD + out, vD + 2 * out), c1, top, Math.max(vD + out, vD + 2 * out));
		// the track fixed to the inside of one side wall, in the car's back row
		final boolean left = out > 0;
		final int cTrack = left ? c0 : c1;
		final LocalDir toWall = left ? LocalDir.MINUS_U : LocalDir.PLUS_U;
		for (int y = low; y <= high + 1; y++) {
			boolean stop = false;
			for (final int s : stops) {
				stop |= s == y;
			}
			put(Role.LIFT, cTrack, y, vD + 2 * out, toWall, stop ? StylePalette.MTR_LIFT_FLOOR : StylePalette.MTR_LIFT_TRACK);
		}
		for (final int stop : stops) {
			for (int c = c0; c <= c1; c++) {
				final String side = c == cT ? "side=left" : "side=right";
				put(Role.LIFT, c, stop, vD, in, StylePalette.MTR_LIFT_DOOR, "half=lower," + side + ",unlocked=true");
				put(Role.LIFT, c, stop + 1, vD, in, StylePalette.MTR_LIFT_DOOR, "half=upper," + side + ",unlocked=true");
			}
		}
		// the walls: the door row between the stops, the sides, the back, and the roof
		for (int y = low; y <= top; y++) {
			for (int c = c0 - 1; c <= c1 + 1; c++) {
				put(Role.WALL, c, y, vD, in.opposite(), wallId);
				put(Role.WALL, c, y, vBack, in, wallId);
			}
			for (int v = vD + out; v != vBack; v += out) {
				put(Role.WALL, c0 - 1, y, v, LocalDir.MINUS_U, wallId);
				put(Role.WALL, c1 + 1, y, v, LocalDir.PLUS_U, wallId);
			}
		}
		for (int c = c0 - 1; c <= c1 + 1; c++) {
			for (int v = vD; v != vBack + out; v += out) {
				put(Role.BUILDING_ROOF, c, top + 1, v, LocalDir.PLUS_V, palette.building().roof());
			}
		}
		// a call button on the wall post beside each pair of doors, facing the landing
		for (final int stop : stops) {
			cells.put(new Cell(c0 - 1, stop + 1, vD), new PlannedBlock(c0 - 1, stop + 1, vD, Role.LIFT, StylePalette.MTR_LIFT_BUTTONS, in.opposite(), "", "unlocked=true"));
		}
		for (int c = c0; c <= c1; c++) {
			for (int v = vD + out; v != vBack; v += out) {
				for (int y = low; y <= top; y++) {
					air.add(new Cell(c, y, v));
				}
			}
		}
	}

	/**
	 * A rail on the street's floor round every hole the dig makes in it (an escalator shaft coming up, a lift): wherever a street
	 * cell has a dug cell beside it at its own level that nothing planned fills. The open end of a canopy or a door is not a hole.
	 */
	void streetRails(Carve carve, int d) {
		for (int u = 0; u < layout.width(); u++) {
			for (int v = layout.vMin(); v <= layout.vMax(); v++) {
				final PlannedBlock floor = at(u, d - 1, v);
				if (floor == null || floor.role() != Role.FLOOR && floor.role() != Role.BUILDING_ROOF || taken(u, d, v)) {
					continue;
				}
				for (final int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					if (carve.has(u + side[0], d - 1, v + side[1]) && at(u + side[0], d - 1, v + side[1]) == null) {
						put(Role.BARRIER, u, d, v, side[0] == 0 ? LocalDir.PLUS_V : LocalDir.PLUS_U);
						break;
					}
				}
			}
		}
	}

	/** The ticket hall's fit-out: a rail round the shaft's opening, the gate line, ticket machines, the name and the pylon. */
	void hallFitOut(Carve carve, int d, int hallFront, int hallU0, int hallU1, int hallFrom, int hallTo, int uc, StylePalette.Building building) {
		streetRails(carve, d);
		// the gate line across the hall between the shaft's head and the street door
		final int gateV = shaftTopV() - 3;
		final int half = 3;
		for (int u = hallU0; u <= hallU1; u++) {
			final int dx = u - uc;
			if (Math.abs(dx) <= half) {
				final String kind = Math.abs(dx) == half ? "kind=end" : dx == 0 ? "kind=wide" : "kind=gate";
				gate(u, d, gateV, LocalDir.MINUS_V, kind, dx > 0, LocalDir.MINUS_V);
			} else {
				fence(u, d, gateV, LocalDir.MINUS_V);
			}
		}
		// the ticket machines on the unpaid side, against the side wall
		for (int i = 0; i < 3; i++) {
			ticketMachine(hallU0, d, gateV - 3 - 2 * i, LocalDir.PLUS_U);
			ticketMachine(hallU1, d, gateV - 3 - 2 * i, LocalDir.MINUS_U);
		}
		// the facade: the station's name over the door, and the pylon on the forecourt
		final String name = spec.name().isEmpty() ? "Underground" : spec.name();
		for (int u = uc - 2; u <= uc + 2; u++) {
			putText(Role.FIXTURE, u, d + 3, hallFront - 1, LocalDir.MINUS_V, PlanFitOut.LETTERING, name);
		}
		putText(Role.FIXTURE, uc + 4, d, hallFront - 1, LocalDir.MINUS_V, PYLON, "NONE:" + name);
		put(Role.FIXTURE, uc + 4, d + 1, hallFront - 1, LocalDir.MINUS_V, PYLON, "half=upper");
		// the forecourt, paved
		for (int u = 1; u < layout.width() - 1; u++) {
			for (int v = layout.vMin(); v < hallFront; v++) {
				put(Role.FLOOR, u, d - 1, v, LocalDir.PLUS_V, building.plinth());
			}
		}
	}

	// ---- cut-and-cover box ----------------------------------------------------------------------------------------------

	/** Columns of one escalator bank: three escalators of two columns. */
	static final int BANK_WIDTH = 6;

	/** v of the cell {@code k} rows in from a box's end wall, at the low-v end ({@code low}) or the high-v end. */
	int endV(boolean low, int k) {
		return low ? layout.vMin() + k : layout.vMax() - k;
	}

	void cutAndCover() {
		final int w = layout.width();
		final int d = streetY();
		final int ym = mezzY(d);
		final int hs = streetRise(d);
		final int vMin = layout.vMin();
		final int vMax = layout.vMax();
		final StylePalette.Building building = palette.building();
		final Carve carve = new Carve(new Box(0, -2, vMin, w - 1, d + 6, vMax));
		// the box: one open volume from the platforms to the roof, with a mouth in each end wall over every track
		carve.box(1, 1, vMin + 1, w - 2, d - 2, vMax - 1);
		for (final TrackLayout.Track track : layout.tracks()) {
			carve.box(track.u() - 1, StationGeometry.PLATFORM_Y, vMin, track.u() + 1, StationGeometry.ENVELOPE_HEIGHT - 1, vMax);
		}
		// the floor between the tracks is the walkable level's open ground, not a floor to stand on
		carve.box(layout.tracks().get(0).u() - 1, StationGeometry.PLATFORM_Y, vMin, layout.tracks().get(layout.tracks().size() - 1).u() + 1,
				StationGeometry.PLATFORM_Y, vMax);
		final int streetA = (w - BANK_WIDTH) / 2;
		for (final boolean low : new boolean[]{true, false}) {
			final int toward = low ? -1 : 1;
			final LocalDir up = low ? LocalDir.MINUS_V : LocalDir.PLUS_V;
			// the mezzanine: a floor across the box from the end wall past the gate line to the platform banks' top landings
			final int slabFrom = endV(low, 1);
			final int slabTo = endV(low, hs + 10);
			carve.removeBox(1, ym, Math.min(slabFrom, slabTo), w - 2, ym, Math.max(slabFrom, slabTo));
			// the street bank: from the street at the wall, down towards the box, to the mezzanine
			bank(carve, streetA, endV(low, hs + 4), toward, ym, hs, up, false);
			// the glass canopy over its top: open at the footprint's edge, where people come in
			canopy(carve, streetA, endV(low, 0), endV(low, 9), d);
			// the platform banks: from the mezzanine's top landings down to each platform
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int bw = Math.min(BANK_WIDTH, (band.width() - 2) / 2 * 2);
				if (bw < 2) {
					continue;
				}
				final int a = band.uMin() + (band.width() - bw) / 2;
				bank(carve, a, endV(low, hs + 9 + ym + 2), toward, 0, ym, up, true, bw);
			}
		}
		// a lift up each platform's middle, from its floor through the open box and the roof to a pavilion on the street
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int c0 = band.uMin() + (band.width() - 2) / 2;
			deepLift(carve, c0, (band.vFrom() + band.vTo()) / 2, 1, new int[]{StationGeometry.WALK_Y, d}, building.pier());
		}
		final String floor = palette.blockId(Role.PLATFORM_SURFACE);
		for (final Carve.Lining l : carve.lining()) {
			if (l.y() < -1) {
				continue;
			}
			final int u = l.u();
			final int y = l.y();
			final int v = l.v();
			if (y >= d) {
				put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, ARCH_GLASS);
			} else if (y == d - 1) {
				put(l.floor() ? Role.FLOOR : Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, building.deck());
			} else if (l.floor()) {
				put(Role.FLOOR, u, y, v, LocalDir.PLUS_V, y == ym ? building.floor() : floor);
			} else if (mouthRing(u, y, v)) {
				put(Role.WALL, u, y, v, LocalDir.PLUS_V, TUNNEL_LINING);
			} else {
				put(Role.WALL, u, y, v, LocalDir.PLUS_V, undergroundTile());
			}
		}
		extraHeadroom.addAll(carve.runs());
		streetRails(carve, d);
		boxFitOut(carve, d, ym, hs);
	}

	/** A wall cell round a track's mouth in an end wall. */
	boolean mouthRing(int u, int y, int v) {
		if ((v != layout.vMin() && v != layout.vMax()) || y > StationGeometry.ENVELOPE_HEIGHT) {
			return false;
		}
		return layout.tracks().stream().anyMatch(t -> Math.abs(u - t.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH + 1);
	}

	/** An escalator bank of three escalators (two up, one down) on its own wedge of piers; see {@link #bank(Carve, int, int, int, int, int, LocalDir, boolean, int)}. */
	void bank(Carve carve, int a, int vFirst, int dv, int base, int rise, LocalDir up, boolean replace) {
		bank(carve, a, vFirst, dv, base, rise, up, replace, BANK_WIDTH);
	}

	/**
	 * A bank of {@code width / 2} escalators climbing from {@code base} by {@code rise}, the lowest step at {@code vFirst} and
	 * each next one a row on in {@code dv}: the void over it ({@link #STEP_CLEAR} over every step), the escalators (the last one
	 * runs down when there are three) and a wedge of piers under the steps, from {@code base} up, that holds them.
	 */
	void bank(Carve carve, int a, int vFirst, int dv, int base, int rise, LocalDir up, boolean replace, int width) {
		for (int i = 0; i <= rise + 2; i++) {
			final int v = vFirst + dv * i;
			final int y = stepY(base, rise, i);
			carve.box(a, y, v, a + width - 1, y + STEP_CLEAR, v);
			for (int u = a - 1; u <= a + width; u++) {
				// the platform's furniture standing where the bank comes down is cleared
				for (int clear = base + 1; clear <= y + STEP_CLEAR + 1; clear++) {
					final PlannedBlock block = at(u, clear, v);
					if (block != null && !block.role().flatFloor()) {
						cells.remove(new Cell(u, clear, v));
					}
				}
			}
			for (int u = a; u < a + width; u++) {
				for (int under = base + 1; under < y; under++) {
					put(Role.BRIDGE_PIER, u, under, v, up, palette.building().pier());
				}
			}
			// a glass balustrade down each side, so nobody steps off a bank into the open box
			for (final int u : new int[]{a - 1, a + width}) {
				put(Role.BARRIER, u, y, v, LocalDir.PLUS_U);
				put(Role.BARRIER, u, y + 1, v, LocalDir.PLUS_U);
			}
		}
		for (int e = 0; e < width / 2; e++) {
			escalatorRun(a + 2 * e, vFirst, dv, base, rise, up, !(width >= BANK_WIDTH && e == width / 2 - 1), replace);
		}
	}

	/** A glass vault over a street bank's top: {@code a..a+5} wide, from {@code vFrom} to {@code vTo}, five high over the street. */
	void canopy(Carve carve, int a, int vFrom, int vTo, int d) {
		final double centre = a + (BANK_WIDTH - 1) / 2.0;
		for (int v = Math.min(vFrom, vTo); v <= Math.max(vFrom, vTo); v++) {
			for (int u = a; u < a + BANK_WIDTH; u++) {
				final double x = (u - centre) / 3.6;
				for (int y = d; y <= d + 5; y++) {
					final double z = (y - d) / 5.0;
					if (x * x + z * z <= 1.0) {
						carve.add(u, y, v);
					}
				}
			}
		}
	}

	/** The box's fit-out: rails, the gate lines, ticket machines, lights, columns, the name and a pylon at each street end. */
	void boxFitOut(Carve carve, int d, int ym, int hs) {
		final int w = layout.width();
		final StylePalette.Building building = palette.building();
		for (final boolean low : new boolean[]{true, false}) {
			final LocalDir inward = low ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			// a rail along the mezzanine's inner edge, open in front of each platform bank
			final int edge = endV(low, hs + 10);
			for (int u = 1; u < w - 1; u++) {
				final PlannedBlock top = at(u, ym, edge);
				if (top != null && top.role() == Role.FLOOR && !taken(u, ym + 1, edge)) {
					put(Role.BARRIER, u, ym + 1, edge, inward.opposite());
				}
			}
			// the gate line across the mezzanine, between the street bank's foot and the platform banks
			final int gateV = endV(low, hs + 6);
			final int uc = w / 2;
			final int half = 3;
			for (int u = 1; u < w - 1; u++) {
				final int dx = u - uc;
				if (Math.abs(dx) <= half) {
					final String kind = Math.abs(dx) == half ? "kind=end" : dx == 0 ? "kind=wide" : "kind=gate";
					gate(u, ym + 1, gateV, inward, kind, dx > 0, inward);
				} else {
					fence(u, ym + 1, gateV, inward);
				}
			}
			// ticket machines on the unpaid side, against the side walls
			for (int i = 0; i < 3; i++) {
				final int v = endV(low, hs + 3 + i * 2);
				ticketMachine(1, ym + 1, v, LocalDir.PLUS_U);
				ticketMachine(w - 2, ym + 1, v, LocalDir.MINUS_U);
			}
			// the name over the canopy's open end and a pylon beside it
			final String name = spec.name().isEmpty() ? "Underground" : spec.name();
			final int a = (w - BANK_WIDTH) / 2;
			final int v0 = endV(low, 1);
			for (int u = a; u < a + BANK_WIDTH; u++) {
				putText(Role.FIXTURE, u, d + 5, v0, inward.opposite(), PlanFitOut.LETTERING, name);
			}
			putText(Role.FIXTURE, a - 2, d, v0, inward.opposite(), PYLON, "NONE:" + name);
			put(Role.FIXTURE, a - 2, d + 1, v0, inward.opposite(), PYLON, "half=upper");
		}
		// a glass fence along every track's edge where the platform's floor runs on into the end zones
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				final int u = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + 1);
				for (int v = layout.vMin() + 1; v < layout.vMax(); v++) {
					if (v >= 0 && v < layout.platformLength()) {
						continue;
					}
					final PlannedBlock floor = at(u, 0, v);
					if (floor != null && floor.role() == Role.FLOOR) {
						fence(u, 1, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U);
					}
				}
			}
		}
		// lights hung under the roof and the mezzanines, columns up the platforms
		for (int u = 2; u < w - 2; u += 4) {
			for (int v = layout.vMin() + 2; v < layout.vMax() - 1; v += 4) {
				for (final int y : new int[]{d - 2, ym - 1}) {
					if (!taken(u, y, v) && carve.has(u, y, v) && at(u, y + 1, v) != null && at(u, y + 1, v).role() != Role.STAIR) {
						put(Role.LIGHT_PANEL, u, y, v, LocalDir.PLUS_V, building.light());
					}
				}
			}
		}
		final int bankRows = mezzY(d) + 4;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (band.width() < StationGeometry.BAY + 1) {
				continue;
			}
			final int u = (band.uMin() + band.uMax()) / 2;
			for (int v = bankRows + 1; v <= layout.platformLength() - 1 - bankRows; v += StationGeometry.COLUMN_SPACING) {
				if (!freeColumn(u, 1, d - 2, v)) {
					continue;
				}
				for (int y = 1; y <= d - 2; y++) {
					put(y == d - 2 ? Role.COLUMN_CAP : Role.COLUMN, u, y, v, LocalDir.PLUS_V);
				}
			}
		}
	}
}
