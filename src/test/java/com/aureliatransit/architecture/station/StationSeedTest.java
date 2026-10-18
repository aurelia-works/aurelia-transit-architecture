package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	// ---- hardening: range, fingerprint, reproducibility, variety ---------------------------------------------------------

	private static String fingerprint(String description) {
		return PlanFingerprint.of(StationPlanner.plan(StationDescriptionParser.parse(description).spec()));
	}

	/** Stations that leave the seed's choices open: each has furniture, platform colours, a floor, a roof and a canopy or a shed. */
	private static final List<String> CORPUS_SPECS = List.of(
			"3 tracks, 80 long, hamburg, station building",
			"2 tracks island platforms, 64 long, utrecht, station building, canopy",
			"4 tracks island, 96 long, amsterdam, terminus, hall roof",
			"2 tracks, 48 long, dutch");

	/** 200 distinct seeds, 1 to 6 characters, always the same ones. */
	private static List<String> corpusSeeds() {
		final SplittableRandom dice = new SplittableRandom(20261018L);
		final Set<String> seeds = new java.util.LinkedHashSet<>();
		while (seeds.size() < 200) {
			final String seed = Long.toString(1 + dice.nextLong(36L * 36 * 36 * 36 * 36 * 36), 36);
			seeds.add(seed);
		}
		return new ArrayList<>(seeds);
	}

	@Test
	void seedsAreOneToTwelveLettersAndDigits() {
		assertTrue(StationSeed.valid("k7f2ra"));
		assertTrue(StationSeed.valid("K7F2RA"));
		assertTrue(StationSeed.valid("0"));
		assertTrue(StationSeed.valid("zzzzzzzzzzzz"));
		assertFalse(StationSeed.valid(""));
		assertFalse(StationSeed.valid(null));
		assertFalse(StationSeed.valid("zzzzzzzzzzzzz"), "thirteen characters");
		assertFalse(StationSeed.valid("k7 f2"));
		assertFalse(StationSeed.valid("k7-f2"));
		assertFalse(StationSeed.valid("k\u00fc7"));
		assertFalse(StationSeed.valid("\u0663\u0664"), "other scripts' digits are not digits here");
		assertThrows(IllegalArgumentException.class, () -> StationSeed.parse("not a seed"));
		// the longest seed fits a long: 36^12 - 1
		final long max = StationSeed.parse("zzzzzzzzzzzz");
		assertEquals((long) Math.pow(36, 12) - 1, max, 1L << 12, "within double rounding of 36^12 - 1");
		assertTrue(max > 0);
		assertEquals("zzzzzzzzzzzz", StationSeed.format(max));
		assertEquals("", StationSeed.format(0));
		assertEquals("k7f2ra", StationSeed.format(StationSeed.parse("K7F2RA")));
		assertEquals(0, StationSeed.parse("000"));
	}

	@Test
	void invalidSeedsAreIgnoredWithANoteAndNeverThrow() {
		final String plain = "2 tracks, 64 long, utrecht";
		final StationSpec expected = StationDescriptionParser.parse(plain).spec();
		for (final String bad : new String[]{"seed abcdefghijklm", "seed zzzzzzzzzzzzzzzzzzzzzzzz", "seed k7-f2", "seed k7_f2", "seed -5", "seed 3.5.7x", "seed \u00fcber",
				"seed \u0663\u0664", "seed", "seed,", "seed @@@", "seed k7f2ra/9", "SEED ??", "seed \u00e9\u00e9\u00e9"}) {
			final ParseResult parse = assertDoesNotThrow(() -> StationDescriptionParser.parse(plain + ", " + bad), bad);
			assertEquals(0, parse.spec().seed(), bad);
			assertEquals(expected, parse.spec(), bad + " must not change anything else");
			assertTrue(parse.warnings().stream().anyMatch(w -> w.toLowerCase().contains("seed")), bad + " warnings " + parse.warnings());
			assertFalse(parse.ignored().contains("seed"), bad + " the word seed is reported once, as a warning, not as an unknown word: " + parse.ignored());
			assertFalse(parse.understood().stream().anyMatch(u -> u.startsWith("seed")), bad);
			assertEquals(fingerprint(plain), fingerprint(plain + ", " + bad), bad);
		}
		assertTrue(StationDescriptionParser.parse(plain).warnings().isEmpty(), "no seed, no note");
	}

	@Test
	void theLongestSeedPlansAndTheNextOneDoesNot() {
		final String longest = "zzzzzzzzzzzz";
		final ParseResult ok = StationDescriptionParser.parse("2 tracks, 64 long, seed " + longest);
		assertEquals(StationSeed.parse(longest), ok.spec().seed());
		assertTrue(ok.warnings().isEmpty(), ok.warnings().toString());
		assertEquals(fingerprint("2 tracks, 64 long, seed " + longest), fingerprint("2 tracks, 64 long, seed " + longest.toUpperCase()));
		final ParseResult tooLong = StationDescriptionParser.parse("2 tracks, 64 long, seed " + longest + "z");
		assertEquals(0, tooLong.spec().seed());
		assertFalse(tooLong.warnings().isEmpty());
	}

	@Test
	void seedZeroIsThePlainPlanAndSaysSo() {
		final String plain = "3 tracks, 80 long, hamburg, station building";
		for (final String zero : new String[]{"seed 0", "seed 000", "SEED 0"}) {
			final ParseResult parse = StationDescriptionParser.parse(plain + ", " + zero);
			assertEquals(0, parse.spec().seed(), zero);
			assertTrue(parse.warnings().isEmpty(), zero + " " + parse.warnings());
			assertTrue(parse.understood().stream().anyMatch(u -> u.startsWith("seed") && u.contains("plain")), zero + " " + parse.understood());
			assertEquals(fingerprint(plain), fingerprint(plain + ", " + zero), zero);
		}
	}

	@Test
	void aSeedGivenTwiceUsesTheFirst() {
		final ParseResult parse = StationDescriptionParser.parse("2 tracks, seed a1b2, seed c3d4");
		assertEquals(StationSeed.parse("a1b2"), parse.spec().seed());
		assertTrue(parse.warnings().stream().anyMatch(w -> w.contains("twice")), parse.warnings().toString());
		assertTrue(parse.ignored().isEmpty(), parse.ignored().toString());
		// a bad one first does not stop a good one after it
		final ParseResult recovered = StationDescriptionParser.parse("2 tracks, seed !!!, seed c3d4");
		assertEquals(StationSeed.parse("c3d4"), recovered.spec().seed());
		assertEquals(1, recovered.warnings().size(), recovered.warnings().toString());
	}

	@Test
	void seedWordsAreReadWhateverTheirCaseOrPunctuation() {
		final long seed = StationSeed.parse("k7f2ra");
		for (final String words : new String[]{"2 tracks, seed k7f2ra", "2 tracks, SEED K7F2RA.", "2 tracks, seed: k7f2ra", "2 tracks (seed k7f2ra)",
				"seed k7f2ra, 2 tracks", "2 tracks; seed   k7f2ra;"}) {
			final ParseResult parse = StationDescriptionParser.parse(words);
			assertEquals(seed, parse.spec().seed(), words);
			assertTrue(parse.warnings().isEmpty(), words + " " + parse.warnings());
			assertTrue(parse.ignored().isEmpty(), words + " " + parse.ignored());
		}
	}

	@Test
	void theFingerprintNamesTheBlocksAndNothingElse() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse("2 tracks, 64 long, utrecht, station building, named Leiden").spec());
		final String print = PlanFingerprint.of(plan);
		assertTrue(print.matches("[0-9a-f]{" + PlanFingerprint.HEX_CHARS + "}"), print);
		// order does not matter
		final List<PlannedBlock> shuffled = new ArrayList<>(plan.blocks());
		Collections.shuffle(shuffled, new java.util.Random(7));
		assertEquals(print, PlanFingerprint.of(shuffled));
		// a role does not matter, a position, id, facing, state or sign text does
		final PlannedBlock first = plan.blocks().get(0);
		final List<PlannedBlock> changed = new ArrayList<>(plan.blocks());
		changed.set(0, new PlannedBlock(first.u(), first.y(), first.v(), Role.values()[(first.role().ordinal() + 1) % Role.values().length], first.blockId(),
				first.front(), first.text(), first.state()));
		assertEquals(print, PlanFingerprint.of(changed), "roles are how a block was chosen, not what the world gets");
		final List<PlannedBlock> moved = new ArrayList<>(plan.blocks());
		moved.set(0, new PlannedBlock(first.u() + 1, first.y(), first.v(), first.role(), first.blockId(), first.front(), first.text(), first.state()));
		assertNotEquals(print, PlanFingerprint.of(moved));
		moved.set(0, new PlannedBlock(first.u(), first.y(), first.v(), first.role(), "minecraft:stone", first.front(), first.text(), first.state()));
		assertNotEquals(print, PlanFingerprint.of(moved));
		moved.set(0, new PlannedBlock(first.u(), first.y(), first.v(), first.role(), first.blockId(), first.front().opposite(), first.text(), first.state()));
		assertNotEquals(print, PlanFingerprint.of(moved));
		moved.set(0, new PlannedBlock(first.u(), first.y(), first.v(), first.role(), first.blockId(), first.front(), first.text(), first.state() + ",x=1"));
		assertNotEquals(print, PlanFingerprint.of(moved));
		moved.set(0, new PlannedBlock(first.u(), first.y(), first.v(), first.role(), first.blockId(), first.front(), first.text() + "!", first.state()));
		assertNotEquals(print, PlanFingerprint.of(moved));
		final List<PlannedBlock> missing = new ArrayList<>(plan.blocks());
		missing.remove(missing.size() - 1);
		assertNotEquals(print, PlanFingerprint.of(missing));
		// a different name is a different sign
		assertNotEquals(print, fingerprint("2 tracks, 64 long, utrecht, station building, named Delft"));
	}

	@Test
	void theSameSpecAndSeedGiveTheSameFingerprintFromFreshPlannersOnAnyThread() throws Exception {
		final Map<String, String> first = new LinkedHashMap<>();
		for (final String spec : CORPUS_SPECS) {
			for (final String seed : new String[]{"", "a1", "k7f2ra", "zzzzzzzzzzzz", "0"}) {
				final String words = seed.isEmpty() ? spec : spec + ", seed " + seed;
				first.put(words, fingerprint(words));
			}
		}
		// planning other stations in between must not leak into later plans
		fingerprint("10 tracks island, 300 long, hall roof, station building, grand, seed q9q9q9");
		final ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			final Map<String, Future<String>> again = new LinkedHashMap<>();
			for (final String words : first.keySet()) {
				final Callable<String> task = () -> fingerprint(words);
				again.put(words, pool.submit(task));
			}
			for (final Map.Entry<String, Future<String>> e : again.entrySet()) {
				assertEquals(first.get(e.getKey()), e.getValue().get(), e.getKey());
				assertEquals(first.get(e.getKey()), fingerprint(e.getKey()), e.getKey());
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void seedsVaryThePlanWithinTheLimitsOfWhatTheyChoose() {
		final List<String> seeds = corpusSeeds();
		for (final String words : CORPUS_SPECS) {
			final Map<String, List<String>> byFingerprint = new LinkedHashMap<>();
			final Set<String> effective = new HashSet<>();
			for (final String seed : seeds) {
				final StationSpec spec = StationDescriptionParser.parse(words + ", seed " + seed).spec();
				assertEquals(StationSeed.parse(seed), spec.seed(), seed);
				final String print = PlanFingerprint.of(StationPlanner.plan(spec));
				byFingerprint.computeIfAbsent(print, k -> new ArrayList<>()).add(seed);
				effective.add(StationSeed.effective(spec));
			}
			// the same effective seed plans the same station, so the fingerprints cannot outnumber the effective seeds ...
			assertTrue(byFingerprint.size() <= effective.size(), words + ": " + byFingerprint.size() + " plans from " + effective.size() + " effective seeds");
			// ... and every duplicate is provably the same station: its seeds have an identical effective seed
			for (final Map.Entry<String, List<String>> group : byFingerprint.entrySet()) {
				final Set<String> same = new HashSet<>();
				for (final String seed : group.getValue()) {
					same.add(StationSeed.effective(StationDescriptionParser.parse(words + ", seed " + seed).spec()));
				}
				assertEquals(1, same.size(), words + ": seeds " + group.getValue() + " plan the same station (" + group.getKey()
						+ ") without having the same effective seed: " + same);
			}
			// a seed really changes the station: every effective seed a roll reaches is a station nobody else got
			assertEquals(effective.size(), byFingerprint.size(), words + ": two effective seeds planned the same station");
			assertTrue(byFingerprint.size() >= 40, words + ": only " + byFingerprint.size() + " distinct stations from 200 seeds");
			assertNotEquals(fingerprint(words), byFingerprint.keySet().iterator().next(), "a seed is not the plain plan");
		}
	}

	@Test
	void theColourAndBenchChoicesCoverTheirWholeRange() {
		final int colours = StationPlanner.PLATFORM_COLOURS.size();
		final Set<Integer> offsets = new HashSet<>();
		final Set<Integer> benches = new HashSet<>();
		for (long seed = 1; seed <= 2000; seed++) {
			offsets.add(StationSeed.colourOffset(seed, colours));
			benches.add(StationSeed.benchStart(seed, colours));
			assertTrue(StationSeed.benchStart(seed, colours) >= 4 && StationSeed.benchStart(seed, colours) < 4 + StationSeed.BENCH_SPREAD);
		}
		assertEquals(colours, offsets.size());
		assertEquals(StationSeed.BENCH_SPREAD, benches.size());
		assertEquals(StationSeed.PLAIN_BENCH_START, StationSeed.benchStart(0, colours));
		// the biggest seeds still land in range
		final long max = StationSeed.parse("zzzzzzzzzzzz");
		assertTrue(StationSeed.colourOffset(max, colours) >= 0 && StationSeed.colourOffset(max, colours) < colours);
		assertTrue(StationSeed.benchStart(max, colours) >= 4 && StationSeed.benchStart(max, colours) < 4 + StationSeed.BENCH_SPREAD);
	}
}
