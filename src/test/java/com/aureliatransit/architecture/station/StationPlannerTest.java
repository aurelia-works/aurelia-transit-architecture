package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The planner over a bounded, deterministic matrix of specs. The matrix is planned once; each test then asserts one family of
 * invariants over all of it.
 */
class StationPlannerTest {

	private static final int[] LENGTHS = {16, 47, 160};
	private static final int[] WIDTHS = {3, 8};
	private static final String NAME = "Leidsche Rijn";
	private static final String ATA = "aurelia_transit_architecture:";
	private static final int MAX_REPORTED = 8;

	// ---------------------------------------------------------------- the matrix scan

	/** Failures found while scanning the matrix, by family. */
	private static final class Scan {
		final Map<String, List<String>> failures = new LinkedHashMap<>();
		int specs;

		void fail(String family, StationSpec spec, String detail) {
			final List<String> list = failures.computeIfAbsent(family, key -> new ArrayList<>());
			if (list.size() < MAX_REPORTED) {
				list.add(describe(spec) + ": " + detail);
			}
		}

		void assertNone(String family) {
			assertTrue(specs > 3000, "matrix too small: " + specs);
			final List<String> list = failures.get(family);
			assertTrue(list == null || list.isEmpty(), family + " failed for:\n" + String.join("\n", list == null ? List.of() : list));
		}
	}

	private static Scan scan;

	private static synchronized Scan scan() {
		if (scan == null) {
			scan = runScan();
		}
		return scan;
	}

	private static String describe(StationSpec spec) {
		return spec.trackCount() + "x" + spec.platformLayout() + " L" + spec.platformLength() + " w" + spec.platformWidth() + " " + spec.style() + " "
				+ spec.roof() + " surface=" + spec.surface() + " roofMat=" + spec.roofMaterial() + " stepFree=" + spec.stepFree() + " tactile="
				+ spec.tactile() + " furniture=" + spec.furniture() + " railNodes=" + spec.railNodes() + " name='" + spec.name() + "'";
	}

	private static Scan runScan() {
		final Scan scan = new Scan();
		int index = 0;
		for (int tracks = 1; tracks <= 4; tracks++) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				if (layout == PlatformLayout.ISLAND && tracks < 2) {
					continue;
				}
				for (final RoofType roof : RoofType.values()) {
					for (final StationStyle style : StationStyle.values()) {
						for (final boolean stepFree : new boolean[] {true, false}) {
							for (final boolean furniture : new boolean[] {true, false}) {
								for (final String name : new String[] {"", NAME}) {
									for (final int length : LENGTHS) {
										for (final int width : WIDTHS) {
											index++;
											final StationSpec spec = StationSpec.builder().trackCount(tracks).platformLayout(layout).roof(roof).style(style)
													.stepFree(stepFree).furniture(furniture).name(name).platformLength(length).platformWidth(width)
													// the remaining options cycle with the running index so every value is covered
													.tactile(index % 2 == 0).railNodes(index % 3 != 0)
													.surface(SurfaceMaterial.values()[index % SurfaceMaterial.values().length])
													.roofMaterial(RoofMaterial.values()[index % RoofMaterial.values().length]).build();
											scan.specs++;
											check(scan, spec, StationPlanner.plan(spec), index % 11 == 0);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return scan;
	}

	private static void check(Scan scan, StationSpec spec, StationPlan plan, boolean checkDeterminism) {
		final TrackLayout layout = plan.layout();
		final int length = StationGeometry.builtLength(spec.platformLength());
		final StylePalette palette = StylePalette.of(spec);

		// violations: none (a side-platform hall over three or more tracks stands columns between the tracks)
		final Set<Violation.Kind> kinds = EnumSet.noneOf(Violation.Kind.class);
		for (final Violation violation : LayoutValidator.validate(plan)) {
			kinds.add(violation.kind());
		}
		final Set<Violation.Kind> expected = EnumSet.noneOf(Violation.Kind.class);
		if (!kinds.equals(expected)) {
			scan.fail("violations", spec, "expected " + expected + " but got " + kinds);
		}

		final Map<Cell, PlannedBlock> byCell = new HashMap<>();
		final Map<Role, List<PlannedBlock>> byRole = new EnumMap<>(Role.class);
		for (final PlannedBlock block : plan.blocks()) {
			if (byCell.put(new Cell(block.u(), block.y(), block.v()), block) != null) {
				scan.fail("unique cells", spec, "two blocks at " + block);
			}
			byRole.computeIfAbsent(block.role(), key -> new ArrayList<>()).add(block);
			// fixtures name their own equipment (screens, help points), every other role takes the palette's block
			// a hall's frame is the roof truss where the palette's beam is the plain girder; a ground-floor gate line is MTR's fence
			final boolean substitute = (block.role() == Role.BEAM || block.role() == Role.RIB) && block.blockId().equals(StationPlanner.ROOF_TRUSS)
					&& palette.blockId(block.role()).equals(StationPlanner.GIRDER) || block.role() == Role.BARRIER && block.blockId().equals(PlanState.MTR_FENCE);
			if (block.role() != Role.FIXTURE && !substitute && !block.blockId().equals(palette.blockId(block.role()))) {
				scan.fail("palette ids", spec, "wrong id on " + block);
			}
			// name signs carry the name, departure screens the display style they are set to, nothing else carries text
			final boolean screen = block.blockId().endsWith("platform_pids") || block.blockId().endsWith(":concourse_board");
			if (!(block.role() == Role.NAME_SIGN ? spec.name() : screen ? palette.screenStyle().id() : "").equals(block.text())) {
				scan.fail("sign text", spec, "wrong text on " + block);
			}
		}
		if (!plan.spec().equals(spec) || !plan.foundationId().equals("minecraft:stone_bricks")) {
			scan.fail("plan header", spec, "spec or foundation wrong");
		}

		// build order
		final Comparator<PlannedBlock> order = Comparator.comparingInt((PlannedBlock b) -> b.role().ordinal()).thenComparingInt(PlannedBlock::y)
				.thenComparingInt(PlannedBlock::v).thenComparingInt(PlannedBlock::u);
		for (int i = 0; i + 1 < plan.blocks().size(); i++) {
			if (order.compare(plan.blocks().get(i), plan.blocks().get(i + 1)) >= 0) {
				scan.fail("build order", spec, "blocks " + i + " and " + (i + 1) + " are out of order");
				break;
			}
		}

		// edges face their tracks, and nothing else is an edge
		int sides = 0;
		for (final TrackLayout.Track track : layout.tracks()) {
			if (track.platformLeft()) {
				sides++;
				if (!edgeColumn(byCell, track.u() - StationGeometry.EDGE_OFFSET, length, LocalDir.PLUS_U)) {
					scan.fail("edges", spec, "track " + track.number() + " left edge missing or misdirected");
				}
			}
			if (track.platformRight()) {
				sides++;
				if (!edgeColumn(byCell, track.u() + StationGeometry.EDGE_OFFSET, length, LocalDir.MINUS_U)) {
					scan.fail("edges", spec, "track " + track.number() + " right edge missing or misdirected");
				}
			}
		}
		if (list(byRole, Role.PLATFORM_EDGE).size() != sides * length) {
			scan.fail("edges", spec, "edge count " + list(byRole, Role.PLATFORM_EDGE).size() + " != " + sides * length);
		}
		int floorColumns = 0;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			floorColumns += band.width();
		}
		// platform floor inside the platforms (a column between tracks stands on a plinth of it outside them)
		final long surface = list(byRole, Role.PLATFORM_SURFACE).stream()
				.filter(b -> layout.platforms().stream().anyMatch(p -> b.u() >= p.uMin() && b.u() <= p.uMax())).count();
		if (list(byRole, Role.PLATFORM_EDGE).size() + surface != floorColumns * length) {
			scan.fail("floors", spec, "floor cell count wrong");
		}

		// ramps
		final int rampCells = spec.stepFree() ? floorColumns : 0;
		if (list(byRole, Role.RAMP_LOWER).size() != 2 * rampCells || list(byRole, Role.RAMP_UPPER).size() != 2 * rampCells) {
			scan.fail("ramps", spec, "ramp counts " + list(byRole, Role.RAMP_LOWER).size() + "/" + list(byRole, Role.RAMP_UPPER).size());
		}
		if (spec.stepFree()) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					if (!is(byCell, u, 0, -2, Role.RAMP_LOWER, LocalDir.MINUS_V) || !is(byCell, u, 0, -1, Role.RAMP_UPPER, LocalDir.MINUS_V)
							|| !is(byCell, u, 0, length, Role.RAMP_UPPER, LocalDir.PLUS_V)
							|| !is(byCell, u, 0, length + 1, Role.RAMP_LOWER, LocalDir.PLUS_V)) {
						scan.fail("ramps", spec, "ramp column u=" + u + " wrong");
					}
				}
			}
		}

		// rail nodes
		final List<PlannedBlock> nodes = list(byRole, Role.RAIL_NODE);
		if (nodes.size() != (spec.railNodes() ? 2 * spec.trackCount() : 0)) {
			scan.fail("rail nodes", spec, "node count " + nodes.size());
		}
		if (spec.railNodes()) {
			for (final TrackLayout.Track track : layout.tracks()) {
				if (!is(byCell, track.u(), 0, 0, Role.RAIL_NODE, LocalDir.PLUS_V) || !is(byCell, track.u(), 0, length - 1, Role.RAIL_NODE, LocalDir.PLUS_V)) {
					scan.fail("rail nodes", spec, "track " + track.number() + " nodes wrong");
				}
			}
		}

		// name signs: rows of three, text on every sign
		final List<PlannedBlock> signs = list(byRole, Role.NAME_SIGN);
		int expectedRows = 0;
		if (!spec.name().isEmpty()) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				// one row per platform: on an island one double-sided row down the middle
				expectedRows++;
			}
		}
		if (signs.size() != 3 * expectedRows) {
			scan.fail("name signs", spec, "sign count " + signs.size() + " != " + 3 * expectedRows);
		}
		final Map<Integer, List<Integer>> signV = new TreeMap<>();
		for (final PlannedBlock sign : signs) {
			signV.computeIfAbsent(sign.u(), key -> new ArrayList<>()).add(sign.v());
			if (!sign.text().equals(spec.name()) || sign.y() != 1 || sign.front().alongTrack()) {
				scan.fail("name signs", spec, "bad sign " + sign);
			}
		}
		for (final List<Integer> vs : signV.values()) {
			vs.sort(Comparator.naturalOrder());
			boolean rows = vs.size() % 3 == 0;
			for (int i = 0; rows && i < vs.size(); i += 3) {
				rows = vs.get(i + 1) == vs.get(i) + 1 && vs.get(i + 2) == vs.get(i) + 2;
			}
			if (!rows) {
				scan.fail("name signs", spec, "signs are not in rows of three: " + vs);
			}
		}

		// furniture off or roof present: no lamps / benches / bins
		if (!spec.furniture() && (!list(byRole, Role.LAMP).isEmpty() || !list(byRole, Role.BENCH).isEmpty() || !list(byRole, Role.BIN).isEmpty())) {
			scan.fail("furniture", spec, "furniture present although switched off");
		}
		if (spec.roof() != RoofType.NONE && !list(byRole, Role.LAMP).isEmpty()) {
			scan.fail("furniture", spec, "lamps under a roof");
		}
		if (spec.furniture() && spec.roof() == RoofType.NONE && list(byRole, Role.LAMP).isEmpty()) {
			scan.fail("furniture", spec, "no lamps on an open platform");
		}
		if (spec.furniture() && list(byRole, Role.BENCH).isEmpty()) {
			scan.fail("furniture", spec, "no benches");
		}
		// departure screens: at least one on every platform when furnished, none otherwise
		for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
			final long screens = list(byRole, Role.FIXTURE).stream().filter(b -> b.blockId().endsWith("platform_pids")
					&& b.u() >= band.uMin() && b.u() <= band.uMax()).count();
			if (spec.furniture() != (screens > 0)) {
				scan.fail("furniture", spec, "platform " + band.number() + " has " + screens + " departure screens");
			}
		}

		// the envelopes: from the layout, independent of the plan's own boxes
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.RAIL_NODE) {
				continue;
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				if (Math.abs(block.u() - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH && block.y() >= 0 && block.y() < StationGeometry.ENVELOPE_HEIGHT) {
					scan.fail("envelopes", spec, block + " inside the envelope of track " + track.number());
				}
			}
		}
		if (plan.trackEnvelopes().size() != spec.trackCount()) {
			scan.fail("envelopes", spec, "envelope count");
		}

		// support boxes cover every platform and ramp cell and every track's centre column
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role().platformFloor() || block.role() == Role.RAMP_LOWER || block.role() == Role.RAMP_UPPER) {
				if (!supported(plan, block.u(), block.v())) {
					scan.fail("support boxes", spec, "no support under " + block);
				}
			}
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (int v = layout.vMin(); v <= layout.vMax(); v++) {
				if (!supported(plan, track.u(), v)) {
					scan.fail("support boxes", spec, "no support under track " + track.number() + " at v=" + v);
					break;
				}
			}
		}
		if (plan.headroom().size() != layout.platforms().size()) {
			scan.fail("headroom", spec, "headroom box count");
		}

		final List<PlannedBlock> floating = unconnected(plan);
		if (!floating.isEmpty()) {
			scan.fail("connected", spec, floating.size() + " blocks not connected to the ground, first " + floating.get(0));
		}
		if (checkDeterminism && !plan.equals(StationPlanner.plan(spec))) {
			scan.fail("determinism", spec, "second plan differs");
		}
	}

	private record Cell(int u, int y, int v) {
	}

	/**
	 * Blocks not joined to the ground (y <= 0) through a chain of face-touching blocks: a roof, wall or fixture left
	 * floating in the air after the cells that held it were skipped.
	 */
	private static List<PlannedBlock> unconnected(StationPlan plan) {
		final Map<Cell, PlannedBlock> byCell = new HashMap<>();
		final List<Cell> queue = new ArrayList<>();
		final Set<Cell> reached = new HashSet<>();
		for (final PlannedBlock block : plan.blocks()) {
			final Cell cell = new Cell(block.u(), block.y(), block.v());
			byCell.put(cell, block);
			if (block.y() <= 0 && reached.add(cell)) {
				queue.add(cell);
			}
		}
		for (int i = 0; i < queue.size(); i++) {
			final Cell cell = queue.get(i);
			for (final Cell next : List.of(new Cell(cell.u() + 1, cell.y(), cell.v()), new Cell(cell.u() - 1, cell.y(), cell.v()),
					new Cell(cell.u(), cell.y() + 1, cell.v()), new Cell(cell.u(), cell.y() - 1, cell.v()), new Cell(cell.u(), cell.y(), cell.v() + 1),
					new Cell(cell.u(), cell.y(), cell.v() - 1))) {
				if (byCell.containsKey(next) && reached.add(next)) {
					queue.add(next);
				}
			}
		}
		final List<PlannedBlock> floating = new ArrayList<>();
		for (final Map.Entry<Cell, PlannedBlock> entry : byCell.entrySet()) {
			if (!reached.contains(entry.getKey())) {
				floating.add(entry.getValue());
			}
		}
		return floating;
	}

	private static List<PlannedBlock> list(Map<Role, List<PlannedBlock>> byRole, Role role) {
		return byRole.getOrDefault(role, List.of());
	}

	private static boolean is(Map<Cell, PlannedBlock> byCell, int u, int y, int v, Role role, LocalDir front) {
		final PlannedBlock block = byCell.get(new Cell(u, y, v));
		return block != null && block.role() == role && block.front() == front;
	}

	private static boolean edgeColumn(Map<Cell, PlannedBlock> byCell, int u, int length, LocalDir front) {
		for (int v = 0; v < length; v++) {
			if (!is(byCell, u, 0, v, Role.PLATFORM_EDGE, front)) {
				return false;
			}
		}
		return true;
	}

	private static boolean supported(StationPlan plan, int u, int v) {
		for (final Box box : plan.supportAreas()) {
			if (box.contains(u, -1, v)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- matrix tests

	@Test
	void matrixHasNoViolations() {
		scan().assertNone("violations");
	}

	@Test
	void matrixBlocksHaveUniqueCellsInBuildOrder() {
		scan().assertNone("unique cells");
		scan().assertNone("build order");
	}

	@Test
	void matrixPlansCarryThePaletteIdsAndSignText() {
		scan().assertNone("palette ids");
		scan().assertNone("sign text");
		scan().assertNone("plan header");
	}

	@Test
	void matrixEdgesFaceTheirTracks() {
		scan().assertNone("edges");
		scan().assertNone("floors");
	}

	@Test
	void matrixRampsAppearOnlyWithStepFreeAccessAndPointAwayFromThePlatform() {
		scan().assertNone("ramps");
	}

	@Test
	void matrixRailNodesSitAtBothPlatformEndsOfEveryTrackIfEnabled() {
		scan().assertNone("rail nodes");
	}

	@Test
	void matrixNameSignsComeInRowsOfThreeWithTheTextIfThereIsAName() {
		scan().assertNone("name signs");
	}

	@Test
	void matrixFurnitureFollowsTheOptionsAndTheRoof() {
		scan().assertNone("furniture");
	}

	@Test
	void matrixKeepsEveryEnvelopeFreeExceptForRailNodes() {
		scan().assertNone("envelopes");
	}

	@Test
	void matrixSupportBoxesCoverEveryPlatformAndRampCell() {
		scan().assertNone("support boxes");
		scan().assertNone("headroom");
	}

	@Test
	void matrixBlocksAreAllConnectedToTheGround() {
		scan().assertNone("connected");
	}

	@Test
	void presetBlocksAreAllConnectedToTheGround() {
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			final List<PlannedBlock> floating = unconnected(plan(StationDescriptionParser.parse(preset.description()).spec()));
			assertTrue(floating.isEmpty(), preset.key() + ": " + floating.size() + " floating blocks, e.g. " + floating.stream().limit(3).toList());
		}
	}

	@Test
	void matrixPlansAreDeterministic() {
		scan().assertNone("determinism");
	}

	// ---------------------------------------------------------------- focused tests

	private static StationPlan plan(StationSpec spec) {
		return StationPlanner.plan(spec);
	}

	private static List<PlannedBlock> blocks(StationPlan plan, Role role) {
		final List<PlannedBlock> out = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == role) {
				out.add(block);
			}
		}
		return out;
	}

	@Test
	void samePlanTwiceIsEqual() {
		final StationSpec spec = StationSpec.builder().trackCount(3).platformLayout(PlatformLayout.ISLAND).roof(RoofType.HALL).name("Utrecht Centraal")
				.build();
		assertEquals(plan(spec), plan(spec));
		assertEquals(plan(spec).hashCode(), plan(spec).hashCode());
		assertNotEquals(plan(spec), plan(spec.toBuilder().platformLength(spec.platformLength() + 1).build()));
	}

	@Test
	void knownSpecHasExactRoleCounts() {
		final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.SIDE).platformLength(47).platformWidth(4)
				.roof(RoofType.CANOPY).style(StationStyle.MODERN).build(); // built 48 long
		final Map<Role, Integer> counts = plan(spec).countByRole();
		final Map<Role, Integer> expected = new EnumMap<>(Role.class);
		expected.put(Role.PLATFORM_SURFACE, 288);
		expected.put(Role.PLATFORM_EDGE, 96);
		expected.put(Role.RAMP_LOWER, 16);
		expected.put(Role.RAMP_UPPER, 16);
		expected.put(Role.RAIL_NODE, 4);
		// a canopy 5 wide (the platform and a cell out over its boarding edge) has one column line, so 5 columns (canopyPositions(48):
		// 2, 12, 23, 34, 45) per platform, each a post and a cap
		expected.put(Role.COLUMN, 40);
		expected.put(Role.COLUMN_CAP, 10);
		// per platform: an arm of 3 across each column row (5 * 3), and between them a girder along the line plus an edge girder
		// at each arm's end (43 rows * 3)
		expected.put(Role.BEAM, 288);
		// 3 roof cells per row between the two roof edges (48 rows * 3 = 144 a platform), 12 of them lit
		expected.put(Role.ROOF, 264);
		expected.put(Role.ROOF_EDGE, 192);
		expected.put(Role.ROOF_LIGHT, 24);
		expected.put(Role.BENCH, 12);
		expected.put(Role.BIN, 6);
		// per platform: two departure screens fixed to a column, two blocks wide on both faces of it (8), a help point and two
		// speakers (one every 32 blocks)
		expected.put(Role.FIXTURE, 22);
		assertEquals(expected, counts);
		assertEquals(1278, plan(spec).blocks().size());
	}

	@Test
	void knownSpecHasTheExpectedColumnPositionsAndEnvelopes() {
		final StationSpec spec = StationSpec.builder().platformLength(47).build(); // built 48 long
		final StationPlan plan = plan(spec);
		final Set<Integer> columnV = new HashSet<>();
		for (final PlannedBlock column : blocks(plan, Role.COLUMN_CAP)) {
			columnV.add(column.v());
			assertEquals(StationGeometry.CANOPY_Y - 2, column.y());
			// the one line of a 5 wide canopy (platform and a cell out over the boarding edge) is its middle: u 2 and 12
			assertTrue(column.u() == 2 || column.u() == 12, "column line at " + column.u());
		}
		assertEquals(Set.copyOf(StationPlanner.canopyPositions(48)), columnV);
		assertEquals(Set.of(2, 12, 23, 34, 45), columnV);
		assertEquals(List.of(new Box(4, 0, -2, 6, 4, 49), new Box(8, 0, -2, 10, 4, 49)), plan.trackEnvelopes());
		assertEquals(List.of(new Box(0, 1, 0, 3, 3, 47), new Box(11, 1, 0, 14, 3, 47)), plan.headroom());
		assertEquals(List.of(new Box(0, -1, -2, 3, -1, 49), new Box(11, -1, -2, 14, -1, 49), new Box(5, -1, -2, 5, -1, 49), new Box(9, -1, -2, 9, -1, 49)),
				plan.supportAreas());
	}

	@Test
	void columnPositionsFollowTheSpacingRule() {
		assertEquals(List.of(2, 10, 13), StationPlanner.columnPositions(16));
		assertEquals(List.of(2, 10, 18, 26, 34, 42), StationPlanner.columnPositions(47));
		assertEquals(List.of(2, 10, 18, 26, 34, 42, 45), StationPlanner.columnPositions(48));
		final List<Integer> long160 = StationPlanner.columnPositions(160);
		assertEquals(2, long160.get(0));
		assertEquals(154, long160.get(long160.size() - 2));
		assertEquals(157, long160.get(long160.size() - 1));
	}

	@Test
	void canopyPositionsSpaceTheColumnsAsWideAsAGirderSpans() {
		assertEquals(List.of(2, 13), StationPlanner.canopyPositions(16));
		assertEquals(List.of(2, 13, 24, 35, 46), StationPlanner.canopyPositions(49));
		for (int length = StationSpec.MIN_LENGTH; length <= StationSpec.MAX_LENGTH; length++) {
			final List<Integer> positions = StationPlanner.canopyPositions(length);
			assertEquals(2, positions.get(0), "length " + length);
			assertEquals(length - 3, positions.get(positions.size() - 1), "length " + length);
			for (int i = 1; i < positions.size(); i++) {
				assertTrue(positions.get(i) - positions.get(i - 1) <= StationGeometry.MAX_BEAM_SPAN, "bay too wide at length " + length);
			}
			// as few columns as the span allows: at most one fewer would leave a bay over the limit
			assertTrue(length - 5 > (positions.size() - 2) * StationGeometry.MAX_BEAM_SPAN, "more columns than needed at length " + length);
		}
	}

	@Test
	void islandHasEdgesOnBothFacesAndSidePlatformsFaceInwards() {
		final StationPlan island = plan(StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformWidth(6).build());
		final TrackLayout.PlatformBand band = island.layout().platforms().get(0);
		for (final PlannedBlock edge : blocks(island, Role.PLATFORM_EDGE)) {
			assertEquals(edge.u() == band.uMin() ? LocalDir.MINUS_U : LocalDir.PLUS_U, edge.front());
			assertTrue(edge.u() == band.uMin() || edge.u() == band.uMax());
		}
		assertEquals(2 * StationGeometry.builtLength(island.spec().platformLength()), blocks(island, Role.PLATFORM_EDGE).size());

		final StationPlan side = plan(StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.SIDE).platformWidth(4).build());
		for (final PlannedBlock edge : blocks(side, Role.PLATFORM_EDGE)) {
			assertEquals(edge.u() == 3 ? LocalDir.PLUS_U : LocalDir.MINUS_U, edge.front());
			assertTrue(edge.u() == 3 || edge.u() == 11);
		}
	}

	@Test
	void edgesUseTheTactileEdgeOnlyWhenAsked() {
		final StationSpec tactile = StationSpec.builder().style(StationStyle.GERMAN_SBAHN).tactile(true).build();
		final StationSpec plain = tactile.toBuilder().tactile(false).build();
		assertEquals("aurelia_transit_architecture:sbahn_platform_edge_tactile", blocks(plan(tactile), Role.PLATFORM_EDGE).get(0).blockId());
		assertEquals("aurelia_transit_architecture:sbahn_platform_edge", blocks(plan(plain), Role.PLATFORM_EDGE).get(0).blockId());
	}

	@Test
	void noRampsWithoutStepFreeAndNoRailNodesWhenSwitchedOff() {
		final StationPlan plan = plan(StationSpec.builder().stepFree(false).railNodes(false).build());
		assertTrue(blocks(plan, Role.RAMP_LOWER).isEmpty());
		assertTrue(blocks(plan, Role.RAMP_UPPER).isEmpty());
		assertTrue(blocks(plan, Role.RAIL_NODE).isEmpty());
		assertEquals(0, plan.layout().vMin());
		assertEquals(48, plan.layout().vMax(), "48 picked, 49 built");
	}

	@Test
	void openPlatformsGetLampsOnTheBackColumnFacingTheTrack() {
		final StationPlan plan = plan(StationSpec.builder().roof(RoofType.NONE).platformLength(30).build());
		final List<PlannedBlock> lamps = blocks(plan, Role.LAMP);
		// v = 4, 12, 20, 28 on each of the two side platforms
		assertEquals(8, lamps.size());
		for (final PlannedBlock lamp : lamps) {
			// a lamp standard: the lamp three blocks up, on a pole
			assertEquals(StationGeometry.WALK_Y + 3, lamp.y());
			for (int y = StationGeometry.WALK_Y; y < lamp.y(); y++) {
				final int at = y;
				assertTrue(plan.blocks().stream().anyMatch(b -> b.u() == lamp.u() && b.v() == lamp.v() && b.y() == at && b.blockId().equals(StationPlanner.LAMP_POLE)),
						"a pole under " + lamp);
			}
			assertTrue(Set.of(4, 12, 20, 28).contains(lamp.v()), "lamp at v=" + lamp.v());
			if (lamp.u() == 0) {
				assertEquals(LocalDir.PLUS_U, lamp.front());
			} else {
				assertEquals(14, lamp.u());
				assertEquals(LocalDir.MINUS_U, lamp.front());
			}
		}
	}

	@Test
	void islandBenchesAlternateTheirFacingAndBinsFollowTheirBench() {
		final StationPlan plan = plan(StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformWidth(6).platformLength(60)
				.roof(RoofType.NONE).build());
		final List<PlannedBlock> benches = blocks(plan, Role.BENCH);
		final List<PlannedBlock> bins = blocks(plan, Role.BIN);
		// two-seat benches at v = 8, 24, 40, 56 on the centre column, each with its bin after the second seat
		assertEquals(8, benches.size());
		assertEquals(4, bins.size());
		for (int i = 0; i < bins.size(); i++) {
			final PlannedBlock first = benches.get(2 * i);
			final PlannedBlock second = benches.get(2 * i + 1);
			final PlannedBlock bin = bins.get(i);
			assertEquals(5, first.u());
			assertEquals(8 + 16 * i, first.v());
			assertEquals(first.v() + 1, second.v());
			assertEquals(first.u(), second.u());
			assertEquals(i % 2 == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U, first.front());
			assertEquals(first.front(), second.front());
			assertEquals(first.u(), bin.u());
			assertEquals(first.v() + 2, bin.v());
			assertEquals(first.front(), bin.front());
		}
	}

	@Test
	void hallPutsBeamsAcrossTheFullWidthAtEveryColumnPosition() {
		final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformWidth(6).roof(RoofType.HALL).build();
		final StationPlan plan = plan(spec);
		final int width = plan.layout().width();
		final List<Integer> positions = StationPlanner.columnPositions(StationGeometry.builtLength(spec.platformLength()));
		final List<PlannedBlock> across = blocks(plan, Role.BEAM).stream().filter(b -> b.front() == LocalDir.PLUS_U).toList();
		assertEquals(positions.size() * width, across.size());
		for (final PlannedBlock beam : across) {
			assertTrue(positions.contains(beam.v()));
		}
		// girders along both eaves and both column lines of the island, between the cross girders
		final List<PlannedBlock> along = blocks(plan, Role.BEAM).stream().filter(b -> b.front() == LocalDir.PLUS_V).toList();
		assertEquals(4 * (StationGeometry.builtLength(spec.platformLength()) - positions.size()), along.size());
		for (final PlannedBlock beam : blocks(plan, Role.BEAM)) {
			assertEquals(StationGeometry.HALL_BEAM_Y, beam.y());
			assertEquals(ATA + "roof_truss", beam.blockId(), "a hall frame is the roof truss");
			assertEquals("kind=white", beam.state());
		}
		// every column cap carries a girder: nothing floats between the column and the roof
		for (final PlannedBlock cap : blocks(plan, Role.COLUMN_CAP)) {
			assertTrue(blocks(plan, Role.BEAM).stream().anyMatch(b -> b.u() == cap.u() && b.v() == cap.v() && b.y() == cap.y() + 1), cap.toString());
		}
		final int roofCells = blocks(plan, Role.ROOF).size() + blocks(plan, Role.ROOF_EDGE).size() + blocks(plan, Role.ROOF_LIGHT).size();
		assertEquals(width * StationGeometry.builtLength(spec.platformLength()), roofCells);
		assertEquals(2 * StationGeometry.builtLength(spec.platformLength()), blocks(plan, Role.ROOF_EDGE).size());
		for (final PlannedBlock edge : blocks(plan, Role.ROOF_EDGE)) {
			assertEquals(StationGeometry.HALL_ROOF_Y, edge.y());
			assertEquals(edge.u() == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U, edge.front());
		}
		for (final PlannedBlock cap : blocks(plan, Role.COLUMN_CAP)) {
			assertEquals(StationGeometry.HALL_BEAM_Y - 1, cap.y());
		}
		assertEquals(StationGeometry.HALL_ROOF_Y, plan.maxY());
	}

	@Test
	void wideSideHallsGetASecondColumnLineAtTheBack() {
		// P 0..5, T 7, T 11, P 13..18: one line next to each edge keeps the beam ends within the cantilever
		assertEquals(Set.of(4, 14), columnLines(plan(StationSpec.builder().roof(RoofType.HALL).platformWidth(6).build())));
		// P 0..7, T 9, T 13, P 15..22: the lines next to the edges would leave 6 cells of overhang, so the backs get one too
		assertEquals(Set.of(1, 6, 16, 21), columnLines(plan(StationSpec.builder().roof(RoofType.HALL).platformWidth(8).build())));
	}

	private static Set<Integer> columnLines(StationPlan plan) {
		final Set<Integer> lines = new HashSet<>();
		for (final PlannedBlock cap : blocks(plan, Role.COLUMN_CAP)) {
			lines.add(cap.u());
		}
		return lines;
	}

	@Test
	void canopyHasOneCentreColumnLinePerPlatform() {
		final StationPlan plan = plan(StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformWidth(8).roof(RoofType.CANOPY).build());
		// island 3..10 and a cell of roof out over each boarding edge: 2..11, centre 2 + 9 / 2 = 6
		assertEquals(Set.of(6), columnLines(plan));
		for (final PlannedBlock roof : blocks(plan, Role.ROOF_EDGE)) {
			assertEquals(StationGeometry.CANOPY_Y, roof.y());
			assertTrue(roof.u() == 2 || roof.u() == 11, "roof edge at " + roof.u());
			assertEquals(roof.u() == 2 ? LocalDir.MINUS_U : LocalDir.PLUS_U, roof.front());
		}
		for (final PlannedBlock cap : blocks(plan, Role.COLUMN_CAP)) {
			assertEquals(StationGeometry.CANOPY_Y - 2, cap.y());
		}
	}

	/** One line per 17 cells of canopy (an arm and an edge girder carry 4 cells each way), never on a boarding edge. */
	@Test
	void canopyColumnLinesAreSparseAndNeverOnABoardingEdge() {
		for (final PlatformLayout layout : PlatformLayout.values()) {
			for (int width = StationSpec.MIN_WIDTH; width <= StationSpec.MAX_WIDTH; width++) {
				final StationPlan plan = plan(StationSpec.builder().trackCount(2).platformLayout(layout).platformWidth(width).roof(RoofType.CANOPY).build());
				for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
					final List<Integer> lines = StationPlanner.columnLines(band, RoofType.CANOPY);
					final int canopy = StationPlanner.canopyTo(band) - StationPlanner.canopyFrom(band) + 1;
					final String where = layout + " width " + width + " platform " + band.number();
					assertEquals((canopy + 16) / 17, lines.size(), where);
					for (final int line : lines) {
						assertTrue(line >= band.uMin() && line <= band.uMax(), "line off the platform: " + where);
						assertFalse(band.trackLeft() && line == band.uMin() || band.trackRight() && line == band.uMax(), "line on a boarding edge: " + where);
					}
					assertEquals(new HashSet<>(lines), columnLines(plan, band), where);
				}
			}
		}
	}

	private static Set<Integer> columnLines(StationPlan plan, TrackLayout.PlatformBand band) {
		final Set<Integer> lines = new HashSet<>();
		for (final PlannedBlock cap : blocks(plan, Role.COLUMN_CAP)) {
			if (band.contains(cap.u(), cap.v())) {
				lines.add(cap.u());
			}
		}
		return lines;
	}

	@Test
	void nameSignsSitOnTheBackColumnOfSidePlatformsAndTheCentreOfIslands() {
		final StationPlan side = plan(StationSpec.builder().name("Alpha").roof(RoofType.NONE).furniture(false).platformLength(48).build());
		final List<PlannedBlock> signs = blocks(side, Role.NAME_SIGN);
		assertEquals(6, signs.size());
		for (final PlannedBlock sign : signs) {
			assertTrue(Set.of(23, 24, 25).contains(sign.v()), "sign at v=" + sign.v());
			assertEquals("Alpha", sign.text());
			assertEquals(sign.u() == 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U, sign.front());
			assertTrue(sign.u() == 0 || sign.u() == 14);
		}

		final StationPlan island = plan(StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformWidth(6).name("Alpha")
				.roof(RoofType.NONE).furniture(false).platformLength(48).build());
		final List<PlannedBlock> islandSigns = blocks(island, Role.NAME_SIGN);
		// one double-sided row down the middle of an island
		assertEquals(3, islandSigns.size());
		for (final PlannedBlock sign : islandSigns) {
			assertEquals(LocalDir.MINUS_U, sign.front());
			assertEquals(5, sign.u());
		}
	}

	@Test
	void nameSignsShiftAlongTheStationWhenFurnitureTakesTheirCells() {
		// island 3 wide, canopy, 49 built long: the canopy column on the centre line at v = 24 (canopyPositions(49): 2, 13, 24, 35, 46) blocks
		// 23..25, the first choice, and 25..27 is free
		final StationPlan plan = plan(StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformWidth(3).name("Alpha")
				.roof(RoofType.CANOPY).platformLength(48).build());
		final Set<Integer> signV = new HashSet<>();
		for (final PlannedBlock sign : blocks(plan, Role.NAME_SIGN)) {
			signV.add(sign.v());
		}
		assertEquals(Set.of(25, 26, 27), signV);
		assertTrue(StationPlanner.canopyPositions(49).contains(24));
		assertEquals(Set.of(4), blocks(plan, Role.COLUMN).stream().filter(c -> c.v() == 24).map(PlannedBlock::u).collect(java.util.stream.Collectors.toSet()));
	}

	/**
	 * Climbing a stair your head clears every step: three empty cells above each step (the third is where your head passes as
	 * you step up from it), and no bench under or beside a flight.
	 */
	@Test
	void stairsHaveHeadroomAndNoBenchUnderThem() {
		for (final String description : new String[]{"ten tracks island platforms canopies antwerp hub 101 long named Amsterdam Centraal",
				"four track dutch station, island platforms, canopies, 96 long", "six track hamburg station, 120 long",
				"two track station with station building, side platforms, canopies, 64 long"}) {
			final StationPlan plan = plan(StationDescriptionParser.parse(description).spec());
			final Map<String, PlannedBlock> at = new HashMap<>();
			for (final PlannedBlock b : plan.blocks()) {
				at.put(b.u() + "," + b.y() + "," + b.v(), b);
			}
			for (final PlannedBlock step : blocks(plan, Role.STAIR)) {
				for (int dy = 1; dy <= 3; dy++) {
					final PlannedBlock above = at.get(step.u() + "," + (step.y() + dy) + "," + step.v());
					assertTrue(above == null,
							description + ": " + above + " over the step " + step);
				}
			}
			for (final PlannedBlock bench : blocks(plan, Role.BENCH)) {
				for (int dy = 1; dy <= 2; dy++) {
					final PlannedBlock above = at.get(bench.u() + "," + (bench.y() + dy) + "," + bench.v());
					assertTrue(above == null || above.role() != Role.STAIR, description + ": bench under the stair at " + bench);
				}
			}
		}
	}

	@Test
	void shortStationsStillGetTheirNameSigns() {
		for (final PlatformLayout layout : PlatformLayout.values()) {
			for (final RoofType roof : RoofType.values()) {
				final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(layout).platformWidth(6).platformLength(16).roof(roof)
						.name("Alpha").build();
				// two side platforms, or one double-sided row on an island
				assertEquals(layout == PlatformLayout.ISLAND ? 3 : 6, blocks(plan(spec), Role.NAME_SIGN).size(), layout + " " + roof);
			}
		}
	}

	@Test
	void materialOverridesReachTheBlocks() {
		final StationSpec spec = StationSpec.builder().roof(RoofType.HALL).roofMaterial(RoofMaterial.GLASS).surface(SurfaceMaterial.GRANITE).build();
		final StationPlan plan = plan(spec);
		assertEquals("aurelia_transit_architecture:hamburg_glass_roof", blocks(plan, Role.ROOF).get(0).blockId());
		assertEquals("aurelia_transit_architecture:metro_granite_floor", blocks(plan, Role.PLATFORM_SURFACE).get(0).blockId());
		assertEquals("mtr:rail", blocks(plan, Role.RAIL_NODE).get(0).blockId());
	}

	@Test
	void everyWidthAndLengthFromTheBoundsIsValid() {
		final Set<Integer> lengths = Set.of(StationSpec.MIN_LENGTH, 17, 19, 20, 24, 33, 100, StationSpec.MAX_LENGTH);
		for (int width = StationSpec.MIN_WIDTH; width <= StationSpec.MAX_WIDTH; width++) {
			for (final int length : lengths) {
				for (int tracks = 1; tracks <= 4; tracks++) {
					for (final PlatformLayout layout : PlatformLayout.values()) {
						if (layout == PlatformLayout.ISLAND && tracks < 2) {
							continue;
						}
						for (final RoofType roof : RoofType.values()) {
							final StationSpec spec = StationSpec.builder().trackCount(tracks).platformLayout(layout).platformWidth(width).platformLength(length)
									.roof(roof).name("Alpha").build();
							final Set<Violation.Kind> kinds = EnumSet.noneOf(Violation.Kind.class);
							for (final Violation violation : LayoutValidator.validate(plan(spec))) {
								kinds.add(violation.kind());
							}
							assertEquals(Set.of(), kinds, describe(spec));
							assertFalse(blocks(plan(spec), Role.NAME_SIGN).isEmpty(), describe(spec));
						}
					}
				}
			}
		}
	}

	@Test
	void buildingsHaveShellAndFurnishingForEveryStyle() {
		for (final BuildingStyle style : BuildingStyle.values()) {
			if (style == BuildingStyle.NONE) {
				continue;
			}
			// ATA's gates, whose kinds are checked below (MTR's barriers: EnviroFixesTest)
			final StationSpec spec = StationSpec.builder().building(style).platformLength(48).name("Alpha")
					.options(StationOptions.DEFAULTS.withGates(StationOptions.GateStyle.ATA)).build();
			final StationPlan plan = plan(spec);
			assertEquals(List.of(), LayoutValidator.validate(plan), style.toString());
			assertTrue(StationAudit.audit(plan).buildable(), style.toString());
			// shell
			assertFalse(blocks(plan, Role.FLOOR).isEmpty(), style.toString());
			assertFalse(blocks(plan, Role.WALL).isEmpty(), style.toString());
			assertFalse(blocks(plan, Role.WINDOW).isEmpty(), style.toString());
			assertFalse(blocks(plan, Role.LIGHT_PANEL).isEmpty(), style.toString());
			assertFalse(blocks(plan, Role.BUILDING_ROOF).isEmpty(), style.toString());
			assertFalse(blocks(plan, Role.STAIR).isEmpty(), style.toString());
			// gates with kinds, barriers elsewhere
			final List<PlannedBlock> gates = blocks(plan, Role.GATE);
			assertTrue(gates.size() >= 5, style + " gates " + gates.size());
			final Set<String> kinds = new java.util.HashSet<>();
			for (final PlannedBlock gate : gates) {
				kinds.add(gate.state());
			}
			assertTrue(kinds.contains("kind=end") && kinds.contains("kind=gate") && kinds.contains("kind=wide"), style + " " + kinds);
			assertFalse(blocks(plan, Role.BARRIER).isEmpty(), style.toString());
			// office window, ticket machines (2-tall with halves), kiosk, clock, boards
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().contains("booth_window") && b.front() == LocalDir.PLUS_V), style.toString());
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(PlanState.TICKET_MACHINE)), style.toString());
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().contains("passenger_info_kiosk")), style.toString());
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().contains("clock")), style.toString());
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().contains("concourse_board")), style.toString());
			assertFalse(blocks(plan, Role.BENCH).isEmpty(), style.toString());
			// entrance ramps on the apron pointing streetwards
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RAMP_LOWER && b.u() == 0 && b.front() == LocalDir.MINUS_U), style.toString());
			// footbridge comes with the building (two side platforms: outer platforms stay step-free)
			assertFalse(blocks(plan, Role.BRIDGE_DECK).isEmpty(), style.toString());
			assertFalse(blocks(plan, Role.BRIDGE_PIER).isEmpty(), style.toString());
			// supports cover the building footprint
			boolean covered = false;
			for (final Box box : plan.supportAreas()) {
				if (box.uMin() == 0 && box.uMax() == plan.layout().buildingWidth() - 1) {
					covered = true;
				}
			}
			assertTrue(covered, style.toString());
		}
	}

	@Test
	void buildingStationsAreValidForEveryLayoutAndSize() {
		for (int tracks = 1; tracks <= 4; tracks++) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				if (layout == PlatformLayout.ISLAND && tracks < 2) {
					continue;
				}
				// the parser lengthens a building's platforms to the shortest its stairs and lift fit beside
				for (final int length : new int[]{StationDescriptionParser.BUILDING_LIFT_LENGTH, 48, StationSpec.MAX_LENGTH}) {
					for (final int width : new int[]{StationSpec.MIN_WIDTH, StationSpec.MAX_WIDTH}) {
						for (final RoofType roof : new RoofType[]{RoofType.CANOPY, RoofType.NONE}) {
							final StationSpec spec = StationSpec.builder().trackCount(tracks).platformLayout(layout).platformLength(length)
									.platformWidth(width).roof(roof).building(BuildingStyle.MODERN).build();
							final StationPlan plan = plan(spec);
							assertEquals(List.of(), LayoutValidator.validate(plan), describe(spec));
							assertTrue(StationAudit.audit(plan).buildable(), describe(spec));
						}
					}
				}
			}
		}
	}

	@Test
	void footbridgeWithoutBuildingUsesStairs() {
		final StationSpec spec = StationSpec.builder().trackCount(3).platformLayout(PlatformLayout.ISLAND).footbridge(true).platformLength(48).build();
		final StationPlan plan = plan(spec);
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertFalse(blocks(plan, Role.BRIDGE_DECK).isEmpty());
		assertFalse(blocks(plan, Role.STAIR).isEmpty());
		// stairs climb along their front
		for (final PlannedBlock stair : blocks(plan, Role.STAIR)) {
			assertTrue(stair.front() == LocalDir.PLUS_V || stair.front() == LocalDir.MINUS_V, stair.toString());
		}
	}

	@Test
	void largeBuildingsSeatTheirUpperFloorOnce() {
		final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformLength(96)
				.building(BuildingStyle.AMSTERDAM).build();
		final StationPlan plan = plan(spec);
		// open: no glass wall across a bay (Jah, 10-22)
		assertTrue(plan.blocks().stream().noneMatch(b -> b.blockId().endsWith(":glass_wall")));
		// the concourse fit-out seats the floor in islands of back-to-back benches, not along the walls as well
		final List<PlannedBlock> upstairs = blocks(plan, Role.BENCH).stream().filter(b -> b.y() == StationGeometry.UPPER_WALK_Y).toList();
		for (final PlannedBlock seat : upstairs) {
			assertTrue(upstairs.stream().anyMatch(b -> b.u() == seat.u() && Math.abs(b.v() - seat.v()) == 1 && b.front() != seat.front()),
					"a seat outside the islands: " + seat);
		}
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":concourse_board") && b.y() > StationGeometry.UPPER_FLOOR_Y));
		// and the ground floor has its row of seats
		assertTrue(blocks(plan, Role.BENCH).stream().filter(b -> b.y() == StationGeometry.WALK_Y
				&& plan.layout().indoor(b.u(), b.v())).count() >= PlanFurniture.SEAT_ROW);
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
	}

	@Test
	void smallBuildingsHaveNoWaitingRoom() {
		final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.SIDE).platformLength(16)
				.building(BuildingStyle.MODERN).build();
		assertTrue(plan(spec).blocks().stream().noneMatch(b -> b.blockId().endsWith(":glass_wall")));
	}

	@Test
	void screensUnderACanopyAreFixedToAColumnClearOfTheStairs() {
		for (final CanopyShape shape : CanopyShape.values()) {
			final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformLength(64).platformWidth(6)
					.roof(RoofType.CANOPY).canopy(shape).footbridge(true).build();
			final StationPlan plan = plan(spec);
			final StationLayout site = StationLayout.of(spec, plan.layout());
			final TrackLayout.PlatformBand band = plan.layout().platforms().get(0);
			final List<PlannedBlock> screens = plan.blocks().stream().filter(b -> b.blockId().endsWith(":platform_pids")).toList();
			assertTrue(screens.size() >= 2, shape + ": a screen on a 64 long island");
			assertTrue(plan.blocks().stream().noneMatch(b -> b.blockId().endsWith(":hanging_platform_pids")), shape + ": nothing hangs from a girder");
			for (final PlannedBlock screen : screens) {
				// the girder-hung boards looked wrong beside the poles: a screen is fixed to a column, two blocks wide
				assertTrue(screen.u() >= band.uMin() + 1 && screen.u() <= band.uMax() - 1, shape + ": on a boarding edge: " + screen);
				assertTrue(screen.front().alongTrack(), "faces along the platform: " + screen);
				assertTrue(backsOntoPost(plan, screen) || joinedToAPost(plan, screen), shape + ": nothing to fix it to: " + screen);
				final int lo = Math.min(site.stairWallV(), site.landingV()) - 2;
				final int hi = Math.max(site.stairWallV(), site.landingV()) + 2;
				assertFalse(screen.v() >= lo && screen.v() <= hi, "screen at the stairs: " + screen);
			}
			assertEquals(List.of(), LayoutValidator.validate(plan), shape.name());
		}
	}

	@Test
	void wallUnitsOnPlatformsBackOntoAPost() {
		for (final RoofType roof : RoofType.values()) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(layout).platformLength(48).roof(roof).build();
				final StationPlan plan = plan(spec);
				final List<PlannedBlock> units = plan.blocks().stream().filter(b -> b.y() == StationGeometry.WALK_Y
						&& (b.blockId().endsWith(":help_point") || b.blockId().endsWith(":platform_pids"))).toList();
				assertEquals(plan.layout().platforms().size(), units.stream().filter(b -> b.blockId().endsWith(":help_point")).count(),
						"one help point per platform: " + describe(spec));
				for (final PlannedBlock unit : units) {
					assertTrue(unit.front().alongTrack(), "faces along the platform: " + unit + " in " + describe(spec));
					// a wall unit backs onto a post; a two-wide standing screen has its first block on the post and the
					// second joined to it along the platform
					assertTrue(backsOntoPost(plan, unit) || joinedToAPost(plan, unit),
							"nothing behind " + unit + " in " + describe(spec));
				}
			}
		}
	}

	/** The unit's cell along the platform backs onto a column or lamp post. */
	private static boolean backsOntoPost(StationPlan plan, PlannedBlock unit) {
		final int behind = unit.v() + (unit.front() == LocalDir.PLUS_V ? -1 : 1);
		return plan.blocks().stream().anyMatch(b -> b.u() == unit.u() && b.v() == behind && b.y() == StationGeometry.WALK_Y
				&& (b.role() == Role.COLUMN || b.role() == Role.LAMP || b.blockId().equals(StationPlanner.LAMP_POLE)));
	}

	/** The unit is one block of a screen whose neighbouring block backs onto a post. */
	private static boolean joinedToAPost(StationPlan plan, PlannedBlock unit) {
		for (final int du : new int[]{1, -1}) {
			final int u = unit.u() + du;
			final boolean neighbour = plan.blocks().stream().anyMatch(b -> b.u() == u && b.v() == unit.v() && b.y() == unit.y()
					&& b.blockId().equals(unit.blockId()));
			if (neighbour) {
				for (final PlannedBlock other : plan.blocks()) {
					if (other.u() == u && other.v() == unit.v() && other.y() == unit.y() && other.blockId().equals(unit.blockId())
							&& backsOntoPost(plan, other)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Test
	void everyPlatformGetsASpeakerUnderTheRoofOrOnAPost() {
		for (final RoofType roof : RoofType.values()) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(layout).platformLength(48).roof(roof).build();
				final StationPlan plan = plan(spec);
				for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
					final List<PlannedBlock> speakers = plan.blocks().stream().filter(b -> b.blockId().endsWith("_speaker") && band.contains(b.u(), b.v())).toList();
					assertEquals(2, speakers.size(), "a speaker every 32 blocks of a 48 long platform: " + describe(spec));
					for (final PlannedBlock speaker : speakers) {
						if (speaker.blockId().endsWith(":ceiling_speaker")) {
							assertTrue(speaker.y() >= StationGeometry.WALK_Y + 2, "over head height: " + speaker + " in " + describe(spec));
							assertTrue(plan.blocks().stream().anyMatch(b -> b.u() == speaker.u() && b.v() == speaker.v() && b.y() == speaker.y() + 1 && b.role().covers()),
									"nothing to hang from: " + speaker + " in " + describe(spec));
						} else {
							final int behind = speaker.v() + (speaker.front() == LocalDir.PLUS_V ? -1 : 1);
							assertTrue(plan.blocks().stream().anyMatch(b -> b.u() == speaker.u() && b.v() == behind && b.y() == speaker.y()
									&& (b.role() == Role.COLUMN || b.role() == Role.LAMP)), "nothing behind " + speaker + " in " + describe(spec));
						}
					}
				}
			}
		}
	}

	@Test
	void terminusStationsAreValidForEveryLayoutAndSize() {
		final List<String> problems = new ArrayList<>();
		for (int tracks = 1; tracks <= 4; tracks++) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				if (layout == PlatformLayout.ISLAND && tracks < 2) {
					continue;
				}
				for (final int length : new int[]{StationSpec.MIN_LENGTH, 48, StationSpec.MAX_LENGTH}) {
					for (final int width : new int[]{StationSpec.MIN_WIDTH, StationSpec.MAX_WIDTH}) {
						for (final RoofType roof : RoofType.values()) {
							for (final boolean stepFree : new boolean[]{true, false}) {
								final StationSpec spec = StationSpec.builder().trackCount(tracks).platformLayout(layout).platformLength(length)
										.platformWidth(width).roof(roof).stepFree(stepFree).building(BuildingStyle.GOTHIC_BRICK).terminus(true).build();
								final StationPlan plan = plan(spec);
								final List<Violation> violations = LayoutValidator.validate(plan);
								if (!violations.isEmpty()) {
									problems.add(describe(spec) + " violations " + violations.subList(0, Math.min(3, violations.size())));
								}
								final StationAudit.Result audit = StationAudit.audit(plan);
								if (!audit.problems().isEmpty()) {
									problems.add(describe(spec) + " checks " + audit.problems());
								}
							}
						}
					}
				}
			}
		}
		assertEquals(List.of(), problems.subList(0, Math.min(8, problems.size())), problems.size() + " problems");
	}

	@Test
	void terminusTracksEndAtBuffersInFrontOfTheHeadBuilding() {
		final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformLength(48).roof(RoofType.HALL)
				.building(BuildingStyle.GOTHIC_BRICK).terminus(true).build();
		final StationPlan plan = plan(spec);
		final TrackLayout layout = plan.layout();
		final int length = layout.platformLength();
		assertEquals(0, layout.buildingWidth(), "a terminus has no building beside the tracks");
		assertEquals(layout.headEnd() + StationGeometry.HEAD_APRON, layout.vMax());
		for (final TrackLayout.Track track : layout.tracks()) {
			// the track runs on past its platform's end for an overrun, a rail node at each, to buffers across the track
			final int end = layout.trackEnd(track);
			assertEquals(layout.platformOf(track).vTo() + StationGeometry.overrun(StationGeometry.builtLength(48)), end);
			for (int u = track.u() - 1; u <= track.u() + 1; u++) {
				final int cu = u;
				assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.BUFFER_STOP && b.u() == cu && b.v() == end + 1), "no buffer at u=" + u);
			}
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RAIL_NODE && b.u() == track.u() && b.v() == end));
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RAIL_NODE && b.u() == track.u() && b.v() == layout.platformOf(track).vTo()));
		}
		for (final Box envelope : plan.trackEnvelopes()) {
			assertEquals(length - 2, envelope.vMax(), "clearance must stop at the buffers");
		}
		// ramps only at the open end; the platforms run level into the concourse
		assertTrue(plan.blocks().stream().filter(b -> b.role() == Role.RAMP_LOWER || b.role() == Role.RAMP_UPPER)
				.allMatch(b -> b.v() < 0 || b.v() > layout.headEnd()));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.GATE && b.v() > length && b.v() <= layout.headEnd()),
				"the gate line is across the hall, not on the platforms");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":booth_window")));
		assertFalse(blocks(plan, Role.BEAM).isEmpty(), "the train shed (hall roof) is kept at a terminus");
		assertEquals(List.of(), LayoutValidator.validate(plan));
	}

	@Test
	void terminusIslandsAreReachedStepFreeThroughTheGateLine() {
		final StationSpec spec = StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformLength(48)
				.building(BuildingStyle.ANTWERP).terminus(true).build();
		final StationPlan plan = plan(spec);
		assertEquals(List.of(StationAudit.Access.STEP_FREE), StationAudit.audit(plan).access());
		// close the gate line: the island is then only reached across a track from its open end
		final List<PlannedBlock> closed = new ArrayList<>();
		for (final PlannedBlock b : plan.blocks()) {
			closed.add(b.role() == Role.GATE ? new PlannedBlock(b.u(), b.y(), b.v(), Role.BARRIER, b.blockId(), b.front(), b.text(), "") : b);
		}
		final StationPlan blocked = new StationPlan(plan.spec(), plan.layout(), closed, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(),
				plan.foundationId());
		assertEquals(List.of(StationAudit.Access.AT_GRADE), StationAudit.audit(blocked).access());
	}

	@Test
	void terminusNeedsAHeadBuildingAndNoFootbridge() {
		assertThrows(IllegalArgumentException.class, () -> StationSpec.builder().terminus(true).build());
		assertThrows(IllegalArgumentException.class, () -> StationSpec.builder().building(BuildingStyle.MODERN).terminus(true).footbridge(true).build());
	}

	@Test
	void shedMatrixIsSoundForEveryShapeBuildingAndLayout() {
		final List<String> failures = new ArrayList<>();
		int specs = 0;
		for (final ShedShape shape : ShedShape.values()) {
			for (int tracks = 1; tracks <= 4; tracks++) {
				for (final PlatformLayout layout : PlatformLayout.values()) {
					if (layout == PlatformLayout.ISLAND && tracks < 2 || layout == PlatformLayout.SIDE && tracks >= 3) {
						continue;
					}
					for (final BuildingStyle building : BuildingStyle.values()) {
						for (final int mode : new int[] {0, 1, 2}) {
							// 0: platforms only, 1: building beside the tracks (with its footbridge), 2: terminus
							if (mode > 0 && building == BuildingStyle.NONE || mode == 0 && building != BuildingStyle.NONE) {
								continue;
							}
							for (final int length : new int[] {32, 64, 97}) {
								final StationSpec spec = StationSpec.builder().trackCount(tracks).platformLayout(layout).roof(RoofType.HALL).shed(shape)
										.building(building).terminus(mode == 2).platformLength(length)
										.platformWidth(layout == PlatformLayout.ISLAND ? 6 : 4).name("Alpha").build();
								specs++;
								final StationPlan plan = plan(spec);
								final String label = shape + " " + tracks + "x" + layout + " " + building + " mode" + mode + " L" + length;
								final List<Violation> violations = LayoutValidator.validate(plan);
								if (!violations.isEmpty()) {
									failures.add(label + ": " + violations.get(0));
								}
								final StationAudit.Result report = StationAudit.audit(plan);
								if (!report.buildable()) {
									failures.add(label + ": audit " + report.problems());
								}
								final List<PlannedBlock> floating = unconnected(plan);
								if (!floating.isEmpty()) {
									failures.add(label + ": " + floating.size() + " floating, first " + floating.get(0));
								}
								for (final PlannedBlock block : plan.blocks()) {
									for (final TrackLayout.Track track : plan.layout().tracks()) {
										if (block.role() != Role.RAIL_NODE && block.role() != Role.BUFFER_STOP
												&& Math.abs(block.u() - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH && block.y() >= 0
												&& block.y() < StationGeometry.ENVELOPE_HEIGHT && block.v() <= plan.layout().trackEnd()) {
											failures.add(label + ": " + block + " in the envelope of track " + track.number());
										}
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(specs > 300, "specs " + specs);
		assertEquals(List.of(), failures.subList(0, Math.min(20, failures.size())), failures.size() + " failures of " + specs);
	}

	/**
	 * A grand terminus front follows its look, after the photo study's four kinds of terminal: towers on brick, giant arches,
	 * a sculpted attic on a flat roof, or glass with the city's own extra.
	 */
	@Test
	void terminusFrontsFollowTheLook() {
		final Map<String, StationPlan> plans = new HashMap<>();
		for (final String look : List.of("amsterdam", "st pancras", "tokyo", "zurich", "antwerp", "hamburg", "frankfurt", "grand central", "paris",
				"milan", "munich", "berlin", "hong kong", "beijing")) {
			final ParseResult result = StationDescriptionParser.parse("eight track " + look + " terminus, four island platforms, 160 long, grand building");
			assertEquals(List.of(), result.warnings(), look);
			final StationPlan plan = plan(result.spec());
			assertEquals(List.of(), LayoutValidator.validate(plan), look);
			assertTrue(StationAudit.audit(plan).buildable(), look + " " + StationAudit.audit(plan).problems());
			plans.put(look, plan);
		}
		// brick: two towers for Amsterdam, a spire higher than any pyramid for St Pancras, three dark slate domes (not copper)
		// for Tokyo
		assertEquals(2, count(plans.get("amsterdam"), "minecraft:lightning_rod"));
		assertTrue(top(plans.get("st pancras"), "minecraft:lightning_rod") > top(plans.get("amsterdam"), "minecraft:lightning_rod") + 2);
		assertEquals(3, count(plans.get("tokyo"), "minecraft:lightning_rod"));
		assertEquals(0, count(plans.get("tokyo"), "minecraft:oxidized_cut_copper"));
		// arches: one, one, two and three runs of arch glass across the front at head height
		assertEquals(1, archRuns(plans.get("zurich")));
		assertEquals(1, archRuns(plans.get("antwerp")));
		assertEquals(2, archRuns(plans.get("hamburg")));
		assertEquals(3, archRuns(plans.get("frankfurt")));
		// Beaux-Arts: columns and statues on a flat roof (no roof stairs over the head), Paris with the most statues, Milan's
		// stepped attic the tallest
		for (final String look : List.of("grand central", "paris", "milan")) {
			final StationPlan plan = plans.get(look);
			assertTrue(count(plan, "minecraft:quartz_pillar") > 20, look);
			assertTrue(head(plan).noneMatch(b -> b.role() == Role.BUILDING_ROOF && b.blockId().endsWith("_stairs")), look + " roof is flat");
		}
		assertTrue(count(plans.get("paris"), "minecraft:chiseled_quartz_block") > count(plans.get("grand central"), "minecraft:chiseled_quartz_block"));
		assertTrue(top(plans.get("milan"), "minecraft:quartz_pillar") > top(plans.get("grand central"), "minecraft:quartz_pillar"));
		// glass: Munich's canopy and Beijing's upturned roof over the street, Berlin's glass tower, Hong Kong's wave
		final int front = plans.get("munich").layout().headEnd();
		assertTrue(head(plans.get("munich")).anyMatch(b -> b.role() == Role.BUILDING_ROOF && b.v() == front + 2 && b.y() == StationGeometry.UPPER_FLOOR_Y));
		final StationPlan beijing = plans.get("beijing");
		assertTrue(head(beijing).anyMatch(b -> b.role() == Role.BUILDING_ROOF && b.v() == beijing.layout().headEnd() + 2 && b.y() > StationGeometry.BUILDING_ROOF_Y));
		assertTrue(head(plans.get("berlin")).filter(b -> b.role() == Role.WINDOW).mapToInt(PlannedBlock::y).max().orElse(0)
				> head(plans.get("munich")).filter(b -> b.role() == Role.WINDOW).mapToInt(PlannedBlock::y).max().orElse(0) + 10);
		assertTrue(head(plans.get("hong kong")).filter(b -> b.role() == Role.BUILDING_ROOF).mapToInt(PlannedBlock::y).max().orElse(0)
				> head(plans.get("munich")).filter(b -> b.role() == Role.BUILDING_ROOF).mapToInt(PlannedBlock::y).max().orElse(0) + 3);
	}

	/**
	 * The Asian heritage looks read as their stations: Bangkok's great segmental window rising over the roof with a white
	 * colonnade and a clock but no statues; Kuala Lumpur's flat-topped centre, corner pavilions high over the parapet, kiosks,
	 * pinnacles and ground-floor arcade; Mumbai's onion-domed clock tower between spired Gothic towers, its red tile roof and
	 * its banded pointed arches.
	 */
	@Test
	void asianHeritageFrontsFollowTheirStations() {
		final Map<String, StationPlan> plans = new HashMap<>();
		for (final String look : List.of("bangkok", "kuala lumpur", "mumbai")) {
			for (final String size : List.of("city", "grand")) {
				final ParseResult result = StationDescriptionParser.parse("eight track " + look + " terminus, four island platforms, 160 long, " + size + " building");
				assertEquals(List.of(), result.warnings(), look);
				final StationPlan plan = plan(result.spec());
				assertEquals(List.of(), LayoutValidator.validate(plan), look + " " + size);
				assertTrue(StationAudit.audit(plan).buildable(), look + " " + size + " " + StationAudit.audit(plan).problems());
				plans.put(look + " " + size, plan);
			}
		}
		final int grandRoof = StationGeometry.BUILDING_ROOF_Y + 4 * StationScale.GRAND.extraStoreys();
		final StationPlan bangkok = plans.get("bangkok grand");
		final int bangkokFront = bangkok.layout().headEnd();
		assertTrue(head(bangkok).filter(b -> b.v() == bangkokFront && b.y() > grandRoof && b.blockId().endsWith(":clear_float_glass")).count() > 20,
				"the great window rises over the roof");
		assertTrue(count(bangkok, "minecraft:quartz_pillar") > 20, "colonnade");
		assertEquals(0, count(bangkok, "minecraft:chiseled_quartz_block"), "no statues");
		assertTrue(head(bangkok).anyMatch(b -> b.blockId().endsWith("clock_face_panel")));
		final StationPlan kl = plans.get("kuala lumpur grand");
		assertTrue(count(kl, "minecraft:end_rod") >= 4, "pinnacles and kiosks along the parapet");
		assertEquals(2, count(kl, "minecraft:lightning_rod"), "a dome on each corner pavilion, none over the centre");
		assertTrue(count(kl, "minecraft:calcite") > 20, "white domes");
		assertTrue(top(kl, "minecraft:calcite") > grandRoof + 12, "the corner pavilions rise high over the parapet");
		assertTrue(head(kl).anyMatch(b -> b.role() == Role.WINDOW && b.y() < StationGeometry.UPPER_FLOOR_Y && b.v() == kl.layout().headEnd()), "arcade");
		final StationPlan mumbai = plans.get("mumbai grand");
		assertTrue(count(mumbai, "minecraft:lightning_rod") >= 3, "an onion dome over the clock tower and two spired towers");
		assertTrue(count(mumbai, "minecraft:cut_sandstone") > 10, "the stone dome");
		assertTrue(top(mumbai, "minecraft:cut_sandstone") > top(plans.get("mumbai city"), "minecraft:cut_sandstone"), "taller at grand size");
		assertTrue(count(mumbai, "minecraft:brick_stairs") > 20, "a red tile roof");
		assertTrue(count(mumbai, "minecraft:smooth_red_sandstone") > 5, "banded pointed arches");
	}

	/**
	 * A gravel bed runs wall to wall: every cell across the tracks is ballast, lit strip or black concrete under a platform,
	 * with no holes between neighbouring tracks; and under a high train shed the platform screens are two blocks joined
	 * into one display fixed to a column above head height, not one block on the floor.
	 */
	@Test
	void trackBedsAreFullAndShedScreensHangOnColumns() {
		final ParseResult result = StationDescriptionParser.parse("ten track tokyo terminus, five island platforms, 240 long, gravel bed, train shed, grand building");
		final StationPlan plan = plan(result.spec());
		assertTrue(StationAudit.audit(plan).buildable(), StationAudit.audit(plan).problems().toString());
		final Map<String, PlannedBlock> at = new HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			at.put(block.u() + "," + block.y() + "," + block.v(), block);
		}
		final int uMin = plan.blocks().stream().filter(b -> b.role() == Role.TRACK_BED).mapToInt(PlannedBlock::u).min().orElseThrow();
		final int uMax = plan.blocks().stream().filter(b -> b.role() == Role.TRACK_BED).mapToInt(PlannedBlock::u).max().orElseThrow();
		for (final int v : new int[]{20, plan.layout().platformLength() / 2, plan.layout().platformLength() - 20}) {
			for (int u = uMin; u <= uMax; u++) {
				final PlannedBlock bed = at.get(u + ",-1," + v);
				assertTrue(bed != null, "a hole in the bed at u " + u + ", v " + v);
				final PlannedBlock above = at.get(u + "," + StationGeometry.PLATFORM_Y + "," + v);
				if (above != null && above.role().platformFloor()) {
					assertEquals("minecraft:black_concrete", bed.blockId(), "under the platform at u " + u);
				}
			}
		}
		// on a column, or hung across the gap between two
		final List<PlannedBlock> screens = plan.blocks().stream().filter(b -> b.blockId().endsWith("platform_pids")).toList();
		assertFalse(screens.isEmpty());
		final Map<String, List<Integer>> displays = new TreeMap<>();
		for (final PlannedBlock screen : screens) {
			assertTrue(screen.y() > StationGeometry.WALK_Y + 1, "above head height, not on the floor");
			displays.computeIfAbsent(screen.y() + "," + screen.v() + "," + screen.front(), k -> new ArrayList<>()).add(screen.u());
		}
		// a display is a run of blocks side by side: two fixed to a column behind them, or the whole gap from one column to the next
		for (final Map.Entry<String, List<Integer>> row : displays.entrySet()) {
			final List<Integer> us = row.getValue().stream().sorted().toList();
			final String[] key = row.getKey().split(",");
			final int y = Integer.parseInt(key[0]);
			final int v = Integer.parseInt(key[1]);
			int start = 0;
			for (int i = 1; i <= us.size(); i++) {
				if (i < us.size() && us.get(i) == us.get(i - 1) + 1) {
					continue;
				}
				final List<Integer> run = us.subList(start, i);
				start = i;
				assertTrue(run.size() >= 2, "one display, at least two blocks wide: " + row.getKey() + " " + run);
				final boolean onColumn = run.size() == 2 && run.stream().anyMatch(u -> screens.stream()
						.anyMatch(o -> o.y() == y && o.v() == v && o.u() == u && isColumnBehind(at, o)));
				final PlannedBlock left = at.get((run.get(0) - 1) + "," + y + "," + v);
				final PlannedBlock right = at.get((run.get(run.size() - 1) + 1) + "," + y + "," + v);
				final boolean between = left != null && left.role() == Role.COLUMN && right != null && right.role() == Role.COLUMN;
				assertTrue(onColumn || between, "each display is fixed to a column or spans between two: " + row.getKey() + " " + run);
			}
		}
	}

	private static boolean isColumnBehind(Map<String, PlannedBlock> at, PlannedBlock screen) {
		final int v = screen.v() + (screen.front() == LocalDir.PLUS_V ? -1 : 1);
		final PlannedBlock behind = at.get(screen.u() + "," + screen.y() + "," + v);
		return behind != null && behind.role() == Role.COLUMN;
	}

	/** The head building's blocks: from the platform ends on. */
	private static java.util.stream.Stream<PlannedBlock> head(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.v() >= plan.layout().platformLength());
	}

	private static long count(StationPlan plan, String id) {
		return head(plan).filter(b -> b.blockId().equals(id)).count();
	}

	private static int top(StationPlan plan, String id) {
		return head(plan).filter(b -> b.blockId().equals(id)).mapToInt(PlannedBlock::y).max().orElse(-1);
	}

	/** Separate runs of arch glass along the street front, eight rows up. */
	private static int archRuns(StationPlan plan) {
		final int front = plan.layout().headEnd();
		final Set<Integer> glass = new HashSet<>();
		head(plan).filter(b -> b.v() == front && b.y() == 8 && b.blockId().endsWith(":clear_float_glass")).forEach(b -> glass.add(b.u()));
		int runs = 0;
		for (final int u : glass) {
			// count a run at its first cell; glazing bars split a run every three cells, so look up to three back
			if (!glass.contains(u - 1) && !glass.contains(u - 2) && !glass.contains(u - 3)) {
				runs++;
			}
		}
		return runs;
	}

	@Test
	void hallRowsKeptOffStairsAndDoorsLeaveNoOverlongGirder() {
		// the footbridge stair on a wide side platform's column line and the building's doorways once left a 21-cell gap
		final StationPlan plan = plan(StationDescriptionParser.parse("with a station building train shed 75 long 2 tracks side platforms 8 wide").spec());
		assertEquals(List.of(), LayoutValidator.validate(plan));
	}

	@Test
	void theBuildingStairLandingIsLit() {
		// the stair well once swallowed the ceiling panel over its landing
		final StationPlan plan = plan(StationDescriptionParser.parse("with a station building canopies 42 long 2 tracks island platforms").spec());
		assertTrue(StationAudit.audit(plan).buildable(), () -> StationAudit.audit(plan).problems().toString());
	}
}
