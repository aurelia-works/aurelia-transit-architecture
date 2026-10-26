package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteSurveyTest {

	private static final String SURFACE = "aurelia_transit_architecture:platform_surface";
	private static final String EDGE = "aurelia_transit_architecture:platform_edge";
	private static final String NODE = "mtr:rail_node";
	private static final String BENCH = "aurelia_transit_architecture:bench";
	private static final String SIGN = "aurelia_transit_architecture:name_sign";
	private static final String FOUNDATION = "minecraft:stone_bricks";

	/** Rail level is world y 64, so the support layer is y 63 and the default ground (stone) tops out there. */
	private static final LocalFrame NORTH = new LocalFrame(100, 64, 200, Heading.NORTH);

	// ---- fake world --------------------------------------------------------------------------------------------------------

	private record Pos(int x, int y, int z) {
	}

	private record Cell(CellKind kind, boolean sturdy, String name) {
	}

	/** A grid world: stone up to groundY, air above, with per-cell overrides, height limits and one unloaded column range. */
	private static final class FakeProbe implements SiteProbe {

		private final Map<Pos, Cell> cells = new HashMap<>();
		private final int groundY;
		private int minY = -64;
		private int maxY = 319;
		private int unloadedXMin = 1;
		private int unloadedXMax = 0;

		FakeProbe(int groundY) {
			this.groundY = groundY;
		}

		FakeProbe limits(int min, int max) {
			minY = min;
			maxY = max;
			return this;
		}

		FakeProbe unloadedColumns(int xMin, int xMax) {
			unloadedXMin = xMin;
			unloadedXMax = xMax;
			return this;
		}

		FakeProbe put(int x, int y, int z, CellKind kind, boolean sturdy, String name) {
			cells.put(new Pos(x, y, z), new Cell(kind, sturdy, name));
			return this;
		}

		FakeProbe grass(int x, int y, int z) {
			return put(x, y, z, CellKind.SOFT, false, "minecraft:grass");
		}

		FakeProbe log(int x, int y, int z) {
			return put(x, y, z, CellKind.SOLID, false, "minecraft:oak_log");
		}

		FakeProbe water(int x, int y, int z) {
			return put(x, y, z, CellKind.FLUID, false, "minecraft:water");
		}

		FakeProbe chest(int x, int y, int z) {
			return put(x, y, z, CellKind.PROTECTED, false, "minecraft:chest");
		}

		FakeProbe node(int x, int y, int z) {
			return put(x, y, z, CellKind.RAIL_NODE, false, "mtr:rail");
		}

		FakeProbe slab(int x, int y, int z) {
			return put(x, y, z, CellKind.SOLID, false, "minecraft:stone_slab");
		}

		/** Replaces {@code depth} cells of ground with air, from topY downwards. */
		FakeProbe hole(int x, int topY, int z, int depth) {
			for (int i = 0; i < depth; i++) {
				put(x, topY - i, z, CellKind.AIR, false, "minecraft:air");
			}
			return this;
		}

		private boolean outOfWorld(int y) {
			return y < minY || y > maxY;
		}

		private boolean unloaded(int x) {
			return x >= unloadedXMin && x <= unloadedXMax;
		}

		@Override
		public CellKind kind(int x, int y, int z) {
			if (outOfWorld(y)) {
				return CellKind.OUT_OF_WORLD;
			}
			if (unloaded(x)) {
				return CellKind.UNLOADED;
			}
			final Cell cell = cells.get(new Pos(x, y, z));
			if (cell != null) {
				return cell.kind();
			}
			return y <= groundY ? CellKind.SOLID : CellKind.AIR;
		}

		@Override
		public boolean sturdyTop(int x, int y, int z) {
			if (outOfWorld(y) || unloaded(x)) {
				return false;
			}
			final Cell cell = cells.get(new Pos(x, y, z));
			if (cell != null) {
				return cell.sturdy();
			}
			return y <= groundY;
		}

		@Override
		public String describe(int x, int y, int z) {
			if (outOfWorld(y)) {
				return "out of world";
			}
			final Cell cell = cells.get(new Pos(x, y, z));
			if (cell != null) {
				return cell.name();
			}
			return y <= groundY ? "minecraft:stone" : "minecraft:air";
		}
	}

	// ---- hand-built plan ---------------------------------------------------------------------------------------------------

	/**
	 * A 5 x 4 mini station: platform surface u = 0 and edge u = 1 (v = 0..3), a track at u = 3 with rail nodes at both ends, a bench
	 * and a name sign on the platform. Envelope u = 2..4, headroom over the platform, supports under the platform and the track.
	 */
	private static StationPlan miniPlan() {
		final List<PlannedBlock> blocks = new ArrayList<>();
		for (int v = 0; v < 4; v++) {
			blocks.add(new PlannedBlock(0, 0, v, Role.PLATFORM_SURFACE, SURFACE, LocalDir.PLUS_V, ""));
		}
		for (int v = 0; v < 4; v++) {
			blocks.add(new PlannedBlock(1, 0, v, Role.PLATFORM_EDGE, EDGE, LocalDir.PLUS_U, ""));
		}
		blocks.add(new PlannedBlock(3, 0, 0, Role.RAIL_NODE, NODE, LocalDir.PLUS_V, ""));
		blocks.add(new PlannedBlock(3, 0, 3, Role.RAIL_NODE, NODE, LocalDir.MINUS_V, ""));
		blocks.add(new PlannedBlock(0, 1, 1, Role.BENCH, BENCH, LocalDir.PLUS_U, ""));
		blocks.add(new PlannedBlock(0, 1, 2, Role.NAME_SIGN, SIGN, LocalDir.PLUS_U, "Test"));
		return plan(blocks, List.of(new Box(2, 0, 0, 4, 4, 3)), List.of(new Box(0, 1, 0, 1, 3, 3)),
				List.of(new Box(0, -1, 0, 1, -1, 3), new Box(3, -1, 0, 3, -1, 3)));
	}

	private static StationPlan plan(List<PlannedBlock> blocks, List<Box> envelopes, List<Box> headroom, List<Box> supports) {
		final TrackLayout layout = new TrackLayout(5, 4, 0, List.of(new TrackLayout.Track(1, 3, true, false)),
				List.of(new TrackLayout.PlatformBand(1, 0, 1, false, true)));
		return new StationPlan(StationSpec.defaults(), layout, blocks, envelopes, headroom, supports, FOUNDATION);
	}

	private static StationPlan miniPlanWithBoxes(List<Box> envelopes, List<Box> headroom, List<Box> supports) {
		return plan(miniPlan().blocks(), envelopes, headroom, supports);
	}

	/** World x of a local cell in the north-facing frame (east is +u, north is +v). */
	private static int wx(int u) {
		return NORTH.x(u, 0);
	}

	private static int wz(int v) {
		return NORTH.z(0, v);
	}

	private static int wy(int y) {
		return NORTH.y(y);
	}

	private static FakeProbe flat() {
		return new FakeProbe(63);
	}

	private static SurveyReport survey(FakeProbe probe, boolean approved) {
		return SiteSurvey.survey(miniPlan(), NORTH, probe, approved);
	}

	private static long count(SurveyReport report, WorldOp.Kind kind) {
		return report.ops().stream().filter(op -> op.kind() == kind).count();
	}

	private static void assertPlaceOpsFollowPlan(StationPlan plan, LocalFrame frame, List<WorldOp> ops) {
		assertEquals(plan.blocks().size(), ops.size());
		for (int i = 0; i < ops.size(); i++) {
			final PlannedBlock block = plan.blocks().get(i);
			final WorldOp op = ops.get(i);
			assertEquals(WorldOp.Kind.PLACE, op.kind());
			assertEquals(frame.x(block.u(), block.v()), op.x());
			assertEquals(frame.y(block.y()), op.y());
			assertEquals(frame.z(block.u(), block.v()), op.z());
			assertEquals(block.role(), op.role());
			assertEquals(block.blockId(), op.blockId());
			assertEquals(frame.heading(block.front()), op.facing());
			assertEquals(block.text(), op.text());
		}
	}

	// ---- clean site --------------------------------------------------------------------------------------------------------

	@Test
	void flatGroundHasNoIssuesAndOnlyPlaceOps() {
		for (final boolean approved : new boolean[] {false, true}) {
			final SurveyReport report = survey(flat(), approved);
			assertTrue(report.issues().isEmpty());
			assertTrue(report.counts().isEmpty());
			assertEquals(0, report.terrainChanges());
			assertEquals(approved, report.replaceTerrain());
			assertTrue(report.buildable());
			assertFalse(report.hasHardIssue());
			assertEquals(12, report.placeCount());
			assertPlaceOpsFollowPlan(miniPlan(), NORTH, report.ops());
		}
	}

	@Test
	void placeOpsCarryRoleFrontAndText() {
		final SurveyReport report = survey(flat(), false);
		final WorldOp sign = report.ops().get(11);
		assertEquals(Role.NAME_SIGN, sign.role());
		assertEquals("Test", sign.text());
		assertEquals(SIGN, sign.blockId());
		// north-facing frame: +u is east, +v is north
		assertEquals(Heading.EAST, sign.facing());
		assertEquals(new Pos(100, 65, 198), new Pos(sign.x(), sign.y(), sign.z()));
		assertEquals(Heading.NORTH, report.ops().get(0).facing());
		assertEquals(Heading.SOUTH, report.ops().get(9).facing());
	}

	// ---- planned cells -----------------------------------------------------------------------------------------------------

	@Test
	void grassInAPlannedCellIsOccupiedAndNeedsApproval() {
		final FakeProbe probe = flat().grass(wx(0), wy(0), wz(1));
		final SurveyReport refused = survey(probe, false);
		assertEquals(1, refused.count(SiteIssue.Kind.OCCUPIED));
		assertEquals(1, refused.issues().size());
		final SiteIssue issue = refused.issues().get(0);
		assertEquals(SiteIssue.Kind.OCCUPIED, issue.kind());
		assertEquals(new Pos(100, 64, 199), new Pos(issue.x(), issue.y(), issue.z()));
		assertEquals("minecraft:grass", issue.block());
		assertFalse(refused.buildable());
		assertEquals(1, refused.terrainChanges(), "counted even before the player approves");
		assertPlaceOpsFollowPlan(miniPlan(), NORTH, refused.ops());

		final SurveyReport approved = survey(probe, true);
		assertTrue(approved.buildable());
		assertEquals(1, approved.terrainChanges());
		assertPlaceOpsFollowPlan(miniPlan(), NORTH, approved.ops());
	}

	@Test
	void solidAndFluidInPlannedCellsAreOccupiedToo() {
		final FakeProbe probe = flat().log(wx(0), wy(0), wz(0)).water(wx(1), wy(0), wz(3));
		final SurveyReport report = survey(probe, true);
		assertEquals(2, report.count(SiteIssue.Kind.OCCUPIED));
		assertEquals(2, report.terrainChanges());
		assertEquals(0, count(report, WorldOp.Kind.CLEAR));
		assertTrue(report.buildable());
	}

	@Test
	void plannedCellsInsideAnEnvelopeAreJudgedOnlyOnce() {
		// the rail node cell lies in the track envelope but is a planned cell: OCCUPIED, never also OBSTRUCTED, no CLEAR op
		final FakeProbe probe = flat().log(wx(3), wy(0), wz(0));
		final SurveyReport report = survey(probe, true);
		assertEquals(1, report.count(SiteIssue.Kind.OCCUPIED));
		assertEquals(0, report.count(SiteIssue.Kind.OBSTRUCTED));
		assertEquals(1, report.issues().size());
		assertEquals(0, count(report, WorldOp.Kind.CLEAR));
		assertEquals(1, report.terrainChanges());
	}

	@Test
	void protectedBlockInAPlannedCellIsHardEvenWithApproval() {
		final FakeProbe probe = flat().chest(wx(0), wy(0), wz(2));
		for (final boolean approved : new boolean[] {false, true}) {
			final SurveyReport report = survey(probe, approved);
			assertEquals(1, report.count(SiteIssue.Kind.PROTECTED));
			assertEquals("minecraft:chest", report.issues().get(0).block());
			assertTrue(report.hasHardIssue());
			assertFalse(report.buildable());
			assertEquals(0, report.terrainChanges());
			assertEquals(12, report.placeCount(), "the ops are still listed; the caller checks buildable()");
		}
	}

	// ---- envelope and headroom ---------------------------------------------------------------------------------------------

	@Test
	void logInATrackEnvelopeIsObstructedAndClearedTopDownWhenApproved() {
		final FakeProbe probe = flat().log(wx(2), wy(1), wz(1)).log(wx(2), wy(3), wz(1)).log(wx(4), wy(1), wz(2));
		final SurveyReport refused = survey(probe, false);
		assertEquals(3, refused.count(SiteIssue.Kind.OBSTRUCTED));
		assertFalse(refused.buildable());
		assertEquals(3, refused.terrainChanges());
		assertEquals(0, count(refused, WorldOp.Kind.CLEAR), "no CLEAR op without approval");
		assertPlaceOpsFollowPlan(miniPlan(), NORTH, refused.ops());

		final SurveyReport approved = survey(probe, true);
		assertTrue(approved.buildable());
		assertEquals(3, count(approved, WorldOp.Kind.CLEAR));
		assertEquals(15, approved.ops().size());
		// y descending; the two y = 65 cells keep scan order (v = 1 before v = 2)
		final List<WorldOp> ops = approved.ops();
		assertEquals(new Pos(102, 67, 199), new Pos(ops.get(0).x(), ops.get(0).y(), ops.get(0).z()));
		assertEquals(new Pos(102, 65, 199), new Pos(ops.get(1).x(), ops.get(1).y(), ops.get(1).z()));
		assertEquals(new Pos(104, 65, 198), new Pos(ops.get(2).x(), ops.get(2).y(), ops.get(2).z()));
		for (int i = 0; i < 3; i++) {
			final WorldOp clear = ops.get(i);
			assertEquals(WorldOp.Kind.CLEAR, clear.kind());
			assertNull(clear.role());
			assertEquals(WorldOp.AIR, clear.blockId());
			assertEquals(Heading.NORTH, clear.facing());
			assertEquals("", clear.text());
		}
		assertEquals(WorldOp.Kind.PLACE, ops.get(3).kind(), "CLEAR ops come before every PLACE op");
		assertPlaceOpsFollowPlan(miniPlan(), NORTH, ops.subList(3, 15));
	}

	@Test
	void softPlantInAnEnvelopeOrHeadroomIsIgnored() {
		final FakeProbe probe = flat().grass(wx(2), wy(2), wz(1)).grass(wx(1), wy(2), wz(2)).put(wx(4), wy(4), wz(3), CellKind.SOFT, false,
				"minecraft:poppy");
		final SurveyReport report = survey(probe, true);
		assertTrue(report.issues().isEmpty());
		assertEquals(0, report.terrainChanges());
		assertEquals(12, report.ops().size());
		assertTrue(report.buildable());
	}

	@Test
	void waterInHeadroomIsObstructed() {
		final FakeProbe probe = flat().water(wx(1), wy(2), wz(2));
		final SurveyReport refused = survey(probe, false);
		assertEquals(1, refused.count(SiteIssue.Kind.OBSTRUCTED));
		assertEquals("minecraft:water", refused.issues().get(0).block());
		assertFalse(refused.buildable());
		final SurveyReport approved = survey(probe, true);
		assertTrue(approved.buildable());
		assertEquals(1, count(approved, WorldOp.Kind.CLEAR));
		assertEquals(1, approved.terrainChanges());
	}

	@Test
	void protectedBlockInAnEnvelopeIsHard() {
		final FakeProbe probe = flat().chest(wx(4), wy(2), wz(0));
		final SurveyReport report = survey(probe, true);
		assertEquals(1, report.count(SiteIssue.Kind.PROTECTED));
		assertFalse(report.buildable());
		assertEquals(0, count(report, WorldOp.Kind.CLEAR));
	}

	@Test
	void overlappingBoxesCheckEachCellOnce() {
		// the second headroom box overlaps the first (u = 1) and the envelope (u = 2)
		final StationPlan plan = miniPlanWithBoxes(List.of(new Box(2, 0, 0, 4, 4, 3)),
				List.of(new Box(0, 1, 0, 1, 3, 3), new Box(1, 1, 0, 2, 3, 3)), List.of(new Box(0, -1, 0, 1, -1, 3), new Box(0, -1, 0, 1, -1, 3)));
		final FakeProbe probe = flat().log(wx(1), wy(2), wz(1)).log(wx(2), wy(2), wz(1)).hole(wx(0), wy(-1), wz(0), 1);
		final SurveyReport report = SiteSurvey.survey(plan, NORTH, probe, true);
		assertEquals(2, report.count(SiteIssue.Kind.OBSTRUCTED));
		assertEquals(2, report.terrainChanges());
		assertEquals(1, report.count(SiteIssue.Kind.UNSUPPORTED), "the duplicated support box does not count the column twice");
		assertEquals(1, count(report, WorldOp.Kind.FILL));
	}

	// ---- support -----------------------------------------------------------------------------------------------------------

	@Test
	void shallowHoleUnderAPlatformIsFilledBottomUpWhenApproved() {
		final FakeProbe probe = flat().hole(wx(0), 63, wz(1), 2);
		final SurveyReport refused = survey(probe, false);
		assertEquals(1, refused.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals(new Pos(100, 63, 199), new Pos(refused.issues().get(0).x(), refused.issues().get(0).y(), refused.issues().get(0).z()));
		assertEquals(0, count(refused, WorldOp.Kind.FILL));
		assertFalse(refused.buildable());
		assertEquals(0, refused.terrainChanges(), "filling air changes no existing block");

		final SurveyReport approved = survey(probe, true);
		assertTrue(approved.buildable());
		assertEquals(2, count(approved, WorldOp.Kind.FILL));
		final WorldOp lower = approved.ops().get(0);
		final WorldOp upper = approved.ops().get(1);
		assertEquals(new Pos(100, 62, 199), new Pos(lower.x(), lower.y(), lower.z()));
		assertEquals(new Pos(100, 63, 199), new Pos(upper.x(), upper.y(), upper.z()));
		for (final WorldOp fill : List.of(lower, upper)) {
			assertEquals(WorldOp.Kind.FILL, fill.kind());
			assertEquals(Role.FOUNDATION, fill.role());
			assertEquals(FOUNDATION, fill.blockId());
			assertEquals(Heading.NORTH, fill.facing());
			assertEquals("", fill.text());
		}
		assertEquals(14, approved.ops().size());
		assertEquals(WorldOp.Kind.PLACE, approved.ops().get(2).kind());
	}

	@Test
	void fillingOverWaterCountsAsATerrainChange() {
		final FakeProbe probe = flat().hole(wx(1), 63, wz(2), 2).water(wx(1), 63, wz(2));
		final SurveyReport report = survey(probe, false);
		assertEquals(1, report.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals(1, report.terrainChanges());
	}

	@Test
	void fillDepthLimitIsFourCells() {
		final FakeProbe four = flat().hole(wx(0), 63, wz(1), 4);
		final SurveyReport ok = survey(four, true);
		assertEquals(1, ok.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals(0, ok.count(SiteIssue.Kind.GAP_TOO_DEEP));
		assertEquals(4, count(ok, WorldOp.Kind.FILL));
		assertEquals(60, ok.ops().get(0).y(), "bottom-up: the lowest fill cell comes first");
		assertEquals(63, ok.ops().get(3).y());
		assertTrue(ok.buildable());

		final FakeProbe five = flat().hole(wx(0), 63, wz(1), 5);
		final SurveyReport deep = survey(five, true);
		assertEquals(1, deep.count(SiteIssue.Kind.GAP_TOO_DEEP));
		assertEquals(0, deep.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals(0, count(deep, WorldOp.Kind.FILL));
		assertFalse(deep.buildable());
	}

	@Test
	void deepHoleIsHardEvenWithApproval() {
		final FakeProbe probe = flat().hole(wx(0), 63, wz(1), 6);
		for (final boolean approved : new boolean[] {false, true}) {
			final SurveyReport report = survey(probe, approved);
			assertEquals(1, report.count(SiteIssue.Kind.GAP_TOO_DEEP));
			assertTrue(report.hasHardIssue());
			assertFalse(report.buildable());
			assertEquals(0, count(report, WorldOp.Kind.FILL));
			assertEquals(new Pos(100, 63, 199), new Pos(report.issues().get(0).x(), report.issues().get(0).y(), report.issues().get(0).z()));
		}
	}

	@Test
	void slabAsSupportIsReplacedByOneFoundationBlock() {
		final FakeProbe probe = flat().slab(wx(1), 63, wz(0));
		final SurveyReport refused = survey(probe, false);
		assertEquals(1, refused.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals("minecraft:stone_slab", refused.issues().get(0).block());
		assertEquals(1, refused.terrainChanges(), "the slab would be replaced");
		assertFalse(refused.buildable());

		final SurveyReport approved = survey(probe, true);
		assertEquals(1, count(approved, WorldOp.Kind.FILL));
		assertEquals(new Pos(101, 63, 200), new Pos(approved.ops().get(0).x(), approved.ops().get(0).y(), approved.ops().get(0).z()));
		assertTrue(approved.buildable());
	}

	@Test
	void supportUnderTheTrackCentreIsChecked() {
		final FakeProbe probe = flat().hole(wx(3), 63, wz(2), 1);
		final SurveyReport report = survey(probe, true);
		assertEquals(1, report.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals(1, count(report, WorldOp.Kind.FILL));
	}

	@Test
	void protectedBlockUnderASupportCellIsHardAndLeavesTheColumnUnfilled() {
		// support cell is air, the cell below holds a chest: the fill column would have to replace it
		final FakeProbe probe = flat().hole(wx(0), 63, wz(1), 1).chest(wx(0), 62, wz(1));
		final SurveyReport report = survey(probe, true);
		assertEquals(1, report.count(SiteIssue.Kind.PROTECTED));
		assertEquals(0, report.count(SiteIssue.Kind.UNSUPPORTED));
		assertEquals(new Pos(100, 62, 199), new Pos(report.issues().get(0).x(), report.issues().get(0).y(), report.issues().get(0).z()));
		assertEquals(0, count(report, WorldOp.Kind.FILL));
		assertFalse(report.buildable());
	}

	@Test
	void protectedSupportCellIsHard() {
		final FakeProbe probe = flat().chest(wx(0), 63, wz(0));
		final SurveyReport report = survey(probe, true);
		assertEquals(1, report.count(SiteIssue.Kind.PROTECTED));
		assertFalse(report.buildable());
	}

	// ---- world limits ------------------------------------------------------------------------------------------------------

	@Test
	void unloadedColumnIsHardEverywhereItTouchesTheSite() {
		// x = 103 is u = 3: 2 rail nodes, 18 further envelope cells and 4 support cells
		final FakeProbe probe = flat().unloadedColumns(wx(3), wx(3));
		for (final boolean approved : new boolean[] {false, true}) {
			final SurveyReport report = survey(probe, approved);
			assertEquals(24, report.count(SiteIssue.Kind.UNLOADED));
			assertEquals(24, report.counts().values().stream().mapToInt(Integer::intValue).sum());
			assertEquals(SiteIssue.Kind.UNLOADED, report.issues().get(0).kind());
			assertTrue(report.hasHardIssue());
			assertFalse(report.buildable());
		}
	}

	@Test
	void cellsAboveTheWorldTopAreOutOfWorld() {
		// highest valid y is 64: the bench and sign (y = 65), the envelope above rail level and the headroom are out
		final FakeProbe probe = flat().limits(-64, 64);
		final SurveyReport report = survey(probe, true);
		assertTrue(report.count(SiteIssue.Kind.OUT_OF_WORLD) > 0);
		assertEquals(report.count(SiteIssue.Kind.OUT_OF_WORLD), report.counts().values().stream().mapToInt(Integer::intValue).sum());
		final SiteIssue first = report.issues().get(0);
		assertEquals(SiteIssue.Kind.OUT_OF_WORLD, first.kind());
		assertEquals(new Pos(100, 65, 199), new Pos(first.x(), first.y(), first.z()), "the bench is the first planned cell above the top");
		assertFalse(report.buildable());
		// 2 planned (bench, sign) + envelope y 1..4 (3 x 4 x 4 = 48) + headroom y 1..3 minus the 2 planned (2 x 3 x 4 - 2 = 22)
		assertEquals(72, report.count(SiteIssue.Kind.OUT_OF_WORLD));
	}

	@Test
	void supportBelowTheWorldBottomIsOutOfWorld() {
		final FakeProbe probe = flat().limits(64, 319);
		final SurveyReport report = survey(probe, true);
		assertEquals(12, report.count(SiteIssue.Kind.OUT_OF_WORLD), "the 8 platform and 4 track support cells at y = 63");
		assertEquals(0, report.count(SiteIssue.Kind.GAP_TOO_DEEP));
		assertFalse(report.buildable());
	}

	// ---- ordering and reporting --------------------------------------------------------------------------------------------

	@Test
	void opsAreClearsThenFillsThenPlaces() {
		final FakeProbe probe = flat().log(wx(2), wy(2), wz(0)).hole(wx(0), 63, wz(2), 3).hole(wx(1), 63, wz(0), 1);
		final SurveyReport report = survey(probe, true);
		final List<WorldOp.Kind> kinds = new ArrayList<>();
		for (final WorldOp op : report.ops()) {
			kinds.add(op.kind());
		}
		final List<WorldOp.Kind> expected = new ArrayList<>();
		expected.add(WorldOp.Kind.CLEAR);
		for (int i = 0; i < 4; i++) {
			expected.add(WorldOp.Kind.FILL);
		}
		for (int i = 0; i < 12; i++) {
			expected.add(WorldOp.Kind.PLACE);
		}
		assertEquals(expected, kinds);
		// fills ascend in y; the two y = 63 cells keep scan order (v = 0 column before v = 2)
		assertEquals(List.of(61, 62, 63, 63), List.of(report.ops().get(1).y(), report.ops().get(2).y(), report.ops().get(3).y(),
				report.ops().get(4).y()));
		assertEquals(wz(0), report.ops().get(3).z());
		assertEquals(wz(2), report.ops().get(4).z());
	}

	@Test
	void issuesAreCappedButEveryOneIsCounted() {
		final List<PlannedBlock> blocks = new ArrayList<>();
		final FakeProbe probe = flat();
		for (int v = 0; v < 80; v++) {
			blocks.add(new PlannedBlock(0, 0, v, Role.PLATFORM_SURFACE, SURFACE, LocalDir.PLUS_V, ""));
			probe.grass(wx(0), wy(0), wz(v));
		}
		final StationPlan plan = plan(blocks, List.of(), List.of(), List.of());
		final SurveyReport report = SiteSurvey.survey(plan, NORTH, probe, false);
		assertEquals(SiteSurvey.MAX_LISTED, report.issues().size());
		assertEquals(80, report.count(SiteIssue.Kind.OCCUPIED));
		assertEquals(80, report.terrainChanges());
		assertEquals(80, report.ops().size());
		assertEquals(wz(0), report.issues().get(0).z());
		assertEquals(wz(SiteSurvey.MAX_LISTED - 1), report.issues().get(SiteSurvey.MAX_LISTED - 1).z(), "the first issues in scan order are kept");
	}

	@Test
	void sameInputGivesEqualReports() {
		final SurveyReport first = survey(flat().grass(wx(0), wy(0), wz(1)).log(wx(2), wy(2), wz(2)).hole(wx(1), 63, wz(3), 2), true);
		final SurveyReport second = survey(flat().grass(wx(0), wy(0), wz(1)).log(wx(2), wy(2), wz(2)).hole(wx(1), 63, wz(3), 2), true);
		assertEquals(first, second);
		final FakeProbe shared = flat().grass(wx(0), wy(0), wz(1));
		assertEquals(survey(shared, false), survey(shared, false));
		assertFalse(first.equals(survey(flat(), true)));
	}

	@Test
	void aSurveyWalkedOneCellAtATimeGivesTheSameReport() {
		final SiteSurvey.Survey survey = SiteSurvey.start(miniPlan(), NORTH,
				flat().grass(wx(0), wy(0), wz(1)).log(wx(2), wy(2), wz(2)).hole(wx(1), 63, wz(3), 2), true);
		int steps = 0;
		// a deadline already passed: each step reads one planned block or one column and returns
		while (!survey.step(Long.MIN_VALUE)) {
			steps++;
			assertThrows(IllegalStateException.class, survey::report);
		}
		assertTrue(steps > 10);
		assertEquals(survey(flat().grass(wx(0), wy(0), wz(1)).log(wx(2), wy(2), wz(2)).hole(wx(1), 63, wz(3), 2), true), survey.report());
	}

	// ---- frames ------------------------------------------------------------------------------------------------------------

	@Test
	void eastFacingFrameMapsCoordinatesAndFacings() {
		final LocalFrame east = new LocalFrame(50, 70, 60, Heading.EAST);
		// east: +v is +x, +u (right) is south = +z
		final FakeProbe probe = new FakeProbe(69).grass(52, 70, 61);
		final SurveyReport report = SiteSurvey.survey(miniPlan(), east, probe, false);
		assertEquals(1, report.count(SiteIssue.Kind.OCCUPIED));
		final SiteIssue issue = report.issues().get(0);
		assertEquals(new Pos(52, 70, 61), new Pos(issue.x(), issue.y(), issue.z()), "local u = 1, v = 2");
		assertPlaceOpsFollowPlan(miniPlan(), east, report.ops());
		final WorldOp edge = report.ops().get(4);
		assertEquals(Role.PLATFORM_EDGE, edge.role());
		assertEquals(new Pos(50, 70, 61), new Pos(edge.x(), edge.y(), edge.z()));
		assertEquals(Heading.SOUTH, edge.facing(), "the edge's front points at the track, +u");
		assertEquals(Heading.WEST, report.ops().get(9).facing(), "rail node front -v is west");
		final WorldOp sign = report.ops().get(11);
		assertEquals(new Pos(52, 71, 60), new Pos(sign.x(), sign.y(), sign.z()));
	}

	@Test
	void southFacingFrameMapsCoordinatesAndFacings() {
		final LocalFrame south = new LocalFrame(50, 70, 60, Heading.SOUTH);
		// south: +v is +z, +u (right) is west = -x
		final FakeProbe probe = new FakeProbe(69).log(46, 71, 62);
		final SurveyReport report = SiteSurvey.survey(miniPlan(), south, probe, true);
		// local (4, 1, 2) is inside the track envelope
		assertEquals(1, report.count(SiteIssue.Kind.OBSTRUCTED));
		final SiteIssue issue = report.issues().get(0);
		assertEquals(new Pos(46, 71, 62), new Pos(issue.x(), issue.y(), issue.z()));
		final WorldOp clear = report.ops().get(0);
		assertEquals(WorldOp.Kind.CLEAR, clear.kind());
		assertEquals(Heading.SOUTH, clear.facing(), "CLEAR and FILL ops carry the frame's forward");
		assertPlaceOpsFollowPlan(miniPlan(), south, report.ops().subList(1, 13));
		final WorldOp edge = report.ops().get(1 + 4);
		assertEquals(new Pos(49, 70, 60), new Pos(edge.x(), edge.y(), edge.z()));
		assertEquals(Heading.WEST, edge.facing());
		final WorldOp node = report.ops().get(1 + 9);
		assertEquals(new Pos(47, 70, 63), new Pos(node.x(), node.y(), node.z()));
		assertEquals(Heading.NORTH, node.facing());
	}

	@Test
	void localFrameMapsAllFourHeadings() {
		final int[][] expected = {
				// forward heading ordinal, then world x and z of local (u = 2, v = 3) from origin (10, 20)
				{0, 12, 17},
				{1, 13, 22},
				{2, 8, 23},
				{3, 7, 18},
		};
		for (final int[] row : expected) {
			final Heading forward = Heading.values()[row[0]];
			final LocalFrame frame = new LocalFrame(10, 5, 20, forward);
			assertEquals(row[1], frame.x(2, 3), forward.name());
			assertEquals(row[2], frame.z(2, 3), forward.name());
			assertEquals(5 + 4, frame.y(4));
			assertEquals(forward, frame.heading(LocalDir.PLUS_V));
			assertEquals(forward.opposite(), frame.heading(LocalDir.MINUS_V));
			assertEquals(forward.right(), frame.heading(LocalDir.PLUS_U));
			assertEquals(forward.left(), frame.heading(LocalDir.MINUS_U));
			assertEquals(10, frame.x(0, 0));
			assertEquals(20, frame.z(0, 0));
		}
	}

	@Test
	void frameInFrontOfThePlayerLeavesAGapAndCentresTheFootprint() {
		final TrackLayout layout = new TrackLayout(5, 8, 2, List.of(), List.of());
		// player at (10, 64, 10) looking east: footprint starts 2 + ramp (2) cells ahead, centred across (5 / 2 = 2 columns left)
		final LocalFrame east = LocalFrame.inFrontOf(10, 64, 10, Heading.EAST, layout);
		assertEquals(new LocalFrame(14, 64, 8, Heading.EAST), east);
		assertEquals(10 + 2, east.x(0, -2), "the first ramp cell is the gap in front of the player");
		assertEquals(10, east.z(2, 0), "the player stands in the middle column");
		final LocalFrame north = LocalFrame.inFrontOf(10, 64, 10, Heading.NORTH, layout);
		assertEquals(new LocalFrame(8, 64, 6, Heading.NORTH), north);
		assertEquals(10, north.x(2, 0));
	}

	// ---- existing MTR rail nodes -------------------------------------------------------------------------------------------

	@Test
	void anExistingNodeInAPlannedNodeCellIsKeptWithoutAnIssueOrOp() {
		for (final boolean approved : new boolean[] {false, true}) {
			final SurveyReport report = survey(flat().node(wx(3), wy(0), wz(0)), approved);
			assertTrue(report.counts().isEmpty(), "issues " + report.counts());
			assertTrue(report.buildable());
			assertEquals(0, report.terrainChanges());
			assertEquals(11, report.placeCount(), "every planned block but the kept node");
			assertTrue(report.ops().stream().noneMatch(op -> op.x() == wx(3) && op.y() == wy(0) && op.z() == wz(0)));
		}
	}

	@Test
	void anExistingNodeInTheEnvelopeIsLeftAlone() {
		final SurveyReport report = survey(flat().node(wx(3), wy(0), wz(1)).node(wx(2), wy(0), wz(2)), true);
		assertTrue(report.counts().isEmpty(), "issues " + report.counts());
		assertEquals(0, count(report, WorldOp.Kind.CLEAR));
	}

	@Test
	void anExistingNodeInAnyOtherPlannedOrSupportCellIsProtectedEvenWithApproval() {
		final SurveyReport onPlatform = survey(flat().node(wx(0), wy(0), wz(1)), true);
		assertEquals(1, onPlatform.count(SiteIssue.Kind.PROTECTED));
		assertEquals("mtr:rail", onPlatform.issues().get(0).block());
		assertFalse(onPlatform.buildable());

		// under the platform, in what would otherwise be a foundation fill column
		final SurveyReport inFill = survey(flat().hole(wx(1), wy(-1), wz(2), 2).node(wx(1), wy(-2), wz(2)), true);
		assertEquals(1, inFill.count(SiteIssue.Kind.PROTECTED));
		assertFalse(inFill.buildable());
		assertTrue(inFill.ops().stream().noneMatch(op -> op.x() == wx(1) && op.y() == wy(-2) && op.z() == wz(2)));
	}
}
