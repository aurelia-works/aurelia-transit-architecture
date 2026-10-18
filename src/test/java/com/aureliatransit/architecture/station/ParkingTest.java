package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "parking" / "car park" word: a surface car park is paved in a band the layout leaves in front of a station building's
 * street wall, so it stands inside the footprint (the design rules allow nothing outside it), the audit is clean, and a
 * station with no building beside the street says the lot was left out.
 */
class ParkingTest {

	private static final String SIDE = "two track island station 64 long with a station building and parking";

	@Test
	void theWordLeavesRoomForTheLotInFrontOfTheBuilding() {
		final StationSpec spec = StationDescriptionParser.parse(SIDE).spec();
		assertTrue(spec.parking(), "the word asks for a car park");
		final TrackLayout layout = StationGeometry.layout(spec);
		assertEquals(StationGeometry.PARKING_DEPTH, layout.parkingWidth(), "the lot band is laid out");
		assertEquals(StationGeometry.PARKING_DEPTH + StationGeometry.APRON + StationGeometry.buildingDepth(spec.buildingScale()),
				layout.buildingWidth(), "the building moves back behind the lot");
		assertFalse(layout.indoor(0, 20), "the lot band is not indoor");
		assertTrue(layout.indoor(layout.parkingWidth() + StationGeometry.APRON, 20), "the building is indoor");
	}

	@Test
	void theLotIsPavedInsideTheFootprintAndThePlanIsClean() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(SIDE).spec());
		assertTrue(LayoutValidator.validate(plan).isEmpty(), LayoutValidator.validate(plan).toString());
		assertTrue(StationAudit.audit(plan).buildable(), StationAudit.audit(plan).checks().toString());
		final TrackLayout layout = plan.layout();
		final long kerb = plan.blocks().stream().filter(b -> b.blockId().endsWith("bus_curb")).count();
		assertTrue(kerb > 0, "the lot has a kerb");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith("oak_leaves")), "the central planter");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith("platform_paving_dark")), "the bay markings");
		assertTrue(plan.blocks().stream().filter(b -> b.blockId().endsWith("bus_curb"))
				.allMatch(b -> b.u() >= 0 && b.u() < layout.width() && b.u() < StationGeometry.PARKING_DEPTH
						&& b.y() == StationGeometry.PLATFORM_Y - 1),
				"every kerb cell is the lot band, inside the footprint, at street level");
	}

	@Test
	void aTerminusSaysTheLotWasLeftOut() {
		final ParseResult parse = StationDescriptionParser.parse("grand terminus 64 long with a station building and parking");
		assertFalse(parse.spec().parking(), "a terminus has no street wall to park in front of");
		assertTrue(parse.warnings().stream().anyMatch(w -> w.contains("car park")), parse.warnings().toString());
	}

	@Test
	void aCarParkIsTheSameWord() {
		assertTrue(StationDescriptionParser.parse("two track station with a station building and a car park").spec().parking());
	}

	/** Phase 4: the forecourt has a drop-off by the door, cycle stands by the kerb and trees in the planter, all inside the lot band. */
	@Test
	void theForecourtHasADropOffCycleStandsAndTrees() {
		for (final String words : new String[]{SIDE, "three track island station 96 long with a station building, city, hamburg and parking",
				"four track station, grand station building, utrecht, parking", "city class, parking", "grand class, modern era, parking"}) {
			final StationSpec spec = StationDescriptionParser.parse(words).spec();
			final StationPlan plan = StationPlanner.plan(spec);
			assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
			final int front = StationLayout.of(spec, plan.layout()).entranceV();
			final java.util.List<PlannedBlock> lot = plan.blocks().stream().filter(b -> b.u() < StationGeometry.PARKING_DEPTH).toList();
			// cycle stands: posts and rails by the kerb, in pairs along the first rows
			final long posts = lot.stream().filter(b -> b.blockId().endsWith(":bollard") && b.u() >= 1 && b.u() <= 3).count();
			final long rails = lot.stream().filter(b -> b.blockId().endsWith(":station_fence") && b.u() >= 1 && b.u() <= 3).count();
			assertTrue(posts >= 6 && rails >= 6, words + ": cycle stands " + posts + " posts " + rails + " rails");
			// trees: a trunk standing in the planter, a crown on its top, and none of the leaves can decay
			final long trunks = lot.stream().filter(b -> b.blockId().equals("minecraft:oak_log")).count();
			assertTrue(trunks >= 8, words + ": trunks " + trunks);
			assertTrue(lot.stream().filter(b -> b.blockId().equals("minecraft:oak_log")).allMatch(b -> b.state().equals("axis=y")), "trunks stand upright");
			final java.util.List<PlannedBlock> leaves = lot.stream().filter(b -> b.blockId().equals("minecraft:oak_leaves")).toList();
			assertTrue(leaves.size() > 20, words + ": leaves " + leaves.size());
			assertTrue(leaves.stream().allMatch(b -> b.state().equals("persistent=true")), words + ": leaves that would decay");
			assertTrue(lot.stream().noneMatch(b -> b.blockId().equals("minecraft:oak_log") && Math.abs(b.v() - front) <= 4), "no tree by the door");
			// the drop-off: bollards close its ends and the way to the door is clear to head height
			final java.util.List<PlannedBlock> bollards = lot.stream().filter(b -> b.blockId().endsWith(":bollard") && b.u() >= 10).toList();
			assertEquals(4, bollards.size(), words + ": drop-off bollards");
			for (int a = 0; a < StationGeometry.PARKING_DEPTH; a++) {
				for (int v = front - 1; v <= front + 1; v++) {
					for (int y = StationGeometry.WALK_Y - 1; y <= StationGeometry.WALK_Y + 1; y++) {
						final int au = a;
						final int av = v;
						final int ay = y;
						assertTrue(plan.blocks().stream().noneMatch(b -> b.u() == au && b.v() == av && b.y() == ay && b.y() >= 0),
								words + ": the way to the door is blocked at u=" + a + " v=" + v + " y=" + y);
					}
				}
			}
		}
	}
}
