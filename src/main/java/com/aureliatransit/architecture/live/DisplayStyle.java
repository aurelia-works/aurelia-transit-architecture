package com.aureliatransit.architecture.live;

import java.util.Map;

/**
 * The kinds of departure board a display can be. Each is a whole board, not a colour swap: its own layout, lettering and
 * colours, after the boards of one country (see {@code BoardBuilder}). None copies an operator's name, logo or typeface.
 * All colours are ARGB.
 */
public enum DisplayStyle {
	/**
	 * British dot-matrix: amber LED lettering on black. Platform boards show the next train with a scrolling "Calling at"
	 * line, the 2nd and 3rd trains in turn, and a clock with seconds; concourse boards list time, destination, platform
	 * and expected time.
	 */
	UK_DOT_MATRIX("uk_dot_matrix", 0xFF000000, 0xFF000000, 0xFF000000, 0xFFFFA319, 0xFFFFA319, 0xFFFFA319, 0xFFFFA319, 0xFF000000),
	/**
	 * Metro countdown screen: black, line-coloured route badges, destinations and minutes to go.
	 */
	METRO_COUNTDOWN("metro_countdown", 0xFF000000, 0xFF0C0C0C, 0xFF161616, 0xFFFFFFFF, 0xFF9C9C9C, 0xFFFFC61A, 0xFFFF5C4D, 0xFF2B2B2B),
	/**
	 * Dutch platform screen (the dark version): deep blue, white sans lettering, departure time with a red delay, the
	 * destination large with "via" below, the platform number in a white square and the next train along the bottom.
	 */
	DUTCH_NS("dutch_ns", 0xFF0A1F4B, 0xFF0A1F4B, 0xFF0F2A60, 0xFFFFFFFF, 0xFFB7C4DC, 0xFFFFFFFF, 0xFFFF5B4F, 0xFFFFFFFF),
	/**
	 * German platform screen: blue, white sans lettering, time and train on the left with a white delay box, the stops
	 * "via" above a large destination, following trains below and a white scrolling information band.
	 */
	GERMAN_DB("german_db", 0xFF0C2F78, 0xFF0C2F78, 0xFF0C2F78, 0xFFFFFFFF, 0xFFC4D1EC, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);

	/**
	 * Ids of older styles and the board each now shows. The first three styles were saved by ordinal in 1.4 and 1.5 and keep
	 * their places; the 1.6 test presets were never released.
	 */
	private static final Map<String, DisplayStyle> FORMER = Map.of("european_amber", UK_DOT_MATRIX, "european_modern", METRO_COUNTDOWN,
			"dutch_modern", DUTCH_NS, "uk_lcd", UK_DOT_MATRIX, "county_commuter", UK_DOT_MATRIX, "german_mainline", GERMAN_DB,
			"hamburg_metro", METRO_COUNTDOWN, "european_light", GERMAN_DB, "philadelphia_flap", METRO_COUNTDOWN, "new_york_countdown", METRO_COUNTDOWN);

	/** Ordinal numbering of trains on British boards: "1st", "2nd", "3rd", "4th". */
	public static String ordinal(int n) {
		return n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		});
	}

	private final String id;
	private final int background;
	private final int rowBand;
	private final int header;
	private final int text;
	private final int dim;
	private final int accent;
	private final int delay;
	private final int chip;

	DisplayStyle(String id, int background, int rowBand, int header, int text, int dim, int accent, int delay, int chip) {
		this.id = id;
		this.background = background;
		this.rowBand = rowBand;
		this.header = header;
		this.text = text;
		this.dim = dim;
		this.accent = accent;
		this.delay = delay;
		this.chip = chip;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "live.style." + id;
	}

	public int background() {
		return background;
	}

	public int rowBand() {
		return rowBand;
	}

	public int header() {
		return header;
	}

	public int text() {
		return text;
	}

	public int dim() {
		return dim;
	}

	/** Colour for times and the "Due" / "Boarding" state. */
	public int accent() {
		return accent;
	}

	public int delay() {
		return delay;
	}

	/** Platform number field. */
	public int chip() {
		return chip;
	}

	public DisplayStyle next() {
		final DisplayStyle[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	/** The style saved as {@code ordinal}; anything unknown shows as the metro countdown. */
	public static DisplayStyle byOrdinal(int ordinal) {
		final DisplayStyle[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : METRO_COUNTDOWN;
	}

	/** The style with this id, or the board an older style id now shows; null for an unknown id. */
	public static DisplayStyle byId(String id) {
		for (final DisplayStyle style : values()) {
			if (style.id.equals(id)) {
				return style;
			}
		}
		return FORMER.get(id);
	}
}
