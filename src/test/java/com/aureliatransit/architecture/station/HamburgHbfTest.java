package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hamburg Hauptbahnhof, the second real station (Jah, 2026-10-20): a train shed over five islands, built from its facts. */
class HamburgHbfTest {

	static final RealStation HAMBURG = RealStation.HAMBURG_HBF;

	@Test
	void theStationsFactsInBlocks() {
		assertEquals(RealStation.Kind.TRAIN_SHED, HAMBURG.kind());
		assertTrue(HAMBURG.trainShed());
		assertTrue(!HAMBURG.hallOverTracks() && RealStation.UTRECHT_CENTRAAL.hallOverTracks());
		assertEquals(Operator.DB, HAMBURG.operator());
		assertEquals(10, HAMBURG.tracks());
		// 150 m of shed, 37 m high, 45 m towers, at 1.5 blocks to the metre
		assertEquals(225, HAMBURG.hallDepthBlocks());
		assertEquals(56, HAMBURG.roofTopY());
		assertEquals(68, HAMBURG.towerTopY());
		assertEquals(73.0 / 114, HAMBURG.naveShare(), 1e-9);
		assertEquals(1, RealStation.UTRECHT_CENTRAAL.naveShare(), 1e-9);
		assertEquals(0, RealStation.UTRECHT_CENTRAAL.towerTopY());
	}

	@Test
	void itsNamesAskForIt() {
		assertEquals(HAMBURG, RealStation.of("Hamburg Hauptbahnhof"));
		assertEquals(HAMBURG, RealStation.of("hamburg hbf"));
		assertEquals(RealStation.NONE, StationDescriptionParser.parse("named Hamburg Hauptbahnhof").spec().real());
		assertEquals(RealStation.NONE, StationDescriptionParser.parse("hamburg station").spec().real());
	}

	@Test
	void theWordsAloneGiveTheRealStation() {
		final StationSpec spec = StationDescriptionParser.parse("hamburg hauptbahnhof").spec();
		assertEquals(HAMBURG, spec.real());
		assertEquals(10, spec.trackCount());
		assertTrue(spec.platforms().stream().allMatch(p -> p.kind() == PlatformSpec.Kind.ISLAND));
		assertEquals(225, spec.platformLength());
		assertEquals(RoofType.HALL, spec.roof());
		assertEquals(ShedShape.ARCH, spec.shed());
		assertEquals(StationScale.GRAND, spec.scale());
		assertTrue(spec.sideBuilding() && spec.footbridge() && spec.escalators() && !spec.terminus());
		assertEquals(Operator.DB, spec.operator());
		assertEquals("Hamburg Hauptbahnhof", spec.name());
		assertEquals(HAMBURG, StationDescriptionParser.parse("hamburg hbf, seed 4").spec().real());
	}

	@Test
	void wordsItIsNotAreDroppedWithAWarning() {
		for (final String extra : List.of("terminus", "no building", "side platforms", "canopies", "pointed train shed", "mezzanine", "amsterdam look")) {
			final ParseResult parsed = StationDescriptionParser.parse("hamburg hauptbahnhof, " + extra);
			assertEquals(HAMBURG, parsed.spec().real(), extra);
			assertTrue(!parsed.warnings().isEmpty(), "no warning for " + extra);
			assertEquals(RoofType.HALL, parsed.spec().roof(), extra);
			assertTrue(parsed.spec().sideBuilding() && !parsed.spec().terminus() && !parsed.spec().mezzanine(), extra);
		}
	}

	@Test
	void aClassOrEraLeavesItAsItIs() {
		// Studio rolls an era with every seed; "modern era" once gave Hamburg a red brick building and Dutch platforms
		final StationSpec plain = StationDescriptionParser.parse("hamburg hauptbahnhof").spec();
		for (final String extra : List.of("modern era", "interwar era", "postwar era", "halt class", "city class, modern era")) {
			final ParseResult parsed = StationDescriptionParser.parse("hamburg hauptbahnhof, " + extra);
			assertEquals(List.of(), parsed.warnings(), extra);
			assertEquals(plain.building(), parsed.spec().building(), extra);
			assertEquals(plain.style(), parsed.spec().style(), extra);
			assertEquals(plain.trackCount(), parsed.spec().trackCount(), extra);
			assertEquals(plain.platformLength(), parsed.spec().platformLength(), extra);
		}
		assertEquals(RealStation.UTRECHT_CENTRAAL, StationDescriptionParser.parse("utrecht centraal, modern era").spec().real());
		assertEquals(List.of(), StationDescriptionParser.parse("utrecht centraal, halt class, postwar era").warnings());
	}

	@Test
	void itPlansCleanlyWithANaveOverItsAislesAndARealTower() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse("hamburg hauptbahnhof").spec());
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
		// the roof's top across the shed: the nave reaches the real height, the aisles beside it stay well below it
		final Map<Integer, Integer> top = new TreeMap<>();
		plan.blocks().stream().filter(b -> b.role() == Role.ROOF || b.role() == Role.ROOF_EDGE).forEach(b -> top.merge(b.u(), b.y(), Math::max));
		final int peak = top.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
		assertTrue(peak >= HAMBURG.roofTopY() - 1 && peak <= HAMBURG.roofTopY() + 1, "nave top " + peak);
		final List<Integer> us = List.copyOf(top.keySet());
		final int span = us.get(us.size() - 1) - us.get(0);
		final int aisle = (int) Math.round(span * (1 - HAMBURG.naveShare()) / 2);
		for (final int u : List.of(us.get(0) + aisle / 2, us.get(us.size() - 1) - aisle / 2)) {
			assertTrue(top.get(u) < peak - 10, "aisle at u " + u + " rises to " + top.get(u));
		}
		// the clock tower stands as high as the real ones
		final int highest = plan.blocks().stream().mapToInt(PlannedBlock::y).max().orElseThrow();
		assertTrue(highest >= HAMBURG.towerTopY(), "tallest point " + highest);
		// and reads as a tower, not a chimney: windows up its shaft, not only at its foot
		final List<Integer> windows = plan.blocks().stream().filter(b -> b.role() == Role.WINDOW && b.y() > HAMBURG.roofTopY() - 20)
				.map(PlannedBlock::y).distinct().toList();
		assertTrue(windows.size() >= 9, "window rows high up the tower " + windows);
	}

	@Test
	void theNaveSpringsFromTheAislesCrowns() {
		final int[] rise = new int[100];
		PlanRoofs.naveAndAisles(rise, 100, 0.64, 30);
		// lowest at the eaves, a crown in each aisle, the nave's crown highest in the middle
		assertEquals(0, rise[0]);
		assertEquals(0, rise[99]);
		assertEquals(30, rise[49] == 30 ? 30 : rise[50]);
		final int aisleCrown = rise[9];
		assertTrue(aisleCrown > 0 && aisleCrown < 30);
		// where the nave starts it already stands at the aisle's crown height, over a clerestory step
		assertTrue(rise[18] >= aisleCrown, "nave springing " + rise[18]);
		// too narrow for aisles: one vault
		final int[] narrow = new int[10];
		PlanRoofs.naveAndAisles(narrow, 10, 0.64, 30);
		assertTrue(narrow[4] > 0 && narrow[0] == 0);
	}
}
