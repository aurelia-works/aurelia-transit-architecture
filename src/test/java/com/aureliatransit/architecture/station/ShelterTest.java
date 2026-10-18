package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 5: an open platform gets a waiting shelter, a roofed one does not, and the station stays clean. */
class ShelterTest {

	private static final String SHELTER = "aurelia_transit_architecture:sbahn_shelter";

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	private static List<PlannedBlock> shelters(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(SHELTER)).toList();
	}

	@Test
	void aHaltHasAShelterFacingItsTrack() {
		final StationPlan plan = plan("halt class");
		final List<PlannedBlock> shelters = shelters(plan);
		assertEquals(3, shelters.size(), "one shelter of three blocks on the one platform");
		final TrackLayout.PlatformBand band = plan.layout().platforms().get(0);
		final TrackLayout.Track track = plan.layout().tracks().get(0);
		for (final PlannedBlock shelter : shelters) {
			assertEquals(StationGeometry.WALK_Y, shelter.y());
			assertEquals(shelters.get(0).u(), shelter.u(), "one row");
			assertTrue(shelter.v() > band.vFrom() + 1 && shelter.v() < band.vTo() - 1);
			// the open side is the track's: a side platform on the left of its track faces +u
			assertEquals(track.u() > shelter.u() ? LocalDir.PLUS_U : LocalDir.MINUS_U, shelter.front());
			assertTrue(Math.abs(shelter.u() - track.u()) > StationGeometry.EDGE_OFFSET, "not on the boarding edge");
		}
		// consecutive cells in the middle of the platform
		assertEquals(shelters.get(0).v() + 1, shelters.get(1).v());
		assertEquals(shelters.get(0).v() + 2, shelters.get(2).v());
		final int middle = (band.vFrom() + band.vTo()) / 2;
		assertTrue(Math.abs(shelters.get(1).v() - middle) <= 8, "near the middle: " + shelters.get(1).v() + " of " + middle);
	}

	@Test
	void everyOpenPlatformHasOneAndRoofedOnesNone() {
		assertEquals(6, shelters(plan("2 tracks, 64 long, no roof")).size(), "two side platforms, three blocks each");
		final StationPlan island = plan("2 tracks island, 64 long, no roof, footbridge");
		assertTrue(shelters(island).size() >= 6, "an island: two rows back to back, " + shelters(island).size());
		assertEquals(0, shelters(plan("2 tracks, 64 long, canopy")).size(), "a canopy is cover already");
		assertEquals(0, shelters(plan("2 tracks, 64 long, hall roof")).size());
		assertEquals(0, shelters(plan("2 tracks, 64 long, no roof, no benches")).size(), "no furniture, no shelter");
		assertEquals(0, shelters(plan("2 tracks, 16 long, no roof")).size(), "too short for a shelter");
		// one platform open, the other roofed
		final StationPlan mixed = plan("platform 1: side open; platform 2: side canopy; length 64");
		assertEquals(3, shelters(mixed).size());
	}
}
