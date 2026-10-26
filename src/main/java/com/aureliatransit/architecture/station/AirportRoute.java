package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * The airplane route of an airport as pure geometry, in the local (u, v) cells of {@link AirportGeometry}: for each gate, the loop its
 * plane flies on the ground. Nothing here touches MTR or the world; the world turns the legs into rails.
 * <p>
 * The runway is one way, +v, and runway 0 only (a second runway is not routed here). A plane lands at the threshold, rolls to the exit
 * link near the threshold, taxis up the taxiway to its stand, and backs out of the stand into the stand's siding. From the siding it taxis
 * up to the take-off link (the one near the far end), backs onto the runway and rolls to the far end. Those two links are the only joins
 * between taxiway and runway, and the runway is open at both ends, so no ground path runs from one gate to another through the runway.
 * <p>
 * Each gate's loop, leg after leg, starting and ending at its siding's deep end (the cell by the platform's nose):
 * <ol>
 * <li>SIDING: the stand's siding along row s (half a stand pitch from the platform's row, so sidings never meet), toward the taxiway;</li>
 * <li>PUSH_BACK: the taxiway cell the siding joins;</li>
 * <li>TAXI: up the taxiway to the take-off link;</li>
 * <li>LINK: across to the runway at the take-off link;</li>
 * <li>TAKE_OFF: the take-off roll to the far end;</li>
 * <li>the flight: not a cell, the one break in the loop, from the far end back to the threshold;</li>
 * <li>LANDING: the landing roll from the threshold to the exit link;</li>
 * <li>LINK: off the runway at the exit link, to the taxiway;</li>
 * <li>TAXI: up the taxiway to the stand's row;</li>
 * <li>GATE: the lead-in and the platform along the stand's row, nose at {@link AirportGeometry#stopU()};</li>
 * <li>TURN: round the platform's deep end, back to the siding's row.</li>
 * </ol>
 * The take-off link must lie past every stand row and the exit link before it, or there is no route ({@link #problem()} says so). A
 * siding must hold a plane ({@link #PLANE_LENGTH}); a compact airport's siding does not, so it gets no route either.
 *
 * @param gates   one loop per gate, in stand order; empty when there is no route
 * @param problem why there is no route, or the empty string
 */
public record AirportRoute(List<Gate> gates, String problem) {

	/** A plane's length in blocks: the spike's a320 car. A siding shorter than this cannot hold one. */
	public static final int PLANE_LENGTH = 30;

	/** One cell of the airport's grid: {@code u} across the airport, {@code v} along the runway. */
	public record Cell(int u, int v) {

		/** True when the other cell is the next one along a rail: one step in u or v. */
		public boolean adjacent(Cell other) {
			return Math.abs(u - other.u) + Math.abs(v - other.v) == 1;
		}
	}

	public enum Kind {
		SIDING, PUSH_BACK, TAXI, LINK, TAKE_OFF, LANDING, GATE, TURN
	}

	/** A straight run of cells, in the order the plane flies them. */
	public record Leg(Kind kind, List<Cell> cells) {
	}

	/**
	 * A gate's loop.
	 *
	 * @param index    the stand's number from 0
	 * @param row      the platform's row (v)
	 * @param sidingRow the siding's row (v)
	 * @param platform the platform's cells: the GATE leg from the nose to the apron's edge
	 * @param legs     the loop, starting and ending at the siding's deep end
	 */
	public record Gate(int index, int row, int sidingRow, List<Cell> platform, List<Leg> legs) {
	}

	/** The route of an airport, or none with the reason. */
	public static AirportRoute of(AirportGeometry g) {
		final int count = g.spec().gates();
		if (!g.spec().present() || count < 1) {
			return none("no gates to route");
		}
		final int stop = g.stopU();
		final int tc = g.taxiCentre();
		final int rc = g.runwayCentre(0);
		final int v0 = g.runwayV0();
		final int v1 = g.runwayV1();
		final int m0 = centre(g.links().get(0));
		// links: [near the threshold, near the far end, and a middle one on a long runway, which is not routed]
		final int m1 = centre(g.links().get(1));
		final int siding = tc - stop + 1;
		if (siding < PLANE_LENGTH) {
			return none("the siding would be " + siding + " blocks; a plane is " + PLANE_LENGTH);
		}
		final List<Gate> gates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			final int c = g.standV().get(i);
			final int s = c + g.dims().standPitch() / 2;
			if (!(v0 < m0 && m0 < c && c < s && s < m1 && m1 < v1)) {
				return none("stand " + (i + 1) + " at row " + c + " is not between the exit link " + m0 + " and the take-off link " + m1);
			}
			final List<Leg> legs = loop(stop, tc, rc, c, s, m0, m1, v0, v1);
			final List<Cell> platform = new ArrayList<>();
			for (final Cell cell : legs.stream().filter(leg -> leg.kind() == Kind.GATE).findFirst().orElseThrow().cells()) {
				if (cell.u() <= g.apronU1()) {
					platform.add(cell);
				}
			}
			gates.add(new Gate(i, c, s, List.copyOf(platform), List.copyOf(legs)));
		}
		return new AirportRoute(List.copyOf(gates), "");
	}

	private static AirportRoute none(String why) {
		return new AirportRoute(List.of(), why);
	}

	private static int centre(int[] link) {
		return (link[0] + link[1]) / 2;
	}

	private static List<Leg> loop(int stop, int tc, int rc, int c, int s, int m0, int m1, int v0, int v1) {
		final List<Leg> legs = new ArrayList<>();
		legs.add(new Leg(Kind.SIDING, runU(stop, tc - 1, s)));
		legs.add(new Leg(Kind.PUSH_BACK, List.of(new Cell(tc, s))));
		legs.add(new Leg(Kind.TAXI, runV(tc, s + 1, m1)));
		legs.add(new Leg(Kind.LINK, runU(tc + 1, rc, m1)));
		legs.add(new Leg(Kind.TAKE_OFF, runV(rc, m1 + 1, v1)));
		legs.add(new Leg(Kind.LANDING, runV(rc, v0, m0)));
		legs.add(new Leg(Kind.LINK, runU(rc - 1, tc, m0)));
		legs.add(new Leg(Kind.TAXI, runV(tc, m0 + 1, c)));
		legs.add(new Leg(Kind.GATE, runU(tc - 1, stop, c)));
		legs.add(new Leg(Kind.TURN, runV(stop, c + 1, s)));
		return legs;
	}

	/** Cells along u at row v, from u {@code from} to u {@code to}, both ends included, in either direction. */
	private static List<Cell> runU(int from, int to, int v) {
		final List<Cell> out = new ArrayList<>();
		final int step = from <= to ? 1 : -1;
		for (int u = from; u != to + step; u += step) {
			out.add(new Cell(u, v));
		}
		return out;
	}

	/** Cells along v at column u, from v {@code from} to v {@code to}, both ends included, in either direction. */
	private static List<Cell> runV(int u, int from, int to) {
		final List<Cell> out = new ArrayList<>();
		final int step = from <= to ? 1 : -1;
		for (int v = from; v != to + step; v += step) {
			out.add(new Cell(u, v));
		}
		return out;
	}
}
