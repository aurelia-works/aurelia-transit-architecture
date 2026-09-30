package com.aureliatransit.architecture.transit;

/**
 * MTR stores multilingual names as "segment|segment". Aurelia displays one Latin-script segment where possible, else
 * the first segment that is not CJK, else the first segment. Minecraft draws Arabic-script text without shaping or
 * right-to-left ordering, so a Latin alternative is preferred over it as well.
 */
public final class StationNames {

	private StationNames() {
	}

	public static String display(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		final String[] segments = raw.split("\\|");
		for (final String segment : segments) {
			if (!segment.isBlank() && segment.codePoints().anyMatch(StationNames::isLatinLetter) && segment.codePoints().noneMatch(StationNames::isOtherScriptLetter)) {
				return segment.trim();
			}
		}
		for (final String segment : segments) {
			if (!segment.isBlank() && segment.codePoints().noneMatch(StationNames::isCjk)) {
				return segment.trim();
			}
		}
		return segments.length == 0 ? "" : segments[0].trim();
	}

	private static boolean isLatinLetter(int codePoint) {
		return Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
	}

	private static boolean isOtherScriptLetter(int codePoint) {
		return Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN;
	}

	private static boolean isCjk(int codePoint) {
		final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
	}
}
