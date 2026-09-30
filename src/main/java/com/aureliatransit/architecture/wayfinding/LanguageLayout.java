package com.aureliatransit.architecture.wayfinding;

/**
 * How a secondary-language name is placed next to the primary one.
 */
public enum LanguageLayout {
	/** Primary language only. */
	SINGLE,
	/** Primary and secondary next to each other (split panel). */
	SIDE_BY_SIDE,
	/** Secondary on a smaller line below the primary. */
	STACKED;

	public static LanguageLayout byOrdinal(int ordinal) {
		final LanguageLayout[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SINGLE;
	}
}
