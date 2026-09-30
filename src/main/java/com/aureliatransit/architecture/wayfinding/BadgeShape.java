package com.aureliatransit.architecture.wayfinding;

/**
 * Outline of a line badge.
 */
public enum BadgeShape {
	/** Rounded rectangle (default). */
	ROUNDED,
	/** Circle / disc. */
	CIRCLE,
	/** Square-cornered rectangle. */
	SQUARE;

	public static BadgeShape byOrdinal(int ordinal) {
		final BadgeShape[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ROUNDED;
	}
}
