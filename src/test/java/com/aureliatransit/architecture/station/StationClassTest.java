package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Station class and era: what a class fills in, that anything said wins, that the older words keep their meaning, and the facing. */
class StationClassTest {

	private static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	private static StationPlan plan(String words) {
		return StationPlanner.plan(spec(words));
	}

	private static void buildable(String words) {
		final StationPlan plan = plan(words);
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		final StationAudit.Result audit = StationAudit.audit(plan);
		assertTrue(audit.buildable(), words + "\n" + audit.problems());
	}

	@Test
	void unknownAndMissingValuesAreAuto() {
		assertEquals(StationClass.AUTO, StationClass.of((String) null));
		assertEquals(StationClass.AUTO, StationClass.of(""));
		assertEquals(StationClass.AUTO, StationClass.of("banana"));
		assertEquals(StationClass.AUTO, StationClass.of("AUTO"));
		assertEquals(StationClass.GRAND, StationClass.of(" Grand "));
		assertEquals(StationEra.AUTO, StationEra.of("jurassic"));
		assertEquals(StationEra.INTERWAR, StationEra.of("INTERWAR"));
		assertEquals(Facing.AUTO, Facing.of("up"));
		assertEquals(Facing.WEST, Facing.of("west"));
		assertNull(Facing.AUTO.direction());
	}

	@Test
	void aHaltIsOneOpenPlatformWithNoBuilding() {
		final StationSpec halt = spec("halt class");
		assertEquals(StationClass.HALT, halt.stationClass());
		assertEquals(1, halt.trackCount());
		assertEquals(PlatformLayout.SIDE, halt.platformLayout());
		assertEquals(StationDescriptionParser.HALT_LENGTH, halt.platformLength());
		assertEquals(StationDescriptionParser.HALT_WIDTH, halt.platformWidth());
		assertFalse(halt.hasBuilding());
		assertEquals(RoofType.NONE, halt.roof());
		assertFalse(halt.footbridge());
		buildable("halt class");
		final StationPlan plan = plan("halt class");
		assertTrue(plan.blocks().size() < 4000, "a halt is small: " + plan.blocks().size());
		assertTrue(plan.maxY() <= 8, "no building, no shed: " + plan.maxY());
		final ParseResult parse = StationDescriptionParser.parse("halt class");
		assertTrue(parse.ignored().isEmpty() && parse.warnings().isEmpty(), parse.ignored() + " " + parse.warnings());
		assertTrue(parse.understood().stream().anyMatch(u -> u.startsWith("halt class")), parse.understood().toString());
	}

	@Test
	void aRegionalStationHasACompactBuildingAndNoTrainShed() {
		for (final String era : new String[]{"historic", "interwar", "postwar", "modern"}) {
			final String words = "regional class, " + era + " era";
			final StationSpec regional = spec(words);
			assertEquals(StationScale.COMPACT, regional.buildingScale(), words);
			assertTrue(regional.hasBuilding(), words);
			assertEquals(2, regional.trackCount(), words);
			assertEquals(StationDescriptionParser.REGIONAL_LENGTH, regional.platformLength(), words);
			assertNotEquals(RoofType.HALL, regional.roof(), "no train shed: " + words);
			assertEquals(StationClass.REGIONAL, StationClass.of(regional), words);
			buildable(words);
			assertTrue(plan(words).maxY() <= 20, words + " is a small building: " + plan(words).maxY());
		}
		// the archetypes: historic is side platforms and a footbridge in gabled brick or sandstone, modern one island in glass
		assertEquals(PlatformLayout.SIDE, spec("regional class, historic era").platformLayout());
		assertEquals(CanopyShape.GABLE, spec("regional class, historic era").canopy());
		assertEquals(BuildingStyle.SANDSTONE, spec("regional class, historic era").building());
		assertEquals(BuildingStyle.ART_DECO, spec("regional class, interwar era").building());
		assertEquals(BuildingStyle.CONCRETE, spec("regional class, postwar era").building());
		assertEquals(PlatformLayout.ISLAND, spec("regional class, modern era").platformLayout());
		assertEquals(BuildingStyle.MODERN, spec("regional class, modern era").building());
		assertEquals(RoofMaterial.GLASS, spec("regional class, modern era").roofMaterial());
	}

	@Test
	void townCityAndGrandGrowFromThere() {
		final StationSpec town = spec("town class");
		assertEquals(3, town.trackCount());
		assertEquals(StationScale.CITY, town.buildingScale());
		final StationSpec city = spec("city class");
		assertEquals(4, city.trackCount());
		assertEquals(StationScale.CITY, city.buildingScale());
		assertEquals(StationDescriptionParser.CITY_LENGTH, city.platformLength());
		final StationSpec grand = spec("grand class");
		assertEquals(StationScale.GRAND, grand.buildingScale());
		assertEquals(StationDescriptionParser.GRAND_TRACKS, grand.trackCount());
		assertEquals(RoofType.HALL, grand.roof());
		assertEquals(ShedShape.MULTI_ARCH, grand.shed(), "historic by default: several arches");
		assertEquals(ShedShape.ARCH, spec("grand class, modern era").shed());
		assertTrue(spec("grand class, modern era").escalators());
		assertEquals(BuildingStyle.BERLIN, spec("grand class, modern era").building());
		assertEquals(BuildingStyle.MILAN, spec("grand class, interwar era").building());
		for (final String words : new String[]{"town class", "city class", "grand class", "grand class, modern era", "city class, postwar era"}) {
			buildable(words);
		}
		// bigger classes are bigger stations
		final int regional = plan("regional class").blocks().size();
		final int townBlocks = plan("town class").blocks().size();
		final int grandBlocks = plan("grand class").blocks().size();
		assertTrue(plan("halt class").blocks().size() < regional && regional < townBlocks && townBlocks < grandBlocks,
				"halt < regional < town < grand: " + plan("halt class").blocks().size() + " " + regional + " " + townBlocks + " " + grandBlocks);
	}

	@Test
	void everythingSaidBeatsTheClass() {
		final StationSpec halt = spec("halt class, 3 tracks, 100 long, 7 wide, station building, canopy");
		assertEquals(StationClass.HALT, halt.stationClass());
		assertEquals(3, halt.trackCount());
		assertEquals(100, halt.platformLength());
		assertEquals(7, halt.platformWidth());
		assertTrue(halt.hasBuilding());
		assertEquals(RoofType.CANOPY, halt.roof());
		final StationSpec grand = spec("grand class, 2 tracks, 64 long, compact, no station building, utrecht");
		assertEquals(2, grand.trackCount());
		assertEquals(64, grand.platformLength());
		assertFalse(grand.hasBuilding());
		assertEquals(StationStyle.DUTCH, grand.style(), "a look word beats the era");
		final StationSpec city = spec("city class, hamburg, island platforms, 5 tracks, small");
		assertEquals(5, city.trackCount());
		assertEquals(PlatformLayout.ISLAND, city.platformLayout());
		assertEquals(BuildingStyle.CLINKER, city.building());
		assertEquals(StationClass.CITY, city.stationClass());
		// a terminus keeps its own defaults; the class gives it its size and look
		final StationSpec terminus = spec("grand class, terminus");
		assertTrue(terminus.terminus());
		assertEquals(StationDescriptionParser.TERMINUS_TRACKS, terminus.trackCount());
		assertEquals(StationScale.GRAND, terminus.buildingScale());
		buildable("grand class, terminus");
		buildable("regional class, terminus");
		buildable("halt class, terminus");
		buildable("grand class, hub");
	}

	@Test
	void theOlderWordsKeepTheirMeaning() {
		// without the word "class" nothing about "regional", "halt", "town", "city", "grand" or "modern" changed
		assertEquals(StationScale.CITY, spec("regional station, station building, 2 tracks, 64 long").buildingScale());
		assertEquals(StationScale.CITY, spec("town station, station building, 2 tracks, 64 long").buildingScale());
		assertEquals(StationScale.COMPACT, spec("halt, station building, 2 tracks, 64 long").buildingScale());
		assertEquals(StationScale.GRAND, spec("grand station, station building, 2 tracks, 64 long").buildingScale());
		assertEquals(StationStyle.MODERN, spec("modern station, 2 tracks, 64 long").style());
		for (final String words : new String[]{"regional station, 2 tracks", "halt", "grand station", "modern", "2 tracks, 64 long, city"}) {
			final StationSpec s = spec(words);
			assertEquals(StationClass.AUTO, s.stationClass(), words);
			assertEquals(StationEra.AUTO, s.era(), words);
			assertEquals(Facing.AUTO, s.facing(), words);
		}
		// the phrase "city class" is not the size word "city"
		final ParseResult both = StationDescriptionParser.parse("2 tracks, 64 long, city class, station building");
		assertEquals(StationClass.CITY, both.spec().stationClass());
		assertTrue(both.ignored().isEmpty(), both.ignored().toString());
		// "regional class" does not make a city-sized building: regional class is compact
		assertEquals(StationScale.COMPACT, spec("regional class").buildingScale());
		// "modern era" does not set the modern style on its own
		assertEquals(StationStyle.DUTCH, spec("2 tracks, modern era").style());
		assertEquals(BuildingStyle.MODERN, spec("2 tracks, station building, modern era").building());
	}

	@Test
	void autoReportsTheClassAndEraTheStationAmountsTo() {
		assertEquals(StationClass.HALT, StationClass.of(spec("2 tracks, 64 long")));
		assertEquals(StationClass.HALT, StationClass.of(spec("1 track")));
		assertEquals(StationClass.REGIONAL, StationClass.of(spec("2 tracks, station building, compact")));
		assertEquals(StationClass.TOWN, StationClass.of(spec("2 tracks, station building, city")));
		assertEquals(StationClass.CITY, StationClass.of(spec("5 tracks, station building, city")));
		assertEquals(StationClass.CITY, StationClass.of(spec("terminus, city")));
		assertEquals(StationClass.GRAND, StationClass.of(spec("terminus")));
		assertEquals(StationClass.GRAND, StationClass.of(spec("2 tracks, station building, grand")));
		assertEquals(StationEra.HISTORIC, StationEra.of(spec("2 tracks, station building, st pancras")));
		assertEquals(StationEra.INTERWAR, StationEra.of(spec("2 tracks, station building, hamburg")));
		assertEquals(StationEra.POSTWAR, StationEra.of(spec("2 tracks, station building, munich")));
		assertEquals(StationEra.MODERN, StationEra.of(spec("2 tracks, station building, utrecht")));
		assertEquals(StationEra.MODERN, StationEra.of(spec("2 tracks, 64 long")));
		// a class asked for is the class reported, whatever the numbers became
		assertEquals(StationClass.GRAND, StationClass.of(spec("grand class, 1 track, 20 long, no station building")));
		assertEquals(StationEra.POSTWAR, StationEra.of(spec("postwar era, utrecht, station building")));
	}

	@Test
	void everyClassAndEraPlansACleanStation() {
		final List<String> problems = new ArrayList<>();
		for (final StationClass c : StationClass.values()) {
			for (final StationEra e : StationEra.values()) {
				for (final String mode : new String[]{"", ", terminus", ", hub"}) {
					final String words = (c == StationClass.AUTO ? "" : c.key() + " class, ") + (e == StationEra.AUTO ? "" : e.key() + " era, ") + "named Clean" + mode;
					try {
						final StationPlan plan = plan(words);
						if (!LayoutValidator.validate(plan).isEmpty()) {
							problems.add(words + " breaks " + LayoutValidator.validate(plan));
						}
						final StationAudit.Result audit = StationAudit.audit(plan);
						if (!audit.buildable()) {
							problems.add(words + " fails " + audit.problems());
						}
					} catch (RuntimeException ex) {
						problems.add(words + " threw " + ex);
					}
				}
			}
		}
		assertEquals(List.of(), problems);
	}

	// ---- facing --------------------------------------------------------------------------------------------------------

	@Test
	void theEntranceFacesTheWayItWasToldAndNothingElseChanges() {
		for (final Facing facing : new Facing[]{Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST}) {
			for (final String base : new String[]{"2 tracks, 64 long, station building, utrecht", "4 tracks island, 96 long, terminus, hamburg", "2 tracks, 48 long"}) {
				final StationSpec spec = spec(base + ", facing " + facing.key());
				assertEquals(facing, spec.facing(), base);
				assertEquals(PlanFingerprint.of(plan(base)), PlanFingerprint.of(StationPlanner.plan(spec)), "turning a station changes no block");
				// the street side looks the way it was asked: from the middle of the station to its paste origin
				final StationPlan plan = StationPlanner.plan(spec);
				final LocalFrame frame = StationPlacement.frame(spec);
				final int[] origin = StationPlacement.pasteOrigin(spec, plan);
				final int cu = plan.layout().width() / 2;
				final int cv = (plan.layout().vMin() + plan.layout().vMax()) / 2;
				final int dx = frame.x(origin[0], origin[2]) - frame.x(cu, cv);
				final int dz = frame.z(origin[0], origin[2]) - frame.z(cu, cv);
				final Heading outward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Heading.EAST : Heading.WEST) : (dz > 0 ? Heading.SOUTH : Heading.NORTH);
				if (spec.hasBuilding()) {
					assertEquals(facing.direction(), outward, base + " facing " + facing.key() + ": the street is " + outward);
				}
			}
		}
		// not asked: the studio's own north-up frame
		assertEquals(Heading.NORTH, StationPlacement.heading(spec("2 tracks")));
		assertEquals(Heading.NORTH, StationPlacement.heading(spec("terminus")));
		// asked: a terminus faces its head building's way, a through station its building's side
		assertEquals(Heading.SOUTH, StationPlacement.heading(spec("terminus, facing south")));
		assertEquals(Heading.WEST, StationPlacement.heading(spec("2 tracks, station building, facing south")));
	}

	@Test
	void facingWordsAreReadOnceAndNeverMistaken() {
		final ParseResult parse = StationDescriptionParser.parse("2 tracks, 64 long, facing north, facing east");
		assertEquals(Facing.NORTH, parse.spec().facing());
		assertTrue(parse.warnings().stream().anyMatch(w -> w.contains("facing given twice")), parse.warnings().toString());
		assertTrue(parse.ignored().isEmpty(), parse.ignored().toString());
		final ParseResult sideways = StationDescriptionParser.parse("2 tracks, facing sideways");
		assertEquals(Facing.AUTO, sideways.spec().facing());
		assertTrue(sideways.ignored().contains("facing") && sideways.ignored().contains("sideways"), sideways.ignored().toString());
		// a name is not read for facing
		assertEquals(Facing.AUTO, spec("2 tracks, named \"Facing North\"").facing());
		assertEquals("Facing North", spec("2 tracks, named \"Facing North\"").name());
	}

	/** A description with no class that amounts to an open halt gets a halt's platforms; anything roofed or bigger keeps its width. */
	@Test
	void anOpenHaltWithNoClassNamedGetsAHaltsWidth() {
		assertEquals(StationDescriptionParser.HALT_WIDTH, StationDescriptionParser.parse("1 track, no roof").spec().platformWidth());
		assertEquals(StationDescriptionParser.HALT_WIDTH, StationDescriptionParser.parse("two side platforms, no roof").spec().platformWidth());
		assertEquals(StationSpec.DEFAULT_SIDE_WIDTH, StationDescriptionParser.parse("1 track").spec().platformWidth(), "a canopy keeps the width");
		assertTrue(StationDescriptionParser.parse("3 tracks, no roof").spec().platformWidth() > StationDescriptionParser.HALT_WIDTH, "three tracks is no halt");
		assertEquals(8, StationDescriptionParser.parse("1 track, no roof, 8 wide").spec().platformWidth(), "a said width wins");
		assertEquals(StationSpec.DEFAULT_SIDE_WIDTH, StationDescriptionParser.parse("1 track, no roof, with station building").spec().platformWidth());
	}
}
