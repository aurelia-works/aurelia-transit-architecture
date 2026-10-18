package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 5: an open platform gets a waiting shelter, a roofed one does not, and the station stays clean. */
class ShelterTest {

	private static final String GLASS = "aurelia_transit_architecture:bus_shelter_glass";
	private static final String ROOF = "aurelia_transit_architecture:bus_shelter_roof";
	private static final String SEAT = "aurelia_transit_architecture:bus_shelter_seat";

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	private static List<PlannedBlock> shelters(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(GLASS) || b.blockId().equals(ROOF) || b.blockId().equals(SEAT)).toList();
	}

	private static long count(StationPlan plan, String id) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(id)).count();
	}

	@Test
	void aHaltHasAWalkInShelterWithItsSeatFacingTheTrack() {
		final StationPlan plan = plan("halt class");
		assertEquals(6, count(plan, GLASS), "a wall two high and three long");
		assertEquals(3, count(plan, SEAT));
		assertEquals(6, count(plan, ROOF), "a roof over the wall and the seat");
		final TrackLayout.PlatformBand band = plan.layout().platforms().get(0);
		final TrackLayout.Track track = plan.layout().tracks().get(0);
		final PlannedBlock wall = plan.blocks().stream().filter(b -> b.blockId().equals(GLASS)).findFirst().orElseThrow();
		final PlannedBlock seat = plan.blocks().stream().filter(b -> b.blockId().equals(SEAT)).findFirst().orElseThrow();
		// the wall is at the back, the seat between it and the track, both turned the way the wall is from the seat
		assertTrue(Math.abs(seat.u() - track.u()) < Math.abs(wall.u() - track.u()), "the seat is nearer the track than the wall");
		assertEquals(Math.abs(wall.u() - seat.u()), 1);
		final LocalDir towardWall = wall.u() > seat.u() ? LocalDir.PLUS_U : LocalDir.MINUS_U;
		assertEquals(towardWall, seat.front());
		assertEquals(towardWall, wall.front());
		assertEquals(StationGeometry.WALK_Y, seat.y());
		plan.blocks().stream().filter(b -> b.blockId().equals(ROOF)).forEach(b -> assertEquals(StationGeometry.WALK_Y + 2, b.y()));
		// consecutive cells near the middle of the platform
		final int middle = (band.vFrom() + band.vTo()) / 2;
		assertTrue(Math.abs(seat.v() - middle) <= 10, "near the middle: " + seat.v() + " of " + middle);
		assertTrue(Math.abs(seat.u() - track.u()) > StationGeometry.EDGE_OFFSET, "not on the boarding edge");
	}

	@Test
	void everyOpenPlatformHasOneAndRoofedOnesNone() {
		assertEquals(2, count(plan("2 tracks, 64 long, no roof"), SEAT) / 3, "two side platforms, one shelter each");
		final StationPlan island = plan("2 tracks island, 64 long, no roof, footbridge");
		assertEquals(6, count(island, SEAT), "an island: a seat each side of the wall");
		assertEquals(0, shelters(plan("2 tracks, 64 long, canopy")).size(), "a canopy is cover already");
		assertEquals(0, shelters(plan("2 tracks, 64 long, hall roof")).size());
		assertEquals(0, shelters(plan("2 tracks, 64 long, no roof, no benches")).size(), "no furniture, no shelter");
		assertEquals(0, shelters(plan("2 tracks, 16 long, no roof")).size(), "too short for a shelter");
		// one platform open, the other roofed
		final StationPlan mixed = plan("platform 1: side open; platform 2: side canopy; length 64");
		assertEquals(3, count(mixed, SEAT));
	}
}
