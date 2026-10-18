package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2: a bigger through station gives each wide platform two ways down from its footbridge. */
class TwoWaysDownTest {

	private static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	@Test
	void biggerThroughStationsGetASecondStairAndSmallOnesDoNot() {
		assertTrue(spec("3 tracks island, 96 long, station building, city").stairsBothWays());
		assertTrue(spec("4 tracks island, 96 long, station building, grand").stairsBothWays());
		assertTrue(spec("city class").stairsBothWays());
		assertTrue(spec("grand class").stairsBothWays());
		assertFalse(spec("2 tracks, 64 long, station building, compact").stairsBothWays(), "a compact building keeps one stair");
		assertFalse(spec("regional class").stairsBothWays());
		assertFalse(spec("halt class").stairsBothWays());
		assertFalse(spec("4 tracks island, terminus, grand").stairsBothWays(), "a terminus has no footbridge");
		assertFalse(spec("4 tracks island, 96 long, footbridge").stairsBothWays(), "no building, no size");
		assertFalse(spec("4 tracks island, 96 long, station building, grand, single stairs").stairsBothWays(), "asked to keep one");
		assertTrue(StationDescriptionParser.parse("4 tracks island, 96 long, station building, grand").defaulted().stream().anyMatch(d -> d.contains("second footbridge stair")));
		assertTrue(StationDescriptionParser.parse("4 tracks island, 96 long, station building, grand, single stairs").ignored().isEmpty());
	}

	@Test
	void everyWidePlatformHasTwoFlightsAndTheStationIsClean() {
		for (final String words : new String[]{"3 tracks island, 96 long, station building, city, hamburg", "4 tracks island, 120 long, station building, grand, utrecht",
				"2 tracks, 80 long, station building, city, amsterdam", "town class", "city class", "grand class, modern era"}) {
			final StationSpec spec = spec(words);
			final StationPlan plan = StationPlanner.plan(spec);
			assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
			final StationLayout site = StationLayout.of(spec, plan.layout());
			assertTrue(site.bridge(), words);
			int two = 0;
			for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
				if (site.served().contains(band.number())) {
					final int flights = site.flights(band).size();
					assertTrue(flights == 1 || flights == 2, words + " platform " + band.number());
					two += flights == 2 ? 1 : 0;
				}
			}
			assertTrue(two > 0, words + ": no platform has two ways down");
		}
	}
}
