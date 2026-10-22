package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationRecipe;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The studio's Building lock keeps the roof and size a station was planned with ("resolved" in the plan), not the word
 * "Auto", which follows the city. Planning again with the resolved values must give the same roof and size for every look,
 * with or without a seed, while the look changes.
 */
class StudioLocksTest {

	private static final BlockModels.AssetSource OWN_ASSETS = (namespace, path) -> {
		final Path file = Path.of("src/main/resources/assets", namespace, path);
		return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
	};

	@Test
	void theResolvedRoofAndSizePlanTheSameRoofAndSizeAgain() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			for (final String roof : new String[]{"AUTO", "CANOPIES"}) {
				for (final String seed : new String[]{"", "k7f2ra", "zz9"}) {
					final Map<String, String> q = new HashMap<>(Map.of("look", look.name(), "roof", roof, "seed", seed, "tracks", "4",
							"length", "96", "building", "1", "size", "AUTO"));
					final Map<String, Object> first = Json.object(Json.parse(api.plan(q)));
					final Map<String, Object> resolved = Json.object(first.get("resolved"));
					final String where = look + " " + roof + " seed '" + seed + "'";
					// another city under the locked roof and size: the roof form and size stay what they were
					final StationRecipe.Look other = look == StationRecipe.Look.HAMBURG ? StationRecipe.Look.UTRECHT : StationRecipe.Look.HAMBURG;
					q.putAll(Map.of("look", other.name(), "roof", (String) resolved.get("roof"), "size", (String) resolved.get("size"), "seed", "other1"));
					final Map<String, Object> again = Json.object(Json.parse(api.plan(q)));
					assertEquals(Json.object(first.get("built")).get("roof"), Json.object(again.get("built")).get("roof"), where);
					assertEquals(Json.object(first.get("built")).get("scale"), Json.object(again.get("built")).get("scale"), where);
				}
			}
		}
	}

	@Test
	void aSeedIsNotTypedWordsSoTheControlsStillCount() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		// nothing pinned and no words: every control counts, seed or not
		final Map<String, String> q = new HashMap<>(Map.of("look", "HAMBURG", "mode", "terminus", "tracks", "6", "length", "120", "building", "1",
				"seed", "k7f2ra"));
		final Map<String, Object> built = Json.object(Json.object(Json.parse(api.plan(q))).get("built"));
		assertEquals(6.0, built.get("tracks"));
		assertEquals(120.0, built.get("length"));
		assertEquals("Terminus", built.get("mode"));
		final String description = StudioApi.description(q);
		assertEquals(true, description.endsWith(", seed k7f2ra"), description);
		// with words typed, the pinned controls and the seed still both reach the planner
		q.putAll(Map.of("text", "gravel track bed", "pinned", "tracks"));
		final String typed = StudioApi.description(q);
		assertEquals(true, typed.contains("6 track") && typed.contains("gravel") && typed.endsWith(", seed k7f2ra"), typed);
	}

	@Test
	void flatCanopiesAreWordsTheParserReads() {
		final var parse = StationDescriptionParser.parse("berlin station, flat canopies");
		assertEquals(com.aureliatransit.architecture.station.CanopyShape.FLAT, parse.spec().canopy());
		assertEquals(java.util.List.of(), parse.ignored());
	}

	@Test
	void wordsThatNameARealStationBeatWhateverThePageHadPinned() {
		// two fresh pages of Jah's 10-22 test: the dice had pinned Milan + Paris, gable canopies and a compact building, or a hub
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final String jah = "10 track utrecht station, 200 long, named hi, stairs both ways andesite track bed, seed tmptnb";
		final Map<String, String> mixed = new HashMap<>(Map.ofEntries(Map.entry("text", jah), Map.entry("mode", "through"), Map.entry("layout", "SIDE"),
				Map.entry("tracks", "4"), Map.entry("length", "48"), Map.entry("look", "MILAN"), Map.entry("mix", "PARIS"), Map.entry("roof", "GABLE"),
				Map.entry("building", "1"), Map.entry("size", "COMPACT"), Map.entry("bed", "andesite"), Map.entry("class", "auto"), Map.entry("era", "auto"),
				Map.entry("pinned", "look,mix,era,mode,tracks,length,layout,building,size,roof,esc,walls,trav,bed,class"), Map.entry("seed", "u34zpj")));
		final Map<String, String> hub = new HashMap<>(mixed);
		hub.putAll(Map.of("mode", "hub", "look", "UTRECHT", "mix", "", "roof", "AUTO"));
		for (final Map<String, String> q : java.util.List.of(mixed, hub)) {
			final Map<String, Object> plan = Json.object(Json.parse(api.plan(q)));
			assertEquals("Utrecht Centraal, 10 tracks, 200 long", plan.get("title"), q.toString());
		}
	}
}
