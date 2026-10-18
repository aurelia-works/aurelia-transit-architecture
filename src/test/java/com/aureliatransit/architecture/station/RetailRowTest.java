package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3: a hub's retail row, shop units under the concourse galleries. */
class RetailRowTest {

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	private static long barrels(StationPlan plan) {
		final TrackLayout layout = plan.layout();
		return plan.blocks().stream().filter(b -> b.blockId().equals("minecraft:barrel") && b.v() >= layout.platformLength()
				&& b.v() <= layout.headEnd() && (b.u() <= layout.headFrom() + 1 || b.u() >= layout.headTo() - 1)).count();
	}

	@Test
	void aBigHubHasShopsUnderBothGalleries() {
		for (final String words : new String[]{"8 tracks island, 160 long, hub, st pancras, named Hub", "hub, grand class"}) {
			final StationPlan plan = plan(words);
			final TrackLayout layout = plan.layout();
			final int inner = layout.headTo() - layout.headFrom() - 1;
			assertTrue(inner >= 24, words + ": wide enough for galleries (" + inner + ")");
			final long counters = barrels(plan);
			assertTrue(counters >= 4, words + ": counters against the side walls under the galleries: " + counters);
			// both sides
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals("minecraft:barrel") && b.u() == layout.headFrom() + 1 && b.v() < layout.headEnd() - 10), words);
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals("minecraft:barrel") && b.u() == layout.headTo() - 1 && b.v() < layout.headEnd() - 10), words);
		}
	}

	@Test
	void aPlainTerminusHasNone() {
		final StationPlan plan = plan("8 tracks island, 160 long, terminus, st pancras");
		assertEquals(0, plan.blocks().stream().filter(b -> b.blockId().equals("minecraft:barrel") && b.v() < plan.layout().headEnd() - 12
				&& b.v() >= plan.layout().platformLength()).count(), "the retail row is a hub's");
	}
}
