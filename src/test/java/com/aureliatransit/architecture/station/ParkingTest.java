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
}
