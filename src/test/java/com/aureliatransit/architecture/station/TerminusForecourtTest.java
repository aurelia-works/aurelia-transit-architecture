package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 4: a terminus that asks for parking gets a forecourt past its head building. */
class TerminusForecourtTest {

	private static final String[] TERMINI = {"4 tracks island, 96 long, terminus, hamburg, parking", "2 tracks, 64 long, terminus, parking",
			"grand class, terminus, parking", "6 tracks island, 120 long, terminus, sandstone, car park"};

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	@Test
	void parkingGivesATerminusItsForecourt() {
		for (final String words : TERMINI) {
			final StationPlan plan = plan(words);
			final TrackLayout layout = plan.layout();
			assertEquals(StationGeometry.TERMINUS_FORECOURT_DEPTH, layout.forecourtDepth(), words);
			final List<PlannedBlock> forecourt = plan.blocks().stream().filter(b -> b.v() >= layout.forecourtFrom()).toList();
			final long floor = forecourt.stream().filter(b -> b.role() == Role.FLOOR && b.y() == StationGeometry.PLATFORM_Y - 1).count();
			assertEquals((long) (layout.headTo() - layout.headFrom() + 1) * StationGeometry.TERMINUS_FORECOURT_DEPTH, floor, words + ": all paved");
			assertTrue(forecourt.stream().filter(b -> b.blockId().endsWith(":bus_curb")).count() >= 5, words + ": kerbs");
			assertTrue(forecourt.stream().filter(b -> b.blockId().endsWith(":bollard")).count() >= 3, words + ": bollards and stand posts");
			assertTrue(forecourt.stream().anyMatch(b -> b.blockId().endsWith(":station_fence")), words + ": cycle stands");
			assertTrue(forecourt.stream().anyMatch(b -> b.blockId().equals("minecraft:oak_log")), words + ": trees");
			assertTrue(forecourt.stream().noneMatch(b -> b.blockId().endsWith(":bus_stop_sign")), words + ": no bus interchange");
			final int door = (layout.headFrom() + layout.headTo() + 1) / 2;
			for (final PlannedBlock block : forecourt) {
				assertFalse(Math.abs(block.u() - door) <= 2 && block.y() >= 0, words + ": " + block + " stands in the way from the door");
			}
			assertTrue(plan.blocks().stream().allMatch(b -> b.v() <= layout.vMax()), words);
		}
	}

	@Test
	void withoutParkingATerminusHasNone() {
		assertEquals(0, plan("4 tracks island, 96 long, terminus, hamburg").layout().forecourtDepth());
		final ParseResult hub = StationDescriptionParser.parse("4 tracks island, 96 long, hub, parking");
		assertFalse(hub.spec().parking(), "a hub's forecourt is its own");
		assertTrue(hub.warnings().stream().anyMatch(w -> w.contains("hub's forecourt")), hub.warnings().toString());
	}
}
