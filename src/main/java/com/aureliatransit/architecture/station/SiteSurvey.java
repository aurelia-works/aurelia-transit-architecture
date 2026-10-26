package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a validated plan against the world and lists the changes a build would make. Terrain is preserved unless
 * {@code replaceTerrain} is set, which only an explicit confirmation token sets (never the description).
 *
 * <p>Rules, all in world coordinates through the frame:
 * <ol>
 *     <li>Planned cell: AIR is placed. SOFT, SOLID or FLUID is an {@link SiteIssue.Kind#OCCUPIED} issue; with approval the
 *     PLACE op replaces it (a terrain change). PROTECTED, UNLOADED or OUT_OF_WORLD is a hard issue. An existing MTR rail
 *     node ({@link CellKind#RAIL_NODE}) in a planned rail node cell is kept: no issue and no op, so a station can be planned
 *     onto a line that is already connected. In any other planned cell it is a hard {@link SiteIssue.Kind#PROTECTED} issue.</li>
 *     <li>Track envelope and headroom cells that are not planned cells: SOLID or FLUID is {@link SiteIssue.Kind#OBSTRUCTED};
 *     with approval a CLEAR op. AIR, SOFT and RAIL_NODE are left alone. PROTECTED, UNLOADED or OUT_OF_WORLD is a hard issue.</li>
 *     <li>Support cells (y = -1): sturdy top is fine. Otherwise look down for the first sturdy cell within
 *     {@link #MAX_FILL_DEPTH} cells (y = -1 down to y = -MAX_FILL_DEPTH). Found: {@link SiteIssue.Kind#UNSUPPORTED}, and with
 *     approval FILL ops (foundation) for every cell from y = -1 down to just above the sturdy one. Not found:
 *     {@link SiteIssue.Kind#GAP_TOO_DEEP} (hard). A PROTECTED, RAIL_NODE, UNLOADED or OUT_OF_WORLD cell in a support cell or
 *     fill column is a hard issue.</li>
 * </ol>
 * Each cell is checked once; the result is deterministic for the same plan, frame and world.
 *
 * <p>Scan order (issues are listed in it): planned blocks in plan order; then every track envelope, then every headroom box
 * (cells in order v, u, y ascending); then the support boxes. Support cells use the same order, one column at a time.
 * The sturdy cell is looked for at y = -2 down to y = -(MAX_FILL_DEPTH + 1), so a fill column has 1..MAX_FILL_DEPTH cells.
 * Fill cells are worked out even without approval, because {@link SurveyReport#terrainChanges()} counts them.
 */
public final class SiteSurvey {

	/** Deepest foundation fill below a support cell. */
	public static final int MAX_FILL_DEPTH = 4;
	/** Issues listed individually in a report (all are counted). */
	public static final int MAX_LISTED = 64;

	private SiteSurvey() {
	}

	public static SurveyReport survey(StationPlan plan, LocalFrame frame, SiteProbe probe, boolean replaceTerrain) {
		final Survey survey = start(plan, frame, probe, replaceTerrain);
		survey.step(Long.MAX_VALUE);
		return survey.report();
	}

	/**
	 * A survey walked a piece at a time ({@link Survey#step}), so a site too big for one tick is read over several and the
	 * chunks it loads can unload between them. Same order, issues and ops as {@link #survey}.
	 */
	public static Survey start(StationPlan plan, LocalFrame frame, SiteProbe probe, boolean replaceTerrain) {
		return new Survey(new Run(plan, frame, probe, replaceTerrain));
	}

	/** A survey in progress; see {@link #start}. */
	public static final class Survey {

		private final Run run;
		private final List<Box> clearance = new ArrayList<>();
		private final List<Box> support;
		/** 0: planned blocks, 1: clearance boxes, 2: support boxes, 3: done. */
		private int phase;
		/** Planned block index in phase 0, box index in phases 1 and 2. */
		private int index;
		/** Column within the current box, counted v-major then u, as the scan order says. */
		private int column;

		private Survey(Run run) {
			this.run = run;
			clearance.addAll(run.plan.trackEnvelopes());
			clearance.addAll(run.plan.headroom());
			support = run.plan.supportAreas();
			run.markPlanned();
		}

		/** Reads cells until the survey is done or {@code deadline} ({@link System#nanoTime}) has passed; true when done. */
		public boolean step(long deadline) {
			while (phase < 3) {
				if (phase == 0) {
					if (index < run.plan.blocks().size()) {
						run.checkPlanned(run.plan.blocks().get(index++));
					} else {
						next();
					}
				} else {
					final List<Box> boxes = phase == 1 ? clearance : support;
					if (index >= boxes.size()) {
						next();
						continue;
					}
					final Box box = boxes.get(index);
					final int width = box.uMax() - box.uMin() + 1;
					final int depth = box.vMax() - box.vMin() + 1;
					final int columns = width > 0 && depth > 0 ? width * depth : 0;
					if (column >= columns) {
						index++;
						column = 0;
						continue;
					}
					final int u = box.uMin() + column % width;
					final int v = box.vMin() + column / width;
					column++;
					if (phase == 1) {
						run.checkClearance(box, u, v);
					} else {
						run.checkSupportColumn(box, u, v);
					}
				}
				if (System.nanoTime() >= deadline) {
					return phase == 3;
				}
			}
			return true;
		}

		private void next() {
			phase++;
			index = 0;
			column = 0;
		}

		public boolean done() {
			return phase == 3;
		}

		/** @throws IllegalStateException before the survey is done */
		public SurveyReport report() {
			if (!done()) {
				throw new IllegalStateException("survey not finished");
			}
			return run.report();
		}
	}

	/** One survey: the inputs, the cells already checked and everything found so far. */
	private static final class Run {

		private static final int KEY_OFFSET = 1 << 19;

		private final StationPlan plan;
		private final LocalFrame frame;
		private final SiteProbe probe;
		private final boolean replaceTerrain;
		private final Set<Long> visited = new HashSet<>();
		private final List<SiteIssue> issues = new ArrayList<>();
		private final Map<SiteIssue.Kind, Integer> counts = new EnumMap<>(SiteIssue.Kind.class);
		private final List<WorldOp> clears = new ArrayList<>();
		private final List<WorldOp> fills = new ArrayList<>();
		/** Planned rail node cells that already hold a node: kept, so no PLACE op. */
		private final Set<PlannedBlock> kept = new HashSet<>();
		private int terrainChanges;

		Run(StationPlan plan, LocalFrame frame, SiteProbe probe, boolean replaceTerrain) {
			this.plan = plan;
			this.frame = frame;
			this.probe = probe;
			this.replaceTerrain = replaceTerrain;
		}

		/** Marks a local cell as checked; false when it already was. */
		private boolean first(int u, int y, int v) {
			final long key = ((long) (u + KEY_OFFSET) << 40) | ((long) (y + KEY_OFFSET) << 20) | (long) (v + KEY_OFFSET);
			return visited.add(key);
		}

		private void issue(SiteIssue.Kind kind, int x, int y, int z) {
			counts.merge(kind, 1, Integer::sum);
			if (issues.size() < MAX_LISTED) {
				issues.add(new SiteIssue(kind, x, y, z, probe.describe(x, y, z)));
			}
		}

		/** The hard issue kind for a cell kind that is never touched, or null for a cell that may be changed. */
		private static SiteIssue.Kind hard(CellKind kind) {
			return switch (kind) {
				case PROTECTED, RAIL_NODE -> SiteIssue.Kind.PROTECTED;
				case UNLOADED -> SiteIssue.Kind.UNLOADED;
				case OUT_OF_WORLD -> SiteIssue.Kind.OUT_OF_WORLD;
				default -> null;
			};
		}

		void markPlanned() {
			for (final PlannedBlock block : plan.blocks()) {
				first(block.u(), block.y(), block.v());
			}
		}

		void checkPlanned(PlannedBlock block) {
			final int x = frame.x(block.u(), block.v());
			final int y = frame.y(block.y());
			final int z = frame.z(block.u(), block.v());
			final CellKind kind = probe.kind(x, y, z);
			final SiteIssue.Kind hard = hard(kind);
			if (kind == CellKind.RAIL_NODE && block.role() == Role.RAIL_NODE) {
				kept.add(block);
			} else if (hard != null) {
				issue(hard, x, y, z);
			} else if (kind != CellKind.AIR) {
				issue(SiteIssue.Kind.OCCUPIED, x, y, z);
				terrainChanges++;
			}
		}

		/** One column (u, v) of a track envelope or headroom box, y ascending. */
		void checkClearance(Box box, int u, int v) {
			for (int y = box.yMin(); y <= box.yMax(); y++) {
				if (!first(u, y, v)) {
					continue;
				}
				final int x = frame.x(u, v);
				final int wy = frame.y(y);
				final int z = frame.z(u, v);
				final CellKind kind = probe.kind(x, wy, z);
				final SiteIssue.Kind hard = hard(kind);
				if (kind == CellKind.RAIL_NODE) {
					continue;
				}
				if (hard != null) {
					issue(hard, x, wy, z);
				} else if (kind == CellKind.SOLID || kind == CellKind.FLUID) {
					issue(SiteIssue.Kind.OBSTRUCTED, x, wy, z);
					terrainChanges++;
					clears.add(new WorldOp(WorldOp.Kind.CLEAR, x, wy, z, null, WorldOp.AIR, frame.forward(), ""));
				}
			}
		}

		/** One column (u, v) of a support box, y ascending. */
		void checkSupportColumn(Box box, int u, int v) {
			for (int y = box.yMin(); y <= box.yMax(); y++) {
				if (first(u, y, v)) {
					checkSupport(u, y, v);
				}
			}
		}

		/** One support cell: its own kind, then the first sturdy cell below it, then the fill column in between. */
		private void checkSupport(int u, int y, int v) {
			final int x = frame.x(u, v);
			final int z = frame.z(u, v);
			final int wy = frame.y(y);
			final SiteIssue.Kind own = hard(probe.kind(x, wy, z));
			if (own != null) {
				issue(own, x, wy, z);
				return;
			}
			if (probe.sturdyTop(x, wy, z)) {
				return;
			}
			int sturdy = Integer.MIN_VALUE;
			for (int depth = 1; depth <= MAX_FILL_DEPTH; depth++) {
				if (probe.sturdyTop(x, wy - depth, z)) {
					sturdy = wy - depth;
					break;
				}
			}
			if (sturdy == Integer.MIN_VALUE) {
				issue(SiteIssue.Kind.GAP_TOO_DEEP, x, wy, z);
				return;
			}
			// Fill cells wy down to sturdy + 1, top-down. The support cell itself was already checked above.
			int changes = 0;
			final List<WorldOp> column = new ArrayList<>();
			for (int fillY = wy; fillY > sturdy; fillY--) {
				final CellKind kind = probe.kind(x, fillY, z);
				final SiteIssue.Kind hard = hard(kind);
				if (hard != null) {
					issue(hard, x, fillY, z);
					return;
				}
				if (kind != CellKind.AIR) {
					changes++;
				}
				column.add(new WorldOp(WorldOp.Kind.FILL, x, fillY, z, Role.FOUNDATION, plan.foundationId(), frame.forward(), ""));
			}
			issue(SiteIssue.Kind.UNSUPPORTED, x, wy, z);
			terrainChanges += changes;
			fills.addAll(column);
		}

		SurveyReport report() {
			final List<WorldOp> ops = new ArrayList<>();
			if (replaceTerrain) {
				clears.sort(Comparator.comparingInt(WorldOp::y).reversed());
				fills.sort(Comparator.comparingInt(WorldOp::y));
				ops.addAll(clears);
				ops.addAll(fills);
			}
			for (final PlannedBlock block : plan.blocks()) {
				if (kept.contains(block)) {
					continue;
				}
				ops.add(new WorldOp(WorldOp.Kind.PLACE, frame.x(block.u(), block.v()), frame.y(block.y()), frame.z(block.u(), block.v()),
						block.role(), block.blockId(), frame.heading(block.front()), block.text(), block.state()));
			}
			return new SurveyReport(issues, counts, ops, terrainChanges, replaceTerrain);
		}
	}
}
