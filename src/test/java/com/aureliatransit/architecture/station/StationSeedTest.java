package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generator seed ("seed k7f2ra"): the same seed plans the same station, a seed varies only what the words left open. */
class StationSeedTest {

	private static List<PlannedBlock> blocks(String description) {
		return StationPlanner.plan(StationDescriptionParser.parse(description).spec()).blocks();
	}

	@Test
	void theSameSeedPlansTheSameStation() {
		final String words = "4 tracks island, 96 long, hamburg, station building, seed k7f2ra";
		assertEquals(blocks(words), blocks(words));
		assertEquals(Long.parseLong("k7f2ra", 36), StationDescriptionParser.parse(words).spec().seed());
	}

	@Test
	void noSeedPlansAsBefore() {
		final ParseResult parse = StationDescriptionParser.parse("2 tracks, 64 long, utrecht");
		assertEquals(0, parse.spec().seed());
		assertEquals(blocks("2 tracks, 64 long, utrecht"), blocks("2 tracks, 64 long, utrecht, seed 0"));
	}

	@Test
	void seedsVaryTheStationButNotWhatWasAsked() {
		final Set<List<PlannedBlock>> seen = new HashSet<>();
		for (final String seed : new String[]{"a1", "b2", "c3", "d4", "e5", "f6"}) {
			final StationSpec spec = StationDescriptionParser.parse("3 tracks, 80 long, gable canopies, granite floors, seed " + seed).spec();
			assertEquals(CanopyShape.GABLE, spec.canopy(), seed);
			assertEquals(SurfaceMaterial.GRANITE, spec.surface(), seed);
			assertEquals(3, spec.trackCount(), seed);
			seen.add(StationPlanner.plan(spec).blocks());
		}
		assertTrue(seen.size() > 1, "six seeds all planned the same station");
	}

	@Test
	void theSeedIsReadAndNotMistakenForAName() {
		final ParseResult parse = StationDescriptionParser.parse("2 tracks, seed zz9, named Seed Lane");
		assertEquals("Seed Lane", parse.spec().name());
		assertEquals(Long.parseLong("zz9", 36), parse.spec().seed());
		assertTrue(parse.ignored().isEmpty(), "ignored " + parse.ignored());
		assertTrue(parse.understood().stream().anyMatch(u -> u.startsWith("seed zz9")), "understood " + parse.understood());
		assertNotEquals(blocks("2 tracks, 64 long, seed zz9"), blocks("2 tracks, 64 long, seed zz8"));
	}
}
