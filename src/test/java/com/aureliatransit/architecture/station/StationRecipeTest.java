package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationRecipeTest {

	private static final Map<StationRecipe.Look, BuildingStyle> BUILDINGS = Map.ofEntries(Map.entry(StationRecipe.Look.UTRECHT, BuildingStyle.MODERN),
			Map.entry(StationRecipe.Look.AMSTERDAM, BuildingStyle.AMSTERDAM), Map.entry(StationRecipe.Look.HAMBURG, BuildingStyle.CLINKER),
			Map.entry(StationRecipe.Look.MUNICH, BuildingStyle.CONCRETE), Map.entry(StationRecipe.Look.FRANKFURT, BuildingStyle.SANDSTONE),
			Map.entry(StationRecipe.Look.ST_PANCRAS, BuildingStyle.GOTHIC_BRICK), Map.entry(StationRecipe.Look.ANTWERP, BuildingStyle.ANTWERP),
			Map.entry(StationRecipe.Look.NEW_YORK, BuildingStyle.BEAUX_ARTS), Map.entry(StationRecipe.Look.TOKYO, BuildingStyle.TOKYO),
			Map.entry(StationRecipe.Look.ZURICH, BuildingStyle.ZURICH), Map.entry(StationRecipe.Look.PARIS, BuildingStyle.PARIS),
			Map.entry(StationRecipe.Look.MILAN, BuildingStyle.MILAN), Map.entry(StationRecipe.Look.BERLIN, BuildingStyle.BERLIN),
			Map.entry(StationRecipe.Look.HONG_KONG, BuildingStyle.HONG_KONG), Map.entry(StationRecipe.Look.BEIJING, BuildingStyle.BEIJING),
			Map.entry(StationRecipe.Look.BANGKOK, BuildingStyle.BANGKOK), Map.entry(StationRecipe.Look.KUALA_LUMPUR, BuildingStyle.KUALA_LUMPUR),
			Map.entry(StationRecipe.Look.MUMBAI, BuildingStyle.MUMBAI), Map.entry(StationRecipe.Look.TEDDINGTON, BuildingStyle.VICTORIAN),
			Map.entry(StationRecipe.Look.SURBITON, BuildingStyle.ART_DECO), Map.entry(StationRecipe.Look.COVENTRY, BuildingStyle.MODERNIST));

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

	/**
	 * The looks the sweep covers in this run: all of them, or with {@code -Data.sweep.shard=k/n} every n-th look from the k-th, so CI
	 * spreads the sweep over n jobs.
	 */
	static Stream<StationRecipe.Look> sweptLooks() {
		final String shard = System.getProperty("ata.sweep.shard", "");
		if (shard.isEmpty()) {
			return Stream.of(StationRecipe.Look.values());
		}
		final String[] kn = shard.split("/");
		final int k = Integer.parseInt(kn[0]) - 1;
		final int n = Integer.parseInt(kn[1]);
		return Stream.of(StationRecipe.Look.values()).filter(look -> look.ordinal() % n == k);
	}

	/** One case per look; each look's running index starts where the one sweep over all looks had it, so the choices are unchanged. */
	@ParameterizedTest
	@MethodSource("sweptLooks")
	void everyChoiceGivesACleanBuildableStation(final StationRecipe.Look look) {
		final List<String> failures = new ArrayList<>();
		final List<StationRecipe.Look> mixes = new ArrayList<>();
		mixes.add(null);
		mixes.addAll(List.of(StationRecipe.Look.values()));
		int index = look.ordinal() * mixes.size() * 3 * StationRecipe.Roof.values().length;
		final int first = index;
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
						case CANOPIES, FLAT_CANOPIES, BUTTERFLY, GABLE -> RoofType.CANOPY;
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
		assertEquals(List.of(), failures.subList(0, Math.min(15, failures.size())), failures.size() + " of " + (index - first) + " for " + look);
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
	void typedWordsOverrideEveryControlLeftUntouched() {
		// the studio's starting state: a typed description with nothing pinned decides everything itself
		final StationRecipe recipe = StationRecipe.defaults().withLook(StationRecipe.Look.AUTO);
		final ParseResult result = StationDescriptionParser.parse(recipe.text("8 tracks 200 long terminus", Set.of()));
		assertEquals(8, result.spec().trackCount(), "the words set the track count");
		assertEquals(200, result.spec().platformLength(), "the words set the length");
		assertTrue(result.spec().terminus(), "the words set the type");
		// a terminus always keeps its head building, so the building switch is checked on a through station
		final ParseResult off = StationDescriptionParser.parse(recipe.text("3 tracks side platforms 80 long station, no station building", Set.of()));
		assertFalse(off.spec().hasBuilding(), "the words turn the building off");
	}

	@Test
	void onlyThePinnedControlsGoInFrontOfTheWords() {
		final StationRecipe recipe = StationRecipe.defaults().withLook(StationRecipe.Look.AUTO).withTracks(2).withLength(64);
		final String text = recipe.text("8 tracks 200 long terminus", Set.of("mode"));
		assertEquals("station, 8 tracks 200 long terminus", text);
		assertEquals(8, StationDescriptionParser.parse(text).spec().trackCount(), "untouched controls leave the words alone");
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
