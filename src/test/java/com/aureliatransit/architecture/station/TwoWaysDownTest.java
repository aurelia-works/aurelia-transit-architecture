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

	@Test
	void longBigThroughStationsGetASecondFootbridgeAtTheFarEnd() {
		for (final String words : new String[]{"3 tracks island, 96 long, station building, city", "4 tracks island, 120 long, station building, grand",
				"2 tracks, 96 long, station building, city", "city class", "town class"}) {
			final StationSpec spec = spec(words);
			final StationPlan plan = StationPlanner.plan(spec);
			assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
			final StationLayout site = StationLayout.of(spec, plan.layout());
			final StationLayout far = site.farBridge(spec, plan.layout());
			assertTrue(far != null, words);
			assertTrue(Math.abs(far.bridgeV() - site.bridgeV()) > plan.layout().platformLength() / 3, words + ": the far bridge is at the far end");
			// a deck across every track, and a stair down to every platform wide enough for one
			final java.util.Set<Integer> deckU = new java.util.HashSet<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.v() == far.bridgeV() && block.role() == Role.BRIDGE_DECK) {
					deckU.add(block.u());
				}
			}
			for (int u = far.deckFrom(); u <= far.deckTo(); u++) {
				assertTrue(deckU.contains(u), words + ": far deck missing at u " + u);
			}
			for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
				for (final StationLayout.Flight flight : far.flights(band)) {
					final int top = flight.stepV(StationGeometry.STAIR_STEPS);
					assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.STAIR && b.v() == top && flight.columns().contains(b.u())),
							words + ": no stair at the far bridge for platform " + band.number());
				}
			}
		}
		// under a hall roof the shed's frame rests on the far bridge's roof only where the column rows either side are too far
		// apart for its girders; otherwise no pier pokes up through that roof (Jah, 10-20)
		for (final String words : new String[]{"3 tracks island, 96 long, station building, city, hall roof", "grand class, 120 long",
				"4 tracks island, 120 long, station building, city, arched train shed"}) {
			final StationSpec hall = spec(words);
			assertEquals(RoofType.HALL, hall.roof(), words);
			final StationPlan plan = StationPlanner.plan(hall);
			assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
			final StationLayout far = StationLayout.of(hall, plan.layout()).farBridge(hall, plan.layout());
			assertTrue(far != null, words + ": a far bridge under the hall");
			final java.util.List<Integer> rows = plan.blocks().stream().filter(b -> b.role() == Role.COLUMN_CAP).map(PlannedBlock::v).distinct().sorted().toList();
			assertEquals(PlanRoofs.girdersNeedBridge(rows, far.bridgeV()),
					plan.blocks().stream().anyMatch(b -> b.v() == far.bridgeV() && b.role() == Role.BRIDGE_PIER && b.y() > StationGeometry.BUILDING_ROOF_Y),
					words + ": piers on the far bridge exactly where the girders need them");
		}
		final StationSpec shortCity = spec("3 tracks island, 48 long, station building, city");
		final TrackLayout layout = StationGeometry.layout(shortCity);
		assertEquals(null, StationLayout.of(shortCity, layout).farBridge(shortCity, layout), "too short for two bridges");
	}

	/** Phase 5: any footbridge station with long platforms gets the far bridge, building or not; short ones keep one. */
	@Test
	void longPlatformsGetASecondAccessAtTheFarEnd() {
		for (final String words : new String[]{"4 tracks island, 120 long, footbridge", "2 tracks side platforms, 96 long, footbridge",
				"3 tracks, 160 long, footbridge, hall roof"}) {
			final StationSpec spec = spec(words);
			final StationPlan plan = StationPlanner.plan(spec);
			assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
			final StationLayout far = StationLayout.of(spec, plan.layout()).farBridge(spec, plan.layout());
			assertTrue(far != null, words);
			assertTrue(plan.blocks().stream().anyMatch(b -> b.v() == far.bridgeV() && b.role() == Role.BRIDGE_DECK), words + ": far deck");
			assertTrue(plan.blocks().stream().anyMatch(b -> b.v() == far.bridgeV() && b.blockId().endsWith(":hanging_wayfinding_sign")),
					words + ": the far bridge's stair heads are signed");
		}
		final StationSpec shorter = spec("4 tracks island, 80 long, footbridge");
		final TrackLayout layout = StationGeometry.layout(shorter);
		assertEquals(null, StationLayout.of(shorter, layout).farBridge(shorter, layout));
	}
}
