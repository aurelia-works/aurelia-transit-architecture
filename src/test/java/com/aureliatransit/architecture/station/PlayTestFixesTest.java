package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixes from Jah's in-game play-test of 10-22, on his own Utrecht plan and on every preset. */
class PlayTestFixesTest {

	/** Jah's plan, word for word. */
	static final String JAH = "10 track utrecht station, 200 long, named hi, stairs both ways andesite track bed, seed tmptnb";
	/** The station Jah played on: his words as they read before "utrecht station" meant the real hall, the city's look beside the tracks. */
	static final String PLAYED = JAH.replace("utrecht station", "utrecht");

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static List<StationPlan> everyPlan() {
		final List<StationPlan> plans = new ArrayList<>();
		plans.add(plan(JAH));
		plans.add(plan(PLAYED));
		plans.add(plan("four track utrecht terminus, two island platforms, 96 long, city building"));
		plans.add(plan("two track station with a station building, side platforms, 96 long"));
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			plans.add(plan(preset.description()));
		}
		return plans;
	}

	@Test
	void noMtrTactileMapOrNameBoardAnywhere() {
		for (final StationPlan plan : everyPlan()) {
			assertFalse(plan.blocks().stream().anyMatch(b -> b.blockId().equals(StylePalette.MTR_TACTILE_MAP)),
					plan.spec().name() + ": the tactile map plays music to anyone near it");
			assertFalse(plan.blocks().stream().anyMatch(b -> b.blockId().equals(StylePalette.MTR_STATION_NAME)),
					plan.spec().name() + ": MTR's entrance name board");
		}
	}

	@Test
	void kiosksAgainstAWallSitFlushToIt() {
		int flush = 0;
		for (final StationPlan plan : everyPlan()) {
			for (final PlannedBlock kiosk : plan.blocks()) {
				if (!kiosk.blockId().equals(PlanState.KIOSK)) {
					continue;
				}
				final int du = kiosk.front() == LocalDir.PLUS_U ? 1 : kiosk.front() == LocalDir.MINUS_U ? -1 : 0;
				final int dv = kiosk.front() == LocalDir.PLUS_V ? 1 : kiosk.front() == LocalDir.MINUS_V ? -1 : 0;
				final boolean wall = plan.blocks().stream().anyMatch(b -> b.u() == kiosk.u() - du && b.v() == kiosk.v() - dv
						&& b.y() == kiosk.y() && (b.role() == Role.WALL || b.role() == Role.WINDOW || b.role() == Role.COLUMN));
				final boolean pushed = kiosk.state() != null && kiosk.state().contains("offset=flush_back");
				if (wall) {
					assertTrue(pushed, "kiosk against a wall stands off it: " + kiosk);
					flush++;
				}
			}
		}
		assertTrue(flush > 0, "no kiosk against a wall to check");
	}

	@Test
	void theWaitingAreaHasNoPartitionAndOneSeatingLayout() {
		final StationPlan plan = plan(PLAYED);
		assertEquals(0, plan.blocks().stream().filter(b -> b.blockId().equals(StationPlanner.WAITING_ROOM_GLASS)
				&& b.y() >= StationGeometry.UPPER_WALK_Y).count(), "the glass wall across the waiting bay");
		final List<PlannedBlock> seats = plan.blocks().stream().filter(b -> b.role() == Role.BENCH && b.y() == StationGeometry.UPPER_WALK_Y
				&& b.u() > StationLayout.streetWall() && b.u() < 23 && b.v() > 85 && b.v() < 115).toList();
		assertFalse(seats.isEmpty(), "the upper floor has seats");
		// every seat is in an island of back-to-back benches, none along the side walls as well
		for (final PlannedBlock seat : seats) {
			final boolean backToBack = plan.blocks().stream().anyMatch(b -> b.role() == Role.BENCH && b.y() == seat.y() && b.u() == seat.u()
					&& Math.abs(b.v() - seat.v()) == 1 && b.front() != seat.front());
			assertTrue(backToBack, "a seat outside the islands: " + seat);
		}
	}

	@Test
	void noLoneBenchOnTheBuildingsGroundFloor() {
		final StationPlan plan = plan(PLAYED);
		// Jah's stray seat stood at u 20, v 92, beside the building's lift and the gates: seats now come only in rows
		final List<PlannedBlock> seats = plan.blocks().stream().filter(b -> b.role() == Role.BENCH && b.y() == StationGeometry.WALK_Y
				&& b.u() > StationLayout.streetWall() && b.u() < 23 && b.v() > 85 && b.v() < 115).toList();
		for (final PlannedBlock seat : seats) {
			final long row = seats.stream().filter(b -> b.u() == seat.u() && Math.abs(b.v() - seat.v()) < PlanFurniture.SEAT_ROW).count();
			assertTrue(row >= PlanFurniture.SEAT_ROW - 1, "a seat on its own: " + seat);
		}
		assertFalse(seats.stream().anyMatch(b -> b.u() == 20 && b.v() == 92), "the stray seat by the lift");
	}

	@Test
	void theFacadeNameIsLetteringNotAPlatformBoard() {
		final StationPlan plan = plan(PLAYED);
		final List<PlannedBlock> over = plan.blocks().stream().filter(b -> b.y() == PlanFitOut.CANOPY_Y + 1
				&& b.u() < StationLayout.streetWall() && b.role() == Role.FIXTURE).toList();
		assertFalse(over.isEmpty(), "a name over the entrance canopy");
		for (final PlannedBlock block : over) {
			assertEquals(PlanFitOut.LETTERING, block.blockId(), "over the canopy: " + block);
			assertEquals("hi", block.text());
		}
	}

	@Test
	void everySignFontHasItsFontFileAndLicence() throws java.io.IOException {
		final java.nio.file.Path fonts = java.nio.file.Path.of("src/main/resources/assets/aurelia_transit_architecture/font");
		for (final com.aureliatransit.architecture.text.SignStyle style : com.aureliatransit.architecture.text.SignStyle.values()) {
			if (style.font() == null) {
				continue;
			}
			final String name = style.font().substring(style.font().indexOf(':') + 1);
			final String json = java.nio.file.Files.readString(fonts.resolve(name + ".json"));
			final java.util.regex.Matcher file = java.util.regex.Pattern.compile("\"file\": \"aurelia_transit_architecture:([a-z_]+)\\.ttf\"").matcher(json);
			assertTrue(file.find(), style + ": no ttf provider in " + name + ".json");
			assertTrue(java.nio.file.Files.exists(fonts.resolve(file.group(1) + ".ttf")), style + ": " + file.group(1) + ".ttf");
			assertTrue(java.nio.file.Files.exists(fonts.resolve(file.group(1) + ".LICENSE.txt")), style + ": the font's licence");
		}
	}

	@Test
	void theEntrancePylonStandsBesideTheCanopyFacingTheStreet() {
		final StationPlan plan = plan(PLAYED);
		final List<PlannedBlock> pylons = plan.blocks().stream().filter(b -> b.blockId().equals(PlanUnderground.PYLON) && b.y() == 1).toList();
		assertEquals(1, pylons.size(), "one pylon at the side entrance");
		final PlannedBlock pylon = pylons.get(0);
		assertEquals(LocalDir.MINUS_U, pylon.front(), "faces the street, away from the wall");
		// the canopy's end is the next cell along the wall
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.BUILDING_ROOF && b.u() == pylon.u()
				&& Math.abs(b.v() - pylon.v()) == 1 && b.y() == PlanFitOut.CANOPY_Y), "the canopy ends beside it: " + pylon);
	}

	@Test
	void utrechtStationIsTheRealHallUnlessTheWordsSayOtherwise() {
		// "It does not look like Utrecht Central at all": his words gave the city's look on a station of its own
		final StationSpec jah = StationDescriptionParser.parse(JAH).spec();
		assertEquals(RealStation.UTRECHT_CENTRAAL, jah.real());
		assertEquals("hi", jah.name());
		assertEquals(10, jah.trackCount());
		assertTrue(StationDescriptionParser.parse(JAH).warnings().isEmpty(), StationDescriptionParser.parse(JAH).warnings().toString());
		assertEquals(RealStation.UTRECHT_CENTRAAL, StationDescriptionParser.parse("utrecht central").spec().real());
		assertEquals(RealStation.UTRECHT_CENTRAAL, StationDescriptionParser.parse("utrecht cs, 6 tracks").spec().real());
		// words of their own keep the look on a station of their own, with no warning about the hall
		for (final String own : List.of("utrecht", "utrecht style station", "4 tracks utrecht station building 64 long", "2 track utrecht station, 64 long",
				"utrecht station with canopies", "metro style utrecht station", "four track utrecht terminus", "3 track utrecht station",
				"utrecht station, side platforms", "hamburg station")) {
			final ParseResult parsed = StationDescriptionParser.parse(own);
			assertEquals(RealStation.NONE, parsed.spec().real(), own);
			assertFalse(parsed.warnings().stream().anyMatch(w -> w.contains("Centraal")), own + ": " + parsed.warnings());
		}
	}

	@Test
	void theStudiosUtrechtAndHamburgCardsBuildTheRealStations() {
		// Jah 10-25 ("where's the wave?"): the city card builds the real station; a terminus or a mix keeps the city's look
		for (final int tracks : new int[]{2, 4, 6}) {
			final StationRecipe card = new StationRecipe(StationRecipe.Look.UTRECHT, null, false, tracks, StationRecipe.Platforms.AUTO, 96,
					StationRecipe.Roof.AUTO, true, "", StationRecipe.Size.AUTO, false);
			assertEquals(RealStation.UTRECHT_CENTRAAL, StationDescriptionParser.parse(card.description()).spec().real(), card.description());
			assertEquals(RealStation.UTRECHT_CENTRAAL, StationDescriptionParser.parse(card.text("named hi", java.util.Set.of("look"))).spec().real());
			final StationRecipe hamburg = card.withLook(StationRecipe.Look.HAMBURG);
			assertEquals(RealStation.HAMBURG_HBF, StationDescriptionParser.parse(hamburg.description()).spec().real(), hamburg.description());
			assertEquals(RealStation.NONE, StationDescriptionParser.parse(card.withTerminus(true).description()).spec().real());
			assertEquals(RealStation.NONE, StationDescriptionParser.parse(card.withMix(StationRecipe.Look.AMSTERDAM).description()).spec().real());
		}
	}
}
