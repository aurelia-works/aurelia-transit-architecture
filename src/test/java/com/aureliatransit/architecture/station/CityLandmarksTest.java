package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The realism pass of 10-25 (batch b): a city's real station, built when the station is named after it. */
class CityLandmarksTest {

	static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	static long count(StationPlan plan, String block) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(block)).count();
	}

	@Test
	void tokyoIsRedBrickWithTwoDomedEndsFollowingItsLength() {
		for (final String words : new String[]{"tokyo station, 6 tracks, 120 long, named Tokyo",
				"ten track tokyo station, five island platforms, 300 long, station building with footbridge, named Tokyo"}) {
			final StationSpec spec = spec(words);
			assertEquals(Landmarks.TOKYO, Landmarks.of(spec), words);
			final StationPlan plan = StationPlanner.plan(spec);
			final int bw = plan.layout().buildingWidth();
			final var finials = plan.blocks().stream().filter(b -> b.u() < bw && b.blockId().equals("minecraft:lightning_rod")).toList();
			assertEquals(2, finials.size(), words);
			assertTrue(count(plan, "minecraft:bricks") > 1000, words);
			final var span = plan.blocks().stream().filter(b -> b.u() < bw && b.blockId().equals("minecraft:bricks")).mapToInt(PlannedBlock::v).summaryStatistics();
			// the domes stand at the two ends of the building, and the brick runs the whole way between them
			for (final PlannedBlock f : finials) {
				assertTrue(Math.min(f.v() - span.getMin(), span.getMax() - f.v()) <= 12, "finial at " + f.v() + " in " + span);
				assertTrue(f.y() > 30, "finial at " + f.y());
			}
		}
	}

	@Test
	void aBuildingTooShortForTokyoEndPavilionsStillBuilds() {
		final StationPlan plan = StationPlanner.plan(spec("tokyo station, 2 tracks, 48 long, named Tokyo"));
		assertNotNull(plan);
		assertTrue(plan.blocks().size() > 1000);
	}

	@Test
	void everySheetPassesTheAuditOverASpreadOfSizes() {
		final String[][] cities = {{"tokyo", "Tokyo"}, {"beijing", "Beijing South"}, {"kuala lumpur", "Kuala Lumpur"}, {"surbiton", "Surbiton"}};
		for (final String[] city : cities) {
			for (final int tracks : new int[]{1, 3, 4, 6, 10}) {
				for (final int length : new int[]{96, 120, 160, 300}) {
					for (final String extra : new String[]{"", ", side platforms", ", compact building", ", grand building"}) {
						final String words = tracks + " track " + city[0] + " station, " + length + " long" + extra + ", named " + city[1];
						final StationPlan plan = StationPlanner.plan(spec(words));
						assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
						assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
					}
				}
			}
		}
	}

	@Test
	void beijingKualaLumpurAndSurbitonHaveTheirSheets() {
		assertEquals(Landmarks.BEIJING, Landmarks.of(spec("ten track beijing station, 300 long, named Beijing South")));
		assertEquals(Landmarks.KUALA_LUMPUR, Landmarks.of(spec("four track kuala lumpur station, 160 long, named Kuala Lumpur")));
		assertEquals(Landmarks.SURBITON, Landmarks.of(spec("four track surbiton station, 250 long, named Surbiton")));
		final StationPlan beijing = StationPlanner.plan(spec("ten track beijing station, 300 long, named Beijing South"));
		assertTrue(count(beijing, "minecraft:prismarine_bricks") > 100, "blue-green glazed tile towers");
		final StationPlan kl = StationPlanner.plan(spec("four track kuala lumpur station, 160 long, named Kuala Lumpur"));
		assertTrue(count(kl, "minecraft:calcite") > 1000, "white Moorish walls");
	}
}
