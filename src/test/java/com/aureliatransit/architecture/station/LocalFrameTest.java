package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalFrameTest {

	private static final TrackLayout LAYOUT = StationGeometry.layout(StationSpec.defaults());

	@Test
	void aWrenchClickPutsTheFootprintsFirstRowOnTheClickedColumnCentred() {
		for (final Heading facing : Heading.values()) {
			final LocalFrame frame = LocalFrame.onBlock(10, 64, -20, facing, LAYOUT);
			final int centre = LAYOUT.width() / 2;
			assertEquals(10, frame.x(centre, LAYOUT.vMin()), "x for " + facing);
			assertEquals(-20, frame.z(centre, LAYOUT.vMin()), "z for " + facing);
			assertEquals(65, frame.y(0), "rail level is the top of the clicked block");
			assertEquals(facing, frame.forward());
		}
	}

	@Test
	void aWrenchClickMatchesStandingOnTheBlockAndPlanningFromTheFeetMinusTheGap() {
		for (final Heading facing : Heading.values()) {
			final LocalFrame click = LocalFrame.onBlock(3, 70, 7, facing, LAYOUT);
			final LocalFrame feet = LocalFrame.inFrontOf(3 - facing.dx() * LocalFrame.GAP_IN_FRONT, 71, 7 - facing.dz() * LocalFrame.GAP_IN_FRONT,
					facing, LAYOUT);
			assertEquals(feet, click, "for " + facing);
		}
	}
}
