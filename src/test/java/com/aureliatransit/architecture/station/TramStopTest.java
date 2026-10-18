package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3: the "tram" word puts a tram stop on a hub's forecourt in place of the taxi rank. */
class TramStopTest {

	private static final String[] HUBS = {"6 tracks island, 120 long, hub, berlin, tram", "4 tracks island, 96 long, hub, hamburg, tram stop",
			"hub, grand class, trams", "8 tracks island, 160 long, hub, st pancras, tram, named Hub"};

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	private static List<PlannedBlock> forecourt(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.v() >= plan.layout().forecourtFrom()).toList();
	}

	@Test
	void aHubWithTramsHasAStopInsteadOfTheTaxiRank() {
		for (final String words : HUBS) {
			final StationPlan plan = plan(words);
			assertTrue(plan.spec().tram(), words);
			final TrackLayout layout = plan.layout();
			final List<PlannedBlock> forecourt = forecourt(plan);
			final int f0 = layout.forecourtFrom();
			// the street track: one straight run along u, from the forecourt's side edge, all at street level
			final List<PlannedBlock> track = forecourt.stream().filter(b -> b.blockId().endsWith(":tram_track")).toList();
			assertTrue(track.size() >= 8, words + ": track " + track.size());
			assertTrue(track.stream().allMatch(b -> b.v() == track.get(0).v() && b.y() == StationGeometry.PLATFORM_Y - 1
					&& b.front() == LocalDir.PLUS_U), words);
			assertTrue(track.stream().anyMatch(b -> b.u() == layout.headFrom()), words + ": the track leaves the forecourt at its side");
			// nothing stands in the tram's envelope over the track, except the bollard at its end
			final int maxU = track.stream().mapToInt(PlannedBlock::u).max().orElseThrow();
			for (final PlannedBlock block : forecourt) {
				if (block.y() >= StationGeometry.PLATFORM_Y && block.u() <= maxU
						&& Math.abs(block.v() - track.get(0).v()) <= StationGeometry.ENVELOPE_HALF_WIDTH) {
					throw new AssertionError(words + ": " + block + " stands in the tram's way");
				}
			}
			assertTrue(forecourt.stream().anyMatch(b -> b.blockId().endsWith(":bollard") && b.u() == maxU + 1 && b.v() == track.get(0).v()),
					words + ": a bollard at the track's end");
			// the stop: two signs, a three-seat shelter facing the track, and no taxi bays
			assertEquals(2, forecourt.stream().filter(b -> b.blockId().endsWith(":tram_stop_sign")).count(), words);
			final int door = (layout.headFrom() + layout.headTo() + 1) / 2;
			final List<PlannedBlock> seats = forecourt.stream().filter(b -> b.blockId().endsWith(":bus_shelter_seat") && b.u() < door).toList();
			assertEquals(3, seats.size(), words);
			assertTrue(seats.stream().allMatch(b -> b.front() == LocalDir.MINUS_V), words + ": the seats' backs to the building");
			assertEquals(1, forecourt.stream().filter(b -> b.blockId().endsWith(":bollard")).count(), words + ": no taxi bollards");
			// the bus interchange on the other side stays as it was
			assertEquals(2, forecourt.stream().filter(b -> b.blockId().endsWith(":bus_stop_sign")).count(), words);
			// the way from the door is clear
			for (final PlannedBlock block : forecourt) {
				assertFalse(Math.abs(block.u() - door) <= 2 && block.y() >= 0, words + ": " + block + " stands in the way from the door");
			}
		}
	}

	@Test
	void onlyAHubGetsOne() {
		final ParseResult terminus = StationDescriptionParser.parse("4 tracks island, 96 long, terminus, tram");
		assertFalse(terminus.spec().tram());
		assertTrue(terminus.warnings().stream().anyMatch(w -> w.contains("tram stop")), terminus.warnings().toString());
		assertFalse(StationDescriptionParser.parse("hub, grand class").spec().tram());
		assertTrue(forecourt(plan("hub, grand class")).stream().noneMatch(b -> b.blockId().contains(":tram_")));
	}
}
