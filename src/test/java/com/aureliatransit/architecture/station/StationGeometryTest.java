package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Railway geometry of every track count, platform layout and width: clearances, adjacency flags and the exact layouts the
 * planner relies on.
 */
class StationGeometryTest {

	private static final int[] WIDTHS = {3, 4, 6, 8};

	private static StationSpec spec(int tracks, PlatformLayout layout, int width) {
		return StationSpec.builder().trackCount(tracks).platformLayout(layout).platformWidth(width).build();
	}

	/** Every valid (tracks, layout, width) combination of the matrix. */
	private static List<StationSpec> matrix() {
		final List<StationSpec> specs = new ArrayList<>();
		for (int tracks = 1; tracks <= 4; tracks++) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				if (layout == PlatformLayout.ISLAND && tracks < 2) {
					continue;
				}
				for (final int width : WIDTHS) {
					specs.add(spec(tracks, layout, width));
				}
			}
		}
		return specs;
	}

	private static String label(StationSpec spec) {
		return spec.trackCount() + " tracks " + spec.platformLayout() + " w=" + spec.platformWidth();
	}

	@Test
	void neighbouringTracksAreAtLeastTheMinimumSpacingApart() {
		for (final StationSpec spec : matrix()) {
			final List<TrackLayout.Track> tracks = StationGeometry.layout(spec).tracks();
			assertEquals(spec.trackCount(), tracks.size(), label(spec));
			for (int i = 0; i + 1 < tracks.size(); i++) {
				assertTrue(tracks.get(i + 1).u() - tracks.get(i).u() >= StationGeometry.TRACK_SPACING, label(spec) + " track " + (i + 1));
				assertEquals(i + 1, tracks.get(i).number(), label(spec));
			}
		}
	}

	@Test
	void everyPlatformSideHasABandEdgeExactlyTheEdgeOffsetAway() {
		for (final StationSpec spec : matrix()) {
			final TrackLayout layout = StationGeometry.layout(spec);
			for (final TrackLayout.Track track : layout.tracks()) {
				assertEquals(track.platformLeft(), hasBand(layout, band -> band.trackRight() && band.uMax() == track.u() - StationGeometry.EDGE_OFFSET),
						label(spec) + " track " + track.number() + " left");
				assertEquals(track.platformRight(), hasBand(layout, band -> band.trackLeft() && band.uMin() == track.u() + StationGeometry.EDGE_OFFSET),
						label(spec) + " track " + track.number() + " right");
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				assertTrue(band.trackLeft() || band.trackRight(), label(spec) + " platform " + band.number() + " has no track");
				if (band.trackLeft()) {
					assertTrue(hasTrack(layout, track -> track.platformRight() && track.u() == band.uMin() - StationGeometry.EDGE_OFFSET), label(spec));
				}
				if (band.trackRight()) {
					assertTrue(hasTrack(layout, track -> track.platformLeft() && track.u() == band.uMax() + StationGeometry.EDGE_OFFSET), label(spec));
				}
				assertEquals(spec.platformWidth(), band.width(), label(spec));
			}
		}
	}

	@Test
	void noPlatformCellIsInsideATrackEnvelope() {
		for (final StationSpec spec : matrix()) {
			final TrackLayout layout = StationGeometry.layout(spec);
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final TrackLayout.Track track : layout.tracks()) {
					final int envelopeMin = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH;
					final int envelopeMax = track.u() + StationGeometry.ENVELOPE_HALF_WIDTH;
					assertTrue(band.uMax() < envelopeMin || band.uMin() > envelopeMax, label(spec) + " platform " + band.number() + " vs track " + track.number());
				}
			}
			for (int i = 0; i + 1 < layout.platforms().size(); i++) {
				assertTrue(layout.platforms().get(i).uMax() < layout.platforms().get(i + 1).uMin(), label(spec));
			}
			int leftmost = Integer.MAX_VALUE;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				leftmost = Math.min(leftmost, band.uMin());
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				leftmost = Math.min(leftmost, track.u() - StationGeometry.ENVELOPE_HALF_WIDTH);
			}
			assertEquals(0, leftmost, label(spec));
		}
	}

	@Test
	void widthCoversTheLastBandPlusItsClearance() {
		for (final StationSpec spec : matrix()) {
			final TrackLayout layout = StationGeometry.layout(spec);
			final TrackLayout.Track lastTrack = layout.tracks().get(layout.tracks().size() - 1);
			final TrackLayout.PlatformBand lastPlatform = layout.platforms().get(layout.platforms().size() - 1);
			final int expected = lastPlatform.uMax() > lastTrack.u() ? lastPlatform.uMax() + 1 : lastTrack.u() + StationGeometry.ENVELOPE_HALF_WIDTH + 1;
			assertEquals(expected, layout.width(), label(spec));
			assertEquals(StationGeometry.builtLength(spec.platformLength()), layout.platformLength(), label(spec));
		}
	}

	@Test
	void twoSideTracksAreExact() {
		final TrackLayout layout = StationGeometry.layout(spec(2, PlatformLayout.SIDE, 4));
		assertEquals(15, layout.width());
		assertEquals(2, layout.platforms().size());
		assertBand(layout.platforms().get(0), 1, 0, 3, false, true);
		assertBand(layout.platforms().get(1), 2, 11, 14, true, false);
		assertTrack(layout.tracks().get(0), 1, 5, true, false);
		assertTrack(layout.tracks().get(1), 2, 9, false, true);
	}

	@Test
	void twoIslandTracksAreExact() {
		final TrackLayout layout = StationGeometry.layout(spec(2, PlatformLayout.ISLAND, 6));
		assertEquals(12, layout.width());
		assertEquals(1, layout.platforms().size());
		assertBand(layout.platforms().get(0), 1, 3, 8, true, true);
		assertTrue(layout.platforms().get(0).island());
		assertTrack(layout.tracks().get(0), 1, 1, false, true);
		assertTrack(layout.tracks().get(1), 2, 10, true, false);
	}

	@Test
	void oneSideTrackHasASinglePlatformOnItsLeft() {
		final TrackLayout layout = StationGeometry.layout(spec(1, PlatformLayout.SIDE, 4));
		assertEquals(1, layout.platforms().size());
		assertBand(layout.platforms().get(0), 1, 0, 3, false, true);
		assertTrack(layout.tracks().get(0), 1, 5, true, false);
		assertEquals(7, layout.width());
	}

	@Test
	void threeIslandTracksAreAnIslandPairAndASideTrack() {
		final TrackLayout layout = StationGeometry.layout(spec(3, PlatformLayout.ISLAND, 6));
		// T I T T P
		assertEquals(3, layout.tracks().size());
		assertEquals(2, layout.platforms().size());
		assertTrack(layout.tracks().get(0), 1, 1, false, true);
		assertBand(layout.platforms().get(0), 1, 3, 8, true, true);
		assertTrack(layout.tracks().get(1), 2, 10, true, false);
		assertTrack(layout.tracks().get(2), 3, 14, false, true);
		assertBand(layout.platforms().get(1), 2, 16, 21, true, false);
		assertEquals(22, layout.width());
	}

	@Test
	void fourIslandTracksAreTwoIslandPairs() {
		final TrackLayout layout = StationGeometry.layout(spec(4, PlatformLayout.ISLAND, 6));
		// T I T T I T
		assertEquals(4, layout.tracks().size());
		assertEquals(2, layout.platforms().size());
		assertTrack(layout.tracks().get(0), 1, 1, false, true);
		assertBand(layout.platforms().get(0), 1, 3, 8, true, true);
		assertTrack(layout.tracks().get(1), 2, 10, true, false);
		assertTrack(layout.tracks().get(2), 3, 14, false, true);
		assertBand(layout.platforms().get(1), 2, 16, 21, true, true);
		assertTrack(layout.tracks().get(3), 4, 23, true, false);
		assertEquals(25, layout.width());
	}

	@Test
	void sideLayoutsWithThreeOrFourTracksHaveThroughTracksInside() {
		for (int tracks = 3; tracks <= 4; tracks++) {
			final TrackLayout layout = StationGeometry.layout(spec(tracks, PlatformLayout.SIDE, 4));
			assertEquals(2, layout.platforms().size());
			for (final TrackLayout.Track track : layout.tracks()) {
				final boolean outer = track.number() == 1 || track.number() == tracks;
				assertEquals(!outer, track.through(), tracks + " tracks, track " + track.number());
			}
			assertTrue(layout.tracks().get(0).platformLeft());
			assertFalse(layout.tracks().get(0).platformRight());
			assertTrue(layout.tracks().get(tracks - 1).platformRight());
			assertFalse(layout.tracks().get(tracks - 1).platformLeft());
			assertEquals(StationGeometry.TRACK_SPACING, layout.tracks().get(2).u() - layout.tracks().get(1).u());
		}
	}

	@Test
	void islandLayoutsHaveNoThroughTracks() {
		for (int tracks = 2; tracks <= 4; tracks++) {
			for (final TrackLayout.Track track : StationGeometry.layout(spec(tracks, PlatformLayout.ISLAND, 5)).tracks()) {
				assertFalse(track.through(), tracks + " tracks, track " + track.number());
			}
		}
	}

	@Test
	void rampLengthFollowsStepFreeAccess() {
		for (final StationSpec spec : matrix()) {
			final TrackLayout stepFree = StationGeometry.layout(spec.toBuilder().stepFree(true).build());
			final TrackLayout stepped = StationGeometry.layout(spec.toBuilder().stepFree(false).build());
			assertEquals(StationGeometry.RAMP_LENGTH, stepFree.rampLength(), label(spec));
			assertEquals(0, stepped.rampLength(), label(spec));
			assertEquals(-StationGeometry.RAMP_LENGTH, stepFree.vMin(), label(spec));
			assertEquals(StationGeometry.builtLength(spec.platformLength()) - 1 + StationGeometry.RAMP_LENGTH, stepFree.vMax(), label(spec));
			assertEquals(0, stepped.vMin(), label(spec));
			assertEquals(stepFree.width(), stepped.width(), label(spec));
		}
	}

	@Test
	void layoutIsDeterministicAndIgnoresStyling() {
		for (final StationSpec spec : matrix()) {
			final TrackLayout first = StationGeometry.layout(spec);
			assertEquals(first, StationGeometry.layout(spec), label(spec));
			final StationSpec styled = spec.toBuilder().style(StationStyle.METRO).roof(RoofType.HALL).tactile(false).furniture(false)
					.name("Somewhere").build();
			assertEquals(first, StationGeometry.layout(styled), label(spec));
		}
	}

	private static void assertBand(TrackLayout.PlatformBand band, int number, int uMin, int uMax, boolean trackLeft, boolean trackRight) {
		assertEquals(new TrackLayout.PlatformBand(number, uMin, uMax, trackLeft, trackRight, band.vFrom(), band.vTo()), band);
	}

	private static void assertTrack(TrackLayout.Track track, int number, int u, boolean platformLeft, boolean platformRight) {
		assertEquals(new TrackLayout.Track(number, u, platformLeft, platformRight), track);
	}

	private static boolean hasBand(TrackLayout layout, Predicate<TrackLayout.PlatformBand> test) {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (test.test(band)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasTrack(TrackLayout layout, Predicate<TrackLayout.Track> test) {
		for (final TrackLayout.Track track : layout.tracks()) {
			if (test.test(track)) {
				return true;
			}
		}
		return false;
	}
}
