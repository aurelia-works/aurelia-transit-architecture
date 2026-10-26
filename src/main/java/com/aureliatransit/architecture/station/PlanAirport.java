package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * An airport (words: "airport", "2 runways", "6 gates"): runways with markings and lights, a parallel taxiway with links to the
 * runway, an apron with aircraft stands, and a terminal behind the apron with a boarding bridge to every stand, on the same
 * pipeline as a station. The geometry is {@link AirportGeometry}; this layer paints it. Nothing here runs for a railway station.
 * <p>
 * Pavement is {@link Role#PLATFORM_SURFACE} (a flat walkable floor), lights are {@link Role#LIGHT_PANEL}, the terminal and bridges
 * use the building roles; the block ids are chosen here, not by the style palette. Everything on the airfield is flush with the
 * ground (y = 0); only the terminal, the bridges and the floodlight masts stand up.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanAirport extends PlanFitOut {

	static final String RUNWAY_SURFACE = "minecraft:black_concrete";
	static final String BLAST_SURFACE = "minecraft:polished_andesite";
	static final String TAXI_SURFACE = "minecraft:gray_concrete";
	static final String APRON_SURFACE = "minecraft:light_gray_concrete";
	static final String ROAD_SURFACE = "minecraft:gray_concrete";
	static final String PAINT_WHITE = "minecraft:white_concrete";
	static final String PAINT_YELLOW = "minecraft:yellow_concrete";
	/** Runway edge lights: white, flush in the pavement. */
	static final String EDGE_LIGHT = "minecraft:sea_lantern";
	/** Runway threshold lights: green. */
	static final String THRESHOLD_LIGHT = "minecraft:verdant_froglight";
	/** Taxiway edge lights: blue. */
	static final String TAXI_LIGHT = "minecraft:blue_ice";
	/** The control tower: a white shaft, a light grey cab floor and roof, and glass walls round the cab. */
	static final String TOWER_SHAFT = "minecraft:white_concrete";
	static final String TOWER_FLOOR = "minecraft:light_gray_concrete";
	static final String TOWER_GLASS = "minecraft:glass";
	static final String MAST_LAMP = "aurelia_transit_architecture:platform_lamp";
	/** The airport's geometry; null for a railway station. */
	final AirportGeometry air;

	PlanAirport(StationSpec spec) {
		super(spec);
		this.air = spec.isAirport() ? AirportGeometry.of(spec.airport()) : null;
	}

	/** The terminal's roof, one above its walls. */
	int terminalRoofY() {
		return air.dims().wallTop() + 1;
	}

	/** The airport's rules, in order. Structures first: a cell keeps the first block planned in it, so paint comes before pavement. */
	void airport() {
		if (air == null) {
			return;
		}
		if (spec.airport().terminal()) {
			terminal();
			boardingBridges();
		}
		controlTower();
		masts();
		standMarks();
		for (int i = 0; i < spec.airport().runways(); i++) {
			runwayMarks(i);
		}
		taxiMarks();
		pavement();
	}

	// ---- cells --------------------------------------------------------------------------------------------------------

	private void flat(Role role, int u, int v, String id) {
		put(role, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, id);
	}

	private void paint(int u, int v, String id) {
		flat(Role.PLATFORM_SURFACE, u, v, id);
	}

	private void light(int u, int v, String id) {
		flat(Role.LIGHT_PANEL, u, v, id);
	}

	private void rect(int u0, int u1, int v0, int v1, String id) {
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				paint(u, v, id);
			}
		}
	}

	// ---- runways ------------------------------------------------------------------------------------------------------

	/**
	 * One runway's paint and lights: edge lines and flush edge lights, green threshold lights, threshold stripes, the numbers, and a
	 * dashed centreline. The runway lies along v, so its low end is the "09" end and its high end "27"; with two, the first is
	 * "09L / 27R" and the second "09R / 27L" (left and right as a pilot looking along +v sees them).
	 */
	void runwayMarks(int i) {
		final AirportGeometry.Dims d = air.dims();
		final int u0 = air.runwayU0().get(i);
		final int u1 = air.runwayU1(i);
		final int c = air.runwayCentre(i);
		final int v0 = air.runwayV0();
		final int v1 = air.runwayV1();
		for (int v = v0; v <= v1; v++) {
			if (v % d.edgeLightEvery() == 0) {
				light(u0, v, EDGE_LIGHT);
				light(u1, v, EDGE_LIGHT);
			}
		}
		for (int u = u0; u <= u1; u += 2) {
			light(u, v0, THRESHOLD_LIGHT);
			light(u, v1, THRESHOLD_LIGHT);
		}
		for (int v = v0 + 2; v <= v1 - 2; v++) {
			paint(u0 + 1, v, PAINT_WHITE);
			paint(u1 - 1, v, PAINT_WHITE);
		}
		for (int u = u0 + 3; u <= u1 - 3; u++) {
			// piano keys either side of the centre, leaving the centreline's own column bare
			if ((u - u0) % 2 == 1 && Math.abs(u - c) >= 1) {
				for (int k = 0; k < d.thresholdLength(); k++) {
					paint(u, v0 + 2 + k, PAINT_WHITE);
					paint(u, v1 - 2 - k, PAINT_WHITE);
				}
			}
		}
		final int scale = numberScale();
		final boolean pair = spec.airport().runways() > 1;
		final String low = pair ? (i == 0 ? "L" : "R") : "";
		final String high = pair ? (i == 0 ? "R" : "L") : "";
		paintText(number("09", low), scale, c, v0 + d.numberOffset(), 0, 1, PAINT_WHITE);
		paintText(number("27", high), scale, c, v1 - d.numberOffset(), 0, -1, PAINT_WHITE);
		touchdownMarks(i);
		final int clear = d.numberOffset() + 3 * AirportGlyphs.height(scale) + 6;
		final int start = v0 + clear;
		final int end = v1 - clear;
		for (int v = start; v <= end; v++) {
			if ((v - start) % (d.dash() + d.dashGap()) < d.dash()) {
				paint(c, v, PAINT_WHITE);
			}
		}
	}

	/** The scale of a runway's painted numbers: as large as the runway is wide enough for. */
	int numberScale() {
		final int w = spec.airport().runwayWidth();
		return w >= 40 ? 3 : w >= 20 ? 2 : 1;
	}

	/**
	 * The aiming-point bars and touchdown-zone bars of a real-size runway, from each end: pairs of bars either side of the
	 * centreline at 225, 450 (the aiming point, broader and longer), 675, 900 and 1125 blocks from the threshold (150 to 750 m at
	 * 1.5 blocks a metre) as far as half the runway allows.
	 */
	private void touchdownMarks(int i) {
		if (air.dims() != AirportGeometry.REAL) {
			return;
		}
		final int c = air.runwayCentre(i);
		final int half = spec.airport().runwayLength() / 2;
		for (final int from : new int[]{225, 450, 675, 900, 1125}) {
			final boolean aim = from == 450;
			final int length = aim ? 45 : 22;
			final int width = aim ? 4 : 2;
			final int out = aim ? 12 : 8;
			if (from + length > half - 20 || c - (out + width - 1) < air.runwayU0().get(i) + 3 || c + out + width - 1 > air.runwayU1(i) - 3) {
				continue;
			}
			for (int k = 0; k < length; k++) {
				for (int w = 0; w < width; w++) {
					for (final int side : new int[]{-1, 1}) {
						final int u = c + side * out + (side < 0 ? -w : w);
						paint(u, air.runwayV0() + from + k, PAINT_WHITE);
						paint(u, air.runwayV1() - from - k, PAINT_WHITE);
					}
				}
			}
		}
	}

	private static List<String> number(String number, String letter) {
		return letter.isEmpty() ? List.of(number) : List.of(number, letter);
	}

	/**
	 * Paints lines of text on the pavement, upright for someone looking along ({@code upU}, {@code upV}): the first line farthest
	 * from the viewer, centred on column {@code ou} and row {@code ov}, which is the text's near edge. A viewer looking along +v has
	 * +u on the right; the right hand is a quarter turn clockwise from the viewing direction.
	 */
	private void paintText(List<String> lines, int scale, int ou, int ov, int upU, int upV, String id) {
		final int rightU = upV != 0 ? upV : 0;
		final int rightV = upU != 0 ? -upU : 0;
		final int h = AirportGlyphs.height(scale);
		final int gap = 2 * scale;
		final int total = lines.size() * h + (lines.size() - 1) * gap;
		int row0 = 0;
		for (final String line : lines) {
			final int w = AirportGlyphs.width(line, scale);
			for (final int[] px : AirportGlyphs.pixels(line, scale)) {
				final int d = total - 1 - (row0 + px[1]);
				final int x = px[0] - w / 2;
				paint(ou + d * upU + x * rightU, ov + d * upV + x * rightV, id);
			}
			row0 += h + gap;
		}
	}

	// ---- taxiways -----------------------------------------------------------------------------------------------------

	/** Yellow centrelines, blue edge lights and a hold line before every runway. */
	void taxiMarks() {
		final int centre = air.taxiCentre();
		for (int v = air.taxiV0(); v <= air.taxiV1(); v++) {
			paint(centre, v, PAINT_YELLOW);
			if (v % air.dims().taxiLightEvery() == 0) {
				light(air.taxiU0(), v, TAXI_LIGHT);
				light(air.taxiU1(), v, TAXI_LIGHT);
			}
		}
		final int gapFrom = air.taxiU1() + 1;
		final int gapTo = air.runwayU0().get(0) - 1;
		for (final int[] link : air.links()) {
			final int middle = (link[0] + link[1]) / 2;
			for (int u = gapFrom; u <= gapTo; u++) {
				paint(u, middle, PAINT_YELLOW);
				if (u % air.dims().taxiLightEvery() == 0) {
					light(u, link[0], TAXI_LIGHT);
					light(u, link[1], TAXI_LIGHT);
				}
			}
			// the hold-short line, across the link just before the runway
			for (int v = link[0]; v <= link[1]; v++) {
				paint(gapTo - 1, v, PAINT_YELLOW);
			}
		}
		// rapid exits: the same centreline, edge lights and hold-short bar as the links, along each exit's diagonal strip
		final int half = air.exitHalf();
		for (final int start : air.rapidExits()) {
			for (int u = gapFrom; u <= gapTo; u++) {
				final int c = air.exitCentre(start, u);
				paint(u, c, PAINT_YELLOW);
				if (u % air.dims().taxiLightEvery() == 0) {
					light(u, c - half, TAXI_LIGHT);
					light(u, c + half, TAXI_LIGHT);
				}
			}
			final int bar = air.exitCentre(start, gapTo - 1);
			for (int v = bar - half; v <= bar + half; v++) {
				paint(gapTo - 1, v, PAINT_YELLOW);
			}
		}
		if (spec.airport().runways() > 1) {
			final int far = air.runwayCentre(1);
			final int[] around = air.endAround();
			final int[] stub = air.stub();
			for (final boolean high : new boolean[]{false, true}) {
				final int a = high ? air.mirror(around[1]) : around[0];
				final int b = high ? air.mirror(around[0]) : around[1];
				final int s0 = high ? air.mirror(stub[1]) : stub[0];
				final int s1 = high ? air.mirror(stub[0]) : stub[1];
				for (int u = air.taxiU0(); u <= far; u++) {
					paint(u, (a + b) / 2, PAINT_YELLOW);
				}
				for (int v = s0; v <= s1; v++) {
					paint(far, v, PAINT_YELLOW);
				}
				// hold line before the second runway's blast pad
				for (int u = far - air.dims().taxi() / 2; u <= far + air.dims().taxi() / 2; u++) {
					paint(u, high ? s0 : s1, PAINT_YELLOW);
				}
			}
		}
	}

	// ---- apron and stands ---------------------------------------------------------------------------------------------

	/** Where an aircraft's nose stops on stand: past the boarding bridge, or well in from the apron's edge without a terminal. */
	int stopU() {
		return spec.airport().terminal() ? air.terminalU1() + air.dims().bridge() + 3 : air.apronU0() + 10;
	}

	/** A yellow lead-in line to the stop bar on every stand, the bar, the stand's number, and white lines between stands. */
	void standMarks() {
		final int stop = stopU();
		final int first = air.standV().get(0) - air.dims().standPitch() / 2;
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = first + i * air.dims().standPitch();
			for (int u = stop + 1; u <= air.apronU1(); u++) {
				paint(u, v, PAINT_WHITE);
			}
		}
		for (int i = 0; i < air.standV().size(); i++) {
			final int c = air.standV().get(i);
			for (int u = stop; u <= air.taxiCentre(); u++) {
				paint(u, c, PAINT_YELLOW);
			}
			final int bar = air.dims().bridgeWidth() / 2 + 1;
			for (int v = c - bar; v <= c + bar; v++) {
				paint(stop, v, PAINT_WHITE);
			}
			// the number, upright for a pilot taxiing in towards the terminal
			final int scale = numberScale() > 1 ? 2 : 1;
			paintText(List.of(Integer.toString(i + 1)), scale, stop + 4 + AirportGlyphs.height(scale), c + air.dims().standPitch() / 4, -1, 0, PAINT_WHITE);
		}
	}

	/**
	 * The control tower: a 3 by 3 shaft up to a glazed 5 by 5 cab with a roof, on the apron's far margin by the taxiway. Planned before the
	 * masts and the pavement, and its footprint is apron only, so the runway strip stays clear.
	 */
	void controlTower() {
		final AirportGeometry.Area t = air.tower();
		final int cu = (t.u0() + t.u1()) / 2;
		final int cv = (t.v0() + t.v1()) / 2;
		final int top = 2 * air.dims().mast();
		for (int y = StationGeometry.WALK_Y; y <= top - 5; y++) {
			for (int u = cu - 1; u <= cu + 1; u++) {
				for (int v = cv - 1; v <= cv + 1; v++) {
					put(Role.WALL, u, y, v, LocalDir.PLUS_V, TOWER_SHAFT);
				}
			}
		}
		for (int u = t.u0(); u <= t.u1(); u++) {
			for (int v = t.v0(); v <= t.v1(); v++) {
				put(Role.BUILDING_ROOF, u, top - 4, v, LocalDir.PLUS_V, TOWER_FLOOR);
				put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, TOWER_FLOOR);
				if (u == t.u0() || u == t.u1() || v == t.v0() || v == t.v1()) {
					for (int y = top - 3; y <= top - 1; y++) {
						put(Role.WINDOW, u, y, v, LocalDir.PLUS_V, TOWER_GLASS);
					}
				}
			}
		}
	}

	/** Floodlight masts on the apron between the stands. */
	void masts() {
		final int u = air.apronU1() - 6;
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = air.standV().get(0) - air.dims().standPitch() / 2 + i * air.dims().standPitch();
			for (int y = StationGeometry.WALK_Y; y < air.dims().mast(); y++) {
				put(Role.FIXTURE, u, y, v, LocalDir.MINUS_U, LAMP_POLE);
			}
			put(Role.LAMP, u, air.dims().mast(), v, LocalDir.MINUS_U, MAST_LAMP);
		}
	}

	// ---- pavement -----------------------------------------------------------------------------------------------------

	/** The pavement under all of it: what the paint above left bare. */
	void pavement() {
		for (final AirportGeometry.Area area : air.areas()) {
			final String id = switch (area.kind()) {
				case RUNWAY -> RUNWAY_SURFACE;
				case BLAST_PAD -> BLAST_SURFACE;
				case SHOULDER -> APRON_SURFACE;
				case TAXIWAY -> TAXI_SURFACE;
				case APRON -> APRON_SURFACE;
				case ROAD -> ROAD_SURFACE;
				case TERMINAL -> null;
			};
			if (id != null) {
				rect(area.u0(), area.u1(), area.v0(), area.v1(), id);
			}
		}
	}

	// ---- terminal -----------------------------------------------------------------------------------------------------

	/** The first cell of the entrance: the landside wall's door, five wide, in the middle of the terminal. */
	int entranceV() {
		return (air.terminalV0() + air.terminalV1()) / 2;
	}

	/**
	 * The terminal: a floor, glass along the airside with pilasters, a landside wall with a five-wide entrance and the name over it,
	 * end walls, columns, a roof with lights, check-in counters on the landside, benches in front of every gate, and a doorway onto
	 * every boarding bridge. Built from the modern building set.
	 */
	void terminal() {
		final StylePalette.Building b = palette.building();
		final AirportGeometry.Dims d = air.dims();
		final int u0 = air.terminalU0();
		final int u1 = air.terminalU1();
		final int v0 = air.terminalV0();
		final int v1 = air.terminalV1();
		final int door = entranceV();
		final int half = d.entrance() / 2;
		final int top = d.wallTop();
		// openings first: the entrance and the gate doors stay air
		for (int v = door - half; v <= door + half; v++) {
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
				keepClear(u0, y, v);
			}
		}
		for (final int c : air.standV()) {
			for (int v = c - d.bridgeWidth() / 2; v <= c + d.bridgeWidth() / 2; v++) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
					keepClear(u1, y, v);
				}
			}
		}
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, b.floor());
				if (u > u0 && u < u1 && v > v0 && v < v1) {
					final boolean lit = Math.floorMod(u + v, 4) == 0;
					put(lit ? Role.LIGHT_PANEL : Role.BUILDING_ROOF, u, terminalRoofY(), v, LocalDir.PLUS_V, lit ? b.light() : b.roof());
				} else {
					put(Role.BUILDING_ROOF, u, terminalRoofY(), v, LocalDir.PLUS_V, b.roof());
				}
			}
		}
		for (int y = StationGeometry.WALK_Y; y <= top; y++) {
			for (int v = v0; v <= v1; v++) {
				// airside: glass between pilasters every seven rows
				final boolean pilaster = Math.floorMod(v - v0, 7) == 0 || v == v1;
				put(pilaster ? Role.WALL : Role.WINDOW, u1, y, v, LocalDir.PLUS_U, pilaster ? b.wall() : b.window());
				// landside: a plinth, a band of glass, a fascia
				final boolean glass = y >= 2 && y <= top - 2 && Math.abs(v - door) > half;
				final boolean lintel = y == StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT && Math.abs(v - door) <= half;
				put(glass ? Role.WINDOW : Role.WALL, u0, y, v, LocalDir.MINUS_U, glass ? b.window() : lintel ? b.trim() : b.wall());
			}
			for (int u = u0 + 1; u < u1; u++) {
				put(Role.WALL, u, y, v0, LocalDir.MINUS_V, b.wall());
				put(Role.WALL, u, y, v1, LocalDir.PLUS_V, b.wall());
			}
		}
		// the name, over the entrance, fixed flat to the landside wall
		if (!spec.name().isEmpty()) {
			for (int v = door - half - 3; v <= door + half + 3; v++) {
				putText(Role.FIXTURE, u0 - 1, top - 1, v, LocalDir.MINUS_U, LETTERING, spec.name());
			}
		}
		// columns in two lines, each under the roof
		final int depth = u1 - u0 + 1;
		for (final int u : new int[]{u0 + depth / 3, u0 + 2 * depth / 3}) {
			for (int v = v0 + 4; v < v1 - 2; v += 8) {
				for (int y = StationGeometry.WALK_Y; y < top; y++) {
					put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
				}
				put(Role.COLUMN_CAP, u, top, v, LocalDir.PLUS_V);
			}
		}
		// check-in counters along the landside, facing the entrance, and benches facing the glass in front of every gate
		for (int v = v0 + 5; v <= v1 - 5; v += 6) {
			if (Math.abs(v - door) > half + 3) {
				kiosk(u0 + 3, StationGeometry.WALK_Y, v, LocalDir.MINUS_U);
			}
		}
		final int off = d.standPitch() / 4;
		for (final int c : air.standV()) {
			for (final int v : new int[]{c - off - 1, c - off, c + off, c + off + 1}) {
				put(Role.BENCH, u1 - 2, StationGeometry.WALK_Y, v, LocalDir.PLUS_U);
			}
			put(Role.BIN, u1 - 2, StationGeometry.WALK_Y, c + off + 2, LocalDir.PLUS_U);
		}
	}

	/**
	 * A boarding bridge from every gate door in the terminal's airside wall out over the apron: a floor, glass along both sides,
	 * a roof and a closed docking end. At ground level and at the aircraft's door height only in spirit: the bridge here is the
	 * honest simple version, a covered walkway to the stand.
	 */
	void boardingBridges() {
		final StylePalette.Building b = palette.building();
		final int from = air.terminalU1() + 1;
		final int to = air.terminalU1() + air.dims().bridge();
		final int half = air.dims().bridgeWidth() / 2;
		for (final int c : air.standV()) {
			for (int u = from; u <= to; u++) {
				for (int v = c - half; v <= c + half; v++) {
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, b.floor());
					if (u == to) {
						for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
							put(Role.WALL, u, y, v, LocalDir.PLUS_U, b.wall());
						}
					}
				}
				for (final int side : new int[]{c - half - 1, c + half + 1}) {
					for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
						put(Role.WINDOW, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.window());
					}
				}
				for (int v = c - half - 1; v <= c + half + 1; v++) {
					put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT, v, LocalDir.PLUS_V, b.roof());
				}
			}
		}
	}

	// ---- the plan -----------------------------------------------------------------------------------------------------

	/** The block rows an airport plan is ordered, surveyed and built in: a band is built whole before the next, so the chunk window moves along the runway once. */
	static final int BAND = 32;

	/** The support layer under every area, in v bands. */
	List<Box> airportSupports() {
		final List<Box> out = new ArrayList<>();
		for (final AirportGeometry.Area area : air.areas()) {
			out.add(new Box(area.u0(), -1, area.v0(), area.u1(), -1, area.v1()));
		}
		return bands(out);
	}

	/** The air above every area that terrain must be cleared from, in v bands. */
	List<Box> airportHeadroom() {
		final List<Box> out = new ArrayList<>();
		for (final AirportGeometry.Area area : air.areas()) {
			out.add(new Box(area.u0(), StationGeometry.WALK_Y, area.v0(), area.u1(), area.clearance(), area.v1()));
		}
		return bands(out);
	}

	/** Boxes cut at every {@link #BAND} rows and ordered band by band, so a survey walks along v once instead of once per box. */
	static List<Box> bands(List<Box> boxes) {
		final List<Box> out = new ArrayList<>();
		for (final Box box : boxes) {
			for (int v = box.vMin(); v <= box.vMax(); ) {
				final int end = Math.min(box.vMax(), Math.floorDiv(v, BAND) * BAND + BAND - 1);
				out.add(new Box(box.uMin(), box.yMin(), v, box.uMax(), box.yMax(), end));
				v = end + 1;
			}
		}
		out.sort(java.util.Comparator.comparingInt((Box x) -> Math.floorDiv(x.vMin(), BAND)).thenComparingInt(Box::uMin).thenComparingInt(Box::yMin));
		return out;
	}
}
