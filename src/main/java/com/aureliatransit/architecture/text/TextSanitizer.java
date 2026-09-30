package com.aureliatransit.architecture.text;

import net.minecraft.util.Formatting;

/**
 * The one sanitizer for all player-entered text (signs, information blocks, bus stops, timetables).
 * Strips § formatting codes and control characters, trims, and caps the length.
 */
public final class TextSanitizer {

	private TextSanitizer() {
	}

	public static String sanitize(String text, int maxLength) {
		final String stripped = Formatting.strip(text == null ? "" : text);
		final String clean = stripped == null ? "" : stripped.replace('§', ' ').replaceAll("\\p{Cntrl}", "").trim();
		return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
	}
}
