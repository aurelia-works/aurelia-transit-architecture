package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything of an airport stands, in the plan's local frame, before any block is chosen: u across the airport from the
 * road to the runways, v along the runways. No Minecraft classes; the planner ({@link PlanAirport}), the checks and the tests all
 * read the same numbers.
 * <p>
 * Across (low u to high u): the road in front of the terminal, the terminal, the apron with the aircraft stands, the parallel
 * taxiway, open ground, then the runway (and, with two, open ground and the second runway). Along v: a margin at each end, the
 * runway's blast pads, and the runway between them.
 */
public record AirportGeometry(AirportSpec spec, int width, int length, int roadU0, int roadU1, int terminalU0, int terminalU1, int terminalV0,
		int terminalV1, int apronU0, int apronU1, int apronV0, int apronV1, int taxiU0, int taxiU1, int taxiV0, int taxiV1, int runwayV0,
		int runwayV1, List<Integer> runwayU0, List<Integer> standV, List<int[]> links, Dims dims) {

	/**
	 * The sizes of everything but the runway, in blocks. A runway {@link #REAL_WIDTH} wide or more gets {@link #REAL} (an aircraft
	 * stand big enough for an airliner at 1.5 blocks a metre, a taxiway and terminal to match); a narrower one gets {@link #COMPACT},
	 * small enough to look at whole.
	 *
	 * @param end             open margin past each runway end, with room for the blast pad and the taxiway round it
	 * @param blast           paved blast pad past each runway end
	 * @param road            width of the road in front of the terminal
	 * @param terminalDepth   the terminal's depth, landside to airside wall
	 * @param standDepth      how far an aircraft stand reaches from the terminal's airside wall to the taxiway
	 * @param standPitch      rows of apron one gate takes
	 * @param taxi            taxiway width, and the width of every link to a runway
	 * @param gap             open ground between the parallel taxiway and the first runway
	 * @param runwayGap       open ground between two parallel runways
	 * @param terminalEnd     the terminal runs this far past the first and last stand
	 * @param linkInset       taxiway rows kept clear of the runway ends before the first link
	 * @param bridge          length of a boarding bridge from the terminal wall, its docking end included
	 * @param mast            height of a floodlight mast
	 * @param wallTop         the terminal's walls rise to this y
	 * @param entrance        width of the terminal's street entrance (odd)
	 * @param bridgeWidth     clear width of a boarding bridge (odd)
	 * @param thresholdLength length of the threshold stripes
	 * @param numberOffset    rows from the runway end to the painted number
	 * @param edgeLightEvery  rows between runway edge lights
	 * @param taxiLightEvery  cells between taxiway edge lights
	 * @param dash            a runway centreline's painted length
	 * @param dashGap         and its bare length
	 * @param shoulder        light-grey paved strip along each side of a runway, 0 for none
	 */
	public record Dims(int end, int blast, int road, int terminalDepth, int standDepth, int standPitch, int taxi, int gap, int runwayGap,
			int terminalEnd, int linkInset, int bridge, int mast, int wallTop, int entrance, int bridgeWidth, int thresholdLength,
			int numberOffset, int edgeLightEvery, int taxiLightEvery, int dash, int dashGap, int shoulder) {

		/** Rows of runway that no stand may use: both links, their insets and the apron's margin. */
		public int apronClear() {
			return 2 * (linkInset + taxi + 5) + 2 * terminalEnd;
		}

		/** The runway length with room for one gate. */
		public int minLength() {
			return Math.max(AirportSpec.MIN_RUNWAY_LENGTH, apronClear() + standPitch);
		}
	}

	/** A runway this wide or wider (30 m at 1.5 blocks a metre) is a regional airport's, with the real dimensions. */
	public static final int REAL_WIDTH = 30;
	public static final Dims COMPACT = new Dims(24, 6, 6, 18, 30, 28, 9, 14, 30, 8, 2, 8, 12, 6, 5, 3, 8, 11, 4, 6, 8, 6, 0);
	public static final Dims REAL = new Dims(60, 24, 12, 40, 84, 66, 21, 48, 120, 16, 6, 22, 24, 10, 7, 5, 18, 30, 8, 10, 14, 10, 6);
	/** A runway longer than this gets a link in the middle as well. */
	public static final int MID_LINK_LENGTH = 480;

	/** The dimensions of an airport whose runways are {@code runwayWidth} wide. */
	public static Dims dims(int runwayWidth) {
		return runwayWidth >= REAL_WIDTH ? REAL : COMPACT;
	}

	public static AirportGeometry of(AirportSpec spec) {
		if (!spec.present()) {
			throw new IllegalArgumentException("not an airport");
		}
		final Dims d = dims(spec.runwayWidth());
		final int roadU0 = spec.terminal() ? 0 : -1;
		final int roadU1 = spec.terminal() ? d.road() - 1 : -1;
		final int terminalU0 = spec.terminal() ? d.road() : -1;
		final int terminalU1 = spec.terminal() ? d.road() + d.terminalDepth() - 1 : -1;
		final int apronU0 = spec.terminal() ? terminalU1 + 1 : 0;
		final int apronU1 = apronU0 + d.standDepth() - 1;
		final int taxiU0 = apronU1 + 1;
		final int taxiU1 = taxiU0 + d.taxi() - 1;
		final List<Integer> runwayU0 = new ArrayList<>();
		int u = taxiU1 + 1 + d.gap();
		for (int i = 0; i < spec.runways(); i++) {
			runwayU0.add(u);
			u += spec.runwayWidth() + d.runwayGap();
		}
		final int width = runwayU0.get(runwayU0.size() - 1) + spec.runwayWidth() + d.shoulder();
		final int length = spec.runwayLength() + 2 * d.end();
		final int runwayV0 = d.end();
		final int runwayV1 = d.end() + spec.runwayLength() - 1;
		final int stands = spec.gates() * d.standPitch();
		final int firstStand = d.end() + spec.runwayLength() / 2 - stands / 2;
		final List<Integer> standV = new ArrayList<>();
		for (int i = 0; i < spec.gates(); i++) {
			standV.add(firstStand + i * d.standPitch() + d.standPitch() / 2);
		}
		final int apronV0 = firstStand - d.terminalEnd();
		final int apronV1 = firstStand + stands - 1 + d.terminalEnd();
		// a second runway is reached round the end of the first, so the taxiway runs on to the margin
		final int taxiV0 = spec.runways() > 1 ? 2 : runwayV0 + d.linkInset();
		final int taxiV1 = spec.runways() > 1 ? length - 3 : runwayV1 - d.linkInset();
		final List<int[]> links = new ArrayList<>();
		links.add(new int[]{runwayV0 + d.linkInset(), runwayV0 + d.linkInset() + d.taxi() - 1});
		links.add(new int[]{runwayV1 - d.linkInset() - d.taxi() + 1, runwayV1 - d.linkInset()});
		if (spec.runwayLength() >= MID_LINK_LENGTH) {
			final int mid = (runwayV0 + runwayV1) / 2;
			links.add(new int[]{mid - d.taxi() / 2, mid + d.taxi() / 2});
		}
		return new AirportGeometry(spec, width, length, roadU0, roadU1, terminalU0, terminalU1, apronV0, apronV1, apronU0, apronU1, apronV0, apronV1,
				taxiU0, taxiU1, taxiV0, taxiV1, runwayV0, runwayV1, List.copyOf(runwayU0), List.copyOf(standV), List.copyOf(links), d);
	}

	/** A runway this long or longer gets rapid exits from its first runway to the taxiway. */
	public static final int RAPID_EXIT_LENGTH = 600;
	/** Rows a 30 degree rapid exit runs along for each column it crosses in the gap (1 / tan 30 degrees). */
	static final double EXIT_ROWS_PER_COLUMN = Math.sqrt(3);

	/**
	 * The first row of each rapid exit, where it leaves the first runway: at a quarter and three quarters of its length. None on a runway
	 * shorter than {@link #RAPID_EXIT_LENGTH}.
	 */
	public List<Integer> rapidExits() {
		if (spec.runwayLength() < RAPID_EXIT_LENGTH) {
			return List.of();
		}
		return List.of(runwayV0 + spec.runwayLength() / 4, runwayV0 + 3 * spec.runwayLength() / 4);
	}

	/** The centreline row of a rapid exit that leaves the runway at row {@code start}, in column {@code u} of the gap to the taxiway. */
	public int exitCentre(int start, int u) {
		return start + (int) Math.round((runwayU0.get(0) - 1 - u) * EXIT_ROWS_PER_COLUMN);
	}

	/** Half the width of a rapid exit across the rows: the taxiway's half width. */
	public int exitHalf() {
		return dims.taxi() / 2;
	}

	/**
	 * The control tower's footprint: a 5 by 5 cab on a 3 by 3 shaft, on the apron's far margin past its last stand line, by the taxiway.
	 * It is apron only, so it stays out of the runway strip.
	 */
	public Area tower() {
		return new Area(Kind.APRON, apronU1 - 5, apronU1 - 1, apronV1 - 4, apronV1, 0);
	}

	/** Last column of runway {@code i}. */
	public int runwayU1(int i) {
		return runwayU0.get(i) + spec.runwayWidth() - 1;
	}

	/** The middle column of runway {@code i} (the left one of two when the width is even). */
	public int runwayCentre(int i) {
		return runwayU0.get(i) + (spec.runwayWidth() - 1) / 2;
	}

	/** The middle column of the parallel taxiway. */
	public int taxiCentre() {
		return (taxiU0 + taxiU1) / 2;
	}

	/** Rows of the end-around taxiway at the low end (the high end mirrors it): it carries a second runway's traffic round the first. */
	public int[] endAround() {
		return new int[]{2, 2 + dims.taxi() - 1};
	}

	/** The row range of the stub from the end-around taxiway to a second runway's blast pad, at the low end. */
	public int[] stub() {
		return new int[]{endAround()[1] + 1, runwayV0 - dims.blast() - 1};
	}

	/** Mirrors a row to the same place counted from the high end. */
	public int mirror(int v) {
		return length - 1 - v;
	}

	/** Where an aircraft's nose stops on stand: past the boarding bridge, or well in from the apron's edge without a terminal. */
	public int stopU() {
		return spec.terminal() ? terminalU1 + dims.bridge() + 3 : apronU0 + 10;
	}

	/** True when {@code (u, v)} is inside the terminal building's footprint. */
	public boolean inTerminal(int u, int v) {
		return spec.terminal() && u >= terminalU0 && u <= terminalU1 && v >= terminalV0 && v <= terminalV1;
	}

	/** What a paved or built area is for. */
	public enum Kind {
		ROAD, TERMINAL, APRON, TAXIWAY, RUNWAY, BLAST_PAD, SHOULDER
	}

	/**
	 * A rectangle of the airport: columns u0..u1, rows v0..v1, of one kind, with the height above ground that must be cleared of
	 * terrain for it (the plan's own blocks are exempt).
	 */
	public record Area(Kind kind, int u0, int u1, int v0, int v1, int clearance) {

		public boolean contains(int u, int v) {
			return u >= u0 && u <= u1 && v >= v0 && v <= v1;
		}
	}

	/** Rows of terrain cleared above a runway, taxiway or road. */
	public static final int OPEN_CLEARANCE = 3;


	/** Every area of the airport, in the order the plan paves them; the same list drives the survey's boxes and the checks. */
	public List<Area> areas() {
		final List<Area> out = new ArrayList<>();
		if (spec.terminal()) {
			out.add(new Area(Kind.ROAD, roadU0, roadU1, Math.max(0, apronV0 - 20), Math.min(length - 1, apronV1 + 20), OPEN_CLEARANCE));
			out.add(new Area(Kind.TERMINAL, terminalU0, terminalU1, terminalV0, terminalV1, dims.wallTop() + 2));
		}
		out.add(new Area(Kind.APRON, apronU0, apronU1, apronV0, apronV1, dims.mast() + 1));
		out.add(new Area(Kind.TAXIWAY, taxiU0, taxiU1, taxiV0, taxiV1, OPEN_CLEARANCE));
		for (final int[] link : links) {
			out.add(new Area(Kind.TAXIWAY, taxiU1 + 1, runwayU0.get(0) - 1, link[0], link[1], OPEN_CLEARANCE));
		}
		// rapid exits: one column at a time, each a strip of the taxiway's width whose centre moves 30 degrees off the runway
		for (final int start : rapidExits()) {
			for (int u = taxiU1 + 1; u <= runwayU0.get(0) - 1; u++) {
				final int c = exitCentre(start, u);
				out.add(new Area(Kind.TAXIWAY, u, u, c - exitHalf(), c + exitHalf(), OPEN_CLEARANCE));
			}
		}
		if (spec.runways() > 1) {
			final int far = runwayCentre(1) + dims.taxi() / 2;
			final int[] around = endAround();
			final int[] stub = stub();
			out.add(new Area(Kind.TAXIWAY, taxiU0, far, around[0], around[1], OPEN_CLEARANCE));
			out.add(new Area(Kind.TAXIWAY, taxiU0, far, mirror(around[1]), mirror(around[0]), OPEN_CLEARANCE));
			out.add(new Area(Kind.TAXIWAY, runwayCentre(1) - dims.taxi() / 2, far, stub[0], stub[1], OPEN_CLEARANCE));
			out.add(new Area(Kind.TAXIWAY, runwayCentre(1) - dims.taxi() / 2, far, mirror(stub[1]), mirror(stub[0]), OPEN_CLEARANCE));
		}
		for (int i = 0; i < spec.runways(); i++) {
			out.add(new Area(Kind.RUNWAY, runwayU0.get(i), runwayU1(i), runwayV0, runwayV1, OPEN_CLEARANCE));
			if (dims.shoulder() > 0) {
				out.add(new Area(Kind.SHOULDER, runwayU0.get(i) - dims.shoulder(), runwayU0.get(i) - 1, runwayV0, runwayV1, OPEN_CLEARANCE));
				out.add(new Area(Kind.SHOULDER, runwayU1(i) + 1, runwayU1(i) + dims.shoulder(), runwayV0, runwayV1, OPEN_CLEARANCE));
			}
			out.add(new Area(Kind.BLAST_PAD, runwayU0.get(i), runwayU1(i), runwayV0 - dims.blast(), runwayV0 - 1, OPEN_CLEARANCE));
			out.add(new Area(Kind.BLAST_PAD, runwayU0.get(i), runwayU1(i), runwayV1 + 1, runwayV1 + dims.blast(), OPEN_CLEARANCE));
		}
		return out;
	}
}
