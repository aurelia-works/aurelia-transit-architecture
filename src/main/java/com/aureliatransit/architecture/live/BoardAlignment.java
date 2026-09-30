package com.aureliatransit.architecture.live;

/**
 * Vertical anchoring of a board's content inside its screen. Departure boards read top-down, so {@link #TOP} is the
 * default: header and first rows sit under the top bezel and spare height stays at the bottom.
 */
public enum BoardAlignment {
	TOP,
	CENTER;

	public static BoardAlignment byOrdinal(int ordinal) {
		final BoardAlignment[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TOP;
	}
}
