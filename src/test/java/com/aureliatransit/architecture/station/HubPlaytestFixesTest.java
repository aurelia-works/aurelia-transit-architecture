package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jah's hub play-test, 2026-10-20: the fixes to the big glass-hall hub and the city operators. */
class HubPlaytestFixesTest {

	private static final String HUB = "10 track kuala lumpur and hamburg combined hub, 300 long, named hi, travelators stairs both ways andesite track bed, seed tmptnb";
	private static final String ATA = "aurelia_transit_architecture:";

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	@Test
	void theHubStillPlansCleanly() {
		final StationPlan plan = plan(HUB);
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
	}

	@Test
	void noPiersPokeUpThroughTheFootbridgeRoof() {
		final StationPlan plan = plan(HUB);
		final int roof = StationGeometry.BUILDING_ROOF_Y;
		assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.BRIDGE_PIER && b.y() > roof), "a pier above the bridge roof");
	}

	@Test
	void bridgeRowOnlyWhereTheGirdersCannotSpan() {
		assertFalse(PlanRoofs.girdersNeedBridge(List.of(138, 146, 154, 162), 150));
		assertTrue(PlanRoofs.girdersNeedBridge(List.of(138, 146, 162), 150), "146 to 162 is more than a girder spans");
		assertTrue(PlanRoofs.girdersNeedBridge(List.of(10, 18), 150), "past the last row");
	}

	@Test
	void screensHangOnRodsNotGirders() {
		final StationPlan plan = plan(HUB);
		final List<PlannedBlock> screens = plan.blocks().stream().filter(b -> b.blockId().equals(StationPlanner.HANGING_PIDS)).toList();
		assertFalse(screens.isEmpty());
		for (final PlannedBlock screen : screens) {
			final PlannedBlock above = plan.blocks().stream()
					.filter(b -> b.u() == screen.u() && b.v() == screen.v() && b.y() == screen.y() + 1).findFirst().orElse(null);
			assertTrue(above == null || above.blockId().equals(ATA + "drop_rod") || above.blockId().equals(StationPlanner.HANGING_PIDS),
					"over a screen: " + above);
		}
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(ATA + "drop_rod")));
	}

	@Test
	void rodsOverAScreenLandOnItsHangers() {
		final StationPlan plan = plan(HUB);
		final java.util.Map<String, PlannedBlock> at = new java.util.HashMap<>();
		plan.blocks().forEach(b -> at.put(b.u() + "," + b.y() + "," + b.v(), b));
		int hangers = 0;
		for (final PlannedBlock rod : plan.blocks()) {
			if (!rod.blockId().equals(ATA + "drop_rod")) {
				continue;
			}
			// down the rod to what it holds: every rod over a screen carries the screen's hangers, lined up by its front
			PlannedBlock below = at.get(rod.u() + "," + (rod.y() - 1) + "," + rod.v());
			while (below != null && below.blockId().equals(ATA + "drop_rod")) {
				below = at.get(below.u() + "," + (below.y() - 1) + "," + below.v());
			}
			if (below != null && (below.blockId().equals(StationPlanner.HANGING_PIDS) || below.blockId().equals(PlanState.HANGING_SIGN))) {
				hangers++;
				assertTrue(rod.state().matches("hanger=(both|low|high)"), "rod over a screen: " + rod);
				assertEquals(below.front(), rod.front(), "hangers lined up with the screen: " + rod);
			} else {
				assertEquals(PlanState.PLAIN_ROD, rod.state(), "plain rod: " + rod);
			}
		}
		assertTrue(hangers > 0);
	}

	@Test
	void aWideScreensRodsMeetTheStubsAtItsEnds() {
		final StationPlan plan = plan(HUB);
		final java.util.Map<String, PlannedBlock> at = new java.util.HashMap<>();
		plan.blocks().forEach(b -> at.put(b.u() + "," + b.y() + "," + b.v(), b));
		int screens = 0;
		for (final PlannedBlock first : plan.blocks()) {
			// the lowest-u block of each screen across the platform
			if (!first.blockId().equals(StationPlanner.HANGING_PIDS) || first.front().alongTrack() == false
					|| at.get((first.u() - 1) + "," + first.y() + "," + first.v()) != null
					&& at.get((first.u() - 1) + "," + first.y() + "," + first.v()).blockId().equals(StationPlanner.HANGING_PIDS)) {
				continue;
			}
			int last = first.u();
			while (at.get((last + 1) + "," + first.y() + "," + first.v()) != null
					&& at.get((last + 1) + "," + first.y() + "," + first.v()).blockId().equals(StationPlanner.HANGING_PIDS)) {
				last++;
			}
			if (last == first.u()) {
				continue;
			}
			screens++;
			// facing -v, the screen's left (seen from its front) is -u: its -u end has only the high stub, its +u end the low one
			final boolean minusV = first.front() == LocalDir.MINUS_V;
			for (int u = first.u(); u <= last; u++) {
				final PlannedBlock rod = at.get(u + "," + (first.y() + 1) + "," + first.v());
				final String want = u == first.u() ? (minusV ? "hanger=high" : "hanger=low") : u == last ? (minusV ? "hanger=low" : "hanger=high") : null;
				if (want == null) {
					assertTrue(rod == null || !rod.blockId().equals(PlanState.DROP_ROD), "a rod over a screen's middle, no stub there: " + rod);
				} else if (rod != null && rod.blockId().equals(PlanState.DROP_ROD)) {
					assertEquals(want, rod.state(), "rod over the end of the screen at " + first);
				}
			}
			final PlannedBlock a = at.get(first.u() + "," + (first.y() + 1) + "," + first.v());
			final PlannedBlock b = at.get(last + "," + (first.y() + 1) + "," + first.v());
			assertTrue(a != null && a.blockId().equals(PlanState.DROP_ROD) && b != null && b.blockId().equals(PlanState.DROP_ROD),
					"a screen not hung at both ends: " + first);
		}
		assertTrue(screens > 0);
	}

	@Test
	void eachGateBankSaysWhichPlatformsItLeadsTo() {
		assertEquals("Platform 3", PlanConcourse.gateSignText(List.of(3)));
		assertEquals("Platforms 1–2", PlanConcourse.gateSignText(List.of(2, 1)));
		final StationPlan plan = plan(HUB);
		final List<PlannedBlock> signs = plan.blocks().stream()
				.filter(b -> b.blockId().equals(PlanState.HANGING_SIGN) && b.y() == StationGeometry.WALK_Y + 4 && b.text().startsWith("UP:Platform"))
				.sorted(java.util.Comparator.comparingInt(PlannedBlock::u)).toList();
		// every track face has its own number (Enviro, 10-20): an island is two platforms
		assertEquals(List.of("UP:Platforms 1–4", "UP:Platforms 5–6", "UP:Platforms 7–10"), signs.stream().map(PlannedBlock::text).toList());
		for (final PlannedBlock sign : signs) {
			// over the bank's middle (wide) lane, hung on a rod whose hangers land on the sign's stubs
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.GATE && b.u() == sign.u() && b.v() == sign.v()),
					"no gate under " + sign);
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(PlanState.DROP_ROD) && b.u() == sign.u() && b.v() == sign.v()
					&& b.y() == sign.y() + 1 && b.state().equals("hanger=both") && b.front() == sign.front()), "no rod over " + sign);
		}
	}

	@Test
	void aStaffBoothStandsBesideTheMiddleBank() {
		final StationPlan plan = plan(HUB);
		final List<PlannedBlock> windows = plan.blocks().stream()
				.filter(b -> b.blockId().equals("aurelia_transit_architecture:booth_window") && b.y() == 2).toList();
		final int gateV = plan.blocks().stream().filter(b -> b.role() == Role.GATE).mapToInt(PlannedBlock::v).findFirst().orElseThrow();
		// the middle bank's middle lane, under its sign
		final int wide = plan.blocks().stream().filter(b -> b.blockId().equals(PlanState.HANGING_SIGN) && b.y() == StationGeometry.WALK_Y + 4
				&& b.text().startsWith("UP:Platform")).mapToInt(PlannedBlock::u).sorted().toArray()[1];
		// on the ticket-hall side, just past the middle bank's end, looking at its wide gate and at the gate line
		final PlannedBlock toGate = windows.stream().filter(b -> b.v() == gateV + 2 && b.front() == LocalDir.MINUS_U).findFirst().orElseThrow();
		assertTrue(toGate.u() > wide && toGate.u() - wide <= PlanConcourse.GATE_BANK_HALF + 2, "booth too far from the bank: " + toGate);
		assertTrue(windows.stream().anyMatch(b -> b.v() == gateV + 1 && b.front() == LocalDir.MINUS_V && b.u() == toGate.u() + 1));
		// its door at the back is open and the inside is free to stand in
		final java.util.Set<String> taken = new java.util.HashSet<>();
		plan.blocks().forEach(b -> taken.add(b.u() + "," + b.y() + "," + b.v()));
		for (int y = 1; y <= 2; y++) {
			assertFalse(taken.contains((toGate.u() + 2) + "," + y + "," + (gateV + 2)), "door blocked");
			assertFalse(taken.contains((toGate.u() + 1) + "," + y + "," + (gateV + 2)), "inside blocked");
		}
	}

	@Test
	void hallLampsHangFromTheTrussWithoutTakingAScreensRod() {
		final StationPlan plan = plan(HUB);
		final java.util.Map<String, PlannedBlock> at = new java.util.HashMap<>();
		plan.blocks().forEach(b -> at.put(b.u() + "," + b.y() + "," + b.v(), b));
		final List<PlannedBlock> lamps = plan.blocks().stream().filter(b -> b.blockId().equals(PlanRoofs.HALL_LAMP)).toList();
		// every platform gets lamps down its length
		assertEquals(5, lamps.stream().map(PlannedBlock::u).distinct().count());
		assertTrue(lamps.size() >= 5 * 10, "lamps: " + lamps.size());
		for (final PlannedBlock lamp : lamps) {
			// a plain rod straight up to a girder of the frame
			int h = lamp.y() + 1;
			PlannedBlock above = at.get(lamp.u() + "," + h + "," + lamp.v());
			while (above != null && above.blockId().equals(PlanState.DROP_ROD)) {
				assertEquals(PlanState.PLAIN_ROD, above.state());
				above = at.get(lamp.u() + "," + ++h + "," + lamp.v());
			}
			assertTrue(above != null && above.role() == Role.BEAM, "lamp not hung from the frame: " + lamp + " under " + above);
			// nothing hangs under it
			for (int y = StationGeometry.WALK_Y + 3; y < lamp.y(); y++) {
				assertNull(at.get(lamp.u() + "," + y + "," + lamp.v()), "something under " + lamp);
			}
		}
		// every hanging screen still has its rods
		plan.blocks().stream().filter(b -> b.blockId().equals(StationPlanner.HANGING_PIDS)).map(b -> b.v() + "," + b.y()).distinct()
				.forEach(row -> assertTrue(plan.blocks().stream().anyMatch(r -> r.blockId().equals(PlanState.DROP_ROD) && !r.state().equals(PlanState.PLAIN_ROD)
						&& (r.v() + "," + (r.y() - 1)).equals(row)), "a screen with no rod at v,y " + row));
	}

	@Test
	void galleryRailsTurnTheCornerAtTheStairHead() {
		final StationPlan plan = plan(HUB);
		final java.util.Set<String> rails = new java.util.HashSet<>();
		plan.blocks().stream().filter(b -> b.role() == Role.RAILING && b.y() == StationGeometry.UPPER_WALK_Y)
				.forEach(b -> rails.add(b.u() + "," + b.v()));
		assertFalse(rails.isEmpty());
		// two rails touching only corner to corner leave a gap between them
		for (final String rail : rails) {
			final String[] p = rail.split(",");
			final int u = Integer.parseInt(p[0]);
			final int v = Integer.parseInt(p[1]);
			for (final int du : new int[]{-1, 1}) {
				for (final int dv : new int[]{-1, 1}) {
					if (rails.contains((u + du) + "," + (v + dv))) {
						assertTrue(rails.contains((u + du) + "," + v) || rails.contains(u + "," + (v + dv)), "rails meet only at a corner: " + rail);
					}
				}
			}
		}
	}

	@Test
	void noBenchesInTheWalksOffTheLanes() {
		final StationPlan plan = plan(HUB);
		// the hub's islands run u 3..13 with their middle strip at u 8; the lanes start and stop well short of the ends
		final List<PlannedBlock> middle = plan.blocks().stream()
				.filter(b -> b.role() == Role.BENCH && b.u() == 8 && b.y() == StationGeometry.WALK_Y).toList();
		assertFalse(middle.isEmpty());
		final int lastLane = plan.blocks().stream().filter(b -> b.role() == Role.ESCALATOR && b.u() >= 3 && b.u() <= 13)
				.mapToInt(PlannedBlock::v).max().orElseThrow();
		final int firstLane = plan.blocks().stream().filter(b -> b.role() == Role.ESCALATOR && b.u() >= 3 && b.u() <= 13)
				.mapToInt(PlannedBlock::v).min().orElseThrow();
		assertTrue(middle.stream().allMatch(b -> b.v() >= firstLane && b.v() <= lastLane), "a bench past the lanes: " + middle);
	}

	@Test
	void columnSpeakersStandOnTheirBracketAndTheHallFrameIsATruss() {
		final StationPlan plan = plan(HUB);
		final List<PlannedBlock> speakers = plan.blocks().stream().filter(b -> b.blockId().equals(StationPlanner.WALL_SPEAKER)).toList();
		assertFalse(speakers.isEmpty());
		assertTrue(speakers.stream().allMatch(b -> b.state().contains("post=true")));
		for (final PlannedBlock speaker : speakers) {
			assertTrue(plan.blocks().stream().noneMatch(b -> PlanState.screen(b.blockId()) && b.u() == speaker.u() && b.v() == speaker.v()
					&& b.y() > speaker.y() && b.y() <= speaker.y() + 2), "a speaker tucked under a screen: " + speaker);
		}
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(StationPlanner.ROOF_TRUSS)));
		assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.BEAM && b.blockId().equals(StationPlanner.GIRDER)), "plain girders in the hall frame");
	}

	@Test
	void ataTicketMachinesAndMtrFencesDownstairs() {
		final StationPlan plan = plan(HUB);
		assertTrue(plan.blocks().stream().noneMatch(b -> b.blockId().equals("mtr:ticket_machine")));
		final List<PlannedBlock> machines = plan.blocks().stream().filter(b -> b.blockId().equals(PlanState.TICKET_MACHINE)).toList();
		assertFalse(machines.isEmpty());
		// two blocks tall, facing the same way
		for (final PlannedBlock lower : machines.stream().filter(b -> b.state().equals("half=lower")).toList()) {
			assertTrue(machines.stream().anyMatch(b -> b.u() == lower.u() && b.v() == lower.v() && b.y() == lower.y() + 1
					&& b.state().equals("half=upper") && b.front() == lower.front()), "a machine without its top: " + lower);
		}
		assertEquals(machines.size(), 2 * machines.stream().filter(b -> b.state().equals("half=lower")).count());
		final List<PlannedBlock> fences = plan.blocks().stream().filter(b -> b.blockId().equals(PlanState.MTR_FENCE)).toList();
		assertFalse(fences.isEmpty());
		// two high: every lower half has its upper half over it
		for (final PlannedBlock lower : fences.stream().filter(b -> b.state().contains("half=lower")).toList()) {
			assertTrue(fences.stream().anyMatch(b -> b.u() == lower.u() && b.v() == lower.v() && b.y() == lower.y() + 1 && b.state().contains("half=upper")));
		}
	}

	@Test
	void citiesGiveTheirOperator() {
		assertEquals(Operator.DB, Operator.ofCity("Hamburg"));
		assertEquals(Operator.NS, Operator.ofCity("utrecht"));
		assertEquals(Operator.SBB, Operator.ofCity("zurich"));
		assertEquals(Operator.JR, Operator.ofCity("tokyo"));
		assertEquals(Operator.NATIONAL_RAIL, Operator.ofCity("london"));
		assertNull(Operator.ofCity("kuala lumpur"));
		assertEquals(Operator.DB, StationDescriptionParser.parse(HUB).spec().operator(), "Kuala Lumpur has none, so Hamburg's");
		assertEquals(Operator.UNDERGROUND, StationDescriptionParser.parse("london metro station").spec().operator());
		assertEquals(Operator.NONE, StationDescriptionParser.parse("hamburg station no operator").spec().operator());
		assertEquals(Operator.NS, StationDescriptionParser.parse("hamburg station ns signs").spec().operator(), "a named operator wins");
	}
}
