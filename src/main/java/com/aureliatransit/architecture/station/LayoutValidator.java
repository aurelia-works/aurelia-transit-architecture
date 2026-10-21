package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks a plan against the railway clearances and simple structural rules, without the world. A plan with no violations
 * can still be refused on site by {@link SiteSurvey}.
 */
public final class LayoutValidator {

	/** Highest y a plan may use, room for a grand train shed at 1.5 blocks to the metre; the lowest is -1 (the support layer). */
	private static final int MAX_Y = 96;
	/** The lowest a block may go: the support layer, or with an underpass its floor. */
	private static final int MIN_Y = -1;

	private static final Comparator<Violation> CELL_ORDER = Comparator.comparingInt(Violation::y).thenComparingInt(Violation::v)
			.thenComparingInt(Violation::u).thenComparing(Violation::message);

	private LayoutValidator() {
	}

	/**
	 * Every violation, in a deterministic order; empty means the plan is buildable as designed. The order is rule by rule (the
	 * order of the checks below), then y, v, u.
	 */
	public static List<Violation> validate(StationPlan plan) {
		final Check check = new Check(plan);
		final List<Violation> all = new ArrayList<>();
		add(all, check.outOfBounds());
		add(all, check.overlaps());
		add(all, check.envelopeIntrusions());
		add(all, check.trackSpacing());
		add(all, check.platformOffsets());
		add(all, check.headroom());
		add(all, check.edgeClearway());
		add(all, check.unsupportedColumns());
		add(all, check.unsupportedRoof());
		add(all, check.roofSpans());
		add(all, check.deckSpans());
		add(all, check.floatingFixtures());
		return all;
	}

	private static void add(List<Violation> all, List<Violation> found) {
		found.sort(CELL_ORDER);
		all.addAll(found);
	}

	private record Cell(int u, int y, int v) {
	}

	private record Column(int u, int v) {
	}

	private static final class Check {

		private final StationPlan plan;
		private final TrackLayout layout;
		private final Map<Cell, List<PlannedBlock>> cells = new HashMap<>();

		Check(StationPlan plan) {
			this.plan = plan;
			this.layout = plan.layout();
			for (final PlannedBlock block : plan.blocks()) {
				cells.computeIfAbsent(new Cell(block.u(), block.y(), block.v()), key -> new ArrayList<>(1)).add(block);
			}
		}

		/** True if any block in the cell has this role. */
		private boolean has(int u, int y, int v, Role role) {
			final List<PlannedBlock> blocks = cells.get(new Cell(u, y, v));
			if (blocks != null) {
				for (final PlannedBlock block : blocks) {
					if (block.role() == role) {
						return true;
					}
				}
			}
			return false;
		}

		private boolean isPlatformFloor(int u, int y, int v) {
			final List<PlannedBlock> blocks = cells.get(new Cell(u, y, v));
			if (blocks != null) {
				for (final PlannedBlock block : blocks) {
					if (block.role().platformFloor()) {
						return true;
					}
				}
			}
			return false;
		}

		private boolean isFlatFloor(int u, int y, int v) {
			final List<PlannedBlock> blocks = cells.get(new Cell(u, y, v));
			if (blocks != null) {
				for (final PlannedBlock block : blocks) {
					if (block.role().flatFloor()) {
						return true;
					}
				}
			}
			return false;
		}

		private static Violation at(Violation.Kind kind, PlannedBlock block, String message) {
			return new Violation(kind, block.u(), block.y(), block.v(), message);
		}

		private static String where(PlannedBlock block) {
			return block.role() + " at u=" + block.u() + " y=" + block.y() + " v=" + block.v();
		}

		List<Violation> outOfBounds() {
			final List<Violation> found = new ArrayList<>();
			final int minY = plan.spec().underpass() ? StationGeometry.UNDERPASS_FLOOR_Y : MIN_Y;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.u() < 0 || block.u() >= layout.width() || block.v() < layout.vMin() || block.v() > layout.vMax() || block.y() < minY
						|| block.y() > MAX_Y) {
					found.add(at(Violation.Kind.OUT_OF_BOUNDS, block, where(block) + " is outside the footprint"));
				}
			}
			return found;
		}

		List<Violation> overlaps() {
			final List<Violation> found = new ArrayList<>();
			for (final Map.Entry<Cell, List<PlannedBlock>> entry : cells.entrySet()) {
				if (entry.getValue().size() > 1) {
					final Cell cell = entry.getKey();
					final List<Role> sorted = new ArrayList<>();
					for (final PlannedBlock block : entry.getValue()) {
						sorted.add(block.role());
					}
					sorted.sort(Comparator.naturalOrder());
					final StringBuilder roles = new StringBuilder();
					for (final Role role : sorted) {
						roles.append(roles.length() == 0 ? "" : ", ").append(role);
					}
					found.add(new Violation(Violation.Kind.OVERLAP, cell.u(), cell.y(), cell.v(),
							entry.getValue().size() + " blocks in one cell: " + roles));
				}
			}
			return found;
		}

		List<Violation> envelopeIntrusions() {
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.RAIL_NODE) {
					continue;
				}
				for (final Box envelope : plan.trackEnvelopes()) {
					if (envelope.contains(block.u(), block.y(), block.v())) {
						found.add(at(Violation.Kind.ENVELOPE_INTRUSION, block, where(block) + " is inside a track clearance envelope"));
						break;
					}
				}
			}
			return found;
		}

		List<Violation> trackSpacing() {
			final List<Violation> found = new ArrayList<>();
			final List<TrackLayout.Track> tracks = new ArrayList<>(layout.tracks());
			tracks.sort(Comparator.comparingInt(TrackLayout.Track::u));
			for (int i = 0; i + 1 < tracks.size(); i++) {
				final TrackLayout.Track a = tracks.get(i);
				final TrackLayout.Track b = tracks.get(i + 1);
				if (b.u() - a.u() >= StationGeometry.TRACK_SPACING || platformBetween(a.u(), b.u())) {
					continue;
				}
				found.add(new Violation(Violation.Kind.TRACK_SPACING, a.u(), StationGeometry.PLATFORM_Y, 0,
						"tracks " + a.number() + " and " + b.number() + " are " + (b.u() - a.u()) + " apart; at least "
								+ StationGeometry.TRACK_SPACING + " needed"));
			}
			return found;
		}

		private boolean platformBetween(int uLeft, int uRight) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (band.uMin() > uLeft && band.uMax() < uRight) {
					return true;
				}
			}
			return false;
		}

		List<Violation> platformOffsets() {
			final List<Violation> found = new ArrayList<>();
			for (final TrackLayout.Track track : layout.tracks()) {
				if (track.platformLeft()) {
					edgeAlongTrack(found, track, track.u() - StationGeometry.EDGE_OFFSET, LocalDir.PLUS_U);
				}
				if (track.platformRight()) {
					edgeAlongTrack(found, track, track.u() + StationGeometry.EDGE_OFFSET, LocalDir.MINUS_U);
				}
			}
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.PLATFORM_EDGE && !faceTrackAtOffset(block)) {
					found.add(at(Violation.Kind.PLATFORM_OFFSET, block,
							"platform edge at u=" + block.u() + " v=" + block.v() + " is not " + StationGeometry.EDGE_OFFSET
									+ " columns from a track it faces (front " + block.front() + ")"));
				}
			}
			return found;
		}

		/** One violation at the first v where the platform edge next to a track is missing or faces the wrong way. */
		private void edgeAlongTrack(List<Violation> found, TrackLayout.Track track, int edgeU, LocalDir toward) {
			for (int v = layout.platformFrom(track); v <= layout.platformTo(track); v++) {
				if (!hasEdgeFacing(edgeU, v, toward)) {
					found.add(new Violation(Violation.Kind.PLATFORM_OFFSET, edgeU, StationGeometry.PLATFORM_Y, v,
							"track " + track.number() + " needs a platform edge facing it at u=" + edgeU + " (v=" + v + " has none)"));
					return;
				}
			}
		}

		private boolean hasEdgeFacing(int u, int v, LocalDir front) {
			final List<PlannedBlock> blocks = cells.get(new Cell(u, StationGeometry.PLATFORM_Y, v));
			if (blocks != null) {
				for (final PlannedBlock block : blocks) {
					if (block.role() == Role.PLATFORM_EDGE && block.front() == front) {
						return true;
					}
				}
			}
			return false;
		}

		private boolean faceTrackAtOffset(PlannedBlock edge) {
			final int trackU;
			if (edge.front() == LocalDir.PLUS_U) {
				trackU = edge.u() + StationGeometry.EDGE_OFFSET;
			} else if (edge.front() == LocalDir.MINUS_U) {
				trackU = edge.u() - StationGeometry.EDGE_OFFSET;
			} else {
				return false;
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				if (track.u() == trackU) {
					return true;
				}
			}
			return false;
		}

		List<Violation> headroom() {
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role().roofPart() && block.y() < StationGeometry.MIN_ROOF_Y
						&& isPlatformFloor(block.u(), StationGeometry.PLATFORM_Y, block.v())) {
					found.add(at(Violation.Kind.HEADROOM, block,
							where(block) + " is below the headroom limit y=" + StationGeometry.MIN_ROOF_Y + " over a platform"));
				}
			}
			return found;
		}

		List<Violation> edgeClearway() {
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.RAILING && platformEnd(block.u(), block.v())) {
					continue; // the fence across a platform's end runs to the edge, so nobody steps off onto the tracks there
				}
				if (block.role().obstructsWalkway() && block.y() >= StationGeometry.WALK_Y
						&& block.y() <= StationGeometry.WALK_Y + StationGeometry.HEADROOM - 1
						&& has(block.u(), StationGeometry.PLATFORM_Y, block.v(), Role.PLATFORM_EDGE)) {
					found.add(at(Violation.Kind.EDGE_CLEARWAY, block, where(block) + " stands on a platform edge"));
				}
			}
			return found;
		}

		/** A lamp standard's head, on top of its pole. */
		private boolean onLampPole(PlannedBlock lamp) {
			final List<PlannedBlock> below = cells.get(new Cell(lamp.u(), lamp.y() - 1, lamp.v()));
			return below != null && below.stream().anyMatch(block -> block.blockId().equals(StationPlanner.LAMP_POLE));
		}

		/** The first or last row of a platform. */
		private boolean platformEnd(int u, int v) {
			for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
				if (u >= band.uMin() && u <= band.uMax() && (v == band.vFrom() || v == band.vTo())) {
					return true;
				}
			}
			return false;
		}

		List<Violation> unsupportedColumns() {
			final Map<Column, List<PlannedBlock>> columns = new TreeMap<>(Comparator.comparingInt(Column::v).thenComparingInt(Column::u));
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.COLUMN || block.role() == Role.COLUMN_CAP) {
					columns.computeIfAbsent(new Column(block.u(), block.v()), key -> new ArrayList<>()).add(block);
				}
			}
			final List<Violation> found = new ArrayList<>();
			for (final Map.Entry<Column, List<PlannedBlock>> entry : columns.entrySet()) {
				final Column column = entry.getKey();
				final List<PlannedBlock> stack = new ArrayList<>(entry.getValue());
				stack.sort(Comparator.comparingInt(PlannedBlock::y));
				final String problem = columnProblem(column, stack);
				if (problem != null) {
					found.add(new Violation(Violation.Kind.UNSUPPORTED_COLUMN, column.u(), stack.get(0).y(), column.v(), problem));
				}
			}
			return found;
		}

		/**
		 * Null if the column stands on a platform floor (or the floor beside a terminus overrun, level with it), runs unbroken
		 * from y = 1 to one cap and has a roof part above.
		 */
		private String columnProblem(Column column, List<PlannedBlock> stack) {
			if (!isPlatformFloor(column.u(), StationGeometry.PLATFORM_Y, column.v()) && !has(column.u(), StationGeometry.PLATFORM_Y, column.v(), Role.FLOOR)) {
				return "column at u=" + column.u() + " v=" + column.v() + " does not stand on a platform floor";
			}
			int expectedY = StationGeometry.WALK_Y;
			for (int i = 0; i < stack.size(); i++) {
				final PlannedBlock block = stack.get(i);
				final boolean last = i == stack.size() - 1;
				if (block.y() != expectedY) {
					return "column at u=" + column.u() + " v=" + column.v() + " is broken at y=" + expectedY;
				}
				if (last != (block.role() == Role.COLUMN_CAP)) {
					return "column at u=" + column.u() + " v=" + column.v() + (last ? " does not end in a cap" : " has a cap below its top");
				}
				expectedY++;
			}
			final int capY = stack.get(stack.size() - 1).y();
			final List<PlannedBlock> above = cells.get(new Cell(column.u(), capY + 1, column.v()));
			if (above != null) {
				for (final PlannedBlock block : above) {
					if (block.role().covers()) {
						return null;
					}
				}
			}
			return "column at u=" + column.u() + " v=" + column.v() + " has no roof above its cap";
		}

		List<Violation> unsupportedRoof() {
			final int reachLimit = StationGeometry.MAX_CANTILEVER;
			// a grid over every block plus the reach on each side: per cell the lowest carrier (cap, beam or bridge pier) y
			// standing there, then spread to every cell within the cantilever by a min filter along u and then along v
			int uLo = Integer.MAX_VALUE, uHi = Integer.MIN_VALUE, vLo = Integer.MAX_VALUE, vHi = Integer.MIN_VALUE;
			for (final PlannedBlock block : plan.blocks()) {
				uLo = Math.min(uLo, block.u());
				uHi = Math.max(uHi, block.u());
				vLo = Math.min(vLo, block.v());
				vHi = Math.max(vHi, block.v());
			}
			if (uLo > uHi) {
				return new ArrayList<>();
			}
			final Grid grid = new Grid(uLo - reachLimit, vLo - reachLimit, uHi - uLo + 1 + 2 * reachLimit, vHi - vLo + 1 + 2 * reachLimit);
			final int[] carrier = grid.filled(Integer.MAX_VALUE);
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.COLUMN_CAP || block.role() == Role.BEAM || block.role() == Role.RIB || block.role() == Role.BRIDGE_PIER) {
					final int i = grid.index(block.u(), block.v());
					carrier[i] = Math.min(carrier[i], block.y());
				}
			}
			final int[] reach = grid.spread(carrier, reachLimit, true);
			// a wall standing from the walk level up to a roof's level carries it too (a canopy fixed to a building or a lift
			// shaft): any roof cell within reach and no more than one above the wall's top
			final int[] top = grid.filled(Integer.MIN_VALUE);
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.WALL) {
					final int i = grid.index(block.u(), block.v());
					top[i] = Math.max(top[i], block.y());
				}
			}
			for (int i = 0; i < top.length; i++) {
				if (top[i] != Integer.MIN_VALUE && !wallFromFloor(grid.u(i), grid.v(i), top[i])) {
					top[i] = Integer.MIN_VALUE;
				}
			}
			final int[] wallReach = grid.spread(top, reachLimit, false);
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				final Role role = block.role();
				if (role != Role.ROOF && role != Role.ROOF_EDGE && role != Role.ROOF_LIGHT) {
					continue;
				}
				final int i = grid.index(block.u(), block.v());
				if (reach[i] >= block.y() && wallReach[i] < block.y() - 1) {
					found.add(at(Violation.Kind.ROOF_UNSUPPORTED, block,
							where(block) + " is more than " + reachLimit + " cells from any column top or beam"));
				}
			}
			return found;
		}

		/** A dense u × v grid from (u0, v0), row by row along u. */
		private record Grid(int u0, int v0, int width, int length) {

			int index(int u, int v) {
				return (v - v0) * width + (u - u0);
			}

			int u(int i) {
				return u0 + i % width;
			}

			int v(int i) {
				return v0 + i / width;
			}

			int[] filled(int value) {
				final int[] cells = new int[width * length];
				java.util.Arrays.fill(cells, value);
				return cells;
			}

			/** Each cell's minimum (or maximum) over the square of the given radius round it, as two one-way passes. */
			int[] spread(int[] cells, int radius, boolean min) {
				final int[] along = new int[cells.length];
				for (int v = 0; v < length; v++) {
					for (int u = 0; u < width; u++) {
						int best = cells[v * width + u];
						for (int d = Math.max(0, u - radius); d <= Math.min(width - 1, u + radius); d++) {
							best = min ? Math.min(best, cells[v * width + d]) : Math.max(best, cells[v * width + d]);
						}
						along[v * width + u] = best;
					}
				}
				final int[] out = new int[cells.length];
				for (int v = 0; v < length; v++) {
					for (int u = 0; u < width; u++) {
						int best = along[v * width + u];
						for (int d = Math.max(0, v - radius); d <= Math.min(length - 1, v + radius); d++) {
							best = min ? Math.min(best, along[d * width + u]) : Math.max(best, along[d * width + u]);
						}
						out[v * width + u] = best;
					}
				}
				return out;
			}
		}

		/** True when the wall cells at (u, v) run unbroken from the walk level up to {@code top}. */
		private boolean wallFromFloor(int u, int v, int top) {
			for (int y = StationGeometry.WALK_Y; y <= top; y++) {
				if (!has(u, y, v, Role.WALL)) {
					return false;
				}
			}
			return true;
		}

		List<Violation> roofSpans() {
			// Beam lines: BEAM cells with the same y and v, as u -> block, in u order.
			final Map<Long, TreeMap<Integer, PlannedBlock>> lines = new TreeMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.BEAM && !alongV(block)) {
					lines.computeIfAbsent(((long) block.y() << 32) ^ (block.v() & 0xFFFFFFFFL), k -> new TreeMap<>()).put(block.u(), block);
				}
			}
			final List<Violation> found = new ArrayList<>();
			for (final TreeMap<Integer, PlannedBlock> line : lines.values()) {
				int runStart = Integer.MIN_VALUE;
				int previous = Integer.MIN_VALUE;
				for (final int u : line.keySet()) {
					if (runStart == Integer.MIN_VALUE) {
						runStart = u;
					} else if (u != previous + 1) {
						checkRun(found, line.get(runStart), runStart, previous);
						runStart = u;
					}
					previous = u;
				}
				if (runStart != Integer.MIN_VALUE) {
					checkRun(found, line.get(runStart), runStart, previous);
				}
			}
			found.addAll(longitudinalSpans());
			return found;
		}

		private static boolean alongV(PlannedBlock block) {
			return block.front() == LocalDir.PLUS_V || block.front() == LocalDir.MINUS_V;
		}

		/**
		 * Girders along v (column lines, eaves) rest on cross beams or caps: each run needs a support at an end or a cap under
		 * it, spans at most {@link StationGeometry#MAX_BEAM_SPAN} between supports and overhangs at most
		 * {@link StationGeometry#MAX_CANTILEVER} past its last one.
		 */
		private List<Violation> longitudinalSpans() {
			final Map<Long, TreeMap<Integer, PlannedBlock>> lines = new TreeMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.BEAM && alongV(block)) {
					lines.computeIfAbsent(((long) block.y() << 32) ^ (block.u() & 0xFFFFFFFFL), k -> new TreeMap<>()).put(block.v(), block);
				}
			}
			final List<Violation> found = new ArrayList<>();
			for (final TreeMap<Integer, PlannedBlock> line : lines.values()) {
				final List<int[]> runs = new ArrayList<>();
				for (final int v : line.keySet()) {
					if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == v - 1) {
						runs.get(runs.size() - 1)[1] = v;
					} else {
						runs.add(new int[]{v, v});
					}
				}
				final PlannedBlock any = line.firstEntry().getValue();
				final int u = any.u();
				final int y = any.y();
				for (final int[] run : runs) {
					final boolean before = carriesGirder(u, y, run[0] - 1);
					final boolean after = carriesGirder(u, y, run[1] + 1);
					boolean capped = false;
					for (int v = run[0]; v <= run[1] && !capped; v++) {
						capped = has(u, y - 1, v, Role.COLUMN_CAP);
					}
					final int length = run[1] - run[0] + 1;
					final String where = "girder at u=" + u + " v=" + run[0] + ".." + run[1];
					if (!before && !after && !capped) {
						found.add(new Violation(Violation.Kind.ROOF_SPAN, u, y, run[0], where + " rests on nothing"));
					} else if (before && after && length > StationGeometry.MAX_BEAM_SPAN) {
						found.add(new Violation(Violation.Kind.ROOF_SPAN, u, y, run[0], where + " spans " + length + " cells (max " + StationGeometry.MAX_BEAM_SPAN + ")"));
					} else if (before != after && !capped && length > StationGeometry.MAX_CANTILEVER) {
						found.add(new Violation(Violation.Kind.ROOF_SPAN, u, y, run[0], where + " overhangs " + length + " cells (max " + StationGeometry.MAX_CANTILEVER + ")"));
					}
				}
			}
			return found;
		}

		/** A cross beam, a column cap, a wall or a pier at (u, y, v) that a girder along v can end on. */
		private boolean carriesGirder(int u, int y, int v) {
			final List<PlannedBlock> here = cells.get(new Cell(u, y, v));
			if (here == null) {
				return false;
			}
			for (final PlannedBlock block : here) {
				final Role role = block.role();
				if (role == Role.BEAM && !alongV(block) || role == Role.COLUMN_CAP || role == Role.WALL || role == Role.BRIDGE_PIER) {
					return true;
				}
			}
			return false;
		}

		private void checkRun(List<Violation> found, PlannedBlock first, int uStart, int uEnd) {
			final int y = first.y();
			final int v = first.v();
			final List<Integer> caps = new ArrayList<>();
			// a beam framed into the side of a pier or a wall it meets rests on it
			if (has(uStart - 1, y, v, Role.BRIDGE_PIER) || has(uStart - 1, y, v, Role.WALL)) {
				caps.add(uStart - 1);
			}
			for (int u = uStart; u <= uEnd; u++) {
				if (has(u, y - 1, v, Role.COLUMN_CAP) || has(u, y - 1, v, Role.BRIDGE_PIER)) {
					caps.add(u);
				}
			}
			if (has(uEnd + 1, y, v, Role.BRIDGE_PIER) || has(uEnd + 1, y, v, Role.WALL)) {
				caps.add(uEnd + 1);
			}
			if (caps.isEmpty()) {
				found.add(new Violation(Violation.Kind.ROOF_SPAN, uStart, y, v, "beam at v=" + v + " u=" + uStart + ".." + uEnd + " has no column under it"));
				return;
			}
			if (caps.get(0) - uStart > StationGeometry.MAX_CANTILEVER) {
				found.add(new Violation(Violation.Kind.ROOF_SPAN, uStart, y, v,
						"beam at v=" + v + " overhangs " + (caps.get(0) - uStart) + " cells past its first column (max " + StationGeometry.MAX_CANTILEVER + ")"));
			}
			for (int i = 0; i + 1 < caps.size(); i++) {
				final int gap = caps.get(i + 1) - caps.get(i);
				if (gap > StationGeometry.MAX_BEAM_SPAN) {
					found.add(new Violation(Violation.Kind.ROOF_SPAN, caps.get(i), y, v,
							"beam at v=" + v + " spans " + gap + " cells between columns at u=" + caps.get(i) + " and u=" + caps.get(i + 1) + " (max "
									+ StationGeometry.MAX_BEAM_SPAN + ")"));
				}
			}
			final int lastCap = caps.get(caps.size() - 1);
			if (uEnd - lastCap > StationGeometry.MAX_CANTILEVER) {
				found.add(new Violation(Violation.Kind.ROOF_SPAN, uEnd, y, v,
						"beam at v=" + v + " overhangs " + (uEnd - lastCap) + " cells past its last column (max " + StationGeometry.MAX_CANTILEVER + ")"));
			}
		}

		List<Violation> floatingFixtures() {
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				final Role role = block.role();
				if ((role == Role.LAMP || role == Role.BENCH || role == Role.BIN || role == Role.NAME_SIGN)
						&& !isFlatFloor(block.u(), block.y() - 1, block.v()) && !(role == Role.LAMP && onLampPole(block))) {
					found.add(at(Violation.Kind.FLOATING_FIXTURE, block, where(block) + " does not stand on a flat floor"));
				}
			}
			return found;
		}

		/** Footbridge deck runs must be carried by piers: overhangs of at most 4 and spans of at most 12. */
		List<Violation> deckSpans() {
			final Map<Long, TreeMap<Integer, PlannedBlock>> lines = new TreeMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				// an escalator's top landing lies in the corridor's wall row and carries on its deck
				if (block.role() == Role.BRIDGE_DECK || block.role() == Role.ESCALATOR && block.y() == StationGeometry.UPPER_FLOOR_Y
						&& block.blockId().equals(StylePalette.MTR_ESCALATOR_STEP)) {
					lines.computeIfAbsent(((long) block.y() << 32) ^ (block.v() & 0xFFFFFFFFL), k -> new TreeMap<>()).put(block.u(), block);
				}
			}
			final List<Violation> found = new ArrayList<>();
			for (final Map.Entry<Long, TreeMap<Integer, PlannedBlock>> entry : lines.entrySet()) {
				final TreeMap<Integer, PlannedBlock> line = entry.getValue();
				// a real station's hall floor is one slab over the whole site: an opening in it (a flight, a lift) is framed by
				// the rows either side, so each row is carried from wall to wall
				if (plan.spec().real().hallOverTracks()) {
					checkDeckRun(found, line.firstEntry().getValue(), line.firstKey(), line.lastKey());
					continue;
				}
				int runStart = Integer.MIN_VALUE;
				int previous = Integer.MIN_VALUE;
				for (final int u : line.keySet()) {
					if (runStart == Integer.MIN_VALUE) {
						runStart = u;
					} else if (u != previous + 1) {
						checkDeckRun(found, line.get(runStart), runStart, previous);
						runStart = u;
					}
					previous = u;
				}
				if (runStart != Integer.MIN_VALUE) {
					checkDeckRun(found, line.get(runStart), runStart, previous);
				}
			}
			return found;
		}

		private void checkDeckRun(List<Violation> found, PlannedBlock first, int uStart, int uEnd) {
			final int y = first.y();
			final int v = first.v();
			// a pier carries the whole corridor slab across it: its own row and up to the corridor's half-width either side
			final int reach = StationGeometry.bridgeWallOffset(StationGeometry.bridgeWidth(plan.spec(), layout));
			final List<Integer> piers = new ArrayList<>();
			for (int u = uStart; u <= uEnd; u++) {
				// a wall standing under the slab (a stairwell's side) carries it where it stands
				if (has(u, y - 1, v, Role.WALL)) {
					piers.add(u);
					continue;
				}
				for (int dv = -reach; dv <= reach; dv++) {
					if (has(u, y - 1, v + dv, Role.BRIDGE_PIER)) {
						piers.add(u);
						break;
					}
				}
			}
			// the building wall and slab carry the deck where they meet (same row or next to it)
			for (int dv = -reach; dv <= reach; dv++) {
				if (has(uStart - 1, y, v + dv, Role.FLOOR) || has(uStart - 1, y, v + dv, Role.LIGHT_PANEL) || has(uStart - 1, y, v + dv, Role.WALL)
						|| has(uStart - 1, y, v + dv, Role.BRIDGE_PIER) || has(uStart - 1, y - 1, v + dv, Role.BRIDGE_PIER)) {
					piers.add(0, uStart - 1);
					break;
				}
			}
			// and a wall rising through the slab just past its end (a lift shaft beside a real station's floor) carries that end
			if (has(uEnd + 1, y, v, Role.WALL) && has(uEnd + 1, y - 1, v, Role.WALL)) {
				piers.add(uEnd + 1);
			}
			if (piers.isEmpty()) {
				found.add(new Violation(Violation.Kind.ROOF_SPAN, uStart, y, v,
						"footbridge deck at v=" + v + " u=" + uStart + ".." + uEnd + " has no pier under it"));
				return;
			}
			if (piers.get(0) - uStart > StationGeometry.MAX_CANTILEVER) {
				found.add(new Violation(Violation.Kind.ROOF_SPAN, uStart, y, v,
						"footbridge deck at v=" + v + " overhangs " + (piers.get(0) - uStart) + " cells past its first pier (max "
								+ StationGeometry.MAX_CANTILEVER + ")"));
			}
			for (int i = 0; i + 1 < piers.size(); i++) {
				final int gap = piers.get(i + 1) - piers.get(i);
				if (gap > StationGeometry.MAX_BEAM_SPAN) {
					found.add(new Violation(Violation.Kind.ROOF_SPAN, piers.get(i), y, v,
							"footbridge deck at v=" + v + " spans " + gap + " cells between piers at u=" + piers.get(i) + " and u=" + piers.get(i + 1)
									+ " (max " + StationGeometry.MAX_BEAM_SPAN + ")"));
				}
			}
			final int lastPier = piers.get(piers.size() - 1);
			if (uEnd - lastPier > StationGeometry.MAX_CANTILEVER) {
				found.add(new Violation(Violation.Kind.ROOF_SPAN, uEnd, y, v,
						"footbridge deck at v=" + v + " overhangs " + (uEnd - lastPier) + " cells past its last pier (max " + StationGeometry.MAX_CANTILEVER
								+ ")"));
			}
		}
	}
}
