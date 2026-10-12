package com.aureliatransit.architecture.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The model wrapper turns vertices the same way a blockstate "y" rotation and the block's shapes do. */
class FreePlacementRenderTest {

	/** One vertex at (x, y, z) with a normal pointing north (z = -127). */
	private static int[] vertex(float x, float y, float z) {
		final int[] data = new int[8];
		data[0] = Float.floatToRawIntBits(x);
		data[1] = Float.floatToRawIntBits(y);
		data[2] = Float.floatToRawIntBits(z);
		data[7] = (-127 & 0xFF) << 16;
		return data;
	}

	@Test
	void aQuarterTurnMatchesAClockwiseBlockstateRotation() {
		// a blockstate y=90 turns north to east: the point (x, z) goes to (1 - z, x)
		final int[] turned = FreePlacementRender.moveVertices(vertex(0.2F, 0.3F, 0.1F), 90F, 0, 0);
		assertEquals(0.9F, Float.intBitsToFloat(turned[0]), 1e-5);
		assertEquals(0.3F, Float.intBitsToFloat(turned[1]), 1e-5);
		assertEquals(0.2F, Float.intBitsToFloat(turned[2]), 1e-5);
		// the north normal now points east
		assertEquals(127, (byte) turned[7]);
		assertEquals(0, (byte) (turned[7] >> 16));
	}

	@Test
	void aSixteenthTurnAndANudge() {
		final int[] turned = FreePlacementRender.moveVertices(vertex(0.5F, 0, 0), 22.5F, 0.5, 0);
		// the middle of the north edge swings clockwise towards the east, then moves half a block east
		final double r = Math.toRadians(22.5);
		assertEquals(0.5 + 0.5 * Math.sin(r) + 0.5, Float.intBitsToFloat(turned[0]), 1e-5);
		assertEquals(0.5 - 0.5 * Math.cos(r), Float.intBitsToFloat(turned[2]), 1e-5);
	}
}
