package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro, 10-20: platforms are numbered per track face from the building side, signed so, and laid in MTR with those numbers. */
class PlatformNumbersTest {

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	@Test
	void anIslandHasTwoNumbers() {
		final StationPlan plan = plan("4 track station, two island platforms, footbridge, 64 long");
		final TrackLayout layout = plan.layout();
		final List<Integer> all = layout.platforms().stream().flatMap(band -> layout.platformNumbers(band).stream()).toList();
		assertEquals(List.of(1, 2, 3, 4), all);
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			assertEquals(2, layout.platformNumbers(band).size(), "island " + band.number());
		}
	}

	@Test
	void sidePlatformsCountFromTheLeft() {
		final StationPlan plan = plan("2 track station, side platforms, 48 long");
		final TrackLayout layout = plan.layout();
		assertEquals(List.of(1, 2), layout.tracks().stream().map(layout::platformNumber).toList());
	}

	@Test
	void throughTracksHaveNoNumberAndNoRail() {
		final TrackLayout.Track through = new TrackLayout.Track(2, 9, false, false);
		final TrackLayout layout = new TrackLayout(20, 40, 0, List.of(new TrackLayout.Track(1, 4, false, true), through,
				new TrackLayout.Track(3, 14, true, false)), List.of(), 0, 0, 0, 0);
		assertEquals(0, layout.platformNumber(through));
		assertEquals(2, layout.platformNumber(layout.tracks().get(2)));
	}

	@Test
	void signsShowTheFaceNumbers() {
		final StationPlan plan = plan("4 track station, two island platforms, footbridge, 64 long");
		final List<String> numbers = plan.blocks().stream().filter(b -> b.blockId().equals(StationPlanner.NUMBER_SIGN))
				.map(PlannedBlock::text).distinct().sorted().toList();
		assertEquals(List.of("1", "2", "3", "4"), numbers);
		assertTrue(plan.blocks().stream().anyMatch(b -> b.text().equals("DOWN:Platforms 1–2")), "wayfinding names both faces");
	}

	@Test
	void everyPlatformTrackGetsANumberedRailBetweenItsNodes() {
		final StationPlan plan = plan("4 track station, two island platforms, 64 long");
		final LocalFrame frame = new LocalFrame(100, 64, 200, Heading.NORTH);
		final List<PlatformRail> rails = PlatformRail.of(plan, frame);
		assertEquals(List.of("1", "2", "3", "4"), rails.stream().map(PlatformRail::name).toList());
		for (final PlatformRail rail : rails) {
			final TrackLayout.Track track = plan.layout().tracks().get(Integer.parseInt(rail.name()) - 1);
			final int from = plan.layout().platformFrom(track);
			assertEquals(frame.x(track.u(), from), rail.x1());
			assertEquals(frame.z(track.u(), from), rail.z1());
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RAIL_NODE && b.u() == track.u() && b.v() == from));
		}
		assertTrue(PlatformRail.of(plan("2 track station, no rail nodes"), frame).isEmpty());
	}

	@Test
	void gateSignsRunOverFaces() {
		assertEquals("Platforms 1–4", TrackLayout.platformsText(List.of(1, 2, 3, 4)));
		assertEquals("Platform 3", TrackLayout.platformsText(List.of(3)));
	}
}
