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
	BUS_STOP(false, false, false, false, true, false, 0xFF1E2A33, 0xFF46535E);

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
