package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Canopy roof shapes: which look gets which, how they are asked for, and the profile each one builds. */
class CanopyShapeTest {

	private static final List<CanopyShape> SHAPED = List.of(CanopyShape.WAVE, CanopyShape.BUTTERFLY, CanopyShape.GABLE);

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private record Cell(int u, int y, int v) {
	}

	/** Blocks not joined to the ground (y <= 0) through a chain of face-touching blocks: a raised roof must be braced, not hang by an edge. */
	private static List<PlannedBlock> floating(StationPlan plan) {
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
		final List<PlannedBlock> out = new ArrayList<>();
		for (final Map.Entry<Cell, PlannedBlock> entry : byCell.entrySet()) {
			if (!reached.contains(entry.getKey())) {
				out.add(entry.getValue());
			}
		}
		return out;
	}

	/** The problem with a plan, or null: a validator violation, a failed station check, or a block hanging by an edge. */
	private static String problem(StationPlan plan) {
		final List<Violation> violations = LayoutValidator.validate(plan);
		if (!violations.isEmpty()) {
			return violations.get(0).toString();
		}
		final StationAudit.Result audit = StationAudit.audit(plan);
		if (!audit.buildable()) {
			return "audit " + audit.problems();
		}
		// (the platform number sign that hangs beside a footbridge pier on a very wide platform is not the canopy's concern)
		final List<PlannedBlock> loose = floating(plan).stream().filter(b -> b.role() != Role.FIXTURE).toList();
		return loose.isEmpty() ? null : loose.size() + " floating, first " + loose.get(0);
	}

	/** The y of the roof (roof, edge and lamp cells) at each u of one row v, or -1 where there is none. */
	private static Map<Integer, Integer> roofRow(StationPlan plan, int v) {
		final Map<Integer, Integer> row = new HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.v() == v && (block.role() == Role.ROOF || block.role() == Role.ROOF_EDGE || block.role() == Role.ROOF_LIGHT)) {
				row.put(block.u(), block.y());
			}
		}
		return row;
	}

	/** The y of the roof at each v along one column u (the cell with the highest y when there are several). */
	private static Map<Integer, Integer> roofColumn(StationPlan plan, int u) {
		final Map<Integer, Integer> column = new HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.u() == u && (block.role() == Role.ROOF || block.role() == Role.ROOF_EDGE || block.role() == Role.ROOF_LIGHT)) {
				column.merge(block.v(), block.y(), Math::max);
			}
		}
		return column;
	}

	/** The u of the platform's column line. */
	private static int columnU(StationPlan plan) {
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.COLUMN_CAP) {
				return block.u();
			}
		}
		throw new AssertionError("no column");
	}

	@Test
	void looksChooseTheirCanopies() {
		assertEquals(CanopyShape.WAVE, CanopyShape.forLook(BuildingStyle.MODERN, StationStyle.DUTCH));
		assertEquals(CanopyShape.WAVE, CanopyShape.forLook(BuildingStyle.HONG_KONG, StationStyle.METRO));
		for (final BuildingStyle look : List.of(BuildingStyle.BERLIN, BuildingStyle.CONCRETE, BuildingStyle.SANDSTONE, BuildingStyle.CLINKER)) {
			assertEquals(CanopyShape.BUTTERFLY, CanopyShape.forLook(look, StationStyle.MODERN), look.name());
		}
		for (final BuildingStyle look : List.of(BuildingStyle.GOTHIC_BRICK, BuildingStyle.AMSTERDAM, BuildingStyle.ANTWERP, BuildingStyle.PARIS,
				BuildingStyle.BEAUX_ARTS, BuildingStyle.TOKYO, BuildingStyle.ZURICH, BuildingStyle.MILAN)) {
			assertEquals(CanopyShape.GABLE, CanopyShape.forLook(look, StationStyle.MODERN), look.name());
		}
		assertEquals(CanopyShape.FLAT, CanopyShape.forLook(BuildingStyle.BEIJING, StationStyle.DUTCH));
		// without a building the platform style decides
		assertEquals(CanopyShape.WAVE, CanopyShape.forLook(BuildingStyle.NONE, StationStyle.DUTCH));
		assertEquals(CanopyShape.BUTTERFLY, CanopyShape.forLook(BuildingStyle.NONE, StationStyle.GERMAN_SBAHN));
		assertEquals(CanopyShape.FLAT, CanopyShape.forLook(BuildingStyle.NONE, StationStyle.MODERN));
		assertEquals(CanopyShape.FLAT, CanopyShape.forLook(BuildingStyle.NONE, StationStyle.METRO));
	}

	@Test
	void everyBuildingLookHasACanopy() {
		for (final BuildingStyle look : BuildingStyle.values()) {
			for (final StationStyle style : StationStyle.values()) {
				assertTrue(CanopyShape.forLook(look, style) != null);
			}
		}
	}

	@Test
	void shapeWordsAskForThatCanopy() {
		assertEquals(CanopyShape.WAVE, StationDescriptionParser.parse("wave canopies").spec().canopy());
		assertEquals(CanopyShape.WAVE, StationDescriptionParser.parse("wavy canopy").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("butterfly canopies").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("v roof").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("v-roof canopies").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("v shaped canopies").spec().canopy());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("gable canopies").spec().canopy());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("gabled roof").spec().canopy());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("heritage canopies").spec().canopy());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("pitched canopy").spec().canopy());
		for (final String text : List.of("wave", "butterfly", "gable", "v roof")) {
			final ParseResult result = StationDescriptionParser.parse(text);
			assertEquals(RoofType.CANOPY, result.spec().roof(), text + " asks for canopies");
			assertEquals(List.of(), result.warnings(), text);
			assertEquals(List.of(), result.ignored(), text);
			assertTrue(result.understood().stream().anyMatch(line -> line.contains("canopies")), text + " is noted");
		}
	}

	@Test
	void aShapeWordBeatsTheLook() {
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("utrecht station with gable canopies").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("st pancras station, butterfly canopies").spec().canopy());
		assertEquals(CanopyShape.WAVE, StationDescriptionParser.parse("hamburg, no station building, wave canopies").spec().canopy());
	}

	@Test
	void withoutAShapeWordTheLookDecides() {
		assertEquals(CanopyShape.FLAT, StationDescriptionParser.parse("").spec().canopy());
		assertEquals(CanopyShape.FLAT, StationDescriptionParser.parse("modern station with canopies").spec().canopy());
		assertEquals(CanopyShape.WAVE, StationDescriptionParser.parse("dutch station").spec().canopy());
		assertEquals(CanopyShape.WAVE, StationDescriptionParser.parse("utrecht station with canopies").spec().canopy());
		assertEquals(CanopyShape.WAVE, StationDescriptionParser.parse("hong kong station").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("s-bahn station").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("munich station with canopies").spec().canopy());
		assertEquals(CanopyShape.BUTTERFLY, StationDescriptionParser.parse("hamburg station, canopies").spec().canopy());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("london station, canopies").spec().canopy());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("paris station with canopies").spec().canopy());
		assertEquals(CanopyShape.FLAT, StationDescriptionParser.parse("beijing station").spec().canopy());
		// "no station building" keeps the look's canopy
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("amsterdam station with canopies, no station building").spec().canopy());
		assertTrue(StationDescriptionParser.parse("dutch station").defaulted().stream().anyMatch(line -> line.contains("wave canopies")));
	}

	@Test
	void aShapeWithoutCanopiesIsReported() {
		assertTrue(StationDescriptionParser.parse("wave canopies, no roof").warnings().stream().anyMatch(w -> w.contains("no roof")));
		assertTrue(StationDescriptionParser.parse("gable roof, arched train shed").warnings().stream().anyMatch(w -> w.contains("train shed")));
		assertEquals(RoofType.NONE, StationDescriptionParser.parse("wave canopies, no roof").spec().roof());
		assertEquals(RoofType.HALL, StationDescriptionParser.parse("gable roof, arched train shed").spec().roof());
	}

	@Test
	void twoShapesKeepTheFirst() {
		final ParseResult result = StationDescriptionParser.parse("wave canopies, gable canopies");
		assertEquals(CanopyShape.WAVE, result.spec().canopy());
		assertTrue(result.warnings().stream().anyMatch(w -> w.contains("canopy shape given twice")));
	}

	@Test
	void flatCanopiesAreOneLevelRow() {
		final StationPlan plan = plan("flat canopies 2 tracks island 8 wide 64 long");
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.ROOF || block.role() == Role.ROOF_EDGE || block.role() == Role.ROOF_LIGHT) {
				assertEquals(StationGeometry.CANOPY_Y, block.y());
			}
		}
	}

	@Test
	void everyShapePlansCleanlyAcrossLayoutsWidthsAndLengths() {
		final List<String> problems = new ArrayList<>();
		for (final CanopyShape shape : SHAPED) {
			for (final String layout : List.of("side", "island")) {
				for (final int tracks : new int[] {1, 2, 3, 4}) {
					for (final int width : new int[] {0, 3, 6, 8, 12, 20}) {
						for (final String bridge : new String[] {"", "with a footbridge"}) {
							for (final int length : new int[] {16, 40, 100}) {
								final String text = shape.label() + " canopies " + layout + " " + tracks + " tracks " + (width > 0 ? width + " wide " : "") + bridge + " "
										+ length + " long";
								if (layout.equals("island") && tracks < 2) {
									continue;
								}
								final String problem = problem(plan(text));
								if (problem != null) {
									problems.add(text + " -> " + problem);
								}
							}
						}
					}
				}
			}
		}
		assertEquals(List.of(), problems.subList(0, Math.min(10, problems.size())), problems.size() + " problems");
	}

	@Test
	void shapedCanopiesWithBuildingsAndLooksPlanCleanly() {
		final List<String> problems = new ArrayList<>();
		for (final String look : List.of("utrecht", "dutch", "s-bahn", "london", "paris", "hamburg", "munich", "frankfurt", "hong kong", "new york", "tokyo")) {
			for (final String extra : List.of("with a footbridge, escalators", "with a station building 3 tracks island", "grand building, 4 tracks", "2 tracks, 24 long")) {
				final String text = look + " station with canopies, " + extra;
				final String problem = problem(plan(text));
				if (problem != null) {
					problems.add(text + " -> " + problem);
				}
			}
		}
		assertEquals(List.of(), problems);
	}

	@Test
	void sampledShapedStationsPlanCleanly() {
		final String[] shapes = {"wave canopies", "butterfly canopies", "gable canopies"};
		final String[] kinds = {"", "with a station building", "city building", "grand building", "terminus", "hub"};
		final int[] tracks = {1, 2, 3, 4, 5, 6, 8, 10};
		final Random random = new Random(2024);
		final List<String> problems = new ArrayList<>();
		for (int round = 0; round < 250; round++) {
			final String kind = kinds[random.nextInt(kinds.length)];
			final boolean hub = kind.equals("hub");
			final int count = hub ? 4 + 2 * random.nextInt(4) : tracks[random.nextInt(tracks.length)];
			final String text = kind + " " + shapes[random.nextInt(shapes.length)] + " " + (hub || random.nextBoolean() ? "island" : "side") + " " + count
					+ " tracks " + (random.nextBoolean() ? (StationSpec.MIN_WIDTH + 1 + random.nextInt(17)) + " wide " : "")
					+ (!kind.equals("terminus") && !hub && random.nextBoolean() ? "with a footbridge " : "") + (16 + random.nextInt(120)) + " long";
			final String problem = problem(plan(text));
			if (problem != null) {
				problems.add(text + " -> " + problem);
			}
		}
		assertEquals(List.of(), problems);
	}

	@Test
	void butterflyIsLowOverTheColumnsAndHigherAtBothEdges() {
		final StationPlan plan = plan("butterfly canopies 2 tracks island 8 wide 64 long");
		final int line = columnU(plan);
		final Map<Integer, Integer> row = roofRow(plan, 20);
		final int from = row.keySet().stream().min(Integer::compare).orElseThrow();
		final int to = row.keySet().stream().max(Integer::compare).orElseThrow();
		assertEquals(StationGeometry.CANOPY_Y, row.get(line), "over the columns the roof is at the canopy level");
		assertEquals(StationGeometry.CANOPY_Y + 1, row.get(from), "the left edge is a block higher");
		assertEquals(StationGeometry.CANOPY_Y + 1, row.get(to), "the right edge is a block higher");
		// never dips below the valley and rises (not falls) away from it on both sides
		for (int u = line; u < to; u++) {
			assertTrue(row.get(u + 1) >= row.get(u), "rising to the right at u=" + u);
		}
		for (int u = line; u > from; u--) {
			assertTrue(row.get(u - 1) >= row.get(u), "rising to the left at u=" + u);
		}
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":canopy_slope_lower")));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":canopy_slope_upper")));
	}

	@Test
	void butterflySlopesPointTheirLowEndAtTheColumns() {
		final StationPlan plan = plan("butterfly canopies 2 tracks island 8 wide 64 long");
		final int line = columnU(plan);
		for (final PlannedBlock block : plan.blocks()) {
			if (block.blockId().contains("canopy_slope") && block.v() == 20) {
				assertEquals(block.u() > line ? LocalDir.MINUS_U : LocalDir.PLUS_U, block.front(), "slope at u=" + block.u());
			}
		}
	}

	@Test
	void gableRidgeIsHighestOverTheColumnsAndFallsToTheEdges() {
		final StationPlan plan = plan("gable canopies 2 tracks island 8 wide 64 long");
		final int line = columnU(plan);
		final Map<Integer, Integer> row = roofRow(plan, 20);
		final int from = row.keySet().stream().min(Integer::compare).orElseThrow();
		final int to = row.keySet().stream().max(Integer::compare).orElseThrow();
		final int ridge = row.get(line);
		assertTrue(ridge > StationGeometry.CANOPY_Y, "the ridge stands above the canopy level");
		for (final Map.Entry<Integer, Integer> cell : row.entrySet()) {
			assertTrue(cell.getValue() <= ridge, "u=" + cell.getKey() + " is not above the ridge");
		}
		assertEquals(StationGeometry.CANOPY_Y, row.get(from), "the eaves are at the canopy level, clear of the trains");
		assertEquals(StationGeometry.CANOPY_Y, row.get(to));
		for (int u = line; u < to; u++) {
			assertTrue(row.get(u + 1) <= row.get(u), "falling to the right at u=" + u);
		}
		for (int u = line; u > from; u--) {
			assertTrue(row.get(u - 1) <= row.get(u), "falling to the left at u=" + u);
		}
		// the slopes point their low end away from the ridge
		for (final PlannedBlock block : plan.blocks()) {
			if (block.blockId().contains("canopy_slope") && block.v() == 20) {
				assertEquals(block.u() > line ? LocalDir.PLUS_U : LocalDir.MINUS_U, block.front(), "slope at u=" + block.u());
			}
		}
	}

	@Test
	void aNarrowCanopyIsLeftLevel() {
		for (final String shape : List.of("butterfly", "gable")) {
			final StationPlan plan = plan(shape + " canopies 1 track side 3 wide 32 long");
			final Set<Integer> heights = new HashSet<>(roofRow(plan, 12).values());
			assertEquals(Set.of(StationGeometry.CANOPY_Y), heights, shape + " on a canopy too narrow for a slope");
		}
	}

	@Test
	void waveRisesAndFallsAlongThePlatformWithTheExpectedPeriod() {
		final StationPlan plan = plan("wave canopies 2 tracks island 8 wide 70 long");
		final int u = columnU(plan) + 1;
		final Map<Integer, Integer> column = roofColumn(plan, u);
		final int base = StationGeometry.CANOPY_Y;
		assertEquals(Set.of(base, base + 1), new HashSet<>(column.values()), "one block of amplitude");
		assertEquals(base, column.get(0), "begins low");
		assertEquals(base, column.get(69), "ends low");
		// each rise is two cells of curved blocks, then a flat high stretch
		final List<Integer> crests = new ArrayList<>();
		for (int v = 1; v < 70; v++) {
			if (column.get(v) > base && column.get(v - 1) == base) {
				crests.add(v);
			}
		}
		assertEquals(5, crests.size(), "70 cells hold five waves");
		for (int i = 0; i + 1 < crests.size(); i++) {
			final int period = crests.get(i + 1) - crests.get(i);
			assertTrue(period >= 12 && period <= 16, "period " + period);
		}
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":canopy_wave_rise") && b.front() == LocalDir.MINUS_V));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":canopy_wave_rise") && b.front() == LocalDir.PLUS_V));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":canopy_wave_flat") && b.front() == LocalDir.MINUS_V));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":canopy_wave_flat") && b.front() == LocalDir.PLUS_V));
	}

	@Test
	void waveIsTheSameAcrossThePlatform() {
		final StationPlan plan = plan("wave canopies 2 tracks island 8 wide 64 long");
		final int u = columnU(plan);
		final Map<Integer, Integer> left = roofColumn(plan, u - 1);
		final Map<Integer, Integer> right = roofColumn(plan, u + 1);
		assertEquals(left.keySet(), right.keySet());
		for (final int v : left.keySet()) {
			assertEquals(left.get(v), right.get(v), "v=" + v);
		}
	}

	@Test
	void everyShapeKeepsLampsAndTheHeadroomUnderTheGirders() {
		for (final CanopyShape shape : SHAPED) {
			final StationPlan plan = plan(shape.label() + " canopies 2 tracks island 8 wide 64 long");
			final long lamps = plan.blocks().stream().filter(b -> b.role() == Role.ROOF_LIGHT).count();
			final long flatLamps = plan("flat canopies 2 tracks island 8 wide 64 long").blocks().stream().filter(b -> b.role() == Role.ROOF_LIGHT).count();
			assertTrue(lamps * 2 >= flatLamps, shape + ": " + lamps + " lamps against " + flatLamps + " on a flat roof");
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.BEAM) {
					assertEquals(StationGeometry.CANOPY_Y - 1, block.y(), "girders stay at one level");
				}
				if (block.role() == Role.ROOF || block.role() == Role.ROOF_EDGE || block.role() == Role.ROOF_LIGHT) {
					assertTrue(block.y() >= StationGeometry.CANOPY_Y, shape + " roof at y=" + block.y());
				}
			}
		}
	}

	@Test
	void aFootbridgeKeepsItsCorridorFreeOfRoof() {
		for (final CanopyShape shape : SHAPED) {
			final StationPlan plan = plan(shape.label() + " canopies 2 tracks island 8 wide with a footbridge 64 long");
			assertEquals(List.of(), LayoutValidator.validate(plan), shape.name());
			final Map<String, PlannedBlock> at = new HashMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				at.put(block.u() + "," + block.y() + "," + block.v(), block);
			}
			for (final PlannedBlock deck : plan.blocks()) {
				if (deck.role() != Role.BRIDGE_DECK || deck.y() != StationGeometry.CANOPY_Y) {
					continue;
				}
				for (int y = deck.y() + 1; y < deck.y() + 3; y++) {
					final PlannedBlock above = at.get(deck.u() + "," + y + "," + deck.v());
					assertFalse(above != null && above.role().roofPart() && above.role() != Role.BEAM, shape + ": canopy roof inside the corridor at " + deck.u() + "," + y + "," + deck.v());
				}
			}
		}
	}

	@Test
	void recipeWordsAskForTheShapes() {
		final Map<StationRecipe.Roof, CanopyShape> wanted = Map.of(StationRecipe.Roof.WAVE, CanopyShape.WAVE, StationRecipe.Roof.BUTTERFLY, CanopyShape.BUTTERFLY,
				StationRecipe.Roof.GABLE, CanopyShape.GABLE, StationRecipe.Roof.CANOPIES, CanopyShape.WAVE);
		for (final Map.Entry<StationRecipe.Roof, CanopyShape> entry : wanted.entrySet()) {
			final StationRecipe recipe = StationRecipe.defaults().withRoof(entry.getKey());
			final ParseResult result = StationDescriptionParser.parse(recipe.description());
			assertEquals(List.of(), result.warnings(), recipe.description());
			assertEquals(RoofType.CANOPY, result.spec().roof());
			assertEquals(entry.getValue(), result.spec().canopy(), recipe.description());
			assertEquals(recipe, StationRecipe.decode(recipe.encode()));
		}
		// a wrench saved before the shapes still reads
		assertEquals(StationRecipe.Roof.CANOPIES, StationRecipe.decode("UTRECHT;;0;2;AUTO;64;CANOPIES;1;;AUTO;0").roof());
		assertEquals(StationRecipe.Roof.ARCHED, StationRecipe.decode("UTRECHT;;0;2;AUTO;64;ARCHED;1;;AUTO;0").roof());
	}

	@Test
	void theSpecCarriesTheCanopy() {
		final StationSpec spec = StationSpec.builder().canopy(CanopyShape.GABLE).build();
		assertEquals(CanopyShape.GABLE, spec.canopy());
		assertEquals(CanopyShape.GABLE, spec.toBuilder().build().canopy());
		assertEquals(CanopyShape.FLAT, StationSpec.defaults().canopy());
	}
}
