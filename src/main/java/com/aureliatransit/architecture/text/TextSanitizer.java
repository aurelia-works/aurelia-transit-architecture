package com.aureliatransit.architecture.text;

import net.minecraft.util.Formatting;

/**
 * The one sanitizer for all player-entered text (signs, information blocks, bus stops, timetables).
 * Strips § formatting codes and control characters, trims, and caps the length (in UTF-16 units, never splitting a
 * surrogate pair, so a supplementary character such as a rare CJK ideograph is dropped whole instead of becoming a
 * broken glyph).
 */
public final class TextSanitizer {

	private TextSanitizer() {
	}

	public static String sanitize(String text, int maxLength) {
		final String stripped = Formatting.strip(text == null ? "" : text);
		final String clean = stripped == null ? "" : stripped.replace('§', ' ').replaceAll("\\p{Cntrl}", "").trim();
		if (clean.length() <= maxLength) {
			return clean;
		}
		final int end = maxLength > 0 && Character.isHighSurrogate(clean.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
		return clean.substring(0, Math.max(0, end));
	}
}
