package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Airport words, geometry, plan, validator and audit (milestone 1: Station Studio shows an airport). */
class AirportPlanTest {

	static StationPlan plan(String words) {
		return StationPlanner.plan(StationDescriptionParser.parse(words).spec());
	}

	@Test
	void airportWordsReachTheSpec() {
		final AirportSpec a = StationDescriptionParser.parse("airport").spec().airport();
		assertTrue(a.present());
		assertEquals(AirportSpec.defaults(), a);
		assertEquals(2550, a.runwayLength());
		final AirportSpec b = StationDescriptionParser.parse("airport with 2 runways, 6 gates and a 1000 m runway, 30 wide").spec().airport();
		assertEquals(2, b.runways());
		assertEquals(6, b.gates());
		assertEquals(1500, b.runwayLength());
		assertEquals(30, b.runwayWidth());
		assertFalse(StationDescriptionParser.parse("airport, no terminal").spec().airport().terminal());
		assertFalse(StationDescriptionParser.parse("2 tracks").spec().isAirport());
	}

	@Test
	void unknownWordsAreIgnoredAndLimitsWarn() {
		final ParseResult parsed = StationDescriptionParser.parse("airport banana, 9000 m runway, 30 gates");
		assertTrue(parsed.ignored().contains("banana"));
		assertEquals(AirportSpec.MAX_RUNWAY_LENGTH, parsed.spec().airport().runwayLength());
		assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("runway")), parsed.warnings().toString());
		assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("gates")), parsed.warnings().toString());
	}

	@Test
	void geometryHasNoOverlapAndGatesFit() {
		for (final String words : new String[]{"airport", "small airport", "airport, 2 runways", "airport, no terminal"}) {
			final AirportSpec spec = StationDescriptionParser.parse(words).spec().airport();
			final AirportGeometry g = AirportGeometry.of(spec);
			assertEquals(spec.gates(), g.standV().size());
			assertTrue(g.apronV0() > g.runwayV0() + g.dims().linkInset() + g.dims().taxi(), words);
			assertTrue(g.apronV1() < g.runwayV1() - g.dims().linkInset() - g.dims().taxi(), words);
			final List<AirportGeometry.Area> areas = g.areas();
			for (final AirportGeometry.Area area : areas) {
				assertTrue(area.u0() >= 0 && area.u1() < g.width() && area.v0() >= 0 && area.v1() < g.length(), words + " " + area);
			}
		}
	}

	@Test
	void glyphsDrawDigitsAndRunwayLetters() {
		assertEquals(14, AirportGlyphs.width("09", 2));
		assertEquals(10, AirportGlyphs.height(2));
		assertTrue(AirportGlyphs.has('L') && AirportGlyphs.has('R') && AirportGlyphs.has('C'));
		// a zero is twelve lit pixels of the 3 x 5 grid, each four cells at scale 2
		assertEquals(12 * 4, AirportGlyphs.pixels("0", 2).size());
		assertEquals(0, AirportGlyphs.pixels("?", 1).size());
	}

	@Test
	void everyAirportPlansCleanlyAndPassesTheChecks() {
		for (final String words : new String[]{"small airport named A", "airport named B", "airport, 2 runways, 6 gates named C", "small airport, no terminal named D",
				"airport, 2 runways, 1000 m runway, 30 wide, 2 gates named E"}) {
			final StationPlan plan = plan(words);
			assertEquals(List.of(), LayoutValidator.validate(plan), words);
			final StationAudit.Result audit = StationAudit.audit(plan);
			assertEquals(List.of(), audit.problems(), words);
			assertTrue(audit.buildable(), words);
			assertEquals(0, plan.trackEnvelopes().size());
		}
	}

	@Test
	void planIsOrderedInBandsAlongTheRunway() {
		final StationPlan plan = plan("small airport");
		int band = Integer.MIN_VALUE;
		for (final PlannedBlock block : plan.blocks()) {
			final int b = Math.floorDiv(block.v(), PlanAirport.BAND);
			assertTrue(b >= band, "blocks go band by band");
			band = b;
		}
		int last = Integer.MIN_VALUE;
		for (final Box box : plan.supportAreas()) {
			final int b = Math.floorDiv(box.vMin(), PlanAirport.BAND);
			assertTrue(b >= last, "support boxes go band by band");
			assertEquals(b, Math.floorDiv(box.vMax(), PlanAirport.BAND), "a box lies in one band");
			last = b;
		}
	}

	@Test
	void anObstacleOnTheRunwayIsAViolation() {
		final StationPlan plan = plan("small airport");
		final AirportGeometry g = AirportGeometry.of(plan.spec().airport());
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(new PlannedBlock(g.runwayCentre(0), 2, (g.runwayV0() + g.runwayV1()) / 2, Role.FIXTURE, "minecraft:stone", LocalDir.PLUS_V, ""));
		final StationPlan bad = new StationPlan(plan.spec(), plan.layout(), blocks, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(), plan.foundationId());
		assertTrue(LayoutValidator.validate(bad).stream().anyMatch(v -> v.kind() == Violation.Kind.AIRFIELD_OBSTACLE));
	}

	@Test
	void randomAirportWordsPlanCleanly() {
		final Random r = new Random(7);
		final List<String> out = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			final StringBuilder d = new StringBuilder(r.nextBoolean() ? "small airport" : "airport");
			d.append(", ").append(1 + r.nextInt(2)).append(" runways");
			d.append(", ").append(1 + r.nextInt(12)).append(" gates");
			d.append(", ").append(500 + r.nextInt(900)).append(" long runway");
			d.append(", ").append(11 + r.nextInt(35)).append(" wide runway");
			if (r.nextInt(4) == 0) {
				d.append(", no terminal");
			}
			d.append(", named Sweep ").append(i);
			final StationPlan plan = plan(d.toString());
			final List<Violation> violations = LayoutValidator.validate(plan);
			final StationAudit.Result audit = StationAudit.audit(plan);
			if (!violations.isEmpty() || !audit.buildable()) {
				out.add(d + " -> " + violations.size() + " " + audit.problems());
			}
		}
		assertEquals(List.of(), out);
	}

	@Test
	void everyAirportPresetParsesWithoutWarningsAndPlansCleanly() {
		for (final AirportPresets.Preset preset : AirportPresets.ALL) {
			final ParseResult parsed = StationDescriptionParser.parse(preset.description());
			assertTrue(parsed.spec().isAirport(), preset.key());
			assertEquals(List.of(), parsed.warnings(), preset.key());
			assertEquals(List.of(), parsed.ignored(), preset.key());
			final StationPlan plan = StationPlanner.plan(parsed.spec());
			assertEquals(List.of(), LayoutValidator.validate(plan), preset.key());
			assertTrue(StationAudit.audit(plan).buildable(), preset.key());
		}
	}

	@Test
	void theAirportWrenchAlwaysPlansAnAirport() {
		assertEquals("airport", StationDescriptionParser.airportWords(""));
		assertEquals("airport, 3 gates", StationDescriptionParser.airportWords("3 gates"));
		assertEquals("small airport", StationDescriptionParser.airportWords("small airport"));
		assertTrue(StationDescriptionParser.parse(StationDescriptionParser.airportWords("2 track station")).spec().isAirport());
	}

	@Test
	void theControlTowerStandsOnTheApronOutsideTheRunwayStrip() {
		final StationPlan plan = plan("airport");
		final AirportGeometry g = AirportGeometry.of(plan.spec().airport());
		final AirportGeometry.Area tower = g.tower();
		assertTrue(tower.u0() > g.apronU0() && tower.u1() <= g.apronU1() && tower.v1() <= g.apronV1(), "the tower stands on the apron");
		for (final AirportGeometry.Area area : g.areas()) {
			if (area.kind() == AirportGeometry.Kind.RUNWAY || area.kind() == AirportGeometry.Kind.SHOULDER
					|| area.kind() == AirportGeometry.Kind.BLAST_PAD || area.kind() == AirportGeometry.Kind.TAXIWAY) {
				assertFalse(overlaps(tower, area), "the tower clears " + area);
			}
		}
		int blocks = 0;
		int top = 0;
		for (final PlannedBlock block : plan.blocks()) {
			if (tower.contains(block.u(), block.v()) && block.y() >= StationGeometry.WALK_Y) {
				blocks++;
				top = Math.max(top, block.y());
			}
		}
		assertTrue(blocks > 0, "the tower is planned");
		assertEquals(2 * g.dims().mast(), top, "the cab's roof is the tower's top");
	}

	@Test
	void rapidExitsRunFromTheRunwayToTheTaxiwayAt30Degrees() {
		final StationPlan plan = plan("airport");
		final AirportGeometry g = AirportGeometry.of(plan.spec().airport());
		final List<Integer> exits = g.rapidExits();
		assertEquals(2, exits.size());
		final int from = g.taxiU1() + 1;
		final int to = g.runwayU0().get(0) - 1;
		for (final int start : exits) {
			final int near = g.exitCentre(start, to);
			final int far = g.exitCentre(start, from);
			assertEquals(Math.sqrt(3), (double) (far - near) / (to - from), 0.05, "30 degrees off the runway");
			assertTrue(blockAt(plan, g.taxiU1(), far) != null, "the exit meets the taxiway");
			assertTrue(blockAt(plan, to, near) != null, "the exit meets the runway");
			for (int u = from; u <= to; u++) {
				final PlannedBlock centre = blockAt(plan, u, g.exitCentre(start, u));
				assertTrue(centre != null && PlanAirport.PAINT_YELLOW.equals(centre.blockId()), "the centreline runs across the exit at column " + u);
			}
		}
	}

	@Test
	void towerAndRapidExitsKeepTheValidatorAtZero() {
		for (final String words : new String[]{"airport", "airport, no terminal", "small airport"}) {
			assertEquals(List.of(), LayoutValidator.validate(plan(words)), words);
		}
	}

	@Test
	void digitsReadUprightFromTheTerminalSideOfAStandAndFromTheThreshold() {
		// "1" at scale 1: rows top to bottom 010, 110, 010, 010, 111 (a flag on the left, a foot along the bottom), set at column 10, row 20
		// a viewer on the terminal side facing out over the apron has +u as up and -v on the right, so the flag is at the high v side
		assertCells(List.of("14,20", "13,21", "13,20", "12,20", "11,20", "10,21", "10,20", "10,19"),
				PlanAirport.textCells(List.of("1"), 1, 10, 20, 1, 0));
		// a viewer at the threshold facing +v has +u on the right, so the flag is at the low u side
		assertCells(List.of("10,24", "9,23", "10,23", "10,22", "10,21", "9,20", "10,20", "11,20"),
				PlanAirport.textCells(List.of("1"), 1, 10, 20, 0, 1));
	}

	@Test
	void southendPresetIsItsRealRunwayAndPlansCleanly() {
		final String words = AirportPresets.ALL.stream().filter(p -> p.key().equals("southend")).findFirst().orElseThrow().description();
		final AirportSpec spec = StationDescriptionParser.parse(words).spec().airport();
		assertEquals(1, spec.runways());
		// 1,856 m at 1.5 blocks a metre
		assertEquals(2784, spec.runwayLength());
		assertEquals(5, spec.gates());
		assertTrue(spec.terminal());
		assertEquals(List.of(), LayoutValidator.validate(plan(words)));
	}

	static void assertCells(List<String> expected, List<int[]> actual) {
		assertEquals(expected.stream().sorted().toList(), actual.stream().map(c -> c[0] + "," + c[1]).sorted().toList());
	}

	static boolean overlaps(AirportGeometry.Area a, AirportGeometry.Area b) {
		return a.u0() <= b.u1() && b.u0() <= a.u1() && a.v0() <= b.v1() && b.v0() <= a.v1();
	}

	static PlannedBlock blockAt(StationPlan plan, int u, int v) {
		for (final PlannedBlock block : plan.blocks()) {
			if (block.u() == u && block.v() == v && block.y() == StationGeometry.PLATFORM_Y) {
				return block;
			}
		}
		return null;
	}
}
