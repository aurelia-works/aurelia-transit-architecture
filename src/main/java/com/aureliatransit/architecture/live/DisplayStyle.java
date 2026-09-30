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
	DUTCH_MODERN("dutch_modern", 0xFF0B2236, 0xFF12314D, 0xFF061726, 0xFFFFFFFF, 0xFFBCCAD8, 0xFFFFD43B, 0xFFFF6B6B, 0xFF1A4066);

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
