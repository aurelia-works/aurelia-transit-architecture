package com.aureliatransit.architecture.live;

/**
 * Colour presets for the live displays. All colours are ARGB. None of these copy a real operator's scheme.
 */
public enum DisplayStyle {
	/**
	 * Black background, amber dot-matrix style text.
	 */
	EUROPEAN_AMBER("european_amber", 0xFF0A0906, 0xFF15110A, 0xFF231A08, 0xFFFFB000, 0xFFB87A00, 0xFFFFD25A, 0xFFFF6A1F, 0xFF1A1407),
	/**
	 * Charcoal background, white text, a restrained teal accent.
	 */
	EUROPEAN_MODERN("european_modern", 0xFF16191D, 0xFF1E2227, 0xFF0F1215, 0xFFF2F2EE, 0xFF9AA0A6, 0xFF4FB8B0, 0xFFFF9A6B, 0xFF272C32),
	/**
	 * Deep navy with high-contrast white and a clear yellow for times: maximum legibility.
	 */
	DUTCH_MODERN("dutch_modern", 0xFF0B2236, 0xFF12314D, 0xFF061726, 0xFFFFFFFF, 0xFFBCCAD8, 0xFFFFD43B, 0xFFFF6B6B, 0xFF1A4066),
	// 1.6 presets. Each takes only the general feel of a kind of board (colours, row bands, capitals, numbered rows), never a
	// real operator's name, logo, typeface or exact scheme.
	/**
	 * British dot-matrix: black, orange-amber text, no row bands, rows numbered 1st, 2nd, 3rd.
	 */
	UK_DOT_MATRIX("uk_dot_matrix", 0xFF050403, 0xFF050403, 0xFF0C0904, 0xFFFF9E1B, 0xFFB86F10, 0xFFFFB84D, 0xFFFF9E1B, 0xFF1A1206, false, false, Numbering.ORDINAL),
	/**
	 * British colour screen: black, white destinations, yellow-orange times, numbered rows.
	 */
	UK_LCD("uk_lcd", 0xFF000000, 0xFF0D0D0D, 0xFF141414, 0xFFFFFFFF, 0xFFB5B5B5, 0xFFFFB81C, 0xFFFF5A3C, 0xFF262626, false, true, Numbering.ORDINAL),
	/**
	 * Commuter railway screen in the style of popular train games: dark navy, white text, yellow times, numbered rows.
	 */
	COUNTY_COMMUTER("county_commuter", 0xFF0E1A2B, 0xFF14243A, 0xFF1F3A5F, 0xFFFFFFFF, 0xFF9FB3CC, 0xFFFFD23F, 0xFFFF6B5B, 0xFF26405F, false, true, Numbering.ORDINAL),
	/**
	 * German main-line platform screen: royal blue, white text, pale yellow for times.
	 */
	GERMAN_MAINLINE("german_mainline", 0xFF14316E, 0xFF1A3A7E, 0xFF0D2254, 0xFFFFFFFF, 0xFFB8C7E6, 0xFFFFE680, 0xFFFF7A7A, 0xFF27488E),
	/**
	 * Hamburg-style metro screen: near-black, white text, an orange accent.
	 */
	HAMBURG_METRO("hamburg_metro", 0xFF121212, 0xFF1B1B1B, 0xFF262626, 0xFFF5F5F5, 0xFFA0A0A0, 0xFFFF8C1A, 0xFFFF5252, 0xFF2E2E2E),
	/**
	 * Wider European screen: light grey, dark text, a blue accent.
	 */
	EUROPEAN_LIGHT("european_light", 0xFFE9ECEF, 0xFFDDE2E6, 0xFF2B3A4A, 0xFF15202B, 0xFF5B6B7A, 0xFF0B5FA5, 0xFFC62828, 0xFFC9D1D8),
	/**
	 * Philadelphia-style split-flap board: black flaps, white capitals, amber for times.
	 */
	PHILADELPHIA_FLAP("philadelphia_flap", 0xFF0B0B0B, 0xFF1C1C1C, 0xFF151515, 0xFFF4F4F0, 0xFFA8A8A0, 0xFFFFC94D, 0xFFFF6E40, 0xFF2A2A2A, true, true, Numbering.NONE),
	/**
	 * New York-style countdown screen: black, white capitals, green minutes, numbered rows.
	 */
	NEW_YORK_COUNTDOWN("new_york_countdown", 0xFF000000, 0xFF000000, 0xFF101010, 0xFFFFFFFF, 0xFFBDBDBD, 0xFF2ECC71, 0xFFFF5A5A, 0xFF1F1F1F, true, false, Numbering.NUMBER);

	/** How the rows of a platform screen are numbered. */
	public enum Numbering {
		NONE, ORDINAL, NUMBER;

		/** The prefix for row {@code n} (1-based): "1st ", "2nd ", or "1 "; empty for {@link #NONE}. */
		public String prefix(int n) {
			return switch (this) {
				case NONE -> "";
				case NUMBER -> n + " ";
				case ORDINAL -> n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
					case 1 -> "st";
					case 2 -> "nd";
					case 3 -> "rd";
					default -> "th";
				}) + " ";
			};
		}
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
	private final boolean uppercase;
	private final boolean banded;
	private final Numbering numbering;

	DisplayStyle(String id, int background, int rowBand, int header, int text, int dim, int accent, int delay, int chip) {
		this(id, background, rowBand, header, text, dim, accent, delay, chip, false, true, Numbering.NONE);
	}

	DisplayStyle(String id, int background, int rowBand, int header, int text, int dim, int accent, int delay, int chip, boolean uppercase, boolean banded,
				 Numbering numbering) {
		this.id = id;
		this.background = background;
		this.rowBand = rowBand;
		this.header = header;
		this.text = text;
		this.dim = dim;
		this.accent = accent;
		this.delay = delay;
		this.chip = chip;
		this.uppercase = uppercase;
		this.banded = banded;
		this.numbering = numbering;
	}

	/** Destinations, titles and times in capitals. */
	public boolean uppercase() {
		return uppercase;
	}

	/** Alternate rows on a slightly lighter band. */
	public boolean banded() {
		return banded;
	}

	/** Numbering of the rows on platform screens. */
	public Numbering numbering() {
		return numbering;
	}

	/** {@code text} as this style shows it (in capitals for {@link #uppercase()} styles). */
	public String cased(String text) {
		return uppercase ? text.toUpperCase(java.util.Locale.ROOT) : text;
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

	/**
	 * Colour for times and the "Due" / "Boarding" state.
	 */
	public int accent() {
		return accent;
	}

	public int delay() {
		return delay;
	}

	/**
	 * Neutral chip colour (platform number chip).
	 */
	public int chip() {
		return chip;
	}

	public DisplayStyle next() {
		final DisplayStyle[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static DisplayStyle byOrdinal(int ordinal) {
		final DisplayStyle[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : EUROPEAN_MODERN;
	}
}
