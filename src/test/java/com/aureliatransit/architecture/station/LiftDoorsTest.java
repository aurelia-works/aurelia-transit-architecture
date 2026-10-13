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

	private static boolean door(StationPlan plan, int u, int y, int v) {
		return plan.blocks().stream().anyMatch(b -> b.u() == u && b.y() == y && b.v() == v && b.blockId().equals(StylePalette.MTR_LIFT_DOOR));
	}
}
