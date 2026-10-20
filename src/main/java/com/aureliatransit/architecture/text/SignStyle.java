package com.aureliatransit.architecture.text;

/**
 * What a kind of sign can show. The server restricts {@link SignData} to its style, so a station sign never stores route
 * badges and a bus stop sign never stores an arrow.
 */
public enum SignStyle {
	/** Freestanding station name sign: name, secondary line, arrow, platform badge, line colour, auto station name. */
	STATION(true, true, true, true, false, false, 0xFFFFFFFF, 0xFFC9D6E6),
	/** Hanging station name sign. */
	HANGING(true, true, true, true, false, false, 0xFFFFFFFF, 0xFFC9D6E6),
	/** Directional sign: light text on charcoal. */
	DIRECTION(true, true, true, true, false, false, 0xFFF2F2EE, 0xFFB8BCC0),
	/** Platform number plate: a large number with an optional caption. */
	PLATFORM_NUMBER(false, false, true, false, false, true, 0xFFFFFFFF, 0xFFC9D6E6),
	/** Bus stop plate: stop name and route badges. */
	BUS_STOP(false, false, false, false, true, false, 0xFF1E2A33, 0xFF46535E),
	/** Lift status panel (1.4, A8): lift name and the levels it serves; the status bar comes from the block state. */
	LIFT(true, false, false, false, false, false, 0xFFFFFFFF, 0xFFC9D6E6),
	/** Platform screen door text panel (1.4, A17): text typed by hand (destination, door number, notice); amber on black. */
	PSD(true, true, true, false, false, false, 0xFFFFB84D, 0xFFE6D3AE),
	/** Stand-back warning for non-stopping trains (1.4, A16): set by hand, never automatic; dark text on yellow. */
	WARNING(true, false, false, false, false, false, 0xFF161616, 0xFF2B2B2B),
	/** Train composition / coach board (1.4, A18): car labels and sector letters typed by hand. */
	COMPOSITION(true, false, false, false, false, false, 0xFFFFFFFF, 0xFFB8C4D0),
	/** 1.5 Dutch-style station sign: dark blue text on yellow. */
	DUTCH_STATION(true, true, true, true, false, false, 0xFF0B2A6B, 0xFF28477F),
	/** 1.5 Dutch-style platform plate: a large dark blue number on yellow. */
	DUTCH_PLATFORM(false, false, true, false, false, true, 0xFF0B2A6B, 0xFF28477F),
	/** 1.5 German-style station sign: black text on white. */
	GERMAN_STATION(true, true, true, true, false, false, 0xFF141414, 0xFF4A4D52),
	/** Operator station signs: the operator's mark at the left end of the row, the name beside it (white on the operator's blue). */
	OPERATOR_NS(true, true, true, true, false, false, 0xFFFFFFFF, 0xFFC8D8F0),
	/** Operator station sign, white panel, black text. */
	OPERATOR_DB(true, true, true, true, false, false, 0xFF141414, 0xFF4A4D52),
	/** Operator station sign, white on the operator's lighter blue. */
	OPERATOR_GVB(true, true, true, true, false, false, 0xFFFFFFFF, 0xFFD6E6F5),
	/** Operator station sign, London Underground: the roundel's blue on white. */
	OPERATOR_UNDERGROUND(true, true, true, true, false, false, 0xFF000F9F, 0xFF3A4691),
	/** Operator station sign, National Rail: black on white. */
	OPERATOR_NATIONAL_RAIL(true, true, true, true, false, false, 0xFF141414, 0xFF4A4D52),
	/** Operator station sign, S-Bahn: black on white. */
	OPERATOR_SBAHN(true, true, true, true, false, false, 0xFF141414, 0xFF4A4D52),
	/** Operator station sign, SBB: white on Swiss station blue. */
	OPERATOR_SBB(true, true, true, true, false, false, 0xFFFFFFFF, 0xFFCAD6E8),
	/** Operator station sign, JR East: black on white. */
	OPERATOR_JR(true, true, true, true, false, false, 0xFF141414, 0xFF4A4D52);

	public static final int MAX_ROUTES = 4;

	private final boolean secondary;
	private final boolean arrow;
	private final boolean platform;
	private final boolean autoName;
	private final boolean routes;
	private final boolean numberPlate;
	private final int textColor;
	private final int secondaryColor;

	SignStyle(boolean secondary, boolean arrow, boolean platform, boolean autoName, boolean routes, boolean numberPlate, int textColor, int secondaryColor) {
		this.secondary = secondary;
		this.arrow = arrow;
		this.platform = platform;
		this.autoName = autoName;
		this.routes = routes;
		this.numberPlate = numberPlate;
		this.textColor = textColor;
		this.secondaryColor = secondaryColor;
	}

	public boolean hasSecondary() {
		return secondary;
	}

	public boolean hasArrow() {
		return arrow;
	}

	public boolean hasPlatform() {
		return platform;
	}

	public boolean hasAutoName() {
		return autoName;
	}

	public boolean hasRoutes() {
		return routes;
	}

	/** True when the platform field is the sign's main content (drawn large) rather than a small badge. */
	public boolean isNumberPlate() {
		return numberPlate;
	}

	public int textColor() {
		return textColor;
	}

	public int secondaryColor() {
		return secondaryColor;
	}
}
