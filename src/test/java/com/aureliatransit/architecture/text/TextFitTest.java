package com.aureliatransit.architecture.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFitTest {

	@Test
	void singleLineIsLimitedByWidthOrHeight() {
		// Plenty of width: limited by height (8 px / 9 units).
		assertEquals(8F / 9F, TextFit.fitOne(10, 100, 8, 10), 1e-5);
		// Narrow box: limited by width.
		assertEquals(0.05F, TextFit.fitOne(100, 5, 8, 10), 1e-5);
		// Capped.
		assertEquals(0.5F, TextFit.fitOne(1, 100, 100, 0.5F), 1e-5);
	}

	@Test
	void degenerateBoxesGiveZero() {
		assertEquals(0, TextFit.fitOne(10, 0, 8, 1));
		assertEquals(0, TextFit.fitStack(new float[]{}, new float[]{}, 10, 10, 1));
		assertEquals(0, TextFit.fitStack(new float[]{5}, new float[]{1}, 10, 0, 1));
	}

	@Test
	void stackFitsTheTallestConstraint() {
		final float[] widths = {60, 30, 30};
		final float[] weights = {1, 1, 1};
		final float scale = TextFit.fitStack(widths, weights, 12, 30, 1);
		// Width of the first row limits: 12 / 60.
		assertEquals(0.2F, scale, 1e-5);
		// Height limits when the rows are short: 3 rows * 9 units * s <= 10.
		assertEquals(10F / 27F, TextFit.fitStack(new float[]{1, 1, 1}, weights, 100, 10, 5), 1e-5);
	}

	@Test
	void weightsScaleRowsRelativelyAndStillFit() {
		final float[] widths = {40, 40};
		final float[] weights = {2, 1};
		final float scale = TextFit.fitStack(widths, weights, 40, 100, 5);
		// The weight-2 row needs 40*2*s <= 40.
		assertEquals(0.5F, scale, 1e-5);
		assertTrue(scale * 2 * 40 <= 40.0001F);
	}

	@Test
	void contrastPicksReadableTextColour() {
		assertEquals(0xFFFFFFFF, TextFit.contrastText(AccentPalette.BLUE.rgb()));
		assertEquals(0xFF1B1F23, TextFit.contrastText(AccentPalette.YELLOW.rgb()));
		assertEquals(0xFF1B1F23, TextFit.contrastText(0xFFFFFF));
	}
}
