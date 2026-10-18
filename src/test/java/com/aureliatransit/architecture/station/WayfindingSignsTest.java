package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 5: direction signs over the stair heads in the footbridge corridor, and an exit sign where it meets the building. */
class WayfindingSignsTest {

	private static final String SIGN = "aurelia_transit_architecture:hanging_wayfinding_sign";
	private static final String EXIT = "aurelia_transit_architecture:exit_sign";

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	@Test
	void everyStairHeadIsSignedWithItsPlatform() {
		for (final String words : new String[]{"4 tracks island, 96 long, station building, footbridge", "3 tracks, 80 long, city class",
				"2 tracks side platforms, 64 long, footbridge", "6 tracks island, 120 long, grand class"}) {
			final StationPlan plan = plan(words);
			final List<PlannedBlock> signs = plan.blocks().stream().filter(b -> b.blockId().equals(SIGN)).toList();
			assertTrue(!signs.isEmpty(), words);
			final Set<String> texts = signs.stream().map(PlannedBlock::text).collect(Collectors.toSet());
			assertEquals(signs.size(), texts.size(), words + ": one sign per platform");
			for (final PlannedBlock sign : signs) {
				assertTrue(sign.text().matches("DOWN:Platform \\d+"), words + ": " + sign.text());
				assertEquals(StationGeometry.BUILDING_ROOF_Y - 1, sign.y(), words + ": hung at the top of the corridor");
				// the walkway under it keeps its headroom
				for (int y = StationGeometry.UPPER_WALK_Y; y < sign.y(); y++) {
					final int h = y;
					assertTrue(plan.blocks().stream().noneMatch(b -> b.u() == sign.u() && b.v() == sign.v() && b.y() == h), words + " at y " + y);
				}
			}
		}
	}

	@Test
	void theBuildingEndHasAnExitSignAndNoBridgeHasNone() {
		final StationPlan withBuilding = plan("4 tracks island, 96 long, station building, footbridge");
		final List<PlannedBlock> exits = withBuilding.blocks().stream().filter(b -> b.blockId().equals(EXIT)).toList();
		assertEquals(1, exits.size());
		assertEquals("NONE:Way out", exits.get(0).text());
		final StationPlan open = plan("1 track, 48 long");
		assertTrue(open.blocks().stream().noneMatch(b -> b.blockId().equals(SIGN) || b.blockId().equals(EXIT)), "no footbridge, no corridor signs");
	}
}
