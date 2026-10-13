package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.WorldOp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationZoneTest {

	private static WorldOp op(WorldOp.Kind kind, int x, int y, int z) {
		return new WorldOp(kind, x, y, z, Role.FLOOR, "minecraft:stone", Heading.NORTH, "");
	}

	@Test
	void zoneIsThePlacedFootprintOneWiderWithoutTerrainWork() {
		final StationZone zone = StationZone.of("  Central ", List.of(op(WorldOp.Kind.PLACE, 10, 64, 20), op(WorldOp.Kind.PLACE, 30, 70, 25),
				op(WorldOp.Kind.CLEAR, 100, 64, 100), op(WorldOp.Kind.FILL, -50, 60, -50)));
		assertEquals("Central", zone.name());
		assertEquals(List.of(9, 19, 31, 26, 67), List.of(zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ(), zone.centerY()));
		assertEquals(StationZone.colorFor("Central"), zone.color());
		assertTrue(zone.overlaps(31, 26, 40, 40), "touching corner overlaps");
		assertFalse(zone.overlaps(32, 0, 40, 40));
		assertTrue(zone.reach() >= Math.hypot(11, 3.5));
	}

	@Test
	void unnamedStationsGetADefaultNameAndEmptyBuildsNoZone() {
		assertEquals(StationZone.UNNAMED, StationZone.of("", List.of(op(WorldOp.Kind.PLACE, 0, 0, 0))).name());
		assertNull(StationZone.of("X", List.of(op(WorldOp.Kind.CLEAR, 0, 0, 0))));
		assertEquals(StationZone.colorFor("A"), StationZone.colorFor("A"), "steady per name");
	}
}
