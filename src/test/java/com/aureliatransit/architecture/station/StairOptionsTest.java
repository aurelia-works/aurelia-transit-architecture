package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.studio.StudioApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A second footbridge stair facing the other way, and walls round the stairwells: only when asked for. */
class StairOptionsTest {

	private static StationSpec spec(String text) {
		return StationDescriptionParser.parse(text).spec();
	}

	private static void clean(StationPlan plan) {
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable(), StationAudit.audit(plan).problems().toString());
	}

	@Test
	void wordsAndStudioAskForThem() {
		final StationSpec plain = spec("4 tracks island footbridge");
		assertFalse(plain.stairsBothWays() || plain.enclosedStairs() || plain.travelators());
		assertTrue(spec("4 tracks island footbridge stairs both ways").stairsBothWays());
		assertTrue(spec("4 tracks island footbridge reverse stairs").stairsBothWays());
		assertTrue(spec("4 tracks island footbridge enclosed stairs").enclosedStairs());
		assertTrue(spec("4 tracks island footbridge moving walkways").travelators());
		final StationSpec studio = spec(StudioApi.typed(Map.of("text", "4 tracks island footbridge", "esc", "both", "walls", "1")));
		assertTrue(studio.escalators() && studio.stairsBothWays() && studio.enclosedStairs());
		for (final String text : List.of("stairs both ways", "reverse stairs", "enclosed stairs", "walled stairwells", "moving walkways", "travelators")) {
			assertEquals(List.of(), StationDescriptionParser.parse("4 tracks island footbridge " + text).ignored(), text);
		}
		// "both" on its own still mixes looks
		assertFalse(spec("both utrecht and hamburg 4 tracks island footbridge compact").stairsBothWays());
	}

	@Test
	void wideIslandsGetASecondFlightBesideTheLift() {
		final StationSpec spec = spec("4 tracks island 12 wide 100 long footbridge stairs both ways");
		final TrackLayout layout = StationGeometry.layout(spec);
		final StationLayout site = StationLayout.of(spec, layout);
		final StationPlan plan = StationPlanner.plan(spec);
		clean(plan);
		int reversed = 0;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final List<StationLayout.Flight> flights = site.flights(band);
			if (flights.size() < 2) {
				continue;
			}
			reversed++;
			final StationLayout.Flight main = flights.get(0);
			final StationLayout.Flight other = flights.get(1);
			assertEquals(-main.side(), other.side());
			assertEquals(main.front().opposite(), other.front());
			// on the corridor's other side, two columns over from the stair (where the lift stands)
			assertEquals(site.bridgeV() - 2 * site.stairSide(), other.wallV());
			assertEquals(main.columns().get(1) + 2, other.columns().get(0));
			final StationLayout.Lift lift = site.platformLift(band, layout);
			assertTrue(lift != null && lift.c0() == main.columns().get(0), "the lift stands on the stair columns beside the second flight");
			for (final int u : other.columns()) {
				for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
					final int uu = u;
					final int yy = y;
					assertTrue(plan.blocks().stream().anyMatch(b -> b.u() == uu && b.y() == yy && b.v() == other.stepV(yy) && b.role() == Role.STAIR
							&& b.front() == other.front()), "step " + y + " at u=" + u);
				}
			}
		}
		assertEquals(2, reversed);
	}

	@Test
	void narrowPlatformsKeepOneStair() {
		final StationSpec spec = spec("4 tracks island 8 wide 100 long footbridge stairs both ways");
		final TrackLayout layout = StationGeometry.layout(spec);
		final StationLayout site = StationLayout.of(spec, layout);
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			assertTrue(site.flights(band).size() <= 1);
		}
		clean(StationPlanner.plan(spec));
	}

	@Test
	void enclosedStairwellsAreWalledNotGlazed() {
		final StationPlan glass = StationPlanner.plan(spec("2 tracks side 6 wide 60 long footbridge"));
		final StationPlan walled = StationPlanner.plan(spec("2 tracks side 6 wide 60 long footbridge enclosed stairs"));
		clean(walled);
		final long glassWalls = glass.blocks().stream().filter(b -> b.role() == Role.WALL).count();
		final long walls = walled.blocks().stream().filter(b -> b.role() == Role.WALL).count();
		final long windows = glass.blocks().stream().filter(b -> b.role() == Role.WINDOW).count();
		final long walledWindows = walled.blocks().stream().filter(b -> b.role() == Role.WINDOW).count();
		assertTrue(walls > glassWalls, "stairwell flanks become walls");
		assertEquals(windows - walledWindows, walls - glassWalls, "every stairwell pane becomes a wall, nothing else changes");
	}
}
