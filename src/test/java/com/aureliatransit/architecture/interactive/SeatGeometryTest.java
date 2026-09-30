package com.aureliatransit.architecture.interactive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatGeometryTest {

	@Test
	void rotationMatchesModelRotation() {
		// North (0,-1) -> east (1,0) -> south (0,1) -> west (-1,0) for clockwise quarter turns.
		assertArrayEquals(new double[]{1, 0}, SeatGeometry.rotate(0, -1, 1), 1e-9);
		assertArrayEquals(new double[]{0, 1}, SeatGeometry.rotate(0, -1, 2), 1e-9);
		assertArrayEquals(new double[]{-1, 0}, SeatGeometry.rotate(0, -1, 3), 1e-9);
		assertArrayEquals(new double[]{0, -1}, SeatGeometry.rotate(0, -1, 4), 1e-9);
	}

	@Test
	void twoSeaterSlotsSitInEachHalfAndRotateWithTheBlock() {
		final double[] leftNorth = SeatGeometry.slotOffset(0, 2, 7.5, 0);
		final double[] rightNorth = SeatGeometry.slotOffset(1, 2, 7.5, 0);
		assertEquals(-0.25, leftNorth[0], 1e-9);
		assertEquals(0.25, rightNorth[0], 1e-9);
		assertEquals(-0.5 / 16, leftNorth[1], 1e-9);

		// Facing east: the model's -X half ends up towards the north, the +X half towards the south.
		final double[] leftEast = SeatGeometry.slotOffset(0, 2, 8, 1);
		final double[] rightEast = SeatGeometry.slotOffset(1, 2, 8, 1);
		assertEquals(-0.25, leftEast[1], 1e-9);
		assertEquals(0.25, rightEast[1], 1e-9);
	}

	@Test
	void singleSeatIsCentred() {
		final double[] offset = SeatGeometry.slotOffset(0, 1, 8, 2);
		assertEquals(0, offset[0], 1e-9);
		assertEquals(0, offset[1], 1e-9);
	}

	@Test
	void clickedHalfSelectsTheSlotInEveryFacing() {
		for (int turns = 0; turns < 4; turns++) {
			for (int slot = 0; slot < 2; slot++) {
				final double[] at = SeatGeometry.slotOffset(slot, 2, 8, turns);
				assertEquals(slot, SeatGeometry.slotForHit(at[0], at[1], 2, turns), "turns=" + turns + " slot=" + slot);
			}
		}
		assertEquals(0, SeatGeometry.slotForHit(0.4, 0.4, 1, 3));
	}

	@Test
	void dismountOffsetsStartInFrontOfTheSitter() {
		// Yaw 0 looks south (+Z); yaw 180 looks north (-Z); yaw 90 looks west (-X).
		assertArrayEquals(new double[]{0, 1}, SeatGeometry.dismountOffsets(0).get(0), 1e-9);
		assertArrayEquals(new double[]{0, -1}, SeatGeometry.dismountOffsets(180).get(0), 1e-9);
		assertArrayEquals(new double[]{-1, 0}, SeatGeometry.dismountOffsets(90).get(0), 1e-9);
		final List<double[]> all = SeatGeometry.dismountOffsets(0);
		assertEquals(8, all.size());
		assertTrue(all.stream().noneMatch(o -> o[0] == 0 && o[1] == 0), "never dismounts into the seat block itself");
	}

	@Test
	void seatEntitySitsBelowTheSeatSurface() {
		assertTrue(SeatGeometry.SEAT_DROP > 0 && SeatGeometry.SEAT_DROP < 0.5);
	}
}
