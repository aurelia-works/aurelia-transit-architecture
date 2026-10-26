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
}
