package com.aureliatransit.architecture.text;

public enum TextAlignment {
	LEFT,
	CENTER,
	RIGHT;

	public static TextAlignment byOrdinal(int ordinal) {
		final TextAlignment[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CENTER;
	}
}
