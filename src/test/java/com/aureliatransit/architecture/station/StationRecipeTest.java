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
	@Test
	void everyChoiceGivesACleanBuildableStation() {
		final List<String> failures = new ArrayList<>();
		int index = 0;
		final List<StationRecipe.Look> mixes = new ArrayList<>();
		mixes.add(null);
		mixes.addAll(List.of(StationRecipe.Look.values()));
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			for (final StationRecipe.Look mix : mixes) {
				for (final boolean terminus : new boolean[] {false, true}) {
					for (final StationRecipe.Roof roof : StationRecipe.Roof.values()) {
						index++;
						final int tracks = 1 + index % 4;
						final StationRecipe recipe = new StationRecipe(look, mix, terminus, tracks, StationRecipe.Platforms.values()[index % 3],
								StationRecipe.Length.values()[index % 4], roof, index % 5 != 0, index % 2 == 0 ? "Alpha Centraal" : "");
						final ParseResult result = StationDescriptionParser.parse(recipe.description());
						final String label = recipe.encode() + " -> " + recipe.description();
						if (!result.warnings().isEmpty() || !result.ignored().isEmpty()) {
							failures.add(label + ": warnings " + result.warnings() + " ignored " + result.ignored());
							continue;
						}
						final StationSpec spec = result.spec();
						if (spec.trackCount() != recipe.tracks() || spec.terminus() != recipe.terminus()
								|| spec.platformLength() != recipe.length().blocks() || !spec.name().equals(recipe.name())
								|| spec.hasBuilding() != recipe.building() || recipe.building() && spec.building() != BUILDINGS.get(look)) {
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
				StationRecipe.Length.LONG, StationRecipe.Roof.ARCHED, true, "Hamburg Hbf");
		assertEquals(recipe, StationRecipe.decode(recipe.encode()));
		assertEquals(StationRecipe.defaults(), StationRecipe.decode(""));
		assertEquals(StationRecipe.defaults(), StationRecipe.decode("nonsense"));
		final StationRecipe partial = StationRecipe.decode("MUNICH;BOGUS;0;9");
		assertEquals(StationRecipe.Look.MUNICH, partial.look());
		assertNull(partial.mix());
		assertEquals(StationRecipe.MAX_TRACKS, partial.tracks());
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
		assertEquals("Hamburg + Utrecht terminus, 4 tracks, long", StationRecipe.defaults().withLook(StationRecipe.Look.HAMBURG)
				.withMix(StationRecipe.Look.UTRECHT).withTerminus(true).withTracks(4).withLength(StationRecipe.Length.LONG).title());
	}
}
