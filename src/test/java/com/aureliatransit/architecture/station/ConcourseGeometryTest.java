package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scale-aware concourse: how deep a side building or a terminus head building is, how wide the over-track corridor
 * is, and that the railway layout agrees with it. The depths grow with the scale, so a city or grand station gets a real
 * hall rather than only more storeys; the corridor stays the compact width for now.
 */
class ConcourseGeometryTest {

	/** A side building beside two tracks: the station the planner shifts the railway back for. */
	private static StationSpec sideBuilding(StationScale scale) {
		return StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.SIDE).platformLength(64).platformWidth(5)
				.building(BuildingStyle.GOTHIC_BRICK).scale(scale).build();
	}

	/** A terminus on an island pair: the station with a head building across the track ends. */
	private static StationSpec terminus(StationScale scale) {
		return StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformLength(48).platformWidth(6)
				.building(BuildingStyle.GOTHIC_BRICK).terminus(true).scale(scale).build();
	}

	/** A grand platform length with a building and a footbridge: the station the corridor is measured on. */
	private static StationSpec footbridge(StationScale scale) {
		return StationSpec.builder().trackCount(2).platformLayout(PlatformLayout.ISLAND).platformLength(96).platformWidth(6)
				.building(BuildingStyle.GOTHIC_BRICK).footbridge(true).scale(scale).build();
	}

	@Test
	void compactKeepsTheOldBlockSizes() {
		assertEquals(9, StationGeometry.BUILDING_DEPTH);
		assertEquals(12, StationGeometry.HEAD_DEPTH);
		assertEquals(3, StationGeometry.BRIDGE_WIDTH);
		assertEquals(StationGeometry.BUILDING_DEPTH, StationGeometry.buildingDepth(StationScale.COMPACT));
		assertEquals(StationGeometry.HEAD_DEPTH, StationGeometry.headDepth(StationScale.COMPACT));
		assertEquals(StationGeometry.BRIDGE_WIDTH, StationGeometry.bridgeWidth(StationScale.COMPACT));
	}

	@Test
	void eachScaleGetsARealHallDepth() {
		// compact keeps the original block; city and grand are real halls, deeper than the one before
		assertEquals(StationGeometry.BUILDING_DEPTH, StationGeometry.buildingDepth(StationScale.COMPACT));
		assertEquals(StationGeometry.CITY_BUILDING_DEPTH, StationGeometry.buildingDepth(StationScale.CITY));
		assertEquals(StationGeometry.GRAND_BUILDING_DEPTH, StationGeometry.buildingDepth(StationScale.GRAND));
		assertTrue(StationGeometry.buildingDepth(StationScale.CITY) > StationGeometry.buildingDepth(StationScale.COMPACT));
		assertTrue(StationGeometry.buildingDepth(StationScale.GRAND) > StationGeometry.buildingDepth(StationScale.CITY));
	}

	@Test
	void eachScaleGetsARealHeadDepth() {
		assertEquals(StationGeometry.HEAD_DEPTH, StationGeometry.headDepth(StationScale.COMPACT));
		assertEquals(StationGeometry.CITY_HEAD_DEPTH, StationGeometry.headDepth(StationScale.CITY));
		assertEquals(StationGeometry.GRAND_HEAD_DEPTH, StationGeometry.headDepth(StationScale.GRAND));
		assertTrue(StationGeometry.headDepth(StationScale.CITY) > StationGeometry.headDepth(StationScale.COMPACT));
		assertTrue(StationGeometry.headDepth(StationScale.GRAND) > StationGeometry.headDepth(StationScale.CITY));
	}

	@Test
	void bridgeWidthIsTheCompactCorridorAtEveryScale() {
		for (final StationScale scale : StationScale.values()) {
			final int width = StationGeometry.bridgeWidth(scale);
			assertEquals(StationGeometry.BRIDGE_WIDTH, width, scale.toString());
			assertEquals(1, width % 2, scale + ": corridor width is odd");
			// the walls stand a whole column off the centre line: half the clear width plus the wall itself
			assertEquals(width / 2 + 1, StationGeometry.bridgeWallOffset(width), scale.toString());
		}
	}

	@Test
	void sideBuildingLayoutShiftsTheRailwayByTheScaledDepth() {
		for (final StationScale scale : StationScale.values()) {
			final StationSpec spec = sideBuilding(scale);
			final TrackLayout layout = StationGeometry.layout(spec);
			assertEquals(StationGeometry.APRON + StationGeometry.buildingDepth(scale), layout.buildingWidth(), scale.toString());
			assertEquals(0, layout.headDepth(), scale.toString());
			// the platform beside the building starts at the building's platform wall
			assertEquals(layout.buildingWidth(), layout.platforms().get(0).uMin(), scale.toString());
		}
	}

	@Test
	void terminusHeadBuildingRunsTheScaledDepthPastThePlatformEnds() {
		for (final StationScale scale : StationScale.values()) {
			final StationSpec spec = terminus(scale);
			final TrackLayout layout = StationGeometry.layout(spec);
			final int depth = StationGeometry.headDepth(scale);
			assertEquals(0, layout.buildingWidth(), scale.toString());
			assertEquals(depth + StationGeometry.HEAD_APRON, layout.headDepth(), scale.toString());
			assertEquals(layout.platformLength() + depth - 1, layout.headEnd(), scale.toString());
			assertEquals(layout.headEnd() + StationGeometry.HEAD_APRON, layout.vMax(), scale.toString());
		}
	}

	@Test
	void footbridgeCorridorTakesTheScaledWidth() {
		for (final StationScale scale : StationScale.values()) {
			final StationSpec spec = footbridge(scale);
			final TrackLayout layout = StationGeometry.layout(spec);
			assertEquals(StationGeometry.bridgeWidth(scale), StationGeometry.bridgeWidth(spec, layout), scale.toString());
			final StationLayout site = StationLayout.of(spec, layout);
			assertTrue(site.bridge(), scale + ": a footbridge was asked for");
			assertEquals(StationGeometry.bridgeWidth(scale), site.bridgeWidth(), scale.toString());
			// centred: the stair wall is offset symmetrically from the corridor's centre line
			assertEquals(site.bridgeV() + StationGeometry.bridgeWallOffset(site.bridgeWidth()), site.stairWallV(), scale.toString());
		}
	}
}
