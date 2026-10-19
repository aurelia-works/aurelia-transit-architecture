package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A platform lift opens into the corridor at the top, and at the platform both into the space under the bridge and onto the platform behind it. */
class LiftDoorsTest {

	private static final String[] TEXTS = {
			"4 tracks island, footbridge, named Halt",
			"two track station, side platforms, length 24, width 6, footbridge, named Halt",
			"platform 1: island; platform 2: island; platform 3: island; platform 4: island; platform 5: island; platform 6: island 13 wide; "
					+ "hub, 201 long, canopies, utrecht, named Halt",
			"6 tracks island 120 long, footbridge, hall roof, named Halt",
			"platform 1: side 5 wide; platform 2: side; platform 3: island 8 wide; platform 4: side 15 wide; platform 5: island 16 wide; "
					+ "platform 6: island 14 wide; 120 long, no building, named Halt",
			"4 tracks island, 64 long, with a building, footbridge, named Halt",
	};

	@Test
	void platformLiftsOpenBothWaysAtTheFootOnly() {
		for (final String text : TEXTS) {
			check(text);
		}
	}

	private static void check(String text) {
		final StationSpec spec = StationDescriptionParser.parse(text).spec();
		final StationPlan plan = StationPlanner.plan(spec);
		final StationLayout site = StationLayout.of(spec, plan.layout());
		int lifts = 0;
		for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
			final StationLayout.Lift lift = site.platformLift(band, plan.layout());
			if (lift == null) {
				continue;
			}
			lifts++;
			final int back = lift.doorV() + 3 * lift.out();
			for (int c = lift.c0(); c <= lift.c0() + 1; c++) {
				final int u = c;
				for (final int y : new int[]{StationGeometry.WALK_Y, StationGeometry.WALK_Y + 1}) {
					assertTrue(door(plan, u, y, back), "no back door at the platform: u=" + u + " y=" + y);
					assertTrue(door(plan, u, y, lift.doorV()), "no front door at the platform: u=" + u + " y=" + y);
				}
				assertFalse(door(plan, u, StationGeometry.UPPER_WALK_Y, back), "a back door at the footbridge: u=" + u);
				assertTrue(door(plan, u, StationGeometry.UPPER_WALK_Y, lift.doorV()), "no front door at the footbridge: u=" + u);
				// the platform in front of the back door is clear to walk out onto, three rows deep
				for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 2; y++) {
					for (int k = 1; k <= 3; k++) {
						final int yy = y;
						final int vv = back + k * lift.out();
						assertTrue(plan.blocks().stream().noneMatch(b -> b.u() == u && b.y() == yy && b.v() == vv),
								text + ": something in front of the back door at u=" + u + " y=" + y + " v=" + vv + ": "
										+ plan.blocks().stream().filter(b -> b.u() == u && b.y() == yy && b.v() == vv).toList());
					}
				}
			}
			// a call button beside the back door
			assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals(StylePalette.MTR_LIFT_BUTTONS) && b.y() == StationGeometry.WALK_Y + 1
					&& (b.v() == back || b.v() == back + lift.out())), "no call button at the back door");
		}
		assertTrue(lifts > 0, text + ": the station has platform lifts");
		assertEquals(List.of(), LayoutValidator.validate(plan), text);
	}

	/**
	 * Every call button stands on a landing with its plate against a corner post or a solid wall: never in the shaft's own
	 * wall (where it breaks the post and the car clips it), never against glass (where it hangs off the pane).
	 */
	@Test
	void callButtonsHangOnAPostOrWall() {
		for (final String text : TEXTS) {
			final StationSpec spec = StationDescriptionParser.parse(text).spec();
			final StationPlan plan = StationPlanner.plan(spec);
			final StylePalette.Building building = StylePalette.of(spec).building();
			int buttons = 0;
			for (final PlannedBlock button : plan.blocks()) {
				if (!button.blockId().equals(StylePalette.MTR_LIFT_BUTTONS)) {
					continue;
				}
				buttons++;
				final int du = button.front() == LocalDir.PLUS_U ? 1 : button.front() == LocalDir.MINUS_U ? -1 : 0;
				final int dv = button.front() == LocalDir.PLUS_V ? 1 : button.front() == LocalDir.MINUS_V ? -1 : 0;
				final PlannedBlock holder = plan.blocks().stream()
						.filter(b -> b.u() == button.u() + du && b.y() == button.y() && b.v() == button.v() + dv).findFirst().orElse(null);
				// a post is clad full width in the plinth block where a button is fixed to it, so the plate has a whole block behind
				assertTrue(holder != null && (holder.blockId().equals(building.pier()) || holder.blockId().equals(building.interiorWall())
								|| holder.blockId().equals(building.plinth())),
						text + ": call button at " + button.u() + " " + button.y() + " " + button.v() + " is held by " + holder);
			}
			assertTrue(buttons > 0, text + ": the station has call buttons");
		}
	}

	private static boolean door(StationPlan plan, int u, int y, int v) {
		return plan.blocks().stream().anyMatch(b -> b.u() == u && b.y() == y && b.v() == v && b.blockId().equals(StylePalette.MTR_LIFT_DOOR));
	}
}
