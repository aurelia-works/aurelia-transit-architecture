package com.aureliatransit.architecture.text;

/**
 * The constrained accent colours used for sign lines, information headers and route badges.
 * Players pick from this list; arbitrary colours are never accepted from packets.
 */
public enum AccentPalette {
	NONE(0xFFFFFF),
	BLUE(0x1F4E8C),
	GREEN(0x2E7D4F),
	YELLOW(0xE0B12A),
	ORANGE(0xD9782D),
	RED(0xB23A3A),
	PURPLE(0x6B4C9A),
	TEAL(0x2A8C8C),
	GREY(0x6E7378);

	private final int rgb;

	AccentPalette(int rgb) {
		this.rgb = rgb;
	}

	public int rgb() {
		return rgb;
	}

	public int argb() {
		return 0xFF000000 | rgb;
	}

	public static AccentPalette byOrdinal(int ordinal) {
		final AccentPalette[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
