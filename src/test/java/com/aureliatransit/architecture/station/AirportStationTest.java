package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The railway station under an airport's terminal (Jah 10-26): words, fit, the composed plan, its checks and its MTR rails. */
class AirportStationTest {

	private record C(int u, int y, int v) {
	}

	private static final String WORDS = "airport with a train station named Aurelia Airport";

	private static StationPlan plan(String words) {
		return StationPlanner.plan(StationDescriptionParser.parse(words).spec());
	}

	@Test
	void railWordsAskForAStationAndSayWhenItDoesNotFit() {
		for (final String words : new String[]{"airport with a train station", "airport with transit", "airport, rail link", "airport with a metro station"}) {
			final ParseResult parsed = StationDescriptionParser.parse(words);
			assertTrue(parsed.spec().airport().rail(), words);
			assertTrue(parsed.ignored().isEmpty(), words + " " + parsed.ignored());
		}
		assertFalse(StationDescriptionParser.parse("airport").spec().airport().rail());
		assertFalse(StationDescriptionParser.parse("airport, no train station").spec().airport().rail());
		final ParseResult small = StationDescriptionParser.parse("small airport with a train station");
		assertFalse(small.spec().airport().rail());
		assertTrue(small.warnings().stream().anyMatch(w -> w.contains("railway station")), small.warnings().toString());
		final ParseResult remote = StationDescriptionParser.parse("airport, no terminal, train station");
		assertFalse(remote.spec().airport().rail());
		assertTrue(remote.warnings().stream().anyMatch(w -> w.contains("railway station")), remote.warnings().toString());
	}

	@Test
	void theBoxFitsTheFootprintItIsGiven() {
		for (final String words : new String[]{WORDS, "airport with 2 gates and a train station", "airport, 12 gates, train station"}) {
			final StationSpec spec = StationDescriptionParser.parse(words).spec();
			final AirportStation rail = AirportStation.of(spec);
			assertNotNull(rail, words);
			final StationPlan station = StationPlanner.plan(rail.spec());
			assertEquals(rail.layout(), station.layout(), words);
			final int length = rail.layout().platformLength();
			for (final PlannedBlock b : station.blocks()) {
				assertTrue(b.u() >= 0 && b.u() < AirportStation.WIDTH, words + " " + b);
				assertTrue(b.v() >= -AirportStation.END && b.v() <= length - 1 + AirportStation.END, words + " " + b);
			}
			// the box, its wells and its lift are under the terminal, inside its walls
			final AirportGeometry air = AirportGeometry.of(spec.airport());
			assertTrue(rail.du() > air.terminalU0() && rail.du() + AirportStation.WIDTH - 1 < air.terminalU1(), words);
			assertTrue(rail.dv() - AirportStation.END > air.terminalV0() && rail.dv() + length - 1 + AirportStation.END < air.terminalV1(), words);
			assertEquals(List.of(), LayoutValidator.validate(station), words);
			assertTrue(StationAudit.audit(station).buildable(), words + " " + StationAudit.audit(station).problems());
		}
		assertNull(AirportStation.of(StationDescriptionParser.parse("airport").spec()));
	}

	@Test
	void theComposedAirportPlansCleanlyAndPassesTheChecks() {
		final StationPlan plan = plan(WORDS);
		assertEquals(List.of(), LayoutValidator.validate(plan));
		final StationAudit.Result audit = StationAudit.audit(plan);
		assertEquals(List.of(), audit.problems());
		assertTrue(audit.buildable());
		assertEquals(2, plan.trackEnvelopes().size());
		for (final Box envelope : plan.trackEnvelopes()) {
			assertEquals(1 - AirportStation.DEPTH, envelope.yMin());
		}
	}

	@Test
	void escalatorsAndTheLiftComeUpIntoTheHallAndNoPavilionDoes() {
		final StationPlan plan = plan(WORDS);
		final AirportGeometry air = AirportGeometry.of(plan.spec().airport());
		int escalators = 0;
		int lifts = 0;
		for (final PlannedBlock b : plan.blocks()) {
			if (b.y() >= 1 && air.inTerminal(b.u(), b.v())) {
				escalators += b.role() == Role.ESCALATOR ? 1 : 0;
				lifts += b.role() == Role.LIFT ? 1 : 0;
				assertFalse(b.blockId().endsWith(":entrance_pylon"), b.toString());
				assertFalse(b.blockId().equals(StationPlanner.ARCH_GLASS) && b.y() < air.dims().wallTop(), "a street pavilion's glass roof in the hall: " + b);
			}
		}
		assertTrue(escalators >= 4, "escalators up into the hall: " + escalators);
		assertTrue(lifts >= 2, "a lift up into the hall: " + lifts);
	}

	@Test
	void nobodyFallsIntoAWellFromTheHall() {
		final StationPlan plan = plan(WORDS);
		final AirportStation rail = AirportStation.of(plan.spec());
		final Map<C, PlannedBlock> cells = new HashMap<>();
		for (final PlannedBlock b : plan.blocks()) {
			cells.put(new C(b.u(), b.y(), b.v()), b);
		}
		final int length = rail.layout().platformLength();
		final List<String> falls = new ArrayList<>();
		for (int v = rail.dv() - AirportStation.END; v <= rail.dv() + length - 1 + AirportStation.END; v++) {
			for (int u = rail.du(); u < rail.du() + AirportStation.WIDTH; u++) {
				// a hole in the hall floor over a drop
				if (cells.containsKey(new C(u, 0, v)) || cells.containsKey(new C(u, -1, v))) {
					continue;
				}
				for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					final PlannedBlock floor = cells.get(new C(u + d[0], 0, v + d[1]));
					final boolean walkable = floor != null && floor.role() == Role.FLOOR && !cells.containsKey(new C(u + d[0], 1, v + d[1]));
					if (walkable && !cells.containsKey(new C(u, 1, v))) {
						falls.add("(" + u + ", " + v + ") from (" + (u + d[0]) + ", " + (v + d[1]) + ")");
					}
				}
			}
		}
		assertEquals(List.of(), falls);
	}

	@Test
	void theSupportLayerStaysOutOfTheDig() {
		final StationPlan plan = plan(WORDS);
		final AirportStation rail = AirportStation.of(plan.spec());
		final int length = rail.layout().platformLength();
		for (final Box support : plan.supportAreas()) {
			if (support.yMin() > -1 || support.yMax() < -1) {
				continue;
			}
			final boolean over = support.uMax() >= rail.du() && support.uMin() < rail.du() + AirportStation.WIDTH
					&& support.vMax() >= rail.dv() - AirportStation.END && support.vMin() <= rail.dv() + length - 1 + AirportStation.END;
			assertFalse(over, "support over the dug station: " + support);
		}
	}

	@Test
	void twoPlatformRailsUnderTheTerminalAndNoneWithoutAStation() {
		final LocalFrame frame = new LocalFrame(0, 64, 0, Heading.NORTH);
		final StationPlan plan = plan(WORDS);
		final List<PlatformRail> rails = PlatformRail.of(plan, frame);
		assertEquals(List.of("1", "2"), rails.stream().filter(r -> !r.turnBack()).map(PlatformRail::name).toList());
		for (final PlatformRail r : rails) {
			assertEquals(frame.y(1 - AirportStation.DEPTH), r.y1());
			assertEquals(r.y1(), r.y2());
		}
		assertEquals(List.of(), PlatformRail.of(plan("airport named Plain"), frame));
	}
}
