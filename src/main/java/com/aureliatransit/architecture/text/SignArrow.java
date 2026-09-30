package com.aureliatransit.architecture.text;

/**
 * Optional wayfinding arrow on a sign. Arrows pointing left, up or down sit at the left end of the sign, the others at
 * the right end, like real directional signage.
 */
public enum SignArrow {
	NONE(""),
	LEFT("←"),
	RIGHT("→"),
	UP("↑"),
	UP_LEFT("↖"),
	UP_RIGHT("↗"),
	DOWN("↓");

	private final String glyph;

	SignArrow(String glyph) {
		this.glyph = glyph;
	}

	public String glyph() {
		return glyph;
	}

	public boolean onLeft() {
		return this == LEFT || this == UP || this == UP_LEFT || this == DOWN;
	}

	public static SignArrow byOrdinal(int ordinal) {
		final SignArrow[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
