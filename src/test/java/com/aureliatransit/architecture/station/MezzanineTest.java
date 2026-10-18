package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3: a mezzanine, the footbridge widened into an upper concourse over the tracks. */
class MezzanineTest {

	static final String[] WORDS = {"4 tracks island, 96 long, station building, mezzanine", "3 tracks, 80 long, city class, upper concourse",
			"2 tracks side platforms, 64 long, mezzanine", "6 tracks island, 120 long, grand class, multi-level",
			"4 tracks island, 160 long, station building, mezzanine, escalators", "2 tracks island, 72 long, mezzanine, no roof"};

	static StationPlan plan(String words) {
		final ParseResult parsed = StationDescriptionParser.parse(words);
		final StationPlan plan = StationPlanner.plan(parsed.spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	@Test
	void theFootbridgeBecomesAWideFurnishedConcourse() {
		for (final String words : WORDS) {
			final StationPlan plan = plan(words);
			assertTrue(plan.spec().mezzanine() && plan.spec().footbridge(), words);
			final StationLayout site = StationLayout.of(plan.spec(), plan.layout());
			assertEquals(StationGeometry.MEZZANINE_WIDTH, site.bridgeWidth(), words);
			final int vB = site.bridgeV();
			final int half = StationGeometry.MEZZANINE_WIDTH / 2;
			// the deck (or an escalator head in its wall row) spans the full width at every column past the building
			final List<PlannedBlock> deck = plan.blocks().stream().filter(b -> b.y() == StationGeometry.UPPER_FLOOR_Y).toList();
			final int from = site.building() ? site.platformWall(plan.layout()) + 1 : site.deckFrom();
			final Map<Integer, Long> rows = deck.stream().filter(b -> Math.abs(b.v() - vB) <= half + 1 && b.u() >= from)
					.collect(Collectors.groupingBy(PlannedBlock::u, Collectors.counting()));
			assertTrue(rows.values().stream().allMatch(n -> n == StationGeometry.MEZZANINE_WIDTH + 2), words + ": " + rows);
			// seats and screens on the deck
			final long benches = plan.blocks().stream().filter(b -> b.role() == Role.BENCH && b.y() == StationGeometry.UPPER_WALK_Y
					&& Math.abs(b.v() - vB) <= half).count();
			// a short deck is all stair and lift openings; a long one has seats
			assertTrue(benches >= 2 || site.deckTo() - from < 16, words + ": benches " + benches);
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":hanging_platform_pids") && b.v() == vB
					&& b.y() == StationGeometry.BUILDING_ROOF_Y - 1), words + ": a departure screen");
			// piers under the deck's edges as well as its centre line
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.BRIDGE_PIER && Math.abs(b.v() - vB) == half), words + ": edge piers");
		}
	}

	@Test
	void aPlainFootbridgeStaysNarrowAndATerminusGetsNone() {
		final StationPlan plain = plan("4 tracks island, 96 long, station building, footbridge");
		assertFalse(plain.spec().mezzanine());
		assertEquals(StationGeometry.BRIDGE_WIDTH, StationLayout.of(plain.spec(), plain.layout()).bridgeWidth());
		final ParseResult terminus = StationDescriptionParser.parse("4 tracks island, 96 long, terminus, mezzanine");
		assertFalse(terminus.spec().mezzanine());
		assertTrue(terminus.warnings().stream().anyMatch(w -> w.contains("mezzanine")), terminus.warnings().toString());
		final ParseResult one = StationDescriptionParser.parse("1 track, 48 long, mezzanine");
		assertFalse(one.spec().mezzanine());
		assertFalse(one.spec().footbridge());
	}
}
