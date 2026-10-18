package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3: a hub's forecourt past its head building has a taxi rank and a bus interchange, inside the footprint, with the audit clean. */
class HubForecourtTest {

	private static final String[] HUBS = {"6 tracks island, 120 long, hub, berlin", "4 tracks island, 96 long, hub, hamburg", "hub class", "8 tracks island, 160 long, hub, st pancras, named Hub"};

	private static StationPlan plan(String words) {
		final StationSpec spec = StationDescriptionParser.parse(words.equals("hub class") ? "hub, grand class" : words).spec();
		final StationPlan plan = StationPlanner.plan(spec);
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	@Test
	void onlyAHubHasAForecourt() {
		for (final String words : HUBS) {
			final StationPlan plan = plan(words);
			assertTrue(plan.spec().hub(), words);
			assertEquals(StationGeometry.FORECOURT_DEPTH, plan.layout().forecourtDepth(), words);
			assertEquals(plan.layout().headEnd() + StationGeometry.HEAD_APRON + 1, plan.layout().forecourtFrom(), words);
			assertEquals(plan.layout().forecourtFrom() + StationGeometry.FORECOURT_DEPTH - 1, plan.layout().vMax(), words);
		}
		for (final String words : new String[]{"4 tracks island, 96 long, terminus, hamburg", "grand class, terminus", "3 tracks, 80 long, station building, city"}) {
			assertEquals(0, plan(words).layout().forecourtDepth(), words);
		}
	}

	@Test
	void theForecourtHasATaxiRankAndABusInterchange() {
		for (final String words : HUBS) {
			final StationPlan plan = plan(words);
			final TrackLayout layout = plan.layout();
			final List<PlannedBlock> forecourt = plan.blocks().stream().filter(b -> b.v() >= layout.forecourtFrom()).toList();
			assertFalse(forecourt.isEmpty(), words);
			final long floor = forecourt.stream().filter(b -> b.role() == Role.FLOOR && b.y() == StationGeometry.PLATFORM_Y - 1).count();
			final int width = layout.headTo() - layout.headFrom() + 1;
			assertEquals((long) width * StationGeometry.FORECOURT_DEPTH, floor, words + ": every cell of the forecourt is paved at street level");
			// the taxi rank: three bays, a bollard at the head of each
			assertEquals(3, forecourt.stream().filter(b -> b.blockId().endsWith(":bollard")).count(), words + ": bollards");
			// the bus interchange: a kerb, a walk-in shelter (wall, seat, roof), two stop signs and a timetable case
			assertTrue(forecourt.stream().filter(b -> b.blockId().endsWith(":bus_curb")).count() >= 10, words + ": bus kerb");
			assertEquals(6, forecourt.stream().filter(b -> b.blockId().endsWith(":bus_shelter_glass")).count(), words);
			assertEquals(3, forecourt.stream().filter(b -> b.blockId().endsWith(":bus_shelter_seat")).count(), words);
			assertEquals(6, forecourt.stream().filter(b -> b.blockId().endsWith(":bus_shelter_roof")).count(), words);
			assertEquals(2, forecourt.stream().filter(b -> b.blockId().endsWith(":bus_stop_sign")).count(), words);
			assertEquals(1, forecourt.stream().filter(b -> b.blockId().endsWith(":bus_timetable_case")).count(), words);
			// the way from the door to the street is clear to head height
			final int door = (layout.headFrom() + layout.headTo() + 1) / 2;
			for (final PlannedBlock block : forecourt) {
				assertFalse(Math.abs(block.u() - door) <= 2 && block.y() >= 0, words + ": " + block + " stands in the way from the door");
			}
			// nothing past the footprint, and the shelter's seat faces the buses
			assertTrue(plan.blocks().stream().allMatch(b -> b.v() <= layout.vMax()), words);
			final PlannedBlock seat = forecourt.stream().filter(b -> b.blockId().endsWith(":bus_shelter_seat")).findFirst().orElseThrow();
			final PlannedBlock wall = forecourt.stream().filter(b -> b.blockId().endsWith(":bus_shelter_glass")).findFirst().orElseThrow();
			assertTrue(wall.u() < seat.u(), "the wall is on the way side, the seat between it and the buses");
			assertEquals(LocalDir.MINUS_U, seat.front(), "turned the way the wall is from the seat");
		}
	}

	@Test
	void theHubStaysBuildableAndThePasteOriginIsPastTheForecourt() {
		final StationSpec spec = StationDescriptionParser.parse(HUBS[0]).spec();
		final StationPlan plan = StationPlanner.plan(spec);
		assertEquals(plan.layout().vMax() + 1, StationPlacement.pasteOrigin(spec, plan)[2]);
	}
}
