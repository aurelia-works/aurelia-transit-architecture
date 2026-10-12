package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bounded description grammar: numbers, layouts, sizes, styles, roofs, materials, accessibility, names, clamping,
 * conflicts, ignored words, and that terrain approval can never come from text.
 */
class StationDescriptionParserTest {

	private static StationSpec spec(String text) {
		return StationDescriptionParser.parse(text).spec();
	}

	private static boolean warned(ParseResult result, String part) {
		return result.warnings().stream().anyMatch(w -> w.contains(part));
	}

	// ---- tracks and layout -----------------------------------------------------------------------------------------

	@Test
	void trackCountsFromDigitsAndWords() {
		assertEquals(3, spec("3 tracks").trackCount());
		assertEquals(3, spec("three tracks").trackCount());
		assertEquals(4, spec("four platform tracks").trackCount());
		assertEquals(2, spec("2 rail tracks").trackCount());
		assertEquals(1, spec("one track").trackCount());
	}

	@Test
	void singleAndDoubleTrack() {
		assertEquals(1, spec("single track halt").trackCount());
		assertEquals(1, spec("single-track halt").trackCount());
		assertEquals(2, spec("double track").trackCount());
		assertEquals(2, spec("two-track").trackCount());
		// "double" elsewhere is not a number
		assertEquals(StationSpec.DEFAULT_TRACKS, spec("double benches").trackCount());
	}

	@Test
	void defaultLayoutFollowsTrackCount() {
		assertEquals(PlatformLayout.SIDE, spec("2 tracks").platformLayout());
		assertEquals(PlatformLayout.SIDE, spec("1 track").platformLayout());
		assertEquals(PlatformLayout.ISLAND, spec("3 tracks").platformLayout());
		assertEquals(PlatformLayout.ISLAND, spec("4 tracks").platformLayout());
		assertTrue(StationDescriptionParser.parse("3 tracks").defaulted().contains("island platforms (default for 3 tracks)"));
	}

	@Test
	void islandAndSidePlatforms() {
		final StationSpec island = spec("an island platform");
		assertEquals(PlatformLayout.ISLAND, island.platformLayout());
		assertEquals(2, island.trackCount());
		assertEquals(StationSpec.DEFAULT_ISLAND_WIDTH, island.platformWidth());
		final StationSpec side = spec("two side platforms");
		assertEquals(PlatformLayout.SIDE, side.platformLayout());
		assertEquals(2, side.trackCount());
		assertEquals(StationSpec.DEFAULT_SIDE_WIDTH, side.platformWidth());
		assertEquals(4, spec("two island platforms").trackCount());
		assertEquals(PlatformLayout.ISLAND, spec("two island platforms").platformLayout());
	}

	@Test
	void islandWithOneTrackBecomesSide() {
		final ParseResult result = StationDescriptionParser.parse("1 track island station");
		assertEquals(PlatformLayout.SIDE, result.spec().platformLayout());
		assertEquals(1, result.spec().trackCount());
		assertTrue(warned(result, "island platform needs two tracks"));
	}

	@Test
	void islandAndSideTogether() {
		final ParseResult three = StationDescriptionParser.parse("3 tracks with island and side platforms");
		assertEquals(PlatformLayout.ISLAND, three.spec().platformLayout());
		assertTrue(three.understood().contains("island and side platforms"));
		assertTrue(three.warnings().isEmpty());
		final ParseResult two = StationDescriptionParser.parse("2 tracks island and side platforms");
		assertEquals(PlatformLayout.ISLAND, two.spec().platformLayout());
		assertTrue(warned(two, "using island"));
	}

	@Test
	void trackCountsAreClampedWithWarnings() {
		final ParseResult many = StationDescriptionParser.parse("14 tracks");
		assertEquals(StationSpec.MAX_TRACKS, many.spec().trackCount());
		assertTrue(warned(many, "more than " + StationSpec.MAX_TRACKS));
		assertEquals(9, spec("9 tracks").trackCount());
		final ParseResult none = StationDescriptionParser.parse("0 tracks");
		assertEquals(StationSpec.MIN_TRACKS, none.spec().trackCount());
		assertTrue(warned(none, "too few"));
		assertEquals(StationSpec.MAX_TRACKS, spec("six island platforms").trackCount());
	}

	@Test
	void secondTrackCountWarns() {
		final ParseResult result = StationDescriptionParser.parse("2 tracks, actually 3 tracks");
		assertEquals(2, result.spec().trackCount());
		assertTrue(warned(result, "track count given twice; using 2"));
	}

	// ---- length and width ------------------------------------------------------------------------------------------

	@Test
	void lengthForms() {
		assertEquals(64, spec("64 blocks long").platformLength());
		assertEquals(100, spec("100m long").platformLength());
		assertEquals(80, spec("platform length of 80 m").platformLength());
		assertEquals(72, spec("length 72").platformLength());
		assertEquals(120, spec("a 120 m station").platformLength());
		assertEquals(64, spec("64 long").platformLength());
	}

	@Test
	void lengthAdjectivesOnlyWithoutANumber() {
		assertEquals(24, spec("short").platformLength());
		assertEquals(96, spec("long platforms").platformLength());
		assertEquals(128, spec("very long").platformLength());
		assertEquals(64, spec("64 blocks long").platformLength());
		assertEquals(32, spec("small halt").platformLength());
		assertEquals(StationSpec.MIN_WIDTH, spec("small halt").platformWidth());
		final StationSpec large = spec("large station");
		assertEquals(96, large.platformLength());
		assertEquals(4, large.trackCount());
		assertEquals(2, spec("large station with 2 tracks").trackCount());
	}

	@Test
	void widthForms() {
		assertEquals(5, spec("5 wide").platformWidth());
		assertEquals(6, spec("6 block wide platforms").platformWidth());
		assertEquals(7, spec("platform width of 7").platformWidth());
		assertEquals(6, spec("wide platforms").platformWidth());
		assertEquals(8, spec("wide island platform").platformWidth());
		assertEquals(3, spec("narrow platforms").platformWidth());
	}

	@Test
	void lengthAndWidthInOneSentence() {
		final StationSpec spec = spec("48 blocks long and 5 wide");
		assertEquals(48, spec.platformLength());
		assertEquals(5, spec.platformWidth());
	}

	@Test
	void sizesAreClampedWithWarnings() {
		final ParseResult longOne = StationDescriptionParser.parse("400 m long");
		assertEquals(StationSpec.MAX_LENGTH, longOne.spec().platformLength());
		assertTrue(warned(longOne, "length 400 is too large; using " + StationSpec.MAX_LENGTH));
		assertEquals(StationSpec.MIN_LENGTH, spec("length 4").platformLength());
		assertEquals(StationSpec.MAX_WIDTH, spec("20 wide").platformWidth());
		final ParseResult thin = StationDescriptionParser.parse("width 1");
		assertEquals(StationSpec.MIN_WIDTH, thin.spec().platformWidth());
		assertTrue(warned(thin, "width 1 is too small"));
	}

	// ---- style -----------------------------------------------------------------------------------------------------

	@Test
	void stylesAndSynonyms() {
		assertEquals(StationStyle.DUTCH, spec("dutch").style());
		assertEquals(StationStyle.DUTCH, spec("like Utrecht").style());
		assertEquals(StationStyle.DUTCH, spec("leidsche rijn style").style());
		assertEquals(StationStyle.GERMAN_SBAHN, spec("s-bahn halt").style());
		assertEquals(StationStyle.GERMAN_SBAHN, spec("hamburg").style());
		assertEquals(StationStyle.METRO, spec("metro").style());
		assertEquals(StationStyle.METRO, spec("u-bahn").style());
		assertEquals(StationStyle.MODERN, spec("modern").style());
		assertEquals(StationStyle.MODERN, spec("").style());
	}

	@Test
	void undergroundAndElevatedAreBuiltAtGroundLevelWithAWarning() {
		final ParseResult underground = StationDescriptionParser.parse("underground station");
		assertEquals(StationStyle.METRO, underground.spec().style());
		assertTrue(warned(underground, "underground stations are not built yet"));
		final ParseResult elevated = StationDescriptionParser.parse("elevated station on a viaduct");
		assertEquals(StationStyle.MODERN, elevated.spec().style());
		assertTrue(warned(elevated, "elevated stations are not built yet"));
		assertEquals(1, elevated.warnings().size());
	}

	@Test
	void secondStyleWarnsAndFirstWins() {
		final ParseResult result = StationDescriptionParser.parse("dutch station in german style");
		assertEquals(StationStyle.DUTCH, result.spec().style());
		assertTrue(warned(result, "style given twice; using Dutch"));
	}

	// ---- roof and materials ----------------------------------------------------------------------------------------

	@Test
	void roofForms() {
		assertEquals(RoofType.NONE, spec("no roof").roof());
		assertEquals(RoofType.NONE, spec("without canopy").roof());
		assertEquals(RoofType.NONE, spec("open platforms").roof());
		assertEquals(RoofType.CANOPY, spec("covered platforms").roof());
		assertEquals(RoofType.HALL, spec("train shed").roof());
		assertEquals(RoofType.HALL, spec("station hall").roof());
		assertEquals(RoofType.HALL, spec("big roof").roof());
		assertEquals(RoofType.CANOPY, spec("").roof());
	}

	@Test
	void negatedRoofBeatsAPlainMention() {
		final ParseResult result = StationDescriptionParser.parse("canopy, no wait, no roof");
		assertEquals(RoofType.NONE, result.spec().roof());
		assertTrue(warned(result, "using no roof"));
	}

	@Test
	void roofMaterials() {
		assertEquals(RoofMaterial.GLASS, spec("glass canopy").roofMaterial());
		assertEquals(RoofMaterial.GLASS, spec("skylights").roofMaterial());
		assertEquals(RoofMaterial.TIMBER, spec("timber roof").roofMaterial());
		assertEquals(RoofMaterial.TIMBER, spec("wooden canopy").roofMaterial());
		assertEquals(RoofMaterial.STEEL, spec("steel canopy").roofMaterial());
		assertEquals(RoofType.HALL, spec("glass train shed").roof());
	}

	@Test
	void woodenBenchesDoNotMakeATimberRoof() {
		assertEquals(RoofMaterial.STYLE_DEFAULT, spec("wooden benches").roofMaterial());
	}

	@Test
	void surfaceMaterials() {
		assertEquals(SurfaceMaterial.LIGHT_PAVING, spec("light paving").surface());
		assertEquals(SurfaceMaterial.DARK_PAVING, spec("dark platforms").surface());
		assertEquals(SurfaceMaterial.CONCRETE, spec("concrete").surface());
		assertEquals(SurfaceMaterial.TERRAZZO, spec("terrazzo floor").surface());
		assertEquals(SurfaceMaterial.GRANITE, spec("granite").surface());
		// "light" alone is not paving
		assertEquals(SurfaceMaterial.STYLE_DEFAULT, spec("light station").surface());
	}

	// ---- accessibility, furniture, rail nodes ----------------------------------------------------------------------

	@Test
	void accessibilityDefaultsOnAndCanBeTurnedOff() {
		assertTrue(spec("").stepFree());
		assertTrue(spec("").tactile());
		assertTrue(spec("step-free").stepFree());
		assertTrue(spec("wheelchair accessible").stepFree());
		assertFalse(spec("no ramps").stepFree());
		assertFalse(spec("not accessible").stepFree());
		assertFalse(spec("stairs only").stepFree());
		assertFalse(spec("no tactile paving").tactile());
		assertFalse(spec("without tactile").tactile());
	}

	@Test
	void furnitureAndRailNodes() {
		assertTrue(spec("").furniture());
		assertFalse(spec("no benches").furniture());
		assertFalse(spec("bare platforms").furniture());
		assertTrue(spec("").railNodes());
		assertFalse(spec("no rail nodes").railNodes());
		assertFalse(spec("without rails").railNodes());
	}

	// ---- names -----------------------------------------------------------------------------------------------------

	@Test
	void quotedAndUnquotedNames() {
		assertEquals("Leidsche Rijn", spec("named \"Leidsche Rijn\"").name());
		assertEquals("Aurelia Centraal", spec("a station called Aurelia Centraal, 3 tracks").name());
		assertEquals("Noriega", spec("named Noriega with a glass canopy").name());
		assertEquals("Alpha", spec("\"Alpha\" two tracks").name());
		assertEquals("", spec("2 tracks").name());
	}

	@Test
	void nameWordsAreNotKeywords() {
		final StationSpec spec = spec("named \"Glass Hall\" 2 tracks");
		assertEquals("Glass Hall", spec.name());
		assertEquals(RoofMaterial.STYLE_DEFAULT, spec.roofMaterial());
		assertEquals(RoofType.CANOPY, spec.roof());
		assertEquals(StationStyle.MODERN, spec("called Utrecht Zuid").style());
	}

	@Test
	void longNamesAreCut() {
		final ParseResult result = StationDescriptionParser.parse("named \"" + "X".repeat(50) + "\"");
		assertEquals(StationSpec.MAX_NAME, result.spec().name().length());
		assertTrue(warned(result, "name is longer"));
	}

	// ---- safety, feedback, robustness ------------------------------------------------------------------------------

	@Test
	void terrainWordsGrantNothing() {
		final ParseResult plain = StationDescriptionParser.parse("2 tracks");
		final ParseResult result = StationDescriptionParser.parse("2 tracks, clear the trees and flatten the hill");
		assertEquals(plain.spec(), result.spec());
		assertTrue(warned(result, "only approved with /ata_station confirm replace_terrain"));
		assertEquals(1, result.warnings().size());
		assertFalse(result.ignored().contains("clear"));
		assertFalse(result.ignored().contains("flatten"));
	}

	@Test
	void unknownWordsAreListedOnceInOrder() {
		final ParseResult result = StationDescriptionParser.parse("2 tracks with purple fountains and purple dragons");
		assertEquals(List.of("purple", "fountains", "dragons"), result.ignored());
	}

	@Test
	void emptyBlankAndNullGiveDefaults() {
		for (final String text : new String[]{null, "", "   "}) {
			final ParseResult result = StationDescriptionParser.parse(text);
			assertEquals(StationSpec.defaults(), result.spec());
			assertEquals("no description: using defaults", result.defaulted().get(0));
			assertTrue(result.understood().isEmpty());
			assertTrue(result.ignored().isEmpty());
		}
	}

	@Test
	void caseAndPunctuationDoNotMatter() {
		assertEquals(spec("two track island station, 64 blocks long"), spec("TWO-TRACK Island Station; 64 Blocks LONG!"));
	}

	@Test
	void overlongInputIsCut() {
		final ParseResult result = StationDescriptionParser.parse("3 tracks " + "station ".repeat(200));
		assertEquals(3, result.spec().trackCount());
		assertTrue(warned(result, "longer than " + StationDescriptionParser.MAX_INPUT));
	}

	@Test
	void sameInputSameResult() {
		final String text = "Build a three track German S-Bahn station, island and side platforms, 80 m long, hall roof, named Reinbek";
		assertEquals(StationDescriptionParser.parse(text), StationDescriptionParser.parse(text));
	}

	@Test
	void realisticSentences() {
		final ParseResult dutch = StationDescriptionParser.parse(
				"Build a two-track Dutch station with an island platform, 64 blocks long, glass canopy, named Leidsche Rijn");
		final StationSpec d = dutch.spec();
		assertEquals(2, d.trackCount());
		assertEquals(PlatformLayout.ISLAND, d.platformLayout());
		assertEquals(64, d.platformLength());
		assertEquals(StationStyle.DUTCH, d.style());
		assertEquals(RoofType.CANOPY, d.roof());
		assertEquals(RoofMaterial.GLASS, d.roofMaterial());
		assertEquals("Leidsche Rijn", d.name());
		assertTrue(dutch.ignored().isEmpty(), dutch.ignored().toString());
		assertEquals(List.of("name \"Leidsche Rijn\"", "2 tracks", "Dutch style", "island platforms", "length 64", "glass roof", "canopy"),
				dutch.understood());

		final StationSpec sbahn = spec("small single track s-bahn halt, no roof, no benches");
		assertEquals(1, sbahn.trackCount());
		assertEquals(StationStyle.GERMAN_SBAHN, sbahn.style());
		assertEquals(RoofType.NONE, sbahn.roof());
		assertFalse(sbahn.furniture());
		assertEquals(32, sbahn.platformLength());

		final StationSpec metro = spec("4 track metro station with terrazzo floors, 120 m long, wide platforms, step-free");
		assertEquals(4, metro.trackCount());
		assertEquals(PlatformLayout.ISLAND, metro.platformLayout());
		assertEquals(SurfaceMaterial.TERRAZZO, metro.surface());
		assertEquals(120, metro.platformLength());
		assertEquals(8, metro.platformWidth());

		final StationSpec hall = spec("modern station hall over 2 tracks, side platforms 6 wide, dark paving, timber roof");
		assertEquals(RoofType.HALL, hall.roof());
		assertEquals(PlatformLayout.SIDE, hall.platformLayout());
		assertEquals(6, hall.platformWidth());
		assertEquals(SurfaceMaterial.DARK_PAVING, hall.surface());
		assertEquals(RoofMaterial.TIMBER, hall.roofMaterial());
	}

	@Test
	void everyResultIsAValidSpecThatPlansWithoutViolations() {
		final String[] texts = {"", "1 track", "4 tracks side platforms hall", "two island platforms, glass hall", "short narrow metro",
				"very long wide dutch island station named \"Centraal\"", "9 tracks 400 m long width 20", "s-bahn, no roof, no ramps"};
		for (final String text : texts) {
			final StationSpec spec = spec(text);
			final List<Violation> violations = LayoutValidator.validate(StationPlanner.plan(spec));
			// a 3-4 track station with side platforms cannot carry a hall roof: the validator says so instead of building it
			if (spec.platformLayout() == PlatformLayout.SIDE && spec.trackCount() >= 3 && spec.roof() == RoofType.HALL) {
				assertTrue(violations.stream().allMatch(v -> v.kind() == Violation.Kind.ROOF_SPAN) && !violations.isEmpty(), text);
			} else {
				assertTrue(violations.isEmpty(), text + ": " + violations);
			}
		}
	}

	@Test
	void stationBuildingsAndLandmarks() {
		assertEquals(BuildingStyle.MODERN, spec("station building").building());
		assertEquals(BuildingStyle.MODERN, spec("two track station with ticket hall").building());
		assertEquals(BuildingStyle.AMSTERDAM, spec("amsterdam station building").building());
		assertEquals(BuildingStyle.GOTHIC_BRICK, spec("st pancras station building").building());
		assertEquals(BuildingStyle.ANTWERP, spec("antwerp central with building").building());
		assertEquals(BuildingStyle.BEAUX_ARTS, spec("grand central station building").building());
		assertEquals(BuildingStyle.BEAUX_ARTS, spec("union station with building").building());
		assertEquals(BuildingStyle.NONE, spec("no station building").building());
		assertTrue(spec("station with footbridge").footbridge());
		assertTrue(spec("station building with footbridge").footbridge());
		// a train shed now goes over a station with a building beside the tracks (tall enough to clear its footbridge)
		assertTrue(StationDescriptionParser.parse("station building with hall roof").warnings().isEmpty());
		assertEquals(RoofType.HALL, spec("station building with hall roof").roof());
	}

	@Test
	void terminusStations() {
		final ParseResult shed = StationDescriptionParser.parse("four track st pancras terminus, two island platforms, train shed");
		assertTrue(shed.spec().terminus());
		assertEquals(BuildingStyle.GOTHIC_BRICK, shed.spec().building());
		assertEquals(RoofType.HALL, shed.spec().roof(), "a terminus keeps its train shed");
		assertTrue(shed.warnings().isEmpty(), shed.warnings().toString());
		assertTrue(spec("kopfbahnhof").terminus());
		assertTrue(spec("terminal station").hasBuilding());
		final ParseResult bridge = StationDescriptionParser.parse("terminus with footbridge");
		assertFalse(bridge.spec().footbridge());
		assertTrue(bridge.warnings().stream().anyMatch(w -> w.contains("no footbridge")), bridge.warnings().toString());
		assertEquals(RoofType.HALL, spec("station building, hall roof").roof());
		assertFalse(spec("station building").terminus());
	}

	@Test
	void aTerminusIsAMainStationUnlessToldOtherwise() {
		assertSize(spec("terminus"), 8, 160);
		assertSize(spec("grand terminus"), 8, 160);
		assertSize(spec("hamburg terminus with a train shed"), 8, 160);
		assertSize(spec("large terminus"), 10, 200);
		assertSize(spec("big terminus"), 10, 200);
		assertSize(spec("city terminus"), 4, 96);
		assertSize(spec("small terminus"), 2, 32);
		assertSize(spec("halt terminus"), 2, 48);
		// what the player says always wins
		assertSize(spec("terminus, 3 tracks"), 3, 160);
		assertSize(spec("terminus, 300 long"), 8, 300);
		assertSize(spec("two track terminus, 64 long"), 2, 64);
		assertSize(spec("large terminus, 6 tracks, 120 long"), 6, 120);
		assertTrue(StationDescriptionParser.parse("terminus").defaulted().contains("8 tracks (default for a terminus)"));
		assertEquals(PlatformLayout.ISLAND, spec("terminus").platformLayout());
		assertEquals(StationScale.GRAND, spec("terminus").scale(), "a main-station terminus gets a grand head building");
	}

	private static void assertSize(StationSpec spec, int tracks, int length) {
		assertEquals(tracks, spec.trackCount(), spec.toString());
		assertEquals(length, spec.platformLength(), spec.toString());
	}

	// ---- mixed looks and train sheds -------------------------------------------------------------------------------

	@Test
	void mixedLooksGiveBuildingPlatformsAndShedFromDifferentCities() {
		final ParseResult result = StationDescriptionParser.parse("make an american train station and make utrecht and hamburg combined");
		final StationSpec spec = result.spec();
		assertEquals(BuildingStyle.BEAUX_ARTS, spec.building(), "the first look gives the building");
		assertEquals(StationStyle.DUTCH, spec.style(), "the first look with platforms gives them");
		assertEquals(RoofType.HALL, spec.roof(), "a Hamburg look brings its train shed");
		assertEquals(ShedShape.ARCH, spec.shed());
		assertEquals(List.of(), result.warnings());
		assertEquals(List.of(), result.ignored());
		assertTrue(result.understood().stream().anyMatch(line -> line.startsWith("mix: building from American")), result.understood().toString());
		assertTrue(LayoutValidator.validate(StationPlanner.plan(spec)).isEmpty());
		assertTrue(StationAudit.audit(StationPlanner.plan(spec)).buildable());

		final StationSpec utrechtHamburg = spec("utrecht x hamburg");
		assertEquals(BuildingStyle.MODERN, utrechtHamburg.building());
		assertEquals(StationStyle.DUTCH, utrechtHamburg.style());
		assertEquals(ShedShape.ARCH, utrechtHamburg.shed());
	}

	@Test
	void aCityAsksForItsBuildingAndSignatureShed() {
		assertEquals(BuildingStyle.CLINKER, spec("hamburg station").building());
		assertEquals(RoofType.HALL, spec("hamburg station").roof());
		assertEquals(ShedShape.POINTED, spec("st pancras terminus").shed());
		assertEquals(ShedShape.MULTI_ARCH, spec("frankfurt terminus").shed());
		assertEquals(BuildingStyle.BEAUX_ARTS, spec("american train station").building());
		assertEquals(RoofType.CANOPY, spec("american train station").roof(), "Grand Central has no train shed");
		assertEquals(BuildingStyle.BEAUX_ARTS, spec("new york station").building());
		// a chosen roof wins over the city's shed, and "no station building" keeps platforms only
		assertEquals(RoofType.CANOPY, spec("hamburg station with canopies").roof());
		assertEquals(BuildingStyle.NONE, spec("hamburg station, no station building").building());
		assertEquals(StationStyle.GERMAN_SBAHN, spec("hamburg station, no station building").style());
		// a style word still wins over a city's platforms
		assertEquals(StationStyle.METRO, spec("metro style utrecht station").style());
	}

	@Test
	void shedWordsPickTheShape() {
		assertEquals(ShedShape.ARCH, spec("arched roof").shed());
		assertEquals(RoofType.HALL, spec("vaulted roof").roof());
		assertEquals(ShedShape.POINTED, spec("pointed arch train shed").shed());
		assertEquals(ShedShape.MULTI_ARCH, spec("four tracks, multi span shed").shed());
		assertEquals(ShedShape.FLAT, spec("hamburg station, flat roof").shed());
		assertEquals(List.of(), StationDescriptionParser.parse("utrecht and hamburg mixed, pointed arch").ignored());
	}

	@Test
	void buildingSizeFromWordsOrFromTheStation() {
		assertEquals(StationScale.GRAND, spec("grand station").scale(), "grand asks for a grand building");
		assertTrue(spec("grand station").hasBuilding());
		assertEquals(StationScale.GRAND, spec("frankfurt hauptbahnhof, 2 tracks, 40 long").scale());
		assertEquals(StationScale.CITY, spec("city station with station building, 6 tracks").scale(), "a size word wins over the station size");
		assertEquals(StationScale.COMPACT, spec("compact station building, 4 tracks, 120 long").scale());
		// without a word: grand from 3 tracks or 100 long, city from 2 tracks and 48 long, else compact
		final ParseResult big = StationDescriptionParser.parse("4 track station with station building");
		assertEquals(StationScale.GRAND, big.spec().scale());
		assertTrue(big.defaulted().stream().anyMatch(d -> d.startsWith("grand station building")), big.defaulted().toString());
		assertEquals(StationScale.GRAND, spec("1 track station with station building, 120 long").scale());
		assertEquals(StationScale.CITY, spec("2 track station with station building, 64 long").scale());
		assertEquals(StationScale.COMPACT, spec("2 track station with station building, 32 long").scale());
		assertEquals(StationScale.COMPACT, spec("small station with station building").scale());
		assertEquals(StationScale.COMPACT, spec("6 tracks").buildingScale(), "no building, nothing to size");
		assertEquals(StationScale.GRAND, spec("grand terminus").buildingScale(), "a head building has a size too");
		assertEquals(StationScale.GRAND, spec("four track grand central terminus, two island platforms, 96 long").scale());
		assertTrue(StationDescriptionParser.parse("four track grand central terminus").warnings().isEmpty());
		assertTrue(warned(StationDescriptionParser.parse("grand city station"), "given twice"));
	}
}
