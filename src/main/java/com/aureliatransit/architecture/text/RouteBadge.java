package com.aureliatransit.architecture.text;

/**
 * A bus route number shown as a coloured badge on a bus stop sign.
 */
public record RouteBadge(String label, AccentPalette color) {

	public static final int MAX_LABEL = 4;

	public RouteBadge {
		label = TextSanitizer.sanitize(label, MAX_LABEL);
		color = color == null ? AccentPalette.NONE : color;
	}
}
