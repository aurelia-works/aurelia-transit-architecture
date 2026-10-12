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

	/** Highest y a plan may use; the lowest is -1 (the support layer). */
	private static final int MAX_Y = 64;
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
			for (final PlannedBlock block : plan.blocks()) {
				if (block.u() < 0 || block.u() >= layout.width() || block.v() < layout.vMin() || block.v() > layout.vMax() || block.y() < MIN_Y
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
			for (int v = 0; v < layout.platformLength(); v++) {
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
				if (block.role().roofPart() && block.y() < StationGeometry.CANOPY_Y
						&& isPlatformFloor(block.u(), StationGeometry.PLATFORM_Y, block.v())) {
					found.add(at(Violation.Kind.HEADROOM, block,
							where(block) + " is below the headroom limit y=" + StationGeometry.CANOPY_Y + " over a platform"));
				}
			}
			return found;
		}

		List<Violation> edgeClearway() {
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role().obstructsWalkway() && block.y() >= StationGeometry.WALK_Y
						&& block.y() <= StationGeometry.WALK_Y + StationGeometry.HEADROOM - 1
						&& has(block.u(), StationGeometry.PLATFORM_Y, block.v(), Role.PLATFORM_EDGE)) {
					found.add(at(Violation.Kind.EDGE_CLEARWAY, block, where(block) + " stands on a platform edge"));
				}
			}
			return found;
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

		/** Null if the column stands on a platform floor, runs unbroken from y = 1 to one cap and has a roof part above. */
		private String columnProblem(Column column, List<PlannedBlock> stack) {
			if (!isPlatformFloor(column.u(), StationGeometry.PLATFORM_Y, column.v())) {
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
					if (block.role().roofPart()) {
						return null;
					}
				}
			}
			return "column at u=" + column.u() + " v=" + column.v() + " has no roof above its cap";
		}

		List<Violation> unsupportedRoof() {
			// For every (u, v) within the cantilever of a support point: the lowest carrier (cap, beam or bridge pier) y that reaches it.
			final Map<Long, Integer> reach = new HashMap<>();
			final int reachLimit = StationGeometry.MAX_CANTILEVER;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() != Role.COLUMN_CAP && block.role() != Role.BEAM && block.role() != Role.RIB && block.role() != Role.BRIDGE_PIER) {
					continue;
				}
				for (int du = -reachLimit; du <= reachLimit; du++) {
					for (int dv = -reachLimit; dv <= reachLimit; dv++) {
						reach.merge(key(block.u() + du, block.v() + dv), block.y(), Math::min);
					}
				}
			}
			final List<Violation> found = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				final Role role = block.role();
				if (role != Role.ROOF && role != Role.ROOF_EDGE && role != Role.ROOF_LIGHT) {
					continue;
				}
				final Integer carrierY = reach.get(key(block.u(), block.v()));
				if (carrierY == null || carrierY >= block.y()) {
					found.add(at(Violation.Kind.ROOF_UNSUPPORTED, block,
							where(block) + " is more than " + reachLimit + " cells from any column top or beam"));
				}
			}
			return found;
		}

		private static long key(int u, int v) {
			return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
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
			for (int u = uStart; u <= uEnd; u++) {
				if (has(u, y - 1, v, Role.COLUMN_CAP) || has(u, y - 1, v, Role.BRIDGE_PIER)) {
					caps.add(u);
				}
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
						&& !isFlatFloor(block.u(), block.y() - 1, block.v())) {
					found.add(at(Violation.Kind.FLOATING_FIXTURE, block, where(block) + " does not stand on a flat floor"));
				}
			}
			return found;
		}

		/** Footbridge deck runs must be carried by piers: overhangs of at most 4 and spans of at most 12. */
		List<Violation> deckSpans() {
			final Map<Long, TreeMap<Integer, PlannedBlock>> lines = new TreeMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.BRIDGE_DECK) {
					lines.computeIfAbsent(((long) block.y() << 32) ^ (block.v() & 0xFFFFFFFFL), k -> new TreeMap<>()).put(block.u(), block);
				}
			}
			final List<Violation> found = new ArrayList<>();
			for (final Map.Entry<Long, TreeMap<Integer, PlannedBlock>> entry : lines.entrySet()) {
				final TreeMap<Integer, PlannedBlock> line = entry.getValue();
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
			final List<Integer> piers = new ArrayList<>();
			for (int u = uStart; u <= uEnd; u++) {
				// a pier carries the whole corridor slab across it: its own row and up to two rows either side
				for (int dv = -2; dv <= 2; dv++) {
					if (has(u, y - 1, v + dv, Role.BRIDGE_PIER)) {
						piers.add(u);
						break;
					}
				}
			}
			// the building wall and slab carry the deck where they meet (same row or next to it)
			for (int dv = -2; dv <= 2; dv++) {
				if (has(uStart - 1, y, v + dv, Role.FLOOR) || has(uStart - 1, y, v + dv, Role.LIGHT_PANEL) || has(uStart - 1, y, v + dv, Role.WALL)
						|| has(uStart - 1, y, v + dv, Role.BRIDGE_PIER) || has(uStart - 1, y - 1, v + dv, Role.BRIDGE_PIER)) {
					piers.add(0, uStart - 1);
					break;
				}
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
