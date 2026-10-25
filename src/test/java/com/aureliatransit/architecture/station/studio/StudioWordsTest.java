package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.BuildingStyle;
import com.aureliatransit.architecture.station.RealStation;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Typing plain words into the Studio: the station, the headline and the city button all follow them, not the dice behind. */
class StudioWordsTest {

	private static StationSpec spec(String text) {
		return StationDescriptionParser.parse(text).spec();
	}

	@Test
	void aHyphenatedNumberWordAndACityAreUnderstood() {
		final StationSpec spec = spec("six-track Amsterdam Damn Station");
		assertEquals(6, spec.trackCount());
		assertEquals(BuildingStyle.AMSTERDAM, spec.building());
	}

	@Test
	void aRealStationLightsItsOwnCity() {
		// Utrecht Centraal lit the Tokyo button (Jah 10-22)
		assertEquals(StationRecipe.Look.UTRECHT, StudioApi.lookOf(RealStation.UTRECHT_CENTRAAL));
		assertEquals(StationRecipe.Look.HAMBURG, StudioApi.lookOf(RealStation.HAMBURG_HBF));
	}

	@Test
	void aBareBlockCountIsALength() {
		assertEquals(200, spec("a station, 200 blocks").platformLength());
		assertEquals(300, spec("2 tracks 300 block station").platformLength());
	}

	@Test
	void theHeadlineSaysWhatTheWordsCameTo() {
		final String title = StudioApi.wordsTitle(spec("six-track Amsterdam station, 120 long"));
		assertEquals("Amsterdam station, 6 tracks, 120 long", title);
	}

	@Test
	void typedWordsSetTheHeadlineAndTheCityButtonNotTheDice() {
		final String json = new StudioApi(new BlockModels((namespace, path) -> {
			final Path file = Path.of("src/main/resources/assets", namespace, path);
			return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
		})).plan(Map.of("text", "six-track Amsterdam station", "look", "TOKYO", "tracks", "4", "length", "200",
				"pinned", "text", "mode", "through", "layout", "AUTO", "roof", "AUTO", "building", "1", "size", "AUTO"));
		assertTrue(json.contains("\"title\":\"Amsterdam station, 6 tracks"), json.substring(0, Math.min(400, json.length())));
		assertTrue(json.contains("\"wordsLook\":\"AMSTERDAM\""));
	}

	@Test
	void anOperatorWordPutsItsMarkOnTheNameSigns() {
		for (final String[] row : new String[][]{{"ns", "ns_station_sign"}, {"deutsche bahn", "db_station_sign"}, {"gvb operator", "gvb_station_sign"},
				{"tfl", "underground_station_sign"}, {"london underground signs", "underground_station_sign"}, {"national rail", "national_rail_station_sign"},
				{"sbahn signs", "sbahn_station_sign"}, {"sbb", "sbb_station_sign"}, {"jr east", "jr_station_sign"}, {"nationalrail operator", "national_rail_station_sign"}}) {
			final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse("2 tracks side, 64 long, named Halt, " + row[0]).spec());
			final long signs = plan.blocks().stream().filter(b -> b.role() == Role.NAME_SIGN).count();
			assertTrue(signs > 0, row[0]);
			assertTrue(plan.blocks().stream().filter(b -> b.role() == Role.NAME_SIGN).allMatch(b -> b.blockId().endsWith(row[1])), row[0]);
		}
		assertTrue(StationPlanner.plan(StationDescriptionParser.parse("2 tracks side, 64 long, named Halt").spec()).blocks().stream()
				.filter(b -> b.role() == Role.NAME_SIGN).noneMatch(b -> b.blockId().contains("ns_station_sign")));
	}

	@Test
	void aLookOrStationTypeWordNamesAnOperatorOnlyWithItsNoun() {
		// "sbahn" is the German look: alone it leaves the signs plain. "london underground" is a London station underground, so the
		// city's operator for one, the Underground (Jah, 10-20: the city picks the operator), not the word as an operator
		assertEquals(com.aureliatransit.architecture.station.Operator.NONE, StationDescriptionParser.parse("2 tracks side, 64 long, sbahn").spec().operator());
		final StationSpec london = StationDescriptionParser.parse("2 tracks side, 64 long, london underground").spec();
		assertEquals(com.aureliatransit.architecture.station.Operator.UNDERGROUND, london.operator());
		final StationSpec apart = StationDescriptionParser.parse("2 tracks side, 64 long, london, underground").spec();
		assertEquals(apart.style(), london.style());
		assertEquals(apart.building(), london.building());
		assertEquals(com.aureliatransit.architecture.station.Operator.SBAHN, StationDescriptionParser.parse("2 tracks side, 64 long, sbahn signs").spec().operator());
	}

	@Test
	void anOperatorNounLeavesTheLookAlone() {
		final StationSpec tokyoNs = StationDescriptionParser.parse("2 tracks side, 64 long, tokyo, ns operator").spec();
		final StationSpec tokyo = StationDescriptionParser.parse("2 tracks side, 64 long, tokyo").spec();
		assertEquals(tokyo.style(), tokyoNs.style());
		assertEquals(com.aureliatransit.architecture.station.Operator.NS, tokyoNs.operator());
		assertEquals(com.aureliatransit.architecture.station.StationStyle.DUTCH, StationDescriptionParser.parse("2 tracks, ns").spec().style());
	}

	@Test
	void wordsNamingNoLookLeaveThePagesMixAlone() {
		// a fresh page rolled Teddington + Zurich; "2 track station" planned that mix, but wordsLook said TEDDINGTON, so the page
		// cleared the mix without planning again and the next change (a block swap, 10-25) built another station
		final StudioApi api = new StudioApi(new BlockModels((namespace, path) -> {
			final Path file = Path.of("src/main/resources/assets", namespace, path);
			return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
		}));
		final Map<String, String> q = new java.util.HashMap<>(Map.ofEntries(Map.entry("text", "2 track station, station building, 64 long"),
				Map.entry("mode", "through"), Map.entry("layout", "AUTO"), Map.entry("tracks", "4"), Map.entry("length", "80"),
				Map.entry("look", "TEDDINGTON"), Map.entry("mix", "ZURICH"), Map.entry("roof", "AUTO"), Map.entry("building", "1"),
				Map.entry("size", "COMPACT"), Map.entry("bed", "andesite"), Map.entry("seed", "qy6ty4"),
				Map.entry("pinned", "look,mix,era,mode,tracks,length,layout,building,size,roof,esc,walls,trav,bed,class,text"),
				Map.entry("rolled", "look,mix,era,mode,tracks,length,layout,building,size,roof,esc,walls,trav,bed,class")));
		final Map<String, Object> first = Json.object(Json.parse(api.plan(q)));
		assertEquals("", first.get("wordsLook"));
		// what the page's followWords then sets: the plan's tracks and length (and, with a wordsLook, that look and no mix)
		q.put("tracks", "2");
		q.put("length", "64");
		final Map<String, Object> again = Json.object(Json.parse(api.plan(q)));
		assertEquals(first.get("fingerprint"), again.get("fingerprint"), (String) again.get("text"));
		// words that name a look still move the city button
		q.put("text", "2 track munich station, station building, 64 long");
		assertEquals("MUNICH", Json.object(Json.parse(api.plan(q))).get("wordsLook"));
	}
}
