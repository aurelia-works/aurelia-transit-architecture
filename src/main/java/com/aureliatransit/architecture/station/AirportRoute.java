package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * The airplane route of an airport as pure geometry, in the local (u, v) cells of {@link AirportGeometry}: for each gate, the loop its
 * plane flies on the ground. Nothing here touches MTR or the world; the world turns the legs into rails.
 * <p>
 * The runway is one way, +v, and runway 0 only (a second runway is not routed here). The taxiway is flown one way too, -v. A plane
 * lands at the threshold and rolls nearly the whole runway to the far link, leaves it there and taxis back down the taxiway to its
 * stand. It backs out of the stand into the stand's siding; from the siding it taxis on down to the near link, turns onto the runway
 * and rolls nearly the whole runway to the far end. Those two links are the only joins between taxiway and runway, and the runway is
 * open at both ends, so no ground path runs from one gate to another through the runway. (Until 10-27 the plane left at the far link
 * and landed at the near one, so it took off and stopped in about 16 blocks.)
 * <p>
 * Each gate's loop, leg after leg, starting and ending at its siding's deep end (the cell by the platform's nose):
 * <ol>
 * <li>SIDING: the stand's siding along row s (half a stand pitch from the platform's row, so sidings never meet), toward the taxiway;</li>
 * <li>PUSH_BACK: the taxiway cell the siding joins;</li>
 * <li>TAXI: down the taxiway to the near link;</li>
 * <li>LINK: across to the runway at the near link;</li>
 * <li>TAKE_OFF: the take-off roll from the near link to the far end;</li>
 * <li>the flight: not a cell, the one break in the loop, from the far end back to the threshold;</li>
 * <li>LANDING: the landing roll from the threshold to the far link;</li>
 * <li>LINK: off the runway at the far link, to the taxiway;</li>
 * <li>TAXI: down the taxiway to the stand's row;</li>
 * <li>GATE: the lead-in and the platform along the stand's row, nose at {@link AirportGeometry#stopU()};</li>
 * <li>TURN: round the platform's deep end, back to the siding's row.</li>
 * </ol>
 * Every stand row and siding row must lie between the two links, or there is no route ({@link #problem()} says so). A siding's
 * straight (the corner onto the taxiway is the push-back) must hold a plane ({@link #PLANE_LENGTH}); a compact airport's does not, so it
 * gets no route either.
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
		// links: [near the threshold (take-off), near the far end (exit), and a middle one on a long runway, which is not routed]
		final int m1 = centre(g.links().get(1));
		// blocks of the siding's straight rail: the corners at both ends are cut (AirportRails), the turn's at the nose and the
		// push-back's onto the taxiway, so the straight runs from the cell past the nose to the cell before the taxiway
		final int siding = tc - 2 - stop;
		if (siding < PLANE_LENGTH) {
			return none("the siding would be " + siding + " blocks; a plane is " + PLANE_LENGTH);
		}
		final List<Gate> gates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			final int c = g.standV().get(i);
			final int s = c + g.dims().standPitch() / 2;
			if (!(v0 < m0 && m0 < c && c < s && s < m1 && m1 < v1)) {
				return none("stand " + (i + 1) + " at row " + c + " is not between the take-off link " + m0 + " and the exit link " + m1);
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
		legs.add(new Leg(Kind.TAXI, runV(tc, s - 1, m0)));
		legs.add(new Leg(Kind.LINK, runU(tc + 1, rc, m0)));
		legs.add(new Leg(Kind.TAKE_OFF, runV(rc, m0 + 1, v1)));
		legs.add(new Leg(Kind.LANDING, runV(rc, v0, m1)));
		legs.add(new Leg(Kind.LINK, runU(rc - 1, tc, m1)));
		legs.add(new Leg(Kind.TAXI, runV(tc, m1 - 1, c)));
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
