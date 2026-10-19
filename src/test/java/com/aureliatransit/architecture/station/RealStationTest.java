package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A real station built as it is: Utrecht Centraal's hall over the tracks, its platforms and its operator. */
class RealStationTest {

	static final RealStation UTRECHT = RealStation.UTRECHT_CENTRAAL;

	static StationPlan plan(String words) {
		final ParseResult parsed = StationDescriptionParser.parse(words);
		final StationPlan plan = StationPlanner.plan(parsed.spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	@Test
	void theStationsFactsInBlocks() {
		assertEquals("Utrecht Centraal", UTRECHT.label());
		assertEquals(16, UTRECHT.tracks());
		assertEquals(Operator.NS, UTRECHT.operator());
		assertEquals(UTRECHT.hallDepthBlocks() + 2 * RealStation.HALL_MARGIN, UTRECHT.fullLength());
		// the full hall on long platforms, odd so it has a centre row
		assertEquals(UTRECHT.hallDepthBlocks() - 1 + UTRECHT.hallDepthBlocks() % 2, UTRECHT.hallDepth(UTRECHT.fullLength()));
		assertEquals(UTRECHT.hallDepth(UTRECHT.fullLength()), UTRECHT.hallDepth(400));
		for (int length = 60; length <= 200; length++) {
			final int depth = UTRECHT.hallDepth(length);
			assertTrue(depth == 0 || depth % 2 == 1 && depth >= RealStation.MIN_HALL_DEPTH && depth <= length - 2 * RealStation.HALL_MARGIN,
					length + ": " + depth);
		}
		assertEquals(0, UTRECHT.hallDepth(RealStation.MIN_HALL_DEPTH + 2 * RealStation.HALL_MARGIN - 1));
		assertEquals(0, RealStation.NONE.hallDepth(400));
	}

	@Test
	void theRoofHasThreeWavesTheMiddleOneHighest() {
		final int eaves = 17;
		final int[] roof = UTRECHT.roofProfile(166, eaves);
		assertEquals(167, roof.length);
		assertEquals(eaves, roof[0]);
		assertEquals(eaves, roof[166]);
		int top = 0;
		for (final int y : roof) {
			assertTrue(y >= eaves);
			top = Math.max(top, y);
		}
		assertEquals(UTRECHT.roofTopY(), top);
		assertEquals(UTRECHT.roofTopY(), roof[83], "the middle wave crests over the middle of the tracks");
		// each side wave crests lower than the middle one, and dips back to the eaves before it
		final int sideCrest = roof[(int) Math.round(166 * (1 - RealStation.MIDDLE_WAVE) / 4)];
		assertTrue(sideCrest > eaves && sideCrest < top, "side crest " + sideCrest);
		final int valley = roof[(int) Math.round(166 * (1 - RealStation.MIDDLE_WAVE) / 2)];
		assertTrue(valley < sideCrest, "valley " + valley);
	}

	@Test
	void onlyTheWholeNameAsksForIt() {
		assertEquals(UTRECHT, RealStation.of("Utrecht Centraal "));
		assertNull(RealStation.of("utrecht"));
		assertNull(RealStation.of(""));
		assertNull(RealStation.of(null));
		final StationSpec named = StationDescriptionParser.parse("4 tracks island, 96 long, station named Utrecht Centraal").spec();
		assertEquals(RealStation.NONE, named.real(), "a station named after it is not it");
	}

	@Test
	void theWordsAloneGiveTheRealStation() {
		final ParseResult parsed = StationDescriptionParser.parse("utrecht centraal");
		final StationSpec spec = parsed.spec();
		assertEquals(UTRECHT, spec.real());
		assertEquals(List.of(), parsed.warnings());
		assertEquals(UTRECHT.tracks(), spec.trackCount());
		assertTrue(spec.platforms().stream().allMatch(p -> p.kind() == PlatformSpec.Kind.ISLAND), spec.platforms().toString());
		assertEquals(StationDescriptionParser.REAL_LENGTH, spec.platformLength());
		assertEquals(BuildingStyle.NONE, spec.building());
		assertTrue(spec.footbridge() && spec.escalators());
		assertFalse(spec.terminus() || spec.mezzanine());
		assertEquals(Operator.NS, spec.operator());
		assertEquals("Utrecht Centraal", spec.name());
	}

	@Test
	void wordsItIsNotAreDroppedWithAWarning() {
		final String[] contradictions = {"terminus", "mezzanine", "side platforms", "station building", "train shed"};
		for (final String extra : contradictions) {
			final ParseResult parsed = StationDescriptionParser.parse("utrecht centraal, " + extra);
			assertEquals(UTRECHT, parsed.spec().real(), extra);
			assertFalse(parsed.warnings().isEmpty(), extra);
			assertFalse(parsed.spec().terminus() || parsed.spec().mezzanine(), extra);
			assertEquals(BuildingStyle.NONE, parsed.spec().building(), extra);
		}
	}

	@Test
	void shortPlatformsGetAShallowerHallOrNone() {
		final ParseResult shallow = StationDescriptionParser.parse("utrecht centraal, 100 long");
		assertEquals(UTRECHT, shallow.spec().real());
		assertTrue(shallow.warnings().stream().anyMatch(w -> w.contains("too short")), shallow.warnings().toString());
		final ParseResult tooShort = StationDescriptionParser.parse("utrecht centraal, 60 long");
		assertEquals(RealStation.NONE, tooShort.spec().real());
		assertTrue(tooShort.warnings().stream().anyMatch(w -> w.contains("needs platforms")), tooShort.warnings().toString());
	}

	@Test
	void aTrackCountMeansIslandsAndAnOddOneFallsBack() {
		final StationSpec two = StationDescriptionParser.parse("utrecht centraal, 2 tracks, 80 long").spec();
		assertEquals(UTRECHT, two.real());
		assertEquals(PlatformLayout.ISLAND, two.platformLayout());
		// an odd track leaves a side platform where the street meets the hall
		final ParseResult three = StationDescriptionParser.parse("utrecht centraal, 3 tracks, 100 long");
		assertEquals(RealStation.NONE, three.spec().real());
		assertTrue(three.warnings().stream().anyMatch(w -> w.contains("island platforms only")), three.warnings().toString());
		plan("utrecht centraal, 3 tracks, 100 long");
		assertThrows(IllegalArgumentException.class, () -> StationSpec.builder().trackCount(3).platformLayout(PlatformLayout.ISLAND)
				.footbridge(true).real(UTRECHT).build());
	}

	@Test
	void theHallIsClosedFurnishedAndHeldUp() {
		final StationPlan plan = plan("utrecht centraal");
		final StationLayout site = StationLayout.of(plan.spec(), plan.layout());
		assertEquals(2, site.streets().size(), "a street each side, as the Stationsplein and the Jaarbeursplein");
		final int middle = plan.layout().width() / 2;
		final List<PlannedBlock> gates = plan.blocks().stream().filter(b -> b.role() == Role.GATE).toList();
		assertTrue(gates.stream().anyMatch(b -> b.u() < middle) && gates.stream().anyMatch(b -> b.u() > middle), "a gate line each side");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(PlanBridges.HALL_GLASS)), "glass walls");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(PlanBridges.HALL_CROWN)), "tree columns");
		assertEquals(UTRECHT.roofTopY(), plan.blocks().stream().filter(b -> b.blockId().equals(PlanBridges.HALL_ROOF))
				.mapToInt(PlannedBlock::y).max().orElse(0));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.NAME_SIGN), "name signs in the hall");
		// glass beside every way down under the hall, so nobody steps off a flight's side
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.WINDOW && b.y() > StationGeometry.WALK_Y
				&& b.y() < StationGeometry.UPPER_FLOOR_Y && Math.abs(b.v() - site.bridgeV()) <= site.bridgeWidth() / 2), "flight glass");
	}

	@Test
	void fewerPlatformsAndShorterOnesStillBuild() {
		for (final String words : new String[] {"utrecht centraal, 4 tracks, 90 long", "utrecht centraal, 2 tracks, 80 long"}) {
			final StationPlan plan = plan(words);
			assertEquals(UTRECHT, plan.spec().real(), words);
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.GATE), words);
			everyWayLeadsSomewhere(plan, words);
		}
	}

	/**
	 * Jah's walk-through (2026-10-19): the first pilot had a strip at track level beside each outer track, under the hall's
	 * floor, with a lift and a stair up from it, which led nowhere. The squares are at the hall's level now, with a broad stair
	 * and a lift up from outside; nothing under the floor is walkable but the platforms and the lift lobbies.
	 */
	@Test
	void theHallIsEnteredFromItsSquaresAndNothingLeadsNowhere() {
		final StationPlan plan = plan("utrecht centraal");
		everyWayLeadsSomewhere(plan, "utrecht centraal");
		final StationLayout site = StationLayout.of(plan.spec(), plan.layout());
		// a broad stair at each end, outside the hall's floor, and no pier standing out there on the square
		for (final boolean west : new boolean[] {true, false}) {
			final long steps = plan.blocks().stream().filter(b -> b.role() == Role.STAIR && (west ? b.u() < site.deckFrom() : b.u() > site.deckTo()))
					.count();
			assertEquals((long) StationGeometry.STAIR_STEPS * (2 * StationLayout.PLATEAU_STAIR_HALF + 1), steps, west ? "west stair" : "east stair");
		}
		assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.BRIDGE_PIER && (b.u() < site.deckFrom() || b.u() > site.deckTo())),
				"no pier on the squares");
		// no lift or flight down to the ground beside the tracks
		for (final TrackLayout.PlatformBand street : site.streets()) {
			assertTrue(site.flights(street).isEmpty());
			assertNull(site.platformLift(street, plan.layout()));
		}
		// at track level under the floor, only the platforms and the lift lobbies off the squares are walkable
		final PassengerWalk walk = new PassengerWalk(plan);
		final int wall = StationGeometry.bridgeWallOffset(site.bridgeWidth());
		for (int u = site.deckFrom(); u <= site.deckTo(); u++) {
			final int column = u;
			if (plan.layout().platforms().stream().anyMatch(band -> column >= band.uMin() && column <= band.uMax())) {
				continue;
			}
			for (int v = site.bridgeV() - wall; v <= site.bridgeV() + wall; v++) {
				if (walk.reached(u, StationGeometry.WALK_Y, v)) {
					assertTrue(lobby(site, u, v), "walkable at track level under the hall at u=" + u + " v=" + v);
				}
			}
		}
	}

	/** True in the lobby before a plateau lift's lower doors, or its way in through the end wall's line. */
	private static boolean lobby(StationLayout site, int u, int v) {
		for (final TrackLayout.PlatformBand street : site.streets()) {
			final StationLayout.Lift lift = site.plateauLift(street);
			if (v == lift.doorV() - 1 && u >= lift.c0() - 1 && u <= lift.c0() + 2) {
				return true;
			}
		}
		return false;
	}

	static void everyWayLeadsSomewhere(StationPlan plan, String words) {
		final PassengerWalk walk = new PassengerWalk(plan);
		assertEquals(List.of(), walk.liftsToNowhere(), words);
		assertEquals(List.of(), walk.flightsToNowhere(), words);
		assertEquals(java.util.Map.of(), walk.unreachedPlatform(plan), words);
	}
}
