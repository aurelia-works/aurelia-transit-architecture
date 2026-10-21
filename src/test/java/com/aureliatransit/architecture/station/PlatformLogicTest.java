package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro and Jah, 10-20: "some of the platform logic doesn't make sense", "mainly it's geography". */
class PlatformLogicTest {

	private static StationSpec spec(String text) {
		return StationDescriptionParser.parse(text).spec();
	}

	/** Platforms facing each other across the tracks get a way over, unless asked otherwise or a halt. */
	@Test
	void facingPlatformsAreBridged() {
		assertTrue(spec("2 track station, side platforms, 96 long").footbridge());
		assertTrue(spec("4 track station, side platforms, 96 long").footbridge());
		assertFalse(spec("2 track station, side platforms, 96 long, no footbridge").footbridge());
		assertFalse(spec("2 track station, side platforms, no roof").footbridge(), "a halt keeps its open platforms");
		assertFalse(spec("2 track station, side platforms, 96 long, underpass").footbridge(), "the underpass is the way across");
		assertFalse(spec("1 track station, 48 long").footbridge(), "one platform has nothing to cross to");
	}

	/** An island with a footbridge ends in a railing, not a ramp down onto the track bed; without one its ramps stay. */
	@Test
	void bridgedIslandsDoNotRampOntoTheTracks() {
		final StationPlan bridged = StationPlanner.plan(spec("2 track station, island platform, station building, 96 long"));
		final TrackLayout.PlatformBand island = bridged.layout().platforms().get(0);
		assertTrue(bridged.blocks().stream().noneMatch(b -> (b.role() == Role.RAMP_LOWER || b.role() == Role.RAMP_UPPER)
				&& b.u() >= island.uMin() && b.u() <= island.uMax() && (b.v() < island.vFrom() || b.v() > island.vTo())));
		assertTrue(bridged.blocks().stream().anyMatch(b -> b.role() == Role.RAILING && b.v() == island.vFrom() && b.u() == island.uMin() + 1));
		assertTrue(StationAudit.audit(bridged).buildable());
		final StationPlan open = StationPlanner.plan(spec("2 track station, island platform, 96 long, no footbridge"));
		assertTrue(open.blocks().stream().anyMatch(b -> b.role() == Role.RAMP_LOWER), "with no footbridge the ramps are the way off");
		// side platforms on the edges keep their ramps to the ground beside the station
		final StationPlan side = StationPlanner.plan(spec("2 track station, side platforms, 96 long"));
		for (final TrackLayout.PlatformBand band : side.layout().platforms()) {
			for (final boolean low : new boolean[]{true, false}) {
				assertTrue(side.blocks().stream().anyMatch(b -> b.role() == Role.RAMP_LOWER && b.u() >= band.uMin() && b.u() <= band.uMax()
						&& (low ? b.v() < band.vFrom() : b.v() > band.vTo())), "platform " + band.number() + (low ? " near" : " far") + " end ramps down");
			}
		}
	}

	/** The presets are laid out like the stations they are after: through stations are not built as termini. */
	@Test
	void presetsFollowTheRealStations() {
		final Map<String, Boolean> terminus = Map.ofEntries(Map.entry("amsterdam", false), Map.entry("hamburg_hbf", false), Map.entry("munich", false),
				Map.entry("tokyo", false), Map.entry("berlin", false), Map.entry("beijing", false), Map.entry("kuala_lumpur", false),
				Map.entry("st_pancras", true), Map.entry("antwerp", true), Map.entry("frankfurt", true), Map.entry("grand_central", true),
				Map.entry("zurich", true), Map.entry("paris", true), Map.entry("milan", true), Map.entry("hong_kong", true),
				Map.entry("bangkok", true), Map.entry("mumbai", true));
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			final Boolean expected = terminus.get(preset.key());
			if (expected != null) {
				assertEquals(expected, spec(preset.description()).terminus(), preset.key());
			}
		}
		assertTrue(spec(preset("amsterdam")).trackCount() >= 8, "Amsterdam Centraal is a big station, not a two-track halt");
	}

	private static String preset(String key) {
		return StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().description();
	}

	/** "tram stop" on its own builds a tram stop: open low platforms by street track with shelters and stop signs. */
	@Test
	void aTramStopStandsOnItsOwn() {
		final ParseResult parse = StationDescriptionParser.parse("2 track tram stop");
		assertEquals(List.of(), parse.warnings());
		final StationSpec spec = parse.spec();
		assertTrue(spec.tram());
		assertFalse(spec.hasBuilding());
		assertFalse(spec.footbridge());
		assertEquals(StationDescriptionParser.TRAM_LENGTH, spec.platformLength());
		final StationPlan plan = StationPlanner.plan(spec);
		for (final TrackLayout.Track track : plan.layout().tracks()) {
			assertTrue(plan.blocks().stream().anyMatch(b -> b.u() == track.u() && b.y() == -1 && b.blockId().equals(Planning.TRAM_TRACK)), "street track under track " + track.number());
		}
		assertEquals(2 * plan.layout().platforms().size(), plan.blocks().stream().filter(b -> b.blockId().equals(Planning.TRAM_STOP_SIGN)).count());
		assertTrue(StationAudit.audit(plan).buildable());
		// an island stop is reached across the street, which is fine for trams
		final StationPlan island = StationPlanner.plan(spec("tram stop, island platform"));
		assertTrue(StationAudit.audit(island).checks().stream().noneMatch(c -> c.status() != StationAudit.Status.PASS), StationAudit.audit(island).checks().toString());
		// words still win: a longer, named stop
		assertEquals(60, spec("2 track tram stop, 60 long, named Rozengracht").platformLength());
	}
}
