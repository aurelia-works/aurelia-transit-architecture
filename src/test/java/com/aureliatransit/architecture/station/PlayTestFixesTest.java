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

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static List<StationPlan> everyPlan() {
		final List<StationPlan> plans = new ArrayList<>();
		plans.add(plan(JAH));
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
	void theWaitingAreaHasNoPartition() {
		final StationPlan plan = plan(JAH);
		assertEquals(0, plan.blocks().stream().filter(b -> b.blockId().equals(StationPlanner.WAITING_ROOM_GLASS)
				&& b.y() >= StationGeometry.UPPER_WALK_Y).count(), "the glass wall across the waiting bay");
		assertTrue(plan.blocks().stream().filter(b -> b.role() == Role.BENCH && b.y() == StationGeometry.UPPER_WALK_Y).count() >= 8,
				"the waiting area keeps its benches");
	}

	@Test
	void noLoneBenchOnTheBuildingsGroundFloor() {
		final StationPlan plan = plan(JAH);
		// Jah's stray seat stood at u 20, v 92, beside the building's lift and the gates
		assertFalse(plan.blocks().stream().anyMatch(b -> b.role() == Role.BENCH && b.y() == StationGeometry.WALK_Y
				&& b.u() > StationLayout.streetWall() && b.u() < 23 && b.v() > 80 && b.v() < 120 && b.u() != 3),
				"a single bench inside the building");
	}

	@Test
	void theFacadeNameIsLetteringNotAPlatformBoard() {
		final StationPlan plan = plan(JAH);
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
		final StationPlan plan = plan(JAH);
		final List<PlannedBlock> pylons = plan.blocks().stream().filter(b -> b.blockId().equals(PlanUnderground.PYLON) && b.y() == 1).toList();
		assertEquals(1, pylons.size(), "one pylon at the side entrance");
		final PlannedBlock pylon = pylons.get(0);
		assertEquals(LocalDir.MINUS_U, pylon.front(), "faces the street, away from the wall");
		// the canopy's end is the next cell along the wall
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.BUILDING_ROOF && b.u() == pylon.u()
				&& Math.abs(b.v() - pylon.v()) == 1 && b.y() == PlanFitOut.CANOPY_Y), "the canopy ends beside it: " + pylon);
	}
}
