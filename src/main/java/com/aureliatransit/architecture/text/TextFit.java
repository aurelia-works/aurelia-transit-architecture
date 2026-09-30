package com.aureliatransit.architecture.text;

/**
 * Pure text-scaling maths shared by signs, information blocks and their editor previews. "Font units" are the units
 * of {@code TextRenderer.getWidth}; scales are expressed in model pixels per font unit.
 */
public final class TextFit {

	/** Height of one text row in font units (glyph height plus leading). */
	public static final float ROW = 9.0F;

	private TextFit() {
	}

	/**
	 * Largest scale at which one line of {@code widthUnits} fits a box, capped at {@code maxScale}.
	 */
	public static float fitOne(float widthUnits, float boxWidth, float boxHeight, float maxScale) {
		if (boxWidth <= 0 || boxHeight <= 0) {
			return 0;
		}
		float scale = Math.min(maxScale, boxHeight / ROW);
		if (widthUnits > 0) {
			scale = Math.min(scale, boxWidth / widthUnits);
		}
		return scale;
	}

	/**
	 * Largest common scale at which a stack of rows fits a box. Row i is drawn at {@code scale * weights[i]}.
	 */
	public static float fitStack(float[] widths, float[] weights, float boxWidth, float boxHeight, float maxScale) {
		if (boxWidth <= 0 || boxHeight <= 0 || widths.length == 0) {
			return 0;
		}
		float totalWeight = 0;
		float scale = maxScale;
		for (int i = 0; i < widths.length; i++) {
			totalWeight += weights[i];
			if (widths[i] > 0) {
				scale = Math.min(scale, boxWidth / (widths[i] * weights[i]));
			}
		}
		return Math.min(scale, boxHeight / (totalWeight * ROW));
	}

	/**
	 * Black or white text, whichever reads better on the given RGB fill.
	 */
	public static int contrastText(int rgb) {
		final int r = (rgb >> 16) & 0xFF;
		final int g = (rgb >> 8) & 0xFF;
		final int b = rgb & 0xFF;
		final double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
		return luminance > 150 ? 0xFF1B1F23 : 0xFFFFFFFF;
	}
}
