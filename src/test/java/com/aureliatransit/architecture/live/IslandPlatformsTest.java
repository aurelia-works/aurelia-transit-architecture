package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.NearestPlatformProvider.Candidate;
import com.aureliatransit.architecture.live.display.BoardSummary;
import com.aureliatransit.architecture.transit.PlatformReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A platform board shows its own platform, or both faces of its island, never the whole station (Jah 10-26). */
class IslandPlatformsTest {

	@Test
	void aBoardOverAnIslandServesBothFacesAndNoMore() {
		// the middle of an 11-wide island: both tracks 6 blocks off, the next island's tracks 12 and more
		assertEquals(List.of(51L, 52L), NearestPlatformProvider.island(List.of(new Candidate(53, 12), new Candidate(52, 6.4), new Candidate(51, 6),
				new Candidate(54, 12.5))));
	}

	@Test
	void aSidePlatformBoardKeepsItsOwnPlatform() {
		// a wall screen 2 off its track; the other platform is across two tracks
		assertEquals(List.of(7L), NearestPlatformProvider.island(List.of(new Candidate(8, 7), new Candidate(7, 2))));
		assertEquals(List.of(), NearestPlatformProvider.island(List.of()), "nothing in reach: the caller shows the station");
	}

	@Test
	void atMostTwoPlatformsEvenWhenMoreAreClose() {
		assertEquals(2, NearestPlatformProvider.island(List.of(new Candidate(1, 3), new Candidate(2, 3), new Candidate(3, 3))).size());
	}

	@Test
	void anIslandBoardIsNamedForBothFaces() {
		final List<PlatformReference> platforms = List.of(new PlatformReference(51, "5a", 1), new PlatformReference(52, "5b", 1));
		assertEquals("5a / 5b", BoardSummary.platformNames(platforms, List.of(51L, 52L)));
		assertEquals("5a / 5b", BoardSummary.platformNames(platforms, List.of(52L, 51L)), "in platform order, not by distance");
		assertEquals("5b", BoardSummary.platformNames(platforms, List.of(52L, 99L)), "another station's platform is left out");
		assertEquals("", BoardSummary.platformNames(platforms, List.of()));
	}
}
