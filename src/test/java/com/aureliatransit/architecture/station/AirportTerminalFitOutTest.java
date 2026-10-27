package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The terminal's security, check-in, baggage hall and gate fit-out with Null's blocks (10-26). */
class AirportTerminalFitOutTest {

	private record C(int u, int y, int v) {
	}

	private static final String RAIL = "airport with 4 gates and a train station, named Fit Out";
	private static final String SMALL = "small airport with 2 gates";

	private static StationPlan plan(String words) {
		return StationPlanner.plan(StationDescriptionParser.parse(words).spec());
	}

	private static Map<C, PlannedBlock> cells(StationPlan plan) {
		final Map<C, PlannedBlock> cells = new HashMap<>();
		for (final PlannedBlock b : plan.blocks()) {
			cells.put(new C(b.u(), b.y(), b.v()), b);
		}
		return cells;
	}

	private static long count(StationPlan plan, String id) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(id)).count();
	}

	@Test
	void everyNewBlockIsBuiltInABigTerminal() {
		final StationPlan plan = plan(RAIL);
		for (final String id : List.of(PlanAirport.SECURITY_ARCH, PlanAirport.XRAY_SCANNER, PlanAirport.SECURITY_DESK, PlanAirport.QUEUE_POST,
				PlanAirport.KIOSK, PlanAirport.BAG_DROP, PlanAirport.CAROUSEL, PlanAirport.CAROUSEL_CORNER, PlanAirport.GATE_DESK,
				PlanAirport.TROLLEY, PlanAirport.AIRPORT_SEAT)) {
			assertTrue(count(plan, id) > 0, "no " + id);
		}
		// a scanner per lane, an arch per lane, and carousels as whole loops (four corners each)
		assertEquals(count(plan, PlanAirport.SECURITY_ARCH), count(plan, PlanAirport.XRAY_SCANNER));
		assertEquals(0, count(plan, PlanAirport.CAROUSEL_CORNER) % 4);
	}

	@Test
	void everyGateHasADeskAndItsNumberOverTheDoor() {
		final StationPlan plan = plan(RAIL);
		assertEquals(4, count(plan, PlanAirport.GATE_DESK));
		final Set<String> signs = new TreeSet<>();
		for (final PlannedBlock b : plan.blocks()) {
			if (b.blockId().equals(PlanState.HANGING_SIGN)) {
				signs.add(b.text());
			}
		}
		assertEquals(Set.of("NONE:Gate 1", "NONE:Gate 2", "NONE:Gate 3", "NONE:Gate 4"), signs);
	}

	@Test
	void theSecurityLineIsClosedExceptThroughItsArchesAndTheExitAndLiesAirsideOfTheStation() {
		for (final String words : List.of(RAIL, SMALL)) {
			final StationSpec spec = StationDescriptionParser.parse(words).spec();
			final StationPlan plan = StationPlanner.plan(spec);
			final Map<C, PlannedBlock> cells = cells(plan);
			final AirportGeometry air = AirportGeometry.of(spec.airport());
			final int line = plan.blocks().stream().filter(b -> b.blockId().equals(PlanAirport.SECURITY_ARCH)).mapToInt(PlannedBlock::u).findFirst()
					.orElseThrow();
			int open = 0;
			for (int v = air.terminalV0() + 1; v < air.terminalV1(); v++) {
				final PlannedBlock at = cells.get(new C(line, StationGeometry.WALK_Y, v));
				if (at == null) {
					open++;
					continue;
				}
				if (at.blockId().equals(PlanAirport.SECURITY_ARCH)) {
					assertFalse(cells.containsKey(new C(line, StationGeometry.WALK_Y + 1, v)), words + ": something over the arch at " + v);
				}
			}
			assertEquals(3, open, words + ": open cells along the line (the exit by the far end wall)");
			final AirportStation rail = AirportStation.of(spec);
			if (rail != null) {
				assertTrue(line >= rail.du() + AirportStation.WIDTH, words + ": the line at " + line + " is over the station box");
			}
			assertTrue(line < air.terminalU1() - 4, words + ": no room for the gate seats");
		}
	}

	@Test
	void seatsFaceTheGlassInJoinedRows() {
		final StationPlan plan = plan(SMALL);
		final List<PlannedBlock> seats = plan.blocks().stream().filter(b -> b.blockId().equals(PlanAirport.AIRPORT_SEAT)).toList();
		assertEquals(2 * 2 * 2 * 4, seats.size(), "two rows of four each side of each of 2 gates");
		assertTrue(seats.stream().allMatch(b -> b.front() == LocalDir.PLUS_U));
	}

	@Test
	void thePlansStillPassTheirChecks() {
		for (final String words : List.of(RAIL, SMALL)) {
			final StationPlan plan = plan(words);
			assertEquals(List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		}
	}
}
