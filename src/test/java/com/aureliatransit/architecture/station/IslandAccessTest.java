package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Island platforms are always reached without walking across a track: through a building's footbridge, or a footbridge of
 * their own, from a side platform on the edge or from a street entrance beside the first track.
 */
class IslandAccessTest {

	private static final String[] NO_BUILDING = {
			"2 tracks island, 64 long, no building, named Halt",
			"4 tracks island, 160 long, paris, butterfly canopy, no building, stairs both ways, named Halt",
			"10 tracks, 24 long, berlin, canopies, no building, footbridge, travelators, named Halt",
			"2 tracks island, 64 long, train shed, no building, named Halt",
			"platform 1: island; platform 2: island 3 wide; no building, named Halt",
			"6 tracks island 120 long, footbridge, hall roof, enclosed, named Halt",
	};

	@Test
	void islandsWithoutABuildingAreReachedStepFreeFromTheStreet() {
		for (final String text : NO_BUILDING) {
			final StationSpec spec = StationDescriptionParser.parse(text).spec();
			final StationPlan plan = StationPlanner.plan(spec);
			final StationLayout site = StationLayout.of(spec, plan.layout());
			assertNotNull(site.street(), text);
			assertNotNull(site.platformLift(site.street(), plan.layout()), text + ": the street entrance has a lift");
			assertEquals(java.util.List.of(), LayoutValidator.validate(plan), text);
			final StationAudit.Result audit = StationAudit.audit(plan);
			for (final StationAudit.Access access : audit.access()) {
				assertEquals(StationAudit.Access.STEP_FREE, access, text + ": " + audit.checks());
			}
			for (final StationAudit.Check check : audit.checks()) {
				assertTrue(check.status() != StationAudit.Status.FAIL, text + ": " + check);
			}
			// the stair comes down beside the first track, never onto it
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.STAIR && b.u() < StationGeometry.STREET_WIDTH), text);
		}
	}

	@Test
	void aSidePlatformOnTheEdgeNeedsOnlyTheFootbridge() {
		final ParseResult parsed = StationDescriptionParser.parse("3 tracks island and side, 64 long, no building, named Halt");
		final StationSpec spec = parsed.spec();
		assertTrue(spec.footbridge(), parsed.defaulted().toString());
		assertFalse(StationGeometry.streetEntrance(spec));
		for (final StationAudit.Access access : StationAudit.audit(StationPlanner.plan(spec)).access()) {
			assertEquals(StationAudit.Access.STEP_FREE, access);
		}
	}

	@Test
	void sidePlatformsBackToBackBetweenTracksGetAFootbridgeAndLifts() {
		for (final String text : new String[]{"platform 1: side; platform 2: side; platform 3: side; platform 4: side; no building, named Halt",
				"platform 1: side; platform 2: side; platform 3: side; platform 4: side; station building, named Halt",
				"platform 1: island; platform 2: side; platform 3: side; city building, flat roof, 200 long, named Halt"}) {
			final StationSpec spec = StationDescriptionParser.parse(text).spec();
			final StationAudit.Result audit = StationAudit.audit(StationPlanner.plan(spec));
			for (final StationAudit.Access access : audit.access()) {
				assertEquals(StationAudit.Access.STEP_FREE, access, text + ": " + audit.checks());
			}
		}
	}

	@Test
	void noFootbridgeIsKeptAndWarned() {
		final ParseResult parsed = StationDescriptionParser.parse("4 tracks island, no footbridge, no building, named Halt");
		assertFalse(parsed.spec().footbridge());
		assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("walking across a track")), parsed.warnings().toString());
		assertNull(StationLayout.of(parsed.spec(), StationGeometry.layout(parsed.spec())).street());
	}

	@Test
	void aBuildingNeedsNoStreetEntrance() {
		final StationSpec spec = StationDescriptionParser.parse("4 tracks island, 96 long, station building, named Halt").spec();
		assertFalse(StationGeometry.streetEntrance(spec));
		assertEquals(StationGeometry.APRON + StationGeometry.BUILDING_DEPTH, StationGeometry.layout(spec).buildingWidth());
	}

	@Test
	void narrowIslandsAreWidenedForTheirStairAndLift() {
		final ParseResult stepFree = StationDescriptionParser.parse("4 tracks island, 3 wide, station building, named Halt");
		assertEquals(StationGeometry.MIN_LIFT_ISLAND_WIDTH, stepFree.spec().platformWidth());
		assertTrue(stepFree.warnings().stream().anyMatch(w -> w.contains("stair and lift")), stepFree.warnings().toString());
		final ParseResult stairs = StationDescriptionParser.parse("4 tracks island, 3 wide, station building, no ramps, named Halt");
		assertEquals(StationLayout.MIN_STAIR_ISLAND, stairs.spec().platformWidth(), stairs.warnings().toString());
		final ParseResult wide = StationDescriptionParser.parse("4 tracks island, 8 wide, station building, named Halt");
		assertEquals(8, wide.spec().platformWidth());
		assertTrue(wide.warnings().isEmpty(), wide.warnings().toString());
	}

	@Test
	void aSizeWordBeatsTheLandmarksOwnSize() {
		for (final String text : new String[]{"grand central, city, named Halt", "city, grand central, named Halt"}) {
			final ParseResult parsed = StationDescriptionParser.parse(text);
			assertEquals(StationScale.CITY, parsed.spec().scale(), text);
			assertTrue(parsed.warnings().stream().noneMatch(w -> w.contains("size")), text + ": " + parsed.warnings());
		}
		assertEquals(StationScale.GRAND, StationDescriptionParser.parse("grand central, named Halt").spec().scale());
	}
}
