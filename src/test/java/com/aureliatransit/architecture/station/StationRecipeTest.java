package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationRecipeTest {

	private static final Map<StationRecipe.Look, BuildingStyle> BUILDINGS = Map.of(StationRecipe.Look.UTRECHT, BuildingStyle.MODERN,
			StationRecipe.Look.AMSTERDAM, BuildingStyle.AMSTERDAM, StationRecipe.Look.HAMBURG, BuildingStyle.CLINKER, StationRecipe.Look.MUNICH,
			BuildingStyle.CONCRETE, StationRecipe.Look.FRANKFURT, BuildingStyle.SANDSTONE, StationRecipe.Look.ST_PANCRAS, BuildingStyle.GOTHIC_BRICK,
			StationRecipe.Look.ANTWERP, BuildingStyle.ANTWERP, StationRecipe.Look.NEW_YORK, BuildingStyle.BEAUX_ARTS);

	/**
	 * Every look with every mix, both station types and every roof (tracks, platforms, length and building cycle along): the
	 * description parses cleanly into what was chosen and the plan passes the design rules and the station checks.
	 */
	private static final int[] LENGTHS = {32, 64, 96, 128};

	/** The Mix button steps through every other city and back to none, whatever look is chosen; it never sticks on none. */
	@Test
	void mixButtonCyclesThroughEveryOtherCity() {
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			StationRecipe recipe = StationRecipe.defaults().withLook(look);
			final List<StationRecipe.Look> seen = new ArrayList<>();
			do {
				recipe = recipe.withMix(recipe.nextMix());
				if (recipe.mix() != null) {
					seen.add(recipe.mix());
				}
			} while (recipe.mix() != null && seen.size() <= StationRecipe.Look.values().length);
			final List<StationRecipe.Look> expected = new ArrayList<>();
			for (final StationRecipe.Look other : StationRecipe.Look.values()) {
				if (other != StationRecipe.Look.AUTO && other != look) {
					expected.add(other);
				}
			}
			assertEquals(expected, seen, "mix cycle with " + look);
		}
	}

	@Test
	void everyChoiceGivesACleanBuildableStation() {
		final List<String> failures = new ArrayList<>();
		int index = 0;
		final List<StationRecipe.Look> mixes = new ArrayList<>();
		mixes.add(null);
		mixes.addAll(List.of(StationRecipe.Look.values()));
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			for (final StationRecipe.Look mix : mixes) {
				for (final int type : new int[] {0, 1, 2}) { // through, terminus, hub
					for (final StationRecipe.Roof roof : StationRecipe.Roof.values()) {
						index++;
						final int tracks = 1 + index % 4;
						final StationRecipe recipe = new StationRecipe(look, mix, type > 0, tracks, StationRecipe.Platforms.values()[index % 3],
								LENGTHS[index % 4], roof, index % 5 != 0, index % 2 == 0 ? "Alpha Centraal" : "", StationRecipe.Size.values()[index % 4], type == 2);
						final ParseResult result = StationDescriptionParser.parse(recipe.description());
						final String label = recipe.encode() + " -> " + recipe.description();
						if (!result.warnings().isEmpty() || !result.ignored().isEmpty()) {
							failures.add(label + ": warnings " + result.warnings() + " ignored " + result.ignored());
							continue;
						}
						final StationSpec spec = result.spec();
						if (spec.trackCount() != recipe.tracks() || spec.terminus() != recipe.terminus() || spec.hub() != recipe.hub()
								|| spec.platformLength() != recipe.length() || !spec.name().equals(recipe.name())
								|| spec.hasBuilding() != recipe.building() || recipe.building() && look != StationRecipe.Look.AUTO && spec.building() != BUILDINGS.get(look)) {
							failures.add(label + ": got " + spec);
							continue;
						}
						final RoofType wantedRoof = switch (roof) {
							case AUTO -> null;
							case CANOPIES -> RoofType.CANOPY;
							case OPEN -> RoofType.NONE;
							default -> RoofType.HALL;
						};
						if (wantedRoof != null && spec.roof() != wantedRoof) {
							failures.add(label + ": roof " + spec.roof());
							continue;
						}
						final StationPlan plan = StationPlanner.plan(spec);
						final List<Violation> violations = LayoutValidator.validate(plan);
						final StationAudit.Result audit = StationAudit.audit(plan);
						if (!violations.isEmpty()) {
							failures.add(label + ": " + violations.get(0));
						} else if (!audit.buildable()) {
							failures.add(label + ": " + audit.problems());
						}
					}
				}
			}
		}
		assertEquals(List.of(), failures.subList(0, Math.min(15, failures.size())), failures.size() + " of " + index);
	}

	@Test
	void encodeRoundTripsAndBadInputFallsBack() {
		final StationRecipe recipe = new StationRecipe(StationRecipe.Look.HAMBURG, StationRecipe.Look.UTRECHT, true, 4, StationRecipe.Platforms.ISLAND,
				96, StationRecipe.Roof.ARCHED, true, "Hamburg Hbf", StationRecipe.Size.CITY, false);
		assertEquals(recipe, StationRecipe.decode(recipe.encode()));
		final StationRecipe hub = recipe.withHub(true);
		assertEquals(hub, StationRecipe.decode(hub.encode()));
		assertTrue(StationRecipe.decode(hub.encode()).hub());
		assertEquals(StationRecipe.Size.AUTO, StationRecipe.decode("MUNICH;;0;2;AUTO;64;AUTO;1;Name").size(), "wrenches saved before sizes");
		assertEquals(StationRecipe.defaults(), StationRecipe.decode(""));
		assertEquals(StationRecipe.defaults(), StationRecipe.decode("nonsense"));
		final StationRecipe partial = StationRecipe.decode("MUNICH;BOGUS;0;99");
		assertEquals(StationRecipe.Look.MUNICH, partial.look());
		assertNull(partial.mix());
		assertEquals(StationRecipe.MAX_TRACKS, partial.tracks());
		assertEquals(96, StationRecipe.decode("MUNICH;;0;2;AUTO;LONG").length(), "wrenches saved with the old length buttons");
	}

	@Test
	void choicesWinOverTypedWords() {
		final StationRecipe recipe = StationRecipe.defaults().withLook(StationRecipe.Look.AUTO).withTracks(3).withLength(120);
		final ParseResult result = StationDescriptionParser.parse(recipe.text("sandstone station with a clock tower, 2 tracks, 50 long"));
		assertEquals(3, result.spec().trackCount());
		assertEquals(120, result.spec().platformLength());
		assertTrue(result.warnings().stream().anyMatch(w -> w.contains("given twice")), result.warnings().toString());
		assertEquals(recipe.description(), recipe.text("  "));
	}

	@Test
	void choicesAreKeptConsistent() {
		final StationRecipe one = StationRecipe.defaults().withTracks(1).withPlatforms(StationRecipe.Platforms.ISLAND);
		assertEquals(StationRecipe.Platforms.AUTO, one.platforms(), "an island needs two tracks");
		assertEquals(StationRecipe.Platforms.AUTO, StationRecipe.defaults().withPlatforms(StationRecipe.Platforms.SIDE).withTracks(3).platforms(),
				"one shed cannot span three tracks between side platforms");
		assertNull(StationRecipe.defaults().withMix(StationRecipe.Look.UTRECHT).mix(), "mixing a look with itself is no mix");
		assertTrue(StationRecipe.defaults().withBuilding(false).withTerminus(true).building(), "a terminus has its head building");
		assertEquals("Bad name", StationRecipe.defaults().withName("Bad; name\"").name());
		assertEquals("Hamburg + Utrecht terminus, 4 tracks, 96 long", StationRecipe.defaults().withLook(StationRecipe.Look.HAMBURG)
				.withMix(StationRecipe.Look.UTRECHT).withTerminus(true).withTracks(4).withLength(96).title());
	}
}
