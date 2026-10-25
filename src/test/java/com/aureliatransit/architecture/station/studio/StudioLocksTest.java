package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.BuildingStyle;
import com.aureliatransit.architecture.station.RealStation;
import com.aureliatransit.architecture.station.RoofType;
import com.aureliatransit.architecture.station.ShedShape;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.StationSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
					// a card that builds its real station has no roof and size of the player's own to carry (Jah 10-25)
					if (StationDescriptionParser.parse(StudioApi.description(q)).spec().real() != RealStation.NONE) {
						continue;
					}
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

	/** A fresh page as the dice left it: every rolled control pinned, and said to be rolled. */
	private static Map<String, String> rolledPage(String words, String look, String mix, String roof, String building, String stationClass) {
		final String all = "look,mix,era,mode,tracks,length,layout,building,size,roof,esc,walls,trav,bed,class";
		return new HashMap<>(Map.ofEntries(Map.entry("text", words), Map.entry("mode", "through"), Map.entry("layout", "ISLAND"),
				Map.entry("tracks", "4"), Map.entry("length", "96"), Map.entry("look", look), Map.entry("mix", mix), Map.entry("roof", roof),
				Map.entry("building", building), Map.entry("size", "AUTO"), Map.entry("bed", "andesite"), Map.entry("class", stationClass),
				Map.entry("era", "auto"), Map.entry("pinned", all), Map.entry("rolled", all), Map.entry("seed", "u34zpj")));
	}

	private static StationSpec spec(Map<String, String> q) {
		return StationDescriptionParser.parse(StudioApi.description(q)).spec();
	}

	@Test
	void aNamedCityKeepsItsBuildingAndRoofWhateverThePageRolled() {
		// two fresh pages: one rolled Teddington mixed with Hamburg, a flat shed and no building; one Beijing with pointed arches
		final Map<String, String> a = rolledPage("amsterdam station", "TEDDINGTON", "HAMBURG", "FLAT_SHED", "0", "auto");
		final Map<String, String> b = rolledPage("amsterdam station", "BEIJING", "", "POINTED", "1", "auto");
		for (final Map<String, String> q : List.of(a, b)) {
			assertEquals(BuildingStyle.AMSTERDAM, spec(q).building(), StudioApi.description(q));
			assertTrue(StudioApi.wordsWon(q).containsAll(List.of("building", "look", "roof")), StudioApi.wordsWon(q).toString());
		}
		// the same words and seed give the same station whatever the dice had rolled
		assertEquals(spec(a).building(), spec(b).building());
		assertEquals(spec(a).roof(), spec(b).roof());
		assertEquals(spec(a).shed(), spec(b).shed());
		assertEquals(spec(a).canopy(), spec(b).canopy());
	}

	@Test
	void aRealStationKeepsItsOwnTracksAndLengthWhateverThePageRolled() {
		// a fresh page rolled 4 tracks, 96 long: "hamburg hauptbahnhof" was a 4-track, 96-long station (10-24)
		final StationSpec own = StationDescriptionParser.parse("hamburg hauptbahnhof").spec();
		final Map<String, String> q = rolledPage("hamburg hauptbahnhof", "TEDDINGTON", "", "FLAT_SHED", "1", "auto");
		assertEquals(own.trackCount(), spec(q).trackCount(), StudioApi.description(q));
		assertEquals(own.platformLength(), spec(q).platformLength(), StudioApi.description(q));
		// a count and a length the player set by hand still go in with the words
		q.put("rolled", "look,mix,era,mode,layout,building,size,roof,esc,walls,trav,bed,class");
		assertFalse(StudioApi.wordsWon(q).contains("tracks"), StudioApi.wordsWon(q).toString());
		assertFalse(StudioApi.wordsWon(q).contains("length"), StudioApi.wordsWon(q).toString());
	}

	@Test
	void aRolledClassGivesWayToANamedCityButOneSetByHandHolds() {
		// a fresh page that rolled a grand class pins nothing else: "teddington station" came out under a gabled train shed (10-24)
		final String words = "two track teddington station, side platforms, 200 long, small station building with footbridge, named Teddington";
		final Map<String, String> q = rolledPage(words, "FRANKFURT", "", "ARCHES", "1", "grand");
		q.put("pinned", "text");
		q.put("rolled", "class");
		final StationSpec own = StationDescriptionParser.parse(words).spec();
		assertEquals(RoofType.CANOPY, spec(q).roof(), StudioApi.description(q));
		assertEquals(own.canopy(), spec(q).canopy());
		assertEquals(own.building(), spec(q).building());
		// the same class chosen by hand still asks for a grand station's shed
		q.put("rolled", "");
		assertEquals(RoofType.HALL, spec(q).roof(), StudioApi.description(q));
		// with no city in the words a rolled class still decides
		q.put("rolled", "class");
		q.put("text", "two track station, side platforms, 200 long");
		assertTrue(StudioApi.description(q).contains("grand class"), StudioApi.description(q));
	}

	@Test
	void aRoofOrBuildingSetByHandStillHoldsAgainstACity() {
		final Map<String, String> q = rolledPage("amsterdam station", "TEDDINGTON", "", "FLAT_SHED", "1", "auto");
		// the player picked the flat shed themselves: only the rest was rolled
		q.put("rolled", "look,mix,era,mode,tracks,length,layout,building,size,esc,walls,trav,bed,class");
		assertEquals(ShedShape.FLAT, spec(q).shed(), StudioApi.description(q));
		assertEquals(RoofType.HALL, spec(q).roof());
		assertEquals(BuildingStyle.AMSTERDAM, spec(q).building());
		// a page that says nothing of rolling (the wrench, an older page) keeps every pin, as before
		q.remove("rolled");
		assertEquals(ShedShape.FLAT, spec(q).shed());
	}

	@Test
	void aRolledClassDoesNotTurnUtrechtStationIntoUtrechtStyle() {
		// Jah's 10-22 words on pages that rolled a class: it gave the station a building of its own and kept only the look
		for (final String stationClass : List.of("town", "grand", "city", "regional", "halt")) {
			final Map<String, String> q = rolledPage("10 track utrecht station, 200 long", "FRANKFURT", "", "ARCHES", "1", stationClass);
			assertEquals(RealStation.UTRECHT_CENTRAAL, spec(q).real(), stationClass + ": " + StudioApi.description(q));
			q.put("era", "modern");
			assertEquals(RealStation.UTRECHT_CENTRAAL, spec(q).real(), stationClass + " modern: " + StudioApi.description(q));
		}
		// "utrecht style" still asks for the look on a station of the player's own
		assertEquals(RealStation.NONE, StationDescriptionParser.parse("10 track utrecht style station, 200 long, town class").spec().real());
	}

	@Test
	void aClassAsksForAShedButANamedCitySaysWhichShed() {
		assertEquals(ShedShape.POINTED, StationDescriptionParser.parse("st pancras terminus, grand class").spec().shed());
		assertEquals(ShedShape.MULTI_ARCH, StationDescriptionParser.parse("frankfurt terminus, grand class, modern era").spec().shed());
		assertEquals(ShedShape.ARCH, StationDescriptionParser.parse("amsterdam station, grand class, historic era").spec().shed());
		// with no city named the class and era still choose
		assertEquals(ShedShape.ARCH, StationDescriptionParser.parse("four track station, grand class, modern era").spec().shed());
		assertEquals(ShedShape.MULTI_ARCH, StationDescriptionParser.parse("four track station, grand class, historic era").spec().shed());
	}
}
