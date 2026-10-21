package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flat MTR escalators as moving walkways: on the footbridge clear of the exits, and in stretches along long platforms. */
class TravelatorTest {

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static List<PlannedBlock> steps(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(StylePalette.MTR_ESCALATOR_STEP)).toList();
	}

	@Test
	void onlyWhenAskedFor() {
		assertEquals(List.of(), steps(plan("4 tracks island 12 wide 120 long footbridge")));
	}

	@Test
	void theFootbridgeCorridorHasNoTravelator() {
		final StationPlan plan = plan("4 tracks island 12 wide 120 long footbridge travelators");
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
		assertEquals(List.of(), steps(plan).stream().filter(b -> b.y() == StationGeometry.UPPER_FLOOR_Y).toList(), "travelators only on the platforms");
	}

	@Test
	void longPlatformsGetStretchesWithGapsToCross() {
		final StationPlan plan = plan("4 tracks island 12 wide 120 long footbridge travelators");
		final List<PlannedBlock> floor = steps(plan).stream().filter(b -> b.y() == StationGeometry.PLATFORM_Y).toList();
		assertTrue(!floor.isEmpty());
		for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
			final List<Integer> rows = floor.stream().filter(b -> band.contains(b.u(), b.v())).map(PlannedBlock::v).distinct().sorted().toList();
			assertTrue(!rows.isEmpty(), "platform " + band.number());
			int run = 1;
			for (int i = 1; i < rows.size(); i++) {
				run = rows.get(i) == rows.get(i - 1) + 1 ? run + 1 : 1;
				assertTrue(run <= 16, "a stretch longer than 16 on platform " + band.number());
			}
		}
		// short platforms keep their floor
		assertTrue(steps(plan("4 tracks island 12 wide 48 long footbridge travelators")).stream().noneMatch(b -> b.y() == StationGeometry.PLATFORM_Y));
	}

	@Test
	void wideIslandsGetALaneAlongEachSideAndFurnitureBetween() {
		// Jah, 10-20: one full walkway each way, just inside the column lines, signs and benches in the strip between
		final StationPlan plan = plan("4 tracks island 160 long footbridge travelators named Hi");
		assertEquals(List.of(), LayoutValidator.validate(plan));
		boolean checked = false;
		for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
			if (!band.island()) {
				continue;
			}
			final List<PlannedBlock> floor = steps(plan).stream().filter(b -> b.y() == StationGeometry.PLATFORM_Y && band.contains(b.u(), b.v())).toList();
			final int v = floor.get(floor.size() / 2).v();
			final List<PlannedBlock> row = floor.stream().filter(b -> b.v() == v).sorted(java.util.Comparator.comparingInt(PlannedBlock::u)).toList();
			assertEquals(4, row.size(), "two lanes two wide at v " + v);
			assertEquals(row.get(0).u() + 1, row.get(1).u());
			assertEquals(row.get(2).u() + 1, row.get(3).u());
			assertTrue(row.get(2).u() - row.get(1).u() - 1 >= PlanMovingWalks.SIDE_LANE_MIDDLE, "a middle strip");
			// each lane is one walkway: both halves run the same way, the two lanes opposite ways
			assertEquals(row.get(0).front(), row.get(1).front());
			assertEquals(row.get(2).front(), row.get(3).front());
			assertTrue(row.get(0).front() != row.get(2).front());
			final int lo = row.get(1).u();
			final int hi = row.get(2).u();
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.BENCH && b.u() > lo && b.u() < hi && band.contains(b.u(), b.v())),
					"benches in the middle strip of platform " + band.number());
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.NAME_SIGN && b.u() > lo && b.u() < hi && band.contains(b.u(), b.v())),
					"name signs in the middle strip of platform " + band.number());
			checked = true;
		}
		assertTrue(checked);
	}
}
