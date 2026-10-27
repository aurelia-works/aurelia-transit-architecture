package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The airport findings of the 10-27 review: words, the planes' path through the apron, real separations and runway markings. */
class AirportReviewFixesTest {

	private static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	@Test
	void withoutAnArticleBeforeTheNounStillMeansNone() {
		assertFalse(spec("airport without a train station").airport().rail());
		assertFalse(spec("airport, no train station").airport().rail());
		assertTrue(spec("airport with a train station").airport().rail());
		assertFalse(spec("airport without a terminal").airport().terminal());
		assertFalse(spec("airport with no terminal").airport().terminal());
		assertTrue(spec("airport with a terminal").airport().terminal());
	}

	@Test
	void aRailwayStationNamedAfterAnAirportIsARailwayStation() {
		final StationSpec schiphol = spec("Schiphol airport station, 4 tracks, island platforms");
		assertFalse(schiphol.isAirport());
		assertEquals(4, schiphol.trackCount());
		assertFalse(spec("airport express station").isAirport());
		assertFalse(spec("airport rail station with 2 tracks").isAirport());
		// an airport still is one, with or without a station under it
		assertTrue(spec("airport").isAirport());
		assertTrue(spec("airport, rail link").isAirport());
		assertTrue(spec("airport with 4 gates and a train station").isAirport());
		assertTrue(spec("2 runways").isAirport());
	}

	/** Every cell a plane rolls over is clear above the pavement: no mast, pole or wall on any route cell. */
	@Test
	void nothingStandsOnThePlanesPath() {
		for (final String words : List.of("airport", "airport with 6 gates", "airport with 4 gates and a train station", "large airport")) {
			final StationSpec spec = spec(words);
			final StationPlan plan = StationPlanner.plan(spec);
			final Set<Long> standing = new HashSet<>();
			for (final PlannedBlock b : plan.blocks()) {
				if (b.y() > StationGeometry.WALK_Y) {
					standing.add(key(b.u(), b.v()));
				}
			}
			final AirportRoute route = AirportRoute.of(AirportGeometry.of(spec.airport()));
			assertFalse(route.gates().isEmpty(), words + ": " + route.problem());
			for (final AirportRoute.Gate gate : route.gates()) {
				for (final AirportRoute.Leg leg : gate.legs()) {
					for (final AirportRoute.Cell cell : leg.cells()) {
						assertFalse(standing.contains(key(cell.u(), cell.v())), words + ": something stands on gate " + gate.index() + "'s "
								+ leg.kind() + " at " + cell);
					}
				}
			}
		}
	}

	private static long key(int u, int v) {
		return ((long) u << 32) ^ (v & 0xffffffffL);
	}

	/** ICAO Annex 14, code C instrument runway: taxiway 158 m (237 blocks) off, parallel runways 210 m (315 blocks) apart. */
	@Test
	void taxiwayAndSecondRunwayStandAtRealSeparations() {
		for (final int width : new int[]{30, 38, 45}) {
			final AirportGeometry air = AirportGeometry.of(new AirportSpec(2, 2550, width, 4, true));
			assertTrue(air.runwayCentre(0) - air.taxiCentre() >= 237, width + " wide: taxiway " + (air.runwayCentre(0) - air.taxiCentre()));
			assertTrue(air.runwayCentre(1) - air.runwayCentre(0) >= 315, width + " wide: runways " + (air.runwayCentre(1) - air.runwayCentre(0)));
		}
	}

	@Test
	void thresholdStripesFollowTheRunwayWidth() {
		assertEquals(8, PlanAirport.thresholdStripes(45));
		assertEquals(12, PlanAirport.thresholdStripes(68));
		assertEquals(4, PlanAirport.thresholdStripes(30));
		// a 45-block (30 m) runway: four stripes a side, each 3 wide, inside the edge line and clear of the centreline
		final List<int[]> side = PlanAirport.stripeColumns(0, 22, 45);
		assertEquals(4, side.size());
		for (final int[] stripe : side) {
			assertEquals(3, stripe[1] - stripe[0] + 1);
			assertTrue(stripe[0] >= 3 && stripe[1] <= 20, stripe[0] + ".." + stripe[1]);
		}
	}

	/** A 2,550-block (1,700 m) runway: aiming point at 300 m, four pairs of touchdown bars at 150, 450, 600 and 750 m going 3, 2, 2, 1. */
	@Test
	void touchdownBarsFollowTheLandingDistance() {
		final List<PlanAirport.Bars> bars = PlanAirport.touchdownBars(2550);
		assertEquals(new PlanAirport.Bars(450, 1, 68, 9), bars.get(0), "aiming point");
		assertEquals(List.of(225, 675, 900, 1125), bars.subList(1, bars.size()).stream().map(PlanAirport.Bars::from).toList());
		assertEquals(List.of(3, 2, 2, 1), bars.subList(1, bars.size()).stream().map(PlanAirport.Bars::stripes).toList());
		assertEquals(34, bars.get(1).length());
		// a short runway gets one pair and a shorter aiming point nearer the threshold
		final List<PlanAirport.Bars> shortRunway = PlanAirport.touchdownBars(1000);
		assertEquals(2, shortRunway.size());
		assertEquals(225, shortRunway.get(0).from());
	}

	/** The painted threshold of the default airport: 8 stripes of 3 across a row, plus the two edge lines. */
	@Test
	void theDefaultRunwayIsPaintedWithEightThresholdStripes() {
		final StationSpec spec = spec("airport");
		final AirportGeometry air = AirportGeometry.of(spec.airport());
		final StationPlan plan = StationPlanner.plan(spec);
		final int row = air.runwayV0() + 10;
		final long white = plan.blocks().stream().filter(b -> b.v() == row && b.blockId().equals(PlanAirport.PAINT_WHITE)
				&& b.u() >= air.runwayU0().get(0) && b.u() <= air.runwayU1(0)).count();
		assertEquals(8 * 3 + 2, white);
	}

	@Test
	void thePlansStillPassTheirChecks() {
		for (final String words : List.of("airport", "2 runways", "large airport", "airport with 4 gates and a train station")) {
			final StationPlan plan = StationPlanner.plan(spec(words));
			assertEquals(List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		}
	}
}
