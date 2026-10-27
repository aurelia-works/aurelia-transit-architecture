package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.AirportRoute.Cell;
import com.aureliatransit.architecture.station.AirportRoute.Kind;
import com.aureliatransit.architecture.station.AirportRoute.Leg;
import java.util.ArrayList;
import java.util.List;

/**
 * The MTR rails of each gate's loop, in world blocks. The loop ({@link AirportRoute}) is cut into straight rails between nodes.
 * MTR does not join two straight rails at a right angle, so a corner is cut instead: a curve from the cell before the corner,
 * through the corner cell, to the cell after it. A corner is cut only when both straight rails beside it have at least one
 * block of straight left; any other corner is left open, and the route reports it. A straight rail takes the kind of its first step.
 */
public final class AirportRails {
	private AirportRails() {
	}

	/** A compass direction: the sign of the larger axis of a step. */
	public enum Dir {
		E, S, W, N;

		static Dir of(int dx, int dz) {
			if (Math.abs(dx) >= Math.abs(dz)) {
				return dx >= 0 ? E : W;
			}
			return dz >= 0 ? S : N;
		}
	}

	/** A block position in the world. */
	public record Pos(int x, int y, int z) {
	}

	/**
	 * One MTR rail. {@code via} is the corner a curve passes through, or null for a straight rail. {@code startDir} is the way
	 * the rail leaves {@code from}, {@code endDir} the way it leaves {@code to} (back along the rail).
	 */
	public record AirRail(Kind kind, int gate, Pos from, Pos via, Pos to, Dir startDir, Dir endDir) {
	}

	/** The rails of every gate's loop at the frame's ground level. */
	public static List<AirRail> of(AirportRoute route, LocalFrame frame) {
		final int y = frame.y(StationGeometry.PLATFORM_Y);
		final List<AirRail> rails = new ArrayList<>();
		for (final AirportRoute.Gate gate : route.gates()) {
			for (final Chunk chunk : chunks(gate)) {
				emit(chunk, gate.index(), frame, y, rails);
			}
		}
		return rails;
	}

	/** Cells in flying order, with the kind of the leg each step belongs to; a gap (the flight) starts a new chunk. */
	private record Chunk(List<Cell> cells, List<Kind> kinds) {
	}

	/** The loop starts at its landing leg, so the siding's deep end (where the loop closes) sits inside one chunk, and the only break is the flight. */
	private static List<Chunk> chunks(AirportRoute.Gate gate) {
		final List<Chunk> out = new ArrayList<>();
		final List<Leg> legs = gate.legs();
		int from = 0;
		for (int i = 0; i < legs.size(); i++) {
			if (legs.get(i).kind() == Kind.LANDING) {
				from = i;
			}
		}
		List<Cell> cells = null;
		List<Kind> kinds = null;
		for (int k = 0; k < legs.size(); k++) {
			final Leg leg = legs.get((from + k) % legs.size());
			for (final Cell cell : leg.cells()) {
				if (cells != null) {
					final Cell last = cells.get(cells.size() - 1);
					if (cell.equals(last)) {
						continue;
					}
					if (last.adjacent(cell)) {
						cells.add(cell);
						kinds.add(leg.kind());
						continue;
					}
				}
				cells = new ArrayList<>();
				kinds = new ArrayList<>();
				cells.add(cell);
				out.add(new Chunk(cells, kinds));
			}
		}
		return out;
	}

	private static void emit(Chunk chunk, int gate, LocalFrame frame, int y, List<AirRail> out) {
		final List<Cell> cs = chunk.cells();
		final List<Kind> ks = chunk.kinds();
		final int n = ks.size();
		// straight runs: edges going one way; a run is cells [first, end]
		final List<int[]> runs = new ArrayList<>();
		for (int i = 0; i < n;) {
			int j = i;
			while (j + 1 < n && sameStep(cs, j + 1, i)) {
				j++;
			}
			runs.add(new int[] {i, j});
			i = j + 1;
		}
		int start = 0;
		for (int r = 0; r < runs.size(); r++) {
			final int end = runs.get(r)[1] + 1;
			final Kind kind = ks.get(runs.get(r)[0]);
			boolean cut = false;
			if (r + 1 < runs.size()) {
				final int nextEnd = runs.get(r + 1)[1] + 1;
				cut = end - 1 > start && nextEnd > end + 1;
			}
			if (cut) {
				// a corner is a connector, never a stand: a GATE run's corner takes the kind of the run after it, so only the stand is a platform
				final Kind bend = kind == Kind.GATE ? ks.get(runs.get(r + 1)[0]) : kind;
				straight(kind, gate, frame, y, cs.get(start), cs.get(end - 1), out);
				curve(bend, gate, frame, y, cs.get(end - 1), cs.get(end), cs.get(end + 1), out);
				start = end + 1;
			} else {
				straight(kind, gate, frame, y, cs.get(start), cs.get(end), out);
				start = end;
			}
		}
	}

	/** True when edge {@code edge} (cell edge to edge+1) goes the same way as edge {@code like}. */
	private static boolean sameStep(List<Cell> cs, int edge, int like) {
		return cs.get(edge + 1).u() - cs.get(edge).u() == cs.get(like + 1).u() - cs.get(like).u()
				&& cs.get(edge + 1).v() - cs.get(edge).v() == cs.get(like + 1).v() - cs.get(like).v();
	}

	private static void straight(Kind kind, int gate, LocalFrame frame, int y, Cell from, Cell to, List<AirRail> out) {
		if (from.equals(to)) {
			return;
		}
		final Pos a = pos(frame, y, from);
		final Pos b = pos(frame, y, to);
		out.add(new AirRail(kind, gate, a, null, b, Dir.of(b.x() - a.x(), b.z() - a.z()), Dir.of(a.x() - b.x(), a.z() - b.z())));
	}

	private static void curve(Kind kind, int gate, LocalFrame frame, int y, Cell from, Cell via, Cell to, List<AirRail> out) {
		final Pos a = pos(frame, y, from);
		final Pos c = pos(frame, y, via);
		final Pos b = pos(frame, y, to);
		out.add(new AirRail(kind, gate, a, c, b, Dir.of(c.x() - a.x(), c.z() - a.z()), Dir.of(c.x() - b.x(), c.z() - b.z())));
	}

	private static Pos pos(LocalFrame frame, int y, Cell cell) {
		return new Pos(frame.x(cell.u(), cell.v()), y, frame.z(cell.u(), cell.v()));
	}
}
